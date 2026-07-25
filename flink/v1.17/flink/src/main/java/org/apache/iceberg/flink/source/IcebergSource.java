/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.flink.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import org.apache.flink.annotation.Experimental;
import org.apache.flink.api.connector.source.Boundedness;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.data.RowData;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.BaseMetadataTable;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.flink.FlinkConfigOptions;
import org.apache.iceberg.flink.FlinkReadOptions;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.source.assigner.OrderedSplitAssignerFactory;
import org.apache.iceberg.flink.source.assigner.SimpleSplitAssignerFactory;
import org.apache.iceberg.flink.source.assigner.SplitAssigner;
import org.apache.iceberg.flink.source.assigner.SplitAssignerFactory;
import org.apache.iceberg.flink.source.enumerator.ContinuousIcebergEnumerator;
import org.apache.iceberg.flink.source.enumerator.ContinuousSplitPlanner;
import org.apache.iceberg.flink.source.enumerator.ContinuousSplitPlannerImpl;
import org.apache.iceberg.flink.source.enumerator.IcebergEnumeratorState;
import org.apache.iceberg.flink.source.enumerator.IcebergEnumeratorStateSerializer;
import org.apache.iceberg.flink.source.enumerator.StaticIcebergEnumerator;
import org.apache.iceberg.flink.source.reader.IcebergSourceReader;
import org.apache.iceberg.flink.source.reader.IcebergSourceReaderMetrics;
import org.apache.iceberg.flink.source.reader.MetaDataReaderFunction;
import org.apache.iceberg.flink.source.reader.ReaderFunction;
import org.apache.iceberg.flink.source.reader.RowDataReaderFunction;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitSerializer;
import org.apache.iceberg.flink.source.split.SerializableComparator;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Flink FLIP-27 的 Iceberg source 实现。
 *
 * <p>所属模块：iceberg-flink（source 侧），实现 Flink {@link Source} 接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link SourceReader}（{@link IcebergSourceReader}）与 {@link SplitEnumerator}（静态/连续）。
 *   <li>根据是否流式决定 Boundedness，批量模式在枚举器启动时一次性规划 split。
 *   <li>提供 split 与 enumerator 状态的序列化器。
 * </ul>
 *
 * <p>设计意图：通过 {@link Builder} 收集表加载、扫描上下文、reader 函数、assigner 工厂等众多参数； 懒加载 table 避免重复打开。标记 {@link
 * Experimental} 表示 API 尚不稳定。
 *
 * <p>上下游关系：被 Flink 作业作为数据源；下游创建 reader/enumerator/assigner 等组件。
 *
 * @param <T> 输出记录类型
 */
@Experimental
public class IcebergSource<T> implements Source<T, IcebergSourceSplit, IcebergEnumeratorState> {
  private static final Logger LOG = LoggerFactory.getLogger(IcebergSource.class);

  private final TableLoader tableLoader;
  private final ScanContext scanContext;
  private final ReaderFunction<T> readerFunction;
  private final SplitAssignerFactory assignerFactory;
  private final SerializableComparator<IcebergSourceSplit> splitComparator;

  // Can't use SerializableTable as enumerator needs a regular table
  // that can discover table changes
  private transient Table table;

  /**
   * 构造 IcebergSource。
   *
   * @param tableLoader 表加载器
   * @param scanContext 扫描上下文
   * @param readerFunction reader 函数
   * @param assignerFactory assigner 工厂
   * @param splitComparator split 比较器
   * @param table 表实例
   */
  IcebergSource(
      TableLoader tableLoader,
      ScanContext scanContext,
      ReaderFunction<T> readerFunction,
      SplitAssignerFactory assignerFactory,
      SerializableComparator<IcebergSourceSplit> splitComparator,
      Table table) {
    this.tableLoader = tableLoader;
    this.scanContext = scanContext;
    this.readerFunction = readerFunction;
    this.assignerFactory = assignerFactory;
    this.splitComparator = splitComparator;
    this.table = table;
  }

  /** 返回 source 名称。 */
  String name() {
    return "IcebergSource-" + lazyTable().name();
  }

  /** 生成规划用线程池名（表名+UUID，保证唯一）。 */
  private String planningThreadName() {
    // Ideally, operatorId should be used as the threadPoolName as Flink guarantees its uniqueness
    // within a job. SplitEnumeratorContext doesn't expose the OperatorCoordinator.Context, which
    // would contain the OperatorID. Need to discuss with Flink community whether it is ok to expose
    // a public API like the protected method "OperatorCoordinator.Context getCoordinatorContext()"
    // from SourceCoordinatorContext implementation. For now, <table name>-<random UUID> is used as
    // the unique thread pool name.
    return lazyTable().name() + "-" + UUID.randomUUID();
  }

  /**
   * 批量模式下一次性规划所有 split。
   *
   * <p>逻辑：创建工作线程池，调用 FlinkSplitPlanner 规划 split，结束后关闭线程池。
   */
  private List<IcebergSourceSplit> planSplitsForBatch(String threadName) {
    ExecutorService workerPool =
        ThreadPools.newWorkerPool(threadName, scanContext.planParallelism());
    try {
      List<IcebergSourceSplit> splits =
          FlinkSplitPlanner.planIcebergSourceSplits(lazyTable(), scanContext, workerPool);
      LOG.info(
          "Discovered {} splits from table {} during job initialization",
          splits.size(),
          lazyTable().name());
      return splits;
    } finally {
      workerPool.shutdown();
    }
  }

  /** 懒加载并缓存表实例。 */
  private Table lazyTable() {
    if (table == null) {
      tableLoader.open();
      try (TableLoader loader = tableLoader) {
        this.table = loader.loadTable();
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to close table loader", e);
      }
    }

    return table;
  }

  /** 根据是否流式返回有界/无界。 */
  @Override
  public Boundedness getBoundedness() {
    return scanContext.isStreaming() ? Boundedness.CONTINUOUS_UNBOUNDED : Boundedness.BOUNDED;
  }

  /** 创建 {@link IcebergSourceReader}。 */
  @Override
  public SourceReader<T, IcebergSourceSplit> createReader(SourceReaderContext readerContext) {
    IcebergSourceReaderMetrics metrics =
        new IcebergSourceReaderMetrics(readerContext.metricGroup(), lazyTable().name());
    return new IcebergSourceReader<>(metrics, readerFunction, splitComparator, readerContext);
  }

  /** 创建无状态的枚举器。 */
  @Override
  public SplitEnumerator<IcebergSourceSplit, IcebergEnumeratorState> createEnumerator(
      SplitEnumeratorContext<IcebergSourceSplit> enumContext) {
    return createEnumerator(enumContext, null);
  }

  /** 从状态恢复枚举器。 */
  @Override
  public SplitEnumerator<IcebergSourceSplit, IcebergEnumeratorState> restoreEnumerator(
      SplitEnumeratorContext<IcebergSourceSplit> enumContext, IcebergEnumeratorState enumState) {
    return createEnumerator(enumContext, enumState);
  }

  /** 返回 split 的版本化序列化器。 */
  @Override
  public SimpleVersionedSerializer<IcebergSourceSplit> getSplitSerializer() {
    return new IcebergSourceSplitSerializer(scanContext.caseSensitive());
  }

  /** 返回枚举器状态的版本化序列化器。 */
  @Override
  public SimpleVersionedSerializer<IcebergEnumeratorState> getEnumeratorCheckpointSerializer() {
    return new IcebergEnumeratorStateSerializer(scanContext.caseSensitive());
  }

  /**
   * 创建枚举器的内部实现。
   *
   * <p>逻辑：依据是否有状态创建 assigner；流式模式创建 {@link ContinuousIcebergEnumerator}， 批量模式一次性规划 split 并创建 {@link
   * StaticIcebergEnumerator}。
   */
  private SplitEnumerator<IcebergSourceSplit, IcebergEnumeratorState> createEnumerator(
      SplitEnumeratorContext<IcebergSourceSplit> enumContext,
      @Nullable IcebergEnumeratorState enumState) {
    SplitAssigner assigner;
    if (enumState == null) {
      assigner = assignerFactory.createAssigner();
    } else {
      LOG.info(
          "Iceberg source restored {} splits from state for table {}",
          enumState.pendingSplits().size(),
          lazyTable().name());
      assigner = assignerFactory.createAssigner(enumState.pendingSplits());
    }

    if (scanContext.isStreaming()) {
      ContinuousSplitPlanner splitPlanner =
          new ContinuousSplitPlannerImpl(tableLoader.clone(), scanContext, planningThreadName());
      return new ContinuousIcebergEnumerator(
          enumContext, assigner, scanContext, splitPlanner, enumState);
    } else {
      List<IcebergSourceSplit> splits = planSplitsForBatch(planningThreadName());
      assigner.onDiscoveredSplits(splits);
      return new StaticIcebergEnumerator(enumContext, assigner);
    }
  }

  /** 创建通用 Builder。 */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /** 创建输出 RowData 的 Builder。 */
  public static Builder<RowData> forRowData() {
    return new Builder<>();
  }

  /** {@link IcebergSource} 的构建器，收集表加载、扫描选项、reader 函数、assigner、投影/过滤等参数。 */
  public static class Builder<T> {
    private TableLoader tableLoader;
    private Table table;
    private SplitAssignerFactory splitAssignerFactory;
    private SerializableComparator<IcebergSourceSplit> splitComparator;
    private ReaderFunction<T> readerFunction;
    private ReadableConfig flinkConfig = new Configuration();
    private final ScanContext.Builder contextBuilder = ScanContext.builder();
    private TableSchema projectedFlinkSchema;
    private Boolean exposeLocality;

    private final Map<String, String> readOptions = Maps.newHashMap();

    Builder() {}

    /** 设置表加载器。 */
    public Builder<T> tableLoader(TableLoader loader) {
      this.tableLoader = loader;
      return this;
    }

    /** 直接设置表实例（避免重复加载）。 */
    public Builder<T> table(Table newTable) {
      this.table = newTable;
      return this;
    }

    /** 设置 split assigner 工厂。 */
    public Builder<T> assignerFactory(SplitAssignerFactory assignerFactory) {
      this.splitAssignerFactory = assignerFactory;
      return this;
    }

    /** 设置 split 排序比较器。 */
    public Builder<T> splitComparator(
        SerializableComparator<IcebergSourceSplit> newSplitComparator) {
      this.splitComparator = newSplitComparator;
      return this;
    }

    /** 设置自定义 reader 函数。 */
    public Builder<T> readerFunction(ReaderFunction<T> newReaderFunction) {
      this.readerFunction = newReaderFunction;
      return this;
    }

    /** 设置 Flink 配置。 */
    public Builder<T> flinkConfig(ReadableConfig config) {
      this.flinkConfig = config;
      return this;
    }

    /** 设置是否大小写敏感。 */
    public Builder<T> caseSensitive(boolean newCaseSensitive) {
      readOptions.put(FlinkReadOptions.CASE_SENSITIVE, Boolean.toString(newCaseSensitive));
      return this;
    }

    /** 设置读取的快照 id。 */
    public Builder<T> useSnapshotId(Long newSnapshotId) {
      if (newSnapshotId != null) {
        readOptions.put(FlinkReadOptions.SNAPSHOT_ID.key(), Long.toString(newSnapshotId));
      }
      return this;
    }

    /** 设置流式起始策略。 */
    public Builder<T> streamingStartingStrategy(StreamingStartingStrategy newStartingStrategy) {
      readOptions.put(FlinkReadOptions.STARTING_STRATEGY, newStartingStrategy.name());
      return this;
    }

    /** 设置流式起始快照时间戳。 */
    public Builder<T> startSnapshotTimestamp(Long newStartSnapshotTimestamp) {
      if (newStartSnapshotTimestamp != null) {
        readOptions.put(
            FlinkReadOptions.START_SNAPSHOT_TIMESTAMP.key(),
            Long.toString(newStartSnapshotTimestamp));
      }
      return this;
    }

    /** 设置流式起始快照 id。 */
    public Builder<T> startSnapshotId(Long newStartSnapshotId) {
      if (newStartSnapshotId != null) {
        readOptions.put(
            FlinkReadOptions.START_SNAPSHOT_ID.key(), Long.toString(newStartSnapshotId));
      }
      return this;
    }

    /** 设置读取的 tag。 */
    public Builder<T> tag(String tag) {
      readOptions.put(FlinkReadOptions.TAG.key(), tag);
      return this;
    }

    /** 设置读取的分支。 */
    public Builder<T> branch(String branch) {
      readOptions.put(FlinkReadOptions.BRANCH.key(), branch);
      return this;
    }

    /** 设置流式起始 tag。 */
    public Builder<T> startTag(String startTag) {
      readOptions.put(FlinkReadOptions.START_TAG.key(), startTag);
      return this;
    }

    /** 设置流式结束 tag。 */
    public Builder<T> endTag(String endTag) {
      readOptions.put(FlinkReadOptions.END_TAG.key(), endTag);
      return this;
    }

    /** 设置流式结束快照 id。 */
    public Builder<T> endSnapshotId(Long newEndSnapshotId) {
      if (newEndSnapshotId != null) {
        readOptions.put(FlinkReadOptions.END_SNAPSHOT_ID.key(), Long.toString(newEndSnapshotId));
      }
      return this;
    }

    /** 设置按时间戳读取快照。 */
    public Builder<T> asOfTimestamp(Long newAsOfTimestamp) {
      if (newAsOfTimestamp != null) {
        readOptions.put(FlinkReadOptions.AS_OF_TIMESTAMP.key(), Long.toString(newAsOfTimestamp));
      }
      return this;
    }

    /** 设置单个 split 目标大小。 */
    public Builder<T> splitSize(Long newSplitSize) {
      if (newSplitSize != null) {
        readOptions.put(FlinkReadOptions.SPLIT_SIZE, Long.toString(newSplitSize));
      }
      return this;
    }

    /** 设置 split 合并回看文件数。 */
    public Builder<T> splitLookback(Integer newSplitLookback) {
      if (newSplitLookback != null) {
        readOptions.put(FlinkReadOptions.SPLIT_LOOKBACK, Integer.toString(newSplitLookback));
      }
      return this;
    }

    /** 设置 split 文件打开成本。 */
    public Builder<T> splitOpenFileCost(Long newSplitOpenFileCost) {
      if (newSplitOpenFileCost != null) {
        readOptions.put(FlinkReadOptions.SPLIT_FILE_OPEN_COST, Long.toString(newSplitOpenFileCost));
      }

      return this;
    }

    /** 设置是否流式读取。 */
    public Builder<T> streaming(boolean streaming) {
      readOptions.put(FlinkReadOptions.STREAMING, Boolean.toString(streaming));
      return this;
    }

    /** 设置流式监控间隔。 */
    public Builder<T> monitorInterval(Duration newMonitorInterval) {
      if (newMonitorInterval != null) {
        readOptions.put(FlinkReadOptions.MONITOR_INTERVAL, newMonitorInterval.toNanos() + " ns");
      }
      return this;
    }

    /** 设置字段名映射。 */
    public Builder<T> nameMapping(String newNameMapping) {
      readOptions.put(TableProperties.DEFAULT_NAME_MAPPING, newNameMapping);
      return this;
    }

    /** 设置 Iceberg schema 投影。 */
    public Builder<T> project(Schema newProjectedSchema) {
      this.contextBuilder.project(newProjectedSchema);
      return this;
    }

    /** 设置 Flink TableSchema 投影。 */
    public Builder<T> project(TableSchema newProjectedFlinkSchema) {
      this.projectedFlinkSchema = newProjectedFlinkSchema;
      return this;
    }

    /** 设置过滤表达式。 */
    public Builder<T> filters(List<Expression> newFilters) {
      this.contextBuilder.filters(newFilters);
      return this;
    }

    /** 设置读取记录数上限。 */
    public Builder<T> limit(Long newLimit) {
      if (newLimit != null) {
        readOptions.put(FlinkReadOptions.LIMIT, Long.toString(newLimit));
      }
      return this;
    }

    /** 设置是否包含列统计。 */
    public Builder<T> includeColumnStats(boolean newIncludeColumnStats) {
      readOptions.put(
          FlinkReadOptions.INCLUDE_COLUMN_STATS, Boolean.toString(newIncludeColumnStats));
      return this;
    }

    /** 设置规划并行度（工作线程池大小）。 */
    public Builder<T> planParallelism(int planParallelism) {
      readOptions.put(
          FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key(),
          Integer.toString(planParallelism));
      return this;
    }

    /** 设置是否暴露 split 本地性信息。 */
    public Builder<T> exposeLocality(boolean newExposeLocality) {
      this.exposeLocality = newExposeLocality;
      return this;
    }

    /** 设置规划阶段允许的最大失败次数。 */
    public Builder<T> maxAllowedPlanningFailures(int maxAllowedPlanningFailures) {
      readOptions.put(
          FlinkReadOptions.MAX_ALLOWED_PLANNING_FAILURES_OPTION.key(),
          Integer.toString(maxAllowedPlanningFailures));
      return this;
    }

    /** 设置单个读取属性，支持的属性见 {@link FlinkReadOptions}。 */
    public Builder<T> set(String property, String value) {
      readOptions.put(property, value);
      return this;
    }

    /** 批量设置读取属性，支持的属性见 {@link FlinkReadOptions}。 */
    public Builder<T> setAll(Map<String, String> properties) {
      readOptions.putAll(properties);
      return this;
    }

    /**
     * 已废弃，请改用 {@link #setAll}。
     *
     * @deprecated 请改用 {@link #setAll}。
     */
    @Deprecated
    public Builder<T> properties(Map<String, String> properties) {
      readOptions.putAll(properties);
      return this;
    }

    /**
     * 构建 IcebergSource。
     *
     * <p>逻辑：若未提供表则通过 tableLoader 加载；解析配置与投影；未提供 reader 函数时按表类型 选择 MetaDataReaderFunction 或
     * RowDataReaderFunction；未提供 assigner 工厂时按是否有比较器 选择 Simple/Ordered 工厂；最后校验必填项并构造 IcebergSource。
     *
     * @return IcebergSource 实例
     */
    public IcebergSource<T> build() {
      if (table == null) {
        try (TableLoader loader = tableLoader) {
          loader.open();
          this.table = tableLoader.loadTable();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      contextBuilder.resolveConfig(table, readOptions, flinkConfig);

      Schema icebergSchema = table.schema();
      if (projectedFlinkSchema != null) {
        contextBuilder.project(FlinkSchemaUtil.convert(icebergSchema, projectedFlinkSchema));
      }

      ScanContext context = contextBuilder.build();
      if (readerFunction == null) {
        if (table instanceof BaseMetadataTable) {
          MetaDataReaderFunction rowDataReaderFunction =
              new MetaDataReaderFunction(
                  flinkConfig, table.schema(), context.project(), table.io(), table.encryption());
          this.readerFunction = (ReaderFunction<T>) rowDataReaderFunction;
        } else {
          RowDataReaderFunction rowDataReaderFunction =
              new RowDataReaderFunction(
                  flinkConfig,
                  table.schema(),
                  context.project(),
                  context.nameMapping(),
                  context.caseSensitive(),
                  table.io(),
                  table.encryption(),
                  context.filters());
          this.readerFunction = (ReaderFunction<T>) rowDataReaderFunction;
        }
      }

      if (splitAssignerFactory == null) {
        if (splitComparator == null) {
          splitAssignerFactory = new SimpleSplitAssignerFactory();
        } else {
          splitAssignerFactory = new OrderedSplitAssignerFactory(splitComparator);
        }
      }

      checkRequired();
      // Since builder already load the table, pass it to the source to avoid double loading
      return new IcebergSource<T>(
          tableLoader, context, readerFunction, splitAssignerFactory, splitComparator, table);
    }

    private void checkRequired() {
      Preconditions.checkNotNull(tableLoader, "tableLoader is required.");
      Preconditions.checkNotNull(splitAssignerFactory, "assignerFactory is required.");
      Preconditions.checkNotNull(readerFunction, "readerFunction is required.");
    }
  }
}
