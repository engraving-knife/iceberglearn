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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 列式分区读取器工厂，为每个 InputPartition 创建列式批读取器。
 *
 * <p>设计意图：实现 Spark PartitionReaderFactory，构建 BatchDataReader。
 *
 * <p>上下游关系：由 SparkBatch 使用。
 */
class SparkColumnarReaderFactory implements PartitionReaderFactory {
  private final int batchSize;

  SparkColumnarReaderFactory(int batchSize) {
    Preconditions.checkArgument(batchSize > 1, "Batch size must be > 1");
    this.batchSize = batchSize;
  }
  /** 执行 createReader 相关操作。 */
  @Override
  public PartitionReader<InternalRow> createReader(InputPartition inputPartition) {
    throw new UnsupportedOperationException("Row-based reads are not supported");
  }
  /** 执行 createColumnarReader 相关操作。 */
  @Override
  public PartitionReader<ColumnarBatch> createColumnarReader(InputPartition inputPartition) {
    Preconditions.checkArgument(
        inputPartition instanceof SparkInputPartition,
        "Unknown input partition type: %s",
        inputPartition.getClass().getName());

    SparkInputPartition partition = (SparkInputPartition) inputPartition;

    if (partition.allTasksOfType(FileScanTask.class)) {
      return new BatchDataReader(partition, batchSize);

    } else {
      throw new UnsupportedOperationException(
          "Unsupported task group for columnar reads: " + partition.taskGroup());
    }
  }
  /** 执行 supportColumnarReads 相关操作。 */
  @Override
  public boolean supportColumnarReads(InputPartition inputPartition) {
    return true;
  }
}
