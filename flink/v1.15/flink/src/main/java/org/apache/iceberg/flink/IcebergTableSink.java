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
 * Flink Iceberg 表的 DynamicTableSink 实现，对接 Flink Table API 与 SQL 写入。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：实现 Flink 的 {@link DynamicTableSink}，把上游 RowData 流通过 {@link
 * FlinkSink} 写入 Iceberg 表；支持分区、覆盖写、upsert。
 *
 * <p>设计意图：适配器模式——把 Flink DynamicTableSink 接口适配到 Iceberg FlinkSink Builder。 上下游：由 {@link
 * FlinkDynamicTableFactory} 创建；向下委托 {@link FlinkSink} 执行写入。
 */
public class IcebergTableSink implements DynamicTableSink, SupportsPartitioning, SupportsOverwrite {
  private final TableLoader tableLoader;
  private final TableSchema tableSchema;
  private final ReadableConfig readableConfig;
  private final Map<String, String> writeProps;

  private boolean overwrite = false;

  /** 复制构造，用于 {@link #copy()}。 */
  private IcebergTableSink(IcebergTableSink toCopy) {
    this.tableLoader = toCopy.tableLoader;
    this.tableSchema = toCopy.tableSchema;
    this.overwrite = toCopy.overwrite;
    this.readableConfig = toCopy.readableConfig;
    this.writeProps = toCopy.writeProps;
  }

  /** 构造 IcebergTableSink，绑定 TableLoader、schema、Flink 配置与写入属性。 */
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
   * 创建 sink runtime provider，把 RowData 流接入 {@link FlinkSink}。
   *
   * <p>逻辑：校验 overwrite 仅用于有界流；取主键列作为 equality 字段； 通过 FlinkSink.forRowData 构造并 append 数据流。
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

  /** 静态分区写入由 Flink 的 PartitionFanoutWriter 自动处理，本方法空实现。 */
  @Override
  public void applyStaticPartition(Map<String, String> partition) {
    // The flink's PartitionFanoutWriter will handle the static partition write policy
    // automatically.
  }

  /** 返回支持的 ChangelogMode，透传上游请求的所有 RowKind。 */
  @Override
  public ChangelogMode getChangelogMode(ChangelogMode requestedMode) {
    ChangelogMode.Builder builder = ChangelogMode.newBuilder();
    for (RowKind kind : requestedMode.getContainedKinds()) {
      builder.addContainedKind(kind);
    }
    return builder.build();
  }

  /** 复制当前 sink，用于 Flink planner 重新规划。 */
  @Override
  public DynamicTableSink copy() {
    return new IcebergTableSink(this);
  }

  @Override
  public String asSummaryString() {
    return "Iceberg table sink";
  }

  /** 设置是否覆盖写入。 */
  @Override
  public void applyOverwrite(boolean newOverwrite) {
    this.overwrite = newOverwrite;
  }
}
