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
import org.apache.iceberg.ChangelogScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.read.Batch;
import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.sql.connector.read.PartitionReaderFactory;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现，处理列式批量数据。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkChangelogBatch。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class SparkChangelogBatch implements Batch {

  private final JavaSparkContext sparkContext;
  private final Table table;
  private final List<ScanTaskGroup<ChangelogScanTask>> taskGroups;
  private final Schema expectedSchema;
  private final boolean caseSensitive;
  private final boolean localityEnabled;
  private final int scanHashCode;

  SparkChangelogBatch(
      SparkSession spark,
      Table table,
      SparkReadConf readConf,
      List<ScanTaskGroup<ChangelogScanTask>> taskGroups,
      Schema expectedSchema,
      int scanHashCode) {
    this.sparkContext = JavaSparkContext.fromSparkContext(spark.sparkContext());
    this.table = table;
    this.taskGroups = taskGroups;
    this.expectedSchema = expectedSchema;
    this.caseSensitive = readConf.caseSensitive();
    this.localityEnabled = readConf.localityEnabled();
    this.scanHashCode = scanHashCode;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public InputPartition[] planInputPartitions() {
    Table serializableTable = SerializableTableWithSize.copyOf(table);
    Broadcast<Table> tableBroadcast = sparkContext.broadcast(serializableTable);
    String expectedSchemaString = SchemaParser.toJson(expectedSchema);

    InputPartition[] partitions = new InputPartition[taskGroups.size()];

    Tasks.range(partitions.length)
        .stopOnFailure()
        .executeWith(localityEnabled ? ThreadPools.getWorkerPool() : null)
        .run(
            index ->
                partitions[index] =
                    new SparkInputPartition(
                        taskGroups.get(index),
                        tableBroadcast,
                        expectedSchemaString,
                        caseSensitive,
                        localityEnabled));

    return partitions;
  }

  /**
   * 创建并返回新实例。
   *
   * @return 结果对象
   */
  @Override
  public PartitionReaderFactory createReaderFactory() {
    /** 读取数据。 */
    return new ReaderFactory();
  }

  /** 判断是否与给定对象相等。 */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SparkChangelogBatch that = (SparkChangelogBatch) o;
    return table.name().equals(that.table.name()) && scanHashCode == that.scanHashCode;
  }

  /** 返回该对象的哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(table.name(), scanHashCode);
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的工厂，负责创建实例。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ReaderFactory。
   *
   * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private static class ReaderFactory implements PartitionReaderFactory {
    /**
     * 创建并返回新实例。
     *
     * @param partition 参数
     * @return 结果对象
     */
    @Override
    public PartitionReader<InternalRow> createReader(InputPartition partition) {
      Preconditions.checkArgument(
          partition instanceof SparkInputPartition,
          "Unknown input partition type: %s",
          partition.getClass().getName());

      /** 执行该方法的具体逻辑。 */
      return new RowReader((SparkInputPartition) partition);
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 RowReader。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private static class RowReader extends ChangelogRowReader
      implements PartitionReader<InternalRow> {

    RowReader(SparkInputPartition partition) {
      super(
          partition.table(),
          partition.taskGroup(),
          partition.expectedSchema(),
          partition.isCaseSensitive());
    }
  }
}
