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
package org.apache.iceberg.arrow.vectorized;

import java.util.List;
import org.apache.iceberg.parquet.VectorizedReader;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：按列组织向量化读取器并产出 {@link ColumnarBatch} 的批读取器。
 *
 * <p>所属模块：iceberg-arrow（向量化读取链路，将 Parquet 列式数据读入 Arrow 向量内存）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有一组按预期读取 Schema 排列的列读取器（{@link VectorizedReader}），以及对应的 Arrow 向量持有者（{@link VectorHolder}）。
 *   <li>每次 {@link #read(ColumnarBatch, int)} 读取一个批次的行，逐列调用读取器填充向量， 再组装为 {@link ColumnarBatch} 返回。
 *   <li>负责所持有 Arrow 向量的生命周期管理（关闭/复用）。
 * </ul>
 *
 * <p>设计意图：继承 {@link BaseBatchReader} 复用公共的读取器数组与向量持有者管理； read 方法为 final
 * 以固定批读取流程，子类不可改变核心读取顺序，保证向量与行数一致性。
 *
 * <p>上下游关系：上游由 {@link ArrowReader} 内部的 {@code buildReader} 通过 {@link VectorizedReaderBuilder}
 * 构造；下游产出 {@link ColumnarBatch} 供引擎消费。
 */
class ArrowBatchReader extends BaseBatchReader<ColumnarBatch> {

  /** 按给定列读取器列表构造批读取器。 */
  ArrowBatchReader(List<VectorizedReader<?>> readers) {
    super(readers);
  }

  /**
   * 读取指定行数的列式批次。
   *
   * <p>逻辑：校验行数大于 0；若 reuse 为 null 则先关闭旧向量；逐列调用读取器填充 {@link VectorHolder}，校验每列实际行数与预期一致，最后以各列 {@link
   * ColumnVector} 组装 {@link ColumnarBatch} 返回。
   *
   * @param reuse 可复用的批次对象（当前实现未直接复用，仅用于触发旧向量清理）
   * @param numRowsToRead 本次需要读取的行数
   * @return 包含各列向量的 {@link ColumnarBatch}
   */
  @Override
  public final ColumnarBatch read(ColumnarBatch reuse, int numRowsToRead) {
    Preconditions.checkArgument(
        numRowsToRead > 0, "Invalid number of rows to read: %s", numRowsToRead);

    if (reuse == null) {
      closeVectors();
    }

    ColumnVector[] columnVectors = new ColumnVector[readers.length];
    for (int i = 0; i < readers.length; i += 1) {
      vectorHolders[i] = readers[i].read(vectorHolders[i], numRowsToRead);
      int numRowsInVector = vectorHolders[i].numValues();
      Preconditions.checkState(
          numRowsInVector == numRowsToRead,
          "Number of rows in the vector %s didn't match expected %s ",
          numRowsInVector,
          numRowsToRead);
      // Handle null vector for constant case
      columnVectors[i] = new ColumnVector(vectorHolders[i]);
    }
    return new ColumnarBatch(numRowsToRead, columnVectors);
  }
}
