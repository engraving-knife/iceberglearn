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
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：带过滤的列向量，在向量化读取时根据删除行集合标记被删除行。
 *
 * <p>设计意图：包装底层向量，按 position 集合置 null/标记，支持删除行过滤。
 *
 * <p>上下游关系：由 ColumnarBatchReader 在存在删除文件时使用。
 */
public class ColumnVectorWithFilter extends IcebergArrowColumnVector {
  private final int[] rowIdMapping;

  public ColumnVectorWithFilter(VectorHolder holder, int[] rowIdMapping) {
    super(holder);
    this.rowIdMapping = rowIdMapping;
  }
  /** 判断是否 NullAt。 */
  @Override
  public boolean isNullAt(int rowId) {
    return nullabilityHolder().isNullAt(rowIdMapping[rowId]) == 1;
  }
  /** 返回 Boolean 属性。 */
  @Override
  public boolean getBoolean(int rowId) {
    return accessor().getBoolean(rowIdMapping[rowId]);
  }
  /** 返回 Int 属性。 */
  @Override
  public int getInt(int rowId) {
    return accessor().getInt(rowIdMapping[rowId]);
  }
  /** 返回 Long 属性。 */
  @Override
  public long getLong(int rowId) {
    return accessor().getLong(rowIdMapping[rowId]);
  }
  /** 返回 Float 属性。 */
  @Override
  public float getFloat(int rowId) {
    return accessor().getFloat(rowIdMapping[rowId]);
  }
  /** 返回 Double 属性。 */
  @Override
  public double getDouble(int rowId) {
    return accessor().getDouble(rowIdMapping[rowId]);
  }
  /** 返回 Array 属性。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getArray(rowIdMapping[rowId]);
  }
  /** 返回 Decimal 属性。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getDecimal(rowIdMapping[rowId], precision, scale);
  }
  /** 返回 UTF8String 属性。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getUTF8String(rowIdMapping[rowId]);
  }
  /** 返回 Binary 属性。 */
  @Override
  public byte[] getBinary(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getBinary(rowIdMapping[rowId]);
  }
}
