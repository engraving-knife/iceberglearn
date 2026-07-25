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
package org.apache.iceberg.mr.hive.vector;

import java.io.IOException;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatchCtx;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 文件级说明：将 Hive MRv1 风格的向量化 RecordReader 包装为 CloseableIterator 的迭代器。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块的向量化读取子包）。
 *
 * <p>职责：
 * <ul>
 *   <li>在底层 Hive RecordReader 之上提供迭代器接口，便于 Iceberg 框架统一消费。</li>
 *   <li>实现懒推进（advance）：仅在 hasNext/next 时真正调用底层 next 接口。</li>
 *   <li>对 identity 分区列做常量填充，避免从数据文件重复读取分区列。</li>
 * </ul>
 *
 * <p>设计意图：Hive 的向量化 RecordReader 返回 VectorizedRowBatch，
 * Iceberg 内部使用 CloseableIterator 抽象，本类桥接两种接口。
 *
 * <p>上下游关系：上游为 {@link HiveVectorizedReader}，下游为 Hive 向量化 RecordReader。
 */
public final class VectorizedRowBatchIterator implements CloseableIterator<VectorizedRowBatch> {

  private final RecordReader<NullWritable, VectorizedRowBatch> recordReader;
  private final NullWritable key;
  private final VectorizedRowBatch batch;
  private final VectorizedRowBatchCtx vrbCtx;
  private final int[] partitionColIndices;
  private final Object[] partitionValues;
  private boolean advanced = false;

  /**
   * 构造迭代器，预创建 key/value 与 VectorizedRowBatchCtx。
   *
   * @param recordReader 底层 Hive 向量化 RecordReader
   * @param job 任务配置
   * @param partitionColIndices 分区列下标数组，可为 null
   * @param partitionValues 分区列常量值数组
   */
  VectorizedRowBatchIterator(
      RecordReader<NullWritable, VectorizedRowBatch> recordReader,
      JobConf job,
      int[] partitionColIndices,
      Object[] partitionValues) {
    this.recordReader = recordReader;
    this.key = recordReader.createKey();
    this.batch = recordReader.createValue();
    this.vrbCtx = CompatibilityHiveVectorUtils.findMapWork(job).getVectorizedRowBatchCtx();
    this.partitionColIndices = partitionColIndices;
    this.partitionValues = partitionValues;
  }

  /** 关闭底层 RecordReader，释放资源。 */
  @Override
  public void close() throws IOException {
    this.recordReader.close();
  }

  /**
   * 推进到底层 RecordReader 的下一批，并对分区列做常量填充。
   *
   * <p>逻辑：若未推进过，调用底层 next(key, batch)；无下一批则将 batch.size 置 0；
   * 若存在分区列下标，逐个把分区常量值写入对应列向量。
   */
  private void advance() {
    if (!advanced) {
      try {

        if (!recordReader.next(key, batch)) {
          batch.size = 0;
        }
        // Fill partition values
        if (partitionColIndices != null) {
          for (int i = 0; i < partitionColIndices.length; ++i) {
            int colIdx = partitionColIndices[i];
            CompatibilityHiveVectorUtils.addPartitionColsToBatch(
                batch.cols[colIdx],
                partitionValues[i],
                vrbCtx.getRowColumnNames()[colIdx],
                vrbCtx.getRowColumnTypeInfos()[colIdx]);
          }
        }
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
      advanced = true;
    }
  }

  /** 是否还有下一批数据。 */
  @Override
  public boolean hasNext() {
    advance();
    return batch.size > 0;
  }

  /** 返回当前批并将推进标记重置，以便下次调用继续推进。 */
  @Override
  public VectorizedRowBatch next() {
    advance();
    advanced = false;
    return batch;
  }
}
