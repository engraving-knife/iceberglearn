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

import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.sql.connector.read.PartitionReaderFactory;
import org.apache.spark.sql.vectorized.ColumnarBatch;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的工厂，负责创建实例。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkColumnarReaderFactory。
 *
 * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class SparkColumnarReaderFactory implements PartitionReaderFactory {
  private final int batchSize;

  SparkColumnarReaderFactory(int batchSize) {
    Preconditions.checkArgument(batchSize > 1, "Batch size must be > 1");
    this.batchSize = batchSize;
  }

  /**
   * 创建并返回新实例。
   *
   * @param inputPartition 参数
   * @return 结果对象
   */
  @Override
  public PartitionReader<InternalRow> createReader(InputPartition inputPartition) {
    throw new UnsupportedOperationException("Row-based reads are not supported");
  }

  /**
   * 创建并返回新实例。
   *
   * @param inputPartition 参数
   * @return 结果对象
   */
  @Override
  public PartitionReader<ColumnarBatch> createColumnarReader(InputPartition inputPartition) {
    Preconditions.checkArgument(
        inputPartition instanceof SparkInputPartition,
        "Unknown input partition type: %s",
        inputPartition.getClass().getName());

    SparkInputPartition partition = (SparkInputPartition) inputPartition;

    if (partition.allTasksOfType(FileScanTask.class)) {
      /** 执行该方法的具体逻辑。 */
      return new BatchDataReader(partition, batchSize);

    } else {
      throw new UnsupportedOperationException(
          "Unsupported task group for columnar reads: " + partition.taskGroup());
    }
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param inputPartition 参数
   * @return 结果对象
   */
  @Override
  public boolean supportColumnarReads(InputPartition inputPartition) {
    return true;
  }
}
