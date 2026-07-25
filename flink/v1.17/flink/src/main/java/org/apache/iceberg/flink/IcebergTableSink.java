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
package org.apache.iceberg.flink;

import java.util.List;
import java.util.Map;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.constraints.UniqueConstraint;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.ProviderContext;
import org.apache.flink.table.connector.sink.DataStreamSinkProvider;
import org.apache.flink.table.connector.sink.DynamicTableSink;
import org.apache.flink.table.connector.sink.abilities.SupportsOverwrite;
import org.apache.flink.table.connector.sink.abilities.SupportsPartitioning;
import org.apache.flink.table.data.RowData;
import org.apache.flink.types.RowKind;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.flink.sink.FlinkSink;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 文件级说明：Iceberg 表的 Flink DynamicTableSink 实现。
 *
 * <p>所属模块：iceberg-flink（sink 子包），实现 Flink 的 {@link DynamicTableSink} 接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为 Flink SQL 中 Iceberg 表的写入入口，将 DataStream 写入 Iceberg 表。
 *   <li>支持分区写入（{@link SupportsPartitioning}）和覆盖写入（{@link SupportsOverwrite}）。
 *   <li>支持 Changelog 模式（INSERT/UPDATE_BEFORE/UPDATE_AFTER/DELETE）。
 * </ul>
 *
 * <p>设计意图：作为 Flink Table API 与 Iceberg {@link FlinkSink} 之间的适配层， 将 Flink 的 SinkRuntimeProvider 转换为对
 * FlinkSink Builder 的调用。 equality columns 从 TableSchema 主键中提取，用于 UPSERT/CDC 场景。
 *
 * <p>上下游关系：由 {@link FlinkDynamicTableFactory} 创建；内部委托 {@link FlinkSink} 执行实际写入。
 */
public class IcebergTableSink implements DynamicTableSink, SupportsPartitioning, SupportsOverwrite {
  private final TableLoader tableLoader;
  private final TableSchema tableSchema;
  private final ReadableConfig readableConfig;
  private final Map<String, String> writeProps;

  private boolean overwrite = false;

  /** 拷贝构造方法。 */
  private IcebergTableSink(IcebergTableSink toCopy) {
    this.tableLoader = toCopy.tableLoader;
    this.tableSchema = toCopy.tableSchema;
    this.overwrite = toCopy.overwrite;
    this.readableConfig = toCopy.readableConfig;
    this.writeProps = toCopy.writeProps;
  }

  /**
   * 构造 IcebergTableSink。
   *
   * @param tableLoader 表加载器
   * @param tableSchema Flink 表 schema
   * @param readableConfig Flink 配置
   * @param writeProps 写入属性
   */
  public IcebergTableSink(
      TableLoader tableLoader,
      TableSchema tableSchema,
      ReadableConfig readableConfig,
      Map<String, String> writeProps) {
    this.tableLoader = tableLoader;
    this.tableSchema = tableSchema;
    this.readableConfig = readableConfig;
    this.writeProps = writeProps;
  }

  /**
   * 创建 Sink 运行时 Provider。
   *
   * <p>逻辑：校验 overwrite 仅用于有界流 → 从主键提取 equality columns → 返回 DataStreamSinkProvider， 在其
   * consumeDataStream 中调用 {@link FlinkSink#forRowData} 构建写入链。
   *
   * @param context Flink sink 上下文
   * @return DataStreamSinkProvider
   */
  @Override
  public SinkRuntimeProvider getSinkRuntimeProvider(Context context) {
    Preconditions.checkState(
        !overwrite || context.isBounded(),
        "Unbounded data stream doesn't support overwrite operation.");

    List<String> equalityColumns =
        tableSchema.getPrimaryKey().map(UniqueConstraint::getColumns).orElseGet(ImmutableList::of);

    return new DataStreamSinkProvider() {
      @Override
      public DataStreamSink<?> consumeDataStream(
          ProviderContext providerContext, DataStream<RowData> dataStream) {
        return FlinkSink.forRowData(dataStream)
            .tableLoader(tableLoader)
            .tableSchema(tableSchema)
            .equalityFieldColumns(equalityColumns)
            .overwrite(overwrite)
            .setAll(writeProps)
            .flinkConf(readableConfig)
            .append();
      }
    };
  }

  /** 静态分区由 Flink 的 PartitionFanoutWriter 自动处理，此处无需额外操作。 */
  @Override
  public void applyStaticPartition(Map<String, String> partition) {
    // The flink's PartitionFanoutWriter will handle the static partition write policy
    // automatically.
  }

  /** 接受所有请求的 ChangelogMode（支持 INSERT/UPDATE/DELETE）。 */
  @Override
  public ChangelogMode getChangelogMode(ChangelogMode requestedMode) {
    ChangelogMode.Builder builder = ChangelogMode.newBuilder();
    for (RowKind kind : requestedMode.getContainedKinds()) {
      builder.addContainedKind(kind);
    }
    return builder.build();
  }

  /** 创建当前 sink 的副本。 */
  @Override
  public DynamicTableSink copy() {
    return new IcebergTableSink(this);
  }

  @Override
  public String asSummaryString() {
    return "Iceberg table sink";
  }

  /** 设置覆盖写入模式。 */
  @Override
  public void applyOverwrite(boolean newOverwrite) {
    this.overwrite = newOverwrite;
  }
}
