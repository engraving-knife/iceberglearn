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

import java.io.Serializable;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.Table;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.hadoop.Util;
import org.apache.iceberg.types.Types;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.read.HasPartitionKey;
import org.apache.spark.sql.connector.read.InputPartition;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 输入分区，封装一组扫描任务及其本地性偏好。
 *
 * <p>设计意图：实现 InputPartition，将 Iceberg FileScanTask 集合映射为 Spark 分区。
 *
 * <p>上下游关系：由 SparkBatch 创建；由 SparkColumnarReaderFactory / SparkRowReaderFactory 消费。
 */
class SparkInputPartition implements InputPartition, HasPartitionKey, Serializable {
  private final Types.StructType groupingKeyType;
  private final ScanTaskGroup<?> taskGroup;
  private final Broadcast<Table> tableBroadcast;
  private final String branch;
  private final String expectedSchemaString;
  private final boolean caseSensitive;

  private transient Schema expectedSchema = null;
  private transient String[] preferredLocations = null;

  SparkInputPartition(
      Types.StructType groupingKeyType,
      ScanTaskGroup<?> taskGroup,
      Broadcast<Table> tableBroadcast,
      String branch,
      String expectedSchemaString,
      boolean caseSensitive,
      boolean localityPreferred) {
    this.groupingKeyType = groupingKeyType;
    this.taskGroup = taskGroup;
    this.tableBroadcast = tableBroadcast;
    this.branch = branch;
    this.expectedSchemaString = expectedSchemaString;
    this.caseSensitive = caseSensitive;
    if (localityPreferred) {
      Table table = tableBroadcast.value();
      this.preferredLocations = Util.blockLocations(table.io(), taskGroup);
    } else {
      this.preferredLocations = HadoopInputFile.NO_LOCATION_PREFERENCE;
    }
  }
  /** 执行 preferredLocations 相关操作。 */
  @Override
  public String[] preferredLocations() {
    return preferredLocations;
  }
  /** 执行 partitionKey 相关操作。 */
  @Override
  public InternalRow partitionKey() {
    return new StructInternalRow(groupingKeyType).setStruct(taskGroup.groupingKey());
  }

  @SuppressWarnings("unchecked")
  public <T extends ScanTask> ScanTaskGroup<T> taskGroup() {
    return (ScanTaskGroup<T>) taskGroup;
  }

  public <T extends ScanTask> boolean allTasksOfType(Class<T> javaClass) {
    return taskGroup.tasks().stream().allMatch(javaClass::isInstance);
  }
  /** 执行 table 相关操作。 */
  public Table table() {
    return tableBroadcast.value();
  }
  /** 执行 branch 相关操作。 */
  public String branch() {
    return branch;
  }
  /** 判断是否 CaseSensitive。 */
  public boolean isCaseSensitive() {
    return caseSensitive;
  }
  /** 执行 expectedSchema 相关操作。 */
  public Schema expectedSchema() {
    if (expectedSchema == null) {
      this.expectedSchema = SchemaParser.fromJson(expectedSchemaString);
    }

    return expectedSchema;
  }
}
