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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.ProviderContext;
import org.apache.flink.table.connector.source.DataStreamScanProvider;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.connector.source.ScanTableSource;
import org.apache.flink.table.connector.source.abilities.SupportsFilterPushDown;
import org.apache.flink.table.connector.source.abilities.SupportsLimitPushDown;
import org.apache.flink.table.connector.source.abilities.SupportsProjectionPushDown;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.expressions.ResolvedExpression;
import org.apache.flink.table.types.DataType;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.flink.FlinkConfigOptions;
import org.apache.iceberg.flink.FlinkFilters;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.source.assigner.SplitAssignerType;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：Flink 表 API 中 Iceberg 表的 source 实现。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link ScanTableSource} 与投影、过滤、Limit 下推接口。
 *   <li>根据配置选择使用 legacy {@link FlinkSource} 或 FLIP-27 {@link IcebergSource}。
 *   <li>把 Flink 的过滤表达式转换为 Iceberg {@link Expression}。
 * </ul>
 *
 * <p>设计意图：作为 Flink DynamicTableSource 的 Iceberg 适配器， 把表 schema、属性与下推信息聚合后委托给具体 source 实现。
 *
 * <p>上下游关系：上游为 Flink Table Planner（调用 apply* 方法）， 下游为 {@link FlinkSource} 或 {@link IcebergSource}。
 */
@Internal
public class IcebergTableSource
    implements ScanTableSource,
        SupportsProjectionPushDown,
        SupportsFilterPushDown,
        SupportsLimitPushDown {

  private int[] projectedFields;
  private Long limit;
  private List<Expression> filters;

  private final TableLoader loader;
  private final TableSchema schema;
  private final Map<String, String> properties;
  private final boolean isLimitPushDown;
  private final ReadableConfig readableConfig;

  /** 拷贝构造，用于 {@link #copy()}。 */
  private IcebergTableSource(IcebergTableSource toCopy) {
    this.loader = toCopy.loader;
    this.schema = toCopy.schema;
    this.properties = toCopy.properties;
    this.projectedFields = toCopy.projectedFields;
    this.isLimitPushDown = toCopy.isLimitPushDown;
    this.limit = toCopy.limit;
    this.filters = toCopy.filters;
    this.readableConfig = toCopy.readableConfig;
  }

  /** 构造 IcebergTableSource，无投影、无 Limit、无过滤。 */
  public IcebergTableSource(
      TableLoader loader,
      TableSchema schema,
      Map<String, String> properties,
      ReadableConfig readableConfig) {
    this(loader, schema, properties, null, false, null, ImmutableList.of(), readableConfig);
  }

  /** 全参构造，仅供内部使用。 */
  private IcebergTableSource(
      TableLoader loader,
      TableSchema schema,
      Map<String, String> properties,
      int[] projectedFields,
      boolean isLimitPushDown,
      Long limit,
      List<Expression> filters,
      ReadableConfig readableConfig) {
    this.loader = loader;
    this.schema = schema;
    this.properties = properties;
    this.projectedFields = projectedFields;
    this.isLimitPushDown = isLimitPushDown;
    this.limit = limit;
    this.filters = filters;
    this.readableConfig = readableConfig;
  }

  /**
   * 应用投影下推，仅支持顶层字段投影。
   *
   * @param projectFields 投影字段索引数组
   */
  @Override
  public void applyProjection(int[][] projectFields) {
    this.projectedFields = new int[projectFields.length];
    for (int i = 0; i < projectFields.length; i++) {
      Preconditions.checkArgument(
          projectFields[i].length == 1, "Don't support nested projection in iceberg source now.");
      this.projectedFields[i] = projectFields[i][0];
    }
  }

  /**
   * 创建 legacy {@link FlinkSource} 的 DataStream。
   *
   * <p>逻辑：用 builder 链式调用配置 env、loader、属性、投影、limit、过滤等。
   *
   * @param execEnv 流执行环境
   * @return 产生 RowData 的 DataStream
   */
  private DataStream<RowData> createDataStream(StreamExecutionEnvironment execEnv) {
    return FlinkSource.forRowData()
        .env(execEnv)
        .tableLoader(loader)
        .properties(properties)
        .project(getProjectedSchema())
        .limit(limit)
        .filters(filters)
        .flinkConf(readableConfig)
        .build();
  }

  /**
   * 创建 FLIP-27 {@link IcebergSource} 的 DataStreamSource。
   *
   * <p>逻辑：按配置选择 split assigner 工厂，构造 IcebergSource 后通过 env.fromSource 注册。
   *
   * @param env 流执行环境
   * @return DataStreamSource
   */
  private DataStreamSource<RowData> createFLIP27Stream(StreamExecutionEnvironment env) {
    SplitAssignerType assignerType =
        readableConfig.get(FlinkConfigOptions.TABLE_EXEC_SPLIT_ASSIGNER_TYPE);
    IcebergSource<RowData> source =
        IcebergSource.forRowData()
            .tableLoader(loader)
            .assignerFactory(assignerType.factory())
            .properties(properties)
            .project(getProjectedSchema())
            .limit(limit)
            .filters(filters)
            .flinkConfig(readableConfig)
            .build();
    DataStreamSource stream =
        env.fromSource(
            source,
            WatermarkStrategy.noWatermarks(),
            source.name(),
            TypeInformation.of(RowData.class));
    return stream;
  }

  /** 返回应用投影后的 schema，未投影时返回原 schema。 */
  private TableSchema getProjectedSchema() {
    if (projectedFields == null) {
      return schema;
    } else {
      String[] fullNames = schema.getFieldNames();
      DataType[] fullTypes = schema.getFieldDataTypes();
      return TableSchema.builder()
          .fields(
              Arrays.stream(projectedFields).mapToObj(i -> fullNames[i]).toArray(String[]::new),
              Arrays.stream(projectedFields).mapToObj(i -> fullTypes[i]).toArray(DataType[]::new))
          .build();
    }
  }

  /** 应用 Limit 下推。 */
  @Override
  public void applyLimit(long newLimit) {
    this.limit = newLimit;
  }

  /**
   * 应用过滤下推。
   *
   * <p>逻辑：把每个 Flink ResolvedExpression 尝试转为 Iceberg Expression， 接受转换成功的过滤，其余保留在 Flink 端执行。
   *
   * @param flinkFilters Flink 端的过滤表达式列表
   * @return 接受/剩余的过滤结果
   */
  @Override
  public Result applyFilters(List<ResolvedExpression> flinkFilters) {
    List<ResolvedExpression> acceptedFilters = Lists.newArrayList();
    List<Expression> expressions = Lists.newArrayList();

    for (ResolvedExpression resolvedExpression : flinkFilters) {
      Optional<Expression> icebergExpression = FlinkFilters.convert(resolvedExpression);
      if (icebergExpression.isPresent()) {
        expressions.add(icebergExpression.get());
        acceptedFilters.add(resolvedExpression);
      }
    }

    this.filters = expressions;
    return Result.of(acceptedFilters, flinkFilters);
  }

  /** 暂不支持嵌套投影。 */
  @Override
  public boolean supportsNestedProjection() {
    // TODO: 支持嵌套投影
    return false;
  }

  /** 当前 source 仅支持 INSERT-only 的 changelog 模式。 */
  @Override
  public ChangelogMode getChangelogMode() {
    return ChangelogMode.insertOnly();
  }

  /**
   * 返回扫描运行时提供者。
   *
   * <p>逻辑：根据配置选择 FLIP-27 source 或 legacy source， 通过 {@link DataStreamScanProvider} 把数据流返回给 Flink。
   *
   * @param runtimeProviderContext 扫描上下文
   * @return 扫描运行时提供者
   */
  @Override
  public ScanRuntimeProvider getScanRuntimeProvider(ScanContext runtimeProviderContext) {
    return new DataStreamScanProvider() {
      @Override
      public DataStream<RowData> produceDataStream(
          ProviderContext providerContext, StreamExecutionEnvironment execEnv) {
        if (readableConfig.get(FlinkConfigOptions.TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE)) {
          return createFLIP27Stream(execEnv);
        } else {
          return createDataStream(execEnv);
        }
      }

      /** 返回是否为有界源，由 {@link FlinkSource#isBounded} 判定。 */
      @Override
      public boolean isBounded() {
        return FlinkSource.isBounded(properties);
      }
    };
  }

  /** 拷贝当前 source，用于 Flink planner 在执行计划生成时的复制。 */
  @Override
  public DynamicTableSource copy() {
    return new IcebergTableSource(this);
  }

  /** 返回简短的摘要字符串。 */
  @Override
  public String asSummaryString() {
    return "Iceberg table source";
  }
}
