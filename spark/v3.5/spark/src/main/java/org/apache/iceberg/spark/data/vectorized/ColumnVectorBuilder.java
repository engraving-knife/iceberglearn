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
package org.apache.iceberg.spark.data.vectorized;

import org.apache.iceberg.arrow.vectorized.VectorHolder;
import org.apache.iceberg.arrow.vectorized.VectorHolder.ConstantVectorHolder;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.vectorized.ColumnVector;

/**
 * Spark 列向量构建器（向量化读取）。
 *
 * <p>所属模块：iceberg-spark（data/vectorized 子包）。根据 {@link VectorHolder} 与可选的行映射/ 删除标记，构造对应的 Spark
 * {@link ColumnVector} 实现，供向量化读取器按列产出数据。
 *
 * <p>职责：区分 dummy 向量（删除标记列、常量列）、需过滤的向量与普通向量，分别返回 {@link DeletedColumnVector}、{@link
 * ConstantColumnVector}、{@link ColumnVectorWithFilter} 或 {@link IcebergArrowColumnVector}。
 *
 * <p>设计意图：用构建器模式收集可选的删除行信息，再在 {@link #build} 时统一决策返回实现类， 隔离不同列向量的构造细节。
 */
class ColumnVectorBuilder {
  private boolean[] isDeleted;
  private int[] rowIdMapping;

  /** 设置行 ID 映射与删除标记数组，返回 this 以便链式调用。 */
  public ColumnVectorBuilder withDeletedRows(int[] rowIdMappingArray, boolean[] isDeletedArray) {
    this.rowIdMapping = rowIdMappingArray;
    this.isDeleted = isDeletedArray;
    return this;
  }

  /**
   * 构建列向量。
   *
   * <p>逻辑：dummy 向量按子类型返回删除列或常量列；否则若有行映射返回带过滤的列向量， 否则返回普通 {@link IcebergArrowColumnVector}。
   */
  public ColumnVector build(VectorHolder holder, int numRows) {
    if (holder.isDummy()) {
      if (holder instanceof VectorHolder.DeletedVectorHolder) {
        return new DeletedColumnVector(Types.BooleanType.get(), isDeleted);
      } else if (holder instanceof ConstantVectorHolder) {
        ConstantVectorHolder<?> constantHolder = (ConstantVectorHolder<?>) holder;
        Type icebergType = constantHolder.icebergType();
        Object value = constantHolder.getConstant();
        return new ConstantColumnVector(icebergType, numRows, value);
      } else {
        throw new IllegalStateException("Unknown dummy vector holder: " + holder);
      }
    } else if (rowIdMapping != null) {
      return new ColumnVectorWithFilter(holder, rowIdMapping);
    } else {
      return new IcebergArrowColumnVector(holder);
    }
  }
}
