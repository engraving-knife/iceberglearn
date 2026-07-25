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
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Reporter;
import org.apache.iceberg.mr.mapred.AbstractMapredIcebergRecordReader;
import org.apache.iceberg.mr.mapreduce.IcebergSplit;

/**
 * 文件级说明：基于 MR1 API 实现的向量化记录读取器包装类。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块的向量化读取子包）。
 *
 * <p>职责：将 MR2 API 形式的 {@code IcebergInputFormat.IcebergRecordReader}
 * 产出的 {@link VectorizedRowBatch} 通过 MR1 API 形式传递给 Hive3 执行引擎。
 *
 * <p>设计意图：Hive3 仍使用 MR1 风格的 RecordReader 接口（next/createValue/getPos），
 * 而 Iceberg 的输入格式实现基于 MR2 API。本类作为适配器桥接两种 API，
 * 避免对 Iceberg 输入格式做面向 Hive 的特殊改造。
 *
 * <p>上下游关系：上游为 Hive3 的向量化执行算子，下游为 Iceberg 的 MR2 输入格式实现。
 */
public final class HiveIcebergVectorizedRecordReader
    extends AbstractMapredIcebergRecordReader<VectorizedRowBatch> {

  private final JobConf job;

  /**
   * 构造向量化记录读取器。
   *
   * @param mapreduceInputFormat MR2 形式的 Iceberg 输入格式
   * @param split 输入分片
   * @param job 任务配置
   * @param reporter 进度上报器
   * @throws IOException 当底层 reader 初始化失败时抛出
   */
  public HiveIcebergVectorizedRecordReader(
      org.apache.iceberg.mr.mapreduce.IcebergInputFormat<VectorizedRowBatch> mapreduceInputFormat,
      IcebergSplit split,
      JobConf job,
      Reporter reporter)
      throws IOException {
    super(mapreduceInputFormat, split, job, reporter);
    this.job = job;
  }

  /**
   * 从底层 MR2 reader 读取下一个向量批，并将字段引用拷贝到 Hive 复用的 value 中。
   *
   * @param key 未使用的 Void key
   * @param value Hive 复用的 VectorizedRowBatch 实例
   * @return true 表示读取到下一批；false 表示已无更多数据
   * @throws IOException 当读取发生 I/O 错误时抛出
   */
  @Override
  public boolean next(Void key, VectorizedRowBatch value) throws IOException {
    try {
      if (innerReader.nextKeyValue()) {
        VectorizedRowBatch newBatch = (VectorizedRowBatch) innerReader.getCurrentValue();
        value.cols = newBatch.cols;
        value.endOfFile = newBatch.endOfFile;
        value.selectedInUse = newBatch.selectedInUse;
        value.size = newBatch.size;
        return true;
      } else {
        return false;
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ie);
    }
  }

  /**
   * 创建新的 VectorizedRowBatch 实例，列结构由 MapWork 中的 VectorizedRowBatchCtx 决定。
   *
   * @return 与 Hive 期望列结构匹配的空向量批
   */
  @Override
  public VectorizedRowBatch createValue() {
    return CompatibilityHiveVectorUtils.findMapWork(job)
        .getVectorizedRowBatchCtx()
        .createVectorizedRowBatch();
  }

  /** 返回当前读取位置；此处返回 -1 表示位置不可用。 */
  @Override
  public long getPos() {
    return -1;
  }
}
