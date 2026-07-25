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

import java.util.Arrays;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：列式批次的容器，包装一组 {@link ColumnVector}（借鉴 Spark 的 ColumnarBatch）。
 *
 * <p>所属模块：iceberg-arrow（向量化读取结果对外暴露的批次对象）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有行数与列向量数组，构造时校验每列行数与批行数一致。
 *   <li>提供按列访问、转换为 {@link VectorSchemaRoot}、关闭等能力。
 * </ul>
 *
 * <p>设计意图：作为读取器与计算引擎之间的数据交接单元，行数一致性校验避免列错位； 实现 {@link AutoCloseable} 以纳入 try-with-resources 管理向量内存。
 *
 * <p>上下游关系：由 {@link ArrowBatchReader} 产出；被引擎消费，可转为 VectorSchemaRoot。
 */
public class ColumnarBatch implements AutoCloseable {

  private final int numRows;
  private final ColumnVector[] columns;

  /**
   * 构造列式批次，校验每列向量行数与 numRows 一致。
   *
   * @param numRows 批次行数
   * @param columns 列向量数组
   * @throws IllegalArgumentException 若任一列行数与 numRows 不一致
   */
  ColumnarBatch(int numRows, ColumnVector[] columns) {
    for (int i = 0; i < columns.length; i++) {
      int columnValueCount = columns[i].getFieldVector().getValueCount();
      Preconditions.checkArgument(
          numRows == columnValueCount,
          "Number of rows (="
              + numRows
              + ") != column["
              + i
              + "] size (="
              + columnValueCount
              + ")");
    }
    this.numRows = numRows;
    this.columns = columns;
  }

  /**
   * 用本批次中的 Arrow 向量创建新的 {@link VectorSchemaRoot}。
   *
   * <p>注意：Arrow 向量归读取器所有，调用方不应在关闭批次后继续使用。
   *
   * @return 由各列向量组装的 VectorSchemaRoot
   */
  public VectorSchemaRoot createVectorSchemaRootFromVectors() {
    return VectorSchemaRoot.of(
        Arrays.stream(columns).map(ColumnVector::getArrowVector).toArray(FieldVector[]::new));
  }

  /** 关闭批次中所有列向量。调用后不应再访问数据，必须以此清理内存分配。 */
  @Override
  public void close() {
    for (ColumnVector c : columns) {
      c.close();
    }
  }

  /**
   * 返回批次的列数。
   *
   * @return 列数
   */
  public int numCols() {
    return columns.length;
  }

  /**
   * 返回读取的行数（含被过滤的行）。
   *
   * @return 行数
   */
  public int numRows() {
    return numRows;
  }

  /**
   * 返回指定位置的列向量。
   *
   * @param ordinal 列下标
   * @return 对应的 {@link ColumnVector}
   */
  public ColumnVector column(int ordinal) {
    return columns[ordinal];
  }
}
