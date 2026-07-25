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
 * Spark 列式读取的分区读取器工厂：为每个输入分区创建列式 {@link ColumnarBatch} 读取器。
 *
 * <p>所属模块：iceberg-spark（source 子包，Spark 数据源向量化读取路径）。
 *
 * <p>职责：实现 {@link PartitionReaderFactory}，仅支持列式读取，按输入分区的任务类型 构造对应的 {@link BatchDataReader}。
 *
 * <p>设计意图：专用于向量化读取场景，行式读取直接抛出异常；构造时校验批次大小大于 1。
 *
 * <p>上下游关系：由 Spark 读取执行引擎调用，输入分区为 {@link SparkInputPartition}， 产出 {@link BatchDataReader}。
 */
class SparkColumnarReaderFactory implements PartitionReaderFactory {
  private final int batchSize;

  /**
   * 构造列式读取器工厂。
   *
   * @param batchSize 列式批次大小，必须大于 1
   * @throws IllegalArgumentException 当批次大小不大于 1 时抛出
   */
  SparkColumnarReaderFactory(int batchSize) {
    Preconditions.checkArgument(batchSize > 1, "Batch size must be > 1");
    this.batchSize = batchSize;
  }

  /** 行式读取不支持，始终抛出异常。 */
  @Override
  public PartitionReader<InternalRow> createReader(InputPartition inputPartition) {
    throw new UnsupportedOperationException("Row-based reads are not supported");
  }

  /**
   * 创建列式批次读取器。
   *
   * <p>逻辑：校验输入分区为 {@link SparkInputPartition}，当所有任务为 {@link FileScanTask} 时构造 {@link
   * BatchDataReader}，否则抛出不支持异常。
   *
   * @param inputPartition 输入分区
   * @return 列式批次读取器
   */
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

  /** 始终支持列式读取。 */
  @Override
  public boolean supportColumnarReads(InputPartition inputPartition) {
    return true;
  }
}
