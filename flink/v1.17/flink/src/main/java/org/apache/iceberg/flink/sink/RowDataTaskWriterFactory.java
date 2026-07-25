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
package org.apache.iceberg.flink.sink;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.flink.RowDataWrapper;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.io.PartitionedFanoutWriter;
import org.apache.iceberg.io.TaskWriter;
import org.apache.iceberg.io.UnpartitionedWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.ArrayUtil;
import org.apache.iceberg.util.SerializableSupplier;

/**
 * 文件级说明：Flink sink 中创建 Iceberg 数据写入任务（TaskWriter）的工厂。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按表 schema、分区规范、目标文件大小、文件格式等参数构造 {@link TaskWriter}。
 *   <li>支持纯追加写（仅 INSERT）与等值更新写（INSERT + equality DELETE）两种模式。
 *   <li>对分区表使用 fan-out writer，对非分区表使用普通 writer。
 *   <li>对 upsert 模式只写入等值字段，避免写出错误值。
 * </ul>
 *
 * <p>设计意图：把 writer 创建逻辑与算子解耦，统一从工厂获取 writer； 通过 {@link CachingTableSupplier} 缓存表的初始元数据以支持状态恢复与刷新。
 *
 * <p>上下游关系：上游为 {@code IcebergStreamWriter}（调用 initialize 与 create）， 下游为 Iceberg 的 {@link
 * UnpartitionedWriter}、{@link PartitionedFanoutWriter} 及 Iceberg 的 Delta Writer 实现。
 */
public class RowDataTaskWriterFactory implements TaskWriterFactory<RowData> {
  private final Supplier<Table> tableSupplier;
  private final Schema schema;
  private final RowType flinkSchema;
  private final PartitionSpec spec;
  private final long targetFileSizeBytes;
  private final FileFormat format;
  private final List<Integer> equalityFieldIds;
  private final boolean upsert;
  private final FileAppenderFactory<RowData> appenderFactory;

  private transient OutputFileFactory outputFileFactory;

  /** 通过 {@link Table} 直接构造工厂（内部包装为 supplier）。 */
  public RowDataTaskWriterFactory(
      Table table,
      RowType flinkSchema,
      long targetFileSizeBytes,
      FileFormat format,
      Map<String, String> writeProperties,
      List<Integer> equalityFieldIds,
      boolean upsert) {
    this(
        () -> table,
        flinkSchema,
        targetFileSizeBytes,
        format,
        writeProperties,
        equalityFieldIds,
        upsert);
  }

  /**
   * 通过 {@link SerializableSupplier} 构造工厂。
   *
   * <p>逻辑：从 supplier 取出表，依据是否使用 {@link CachingTableSupplier} 决定使用初始表元数据还是最新元数据；按 equalityFieldIds
   * 是否为空、是否 upsert 决定 appenderFactory 的 equality field IDs 与写入 schema。
   */
  public RowDataTaskWriterFactory(
      SerializableSupplier<Table> tableSupplier,
      RowType flinkSchema,
      long targetFileSizeBytes,
      FileFormat format,
      Map<String, String> writeProperties,
      List<Integer> equalityFieldIds,
      boolean upsert) {
    this.tableSupplier = tableSupplier;

    Table table;
    if (tableSupplier instanceof CachingTableSupplier) {
      // 在支持 schema 演进之前，依赖初始的表元数据
      table = ((CachingTableSupplier) tableSupplier).initialTable();
    } else {
      table = tableSupplier.get();
    }

    this.schema = table.schema();
    this.flinkSchema = flinkSchema;
    this.spec = table.spec();
    this.targetFileSizeBytes = targetFileSizeBytes;
    this.format = format;
    this.equalityFieldIds = equalityFieldIds;
    this.upsert = upsert;

    if (equalityFieldIds == null || equalityFieldIds.isEmpty()) {
      this.appenderFactory =
          new FlinkAppenderFactory(
              table, schema, flinkSchema, writeProperties, spec, null, null, null);
    } else if (upsert) {
      // upsert 模式下，仅新行以 INSERT 类型发出，被删行与插入行除主键外可能不同，
      // 删除文件必须包含与被删行一致的字段值，因此只写入等值删除字段。
      this.appenderFactory =
          new FlinkAppenderFactory(
              table,
              schema,
              flinkSchema,
              writeProperties,
              spec,
              ArrayUtil.toIntArray(equalityFieldIds),
              TypeUtil.select(schema, Sets.newHashSet(equalityFieldIds)),
              null);
    } else {
      this.appenderFactory =
          new FlinkAppenderFactory(
              table,
              schema,
              flinkSchema,
              writeProperties,
              spec,
              ArrayUtil.toIntArray(equalityFieldIds),
              schema,
              null);
    }
  }

  /**
   * 初始化工厂，构建输出文件工厂。
   *
   * <p>逻辑：刷新表后，用 taskId 与 attemptId 构造 {@link OutputFileFactory}。
   *
   * @param taskId 任务 ID
   * @param attemptId 尝试 ID
   */
  @Override
  public void initialize(int taskId, int attemptId) {
    Table table;
    if (tableSupplier instanceof CachingTableSupplier) {
      // 在支持 schema 演进之前，依赖初始的表元数据
      table = ((CachingTableSupplier) tableSupplier).initialTable();
    } else {
      table = tableSupplier.get();
    }

    refreshTable();

    this.outputFileFactory =
        OutputFileFactory.builderFor(table, taskId, attemptId)
            .format(format)
            .ioSupplier(() -> tableSupplier.get().io())
            .build();
  }

  /**
   * 创建一个 TaskWriter。
   *
   * <p>逻辑：根据 equalityFieldIds 是否为空、是否分区，分别返回：
   *
   * <ul>
   *   <li>纯追加 + 非分区：{@link UnpartitionedWriter}
   *   <li>纯追加 + 分区：{@link RowDataPartitionedFanoutWriter}
   *   <li>等值 + 非分区：{@link UnpartitionedDeltaWriter}
   *   <li>等值 + 分区：{@link PartitionedDeltaWriter}
   * </ul>
   *
   * @return 新建的 TaskWriter
   */
  @Override
  public TaskWriter<RowData> create() {
    Preconditions.checkNotNull(
        outputFileFactory,
        "The outputFileFactory shouldn't be null if we have invoked the initialize().");

    refreshTable();

    if (equalityFieldIds == null || equalityFieldIds.isEmpty()) {
      // 构造仅写 INSERT 的 task writer
      if (spec.isUnpartitioned()) {
        return new UnpartitionedWriter<>(
            spec,
            format,
            appenderFactory,
            outputFileFactory,
            tableSupplier.get().io(),
            targetFileSizeBytes);
      } else {
        return new RowDataPartitionedFanoutWriter(
            spec,
            format,
            appenderFactory,
            outputFileFactory,
            tableSupplier.get().io(),
            targetFileSizeBytes,
            schema,
            flinkSchema);
      }
    } else {
      // 构造同时写 INSERT 与 equality DELETE 的 task writer
      if (spec.isUnpartitioned()) {
        return new UnpartitionedDeltaWriter(
            spec,
            format,
            appenderFactory,
            outputFileFactory,
            tableSupplier.get().io(),
            targetFileSizeBytes,
            schema,
            flinkSchema,
            equalityFieldIds,
            upsert);
      } else {
        return new PartitionedDeltaWriter(
            spec,
            format,
            appenderFactory,
            outputFileFactory,
            tableSupplier.get().io(),
            targetFileSizeBytes,
            schema,
            flinkSchema,
            equalityFieldIds,
            upsert);
      }
    }
  }

  /** 若 supplier 是 {@link CachingTableSupplier} 则刷新表，加载最新元数据。 */
  void refreshTable() {
    if (tableSupplier instanceof CachingTableSupplier) {
      ((CachingTableSupplier) tableSupplier).refreshTable();
    }
  }

  /**
   * 文件级说明：用于分区表的 fan-out writer，按行计算分区并写入对应分区文件。
   *
   * <p>逻辑：复用 {@link PartitionKey} 与 {@link RowDataWrapper} 把每条 RowData 转换为 Iceberg 内部结构并计算分区，交由父类
   * {@link PartitionedFanoutWriter} 完成多分区分发写入。
   */
  private static class RowDataPartitionedFanoutWriter extends PartitionedFanoutWriter<RowData> {

    private final PartitionKey partitionKey;
    private final RowDataWrapper rowDataWrapper;

    RowDataPartitionedFanoutWriter(
        PartitionSpec spec,
        FileFormat format,
        FileAppenderFactory<RowData> appenderFactory,
        OutputFileFactory fileFactory,
        FileIO io,
        long targetFileSize,
        Schema schema,
        RowType flinkSchema) {
      super(spec, format, appenderFactory, fileFactory, io, targetFileSize);
      this.partitionKey = new PartitionKey(spec, schema);
      this.rowDataWrapper = new RowDataWrapper(flinkSchema, schema.asStruct());
    }

    /** 把 RowData 包装并计算分区键。 */
    @Override
    protected PartitionKey partition(RowData row) {
      partitionKey.partition(rowDataWrapper.wrap(row));
      return partitionKey;
    }
  }
}
