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
package org.apache.iceberg.spark.source;

import java.util.List;
import java.util.Objects;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.Table;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.connector.read.Batch;
import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.connector.read.PartitionReaderFactory;

/**
 * Spark 批量读取的 {@link Batch} 实现。
 *
 * <p>所属模块：iceberg-spark（source 子包）。负责把扫描任务组规划为 Spark 输入分区，并创建 对应的分区读取器工厂（行式或列式向量化）。
 *
 * <p>职责：广播可序列化表元数据，按 locality 开关并发构建输入分区；根据格式与向量化开关 选择列式（Parquet/ORC batch）或行式读取器工厂。
 *
 * <p>设计意图：列式向量化读取仅在投影均为基本/元数据列且文件格式匹配时启用，避免不兼容场景； 通过 equals/hashCode（表名 + scanHashCode）支持 Spark
 * 缓存与复用。
 *
 * <p>上下游关系：由 {@link SparkScan} 创建；产出 {@link SparkInputPartition} 与读取器工厂供 Spark 任务使用。
 */
class SparkBatch implements Batch {

  private final JavaSparkContext sparkContext;
  private final Table table;
  private final String branch;
  private final SparkReadConf readConf;
  private final Types.StructType groupingKeyType;
  private final List<? extends ScanTaskGroup<?>> taskGroups;
  private final Schema expectedSchema;
  private final boolean caseSensitive;
  private final boolean localityEnabled;
  private final int scanHashCode;

  /** 全参数构造，从读取配置初始化分支、大小写敏感、locality 等开关。 */
  SparkBatch(
      JavaSparkContext sparkContext,
      Table table,
      SparkReadConf readConf,
      Types.StructType groupingKeyType,
      List<? extends ScanTaskGroup<?>> taskGroups,
      Schema expectedSchema,
      int scanHashCode) {
    this.sparkContext = sparkContext;
    this.table = table;
    this.branch = readConf.branch();
    this.readConf = readConf;
    this.groupingKeyType = groupingKeyType;
    this.taskGroups = taskGroups;
    this.expectedSchema = expectedSchema;
    this.caseSensitive = readConf.caseSensitive();
    this.localityEnabled = readConf.localityEnabled();
    this.scanHashCode = scanHashCode;
  }

  /** 规划输入分区：广播表元数据，按 locality 开关并发地为每个任务组构造 {@link SparkInputPartition}。 */
  @Override
  public InputPartition[] planInputPartitions() {
    // broadcast the table metadata as input partitions will be sent to executors
    Broadcast<Table> tableBroadcast =
        sparkContext.broadcast(SerializableTableWithSize.copyOf(table));
    String expectedSchemaString = SchemaParser.toJson(expectedSchema);

    InputPartition[] partitions = new InputPartition[taskGroups.size()];

    Tasks.range(partitions.length)
        .stopOnFailure()
        .executeWith(localityEnabled ? ThreadPools.getWorkerPool() : null)
        .run(
            index ->
                partitions[index] =
                    new SparkInputPartition(
                        groupingKeyType,
                        taskGroups.get(index),
                        tableBroadcast,
                        branch,
                        expectedSchemaString,
                        caseSensitive,
                        localityEnabled));

    return partitions;
  }

  /** 按向量化条件选择列式读取器工厂（Parquet/ORC batch）或行式读取器工厂。 */
  @Override
  public PartitionReaderFactory createReaderFactory() {
    if (useParquetBatchReads()) {
      int batchSize = readConf.parquetBatchSize();
      return new SparkColumnarReaderFactory(batchSize);

    } else if (useOrcBatchReads()) {
      int batchSize = readConf.orcBatchSize();
      return new SparkColumnarReaderFactory(batchSize);

    } else {
      return new SparkRowReaderFactory();
    }
  }

  // conditions for using Parquet batch reads:
  // - Parquet vectorization is enabled
  // - only primitives or metadata columns are projected
  // - all tasks are of FileScanTask type and read only Parquet files
  /** 是否启用 Parquet 向量化读取：向量化开启、投影均为基本/元数据列且全部为 Parquet 文件扫描。 */
  private boolean useParquetBatchReads() {
    return readConf.parquetVectorizationEnabled()
        && expectedSchema.columns().stream().allMatch(this::supportsParquetBatchReads)
        && taskGroups.stream().allMatch(this::supportsParquetBatchReads);
  }

  /** 判断单个任务是否支持 Parquet 向量化读取（FileScanTask 且为 Parquet）。 */
  private boolean supportsParquetBatchReads(ScanTask task) {
    if (task instanceof ScanTaskGroup) {
      ScanTaskGroup<?> taskGroup = (ScanTaskGroup<?>) task;
      return taskGroup.tasks().stream().allMatch(this::supportsParquetBatchReads);

    } else if (task.isFileScanTask() && !task.isDataTask()) {
      FileScanTask fileScanTask = task.asFileScanTask();
      return fileScanTask.file().format() == FileFormat.PARQUET;

    } else {
      return false;
    }
  }

  /** 判断字段是否支持 Parquet 向量化（基本类型或元数据列）。 */
  private boolean supportsParquetBatchReads(Types.NestedField field) {
    return field.type().isPrimitiveType() || MetadataColumns.isMetadataColumn(field.fieldId());
  }

  // conditions for using ORC batch reads:
  // - ORC vectorization is enabled
  // - all tasks are of type FileScanTask and read only ORC files with no delete files
  /** 是否启用 ORC 向量化读取：向量化开启且全部为无删除文件的 ORC 文件扫描。 */
  private boolean useOrcBatchReads() {
    return readConf.orcVectorizationEnabled()
        && taskGroups.stream().allMatch(this::supportsOrcBatchReads);
  }

  /** 判断单个任务是否支持 ORC 向量化读取（FileScanTask、ORC、无删除文件）。 */
  private boolean supportsOrcBatchReads(ScanTask task) {
    if (task instanceof ScanTaskGroup) {
      ScanTaskGroup<?> taskGroup = (ScanTaskGroup<?>) task;
      return taskGroup.tasks().stream().allMatch(this::supportsOrcBatchReads);

    } else if (task.isFileScanTask() && !task.isDataTask()) {
      FileScanTask fileScanTask = task.asFileScanTask();
      return fileScanTask.file().format() == FileFormat.ORC && fileScanTask.deletes().isEmpty();

    } else {
      return false;
    }
  }
  /** 判断是否相等。 */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SparkBatch that = (SparkBatch) o;
    return table.name().equals(that.table.name()) && scanHashCode == that.scanHashCode;
  }
  /** 返回哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(table.name(), scanHashCode);
  }
}
