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

import org.apache.iceberg.arrow.vectorized.ArrowVectorAccessor;
import org.apache.iceberg.arrow.vectorized.NullabilityHolder;
import org.apache.iceberg.arrow.vectorized.VectorHolder;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ArrowColumnVector;
import org.apache.spark.sql.vectorized.ColumnVector;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.sql.vectorized.ColumnarMap;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark {@link ColumnVector} 的 Iceberg Arrow 实现。
 *
 * <p>所属模块：iceberg-spark（data/vectorized 子包）。本类大量借鉴 Spark {@link ArrowColumnVector}， 主要区别在于空值判断依赖
 * {@link NullabilityHolder} 而非 Arrow 向量的 validity 向量，以适配 Iceberg 向量化读取路径预先计算的空值信息。
 *
 * <p>职责：包装 {@link ArrowVectorAccessor}，向 Spark 暴露列式取值接口（基本类型、decimal、 字符串、二进制、数组、子列等），并按需做空值检查。
 *
 * <p>上下游关系：由 {@link ColumnVectorBuilder} 构造，供 Spark 向量化读取器使用。
 */
public class IcebergArrowColumnVector extends ColumnVector {

  private final ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector> accessor;
  private final NullabilityHolder nullabilityHolder;

  /** 由向量持有者构造：转换 Iceberg 类型为 Spark 类型，取空值持有器与访问器。 */
  public IcebergArrowColumnVector(VectorHolder holder) {
    super(SparkSchemaUtil.convert(holder.icebergType()));
    this.nullabilityHolder = holder.nullabilityHolder();
    this.accessor = ArrowVectorAccessors.getVectorAccessor(holder);
  }

  /** 返回底层 Arrow 向量访问器。 */
  protected ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector> accessor() {
    return accessor;
  }

  /** 返回空值持有器。 */
  protected NullabilityHolder nullabilityHolder() {
    return nullabilityHolder;
  }
  /** 关闭资源。 */
  @Override
  public void close() {
    accessor.close();
  }

  /** 是否含空值。 */
  @Override
  public boolean hasNull() {
    return nullabilityHolder.hasNulls();
  }

  /** 返回空值数量。 */
  @Override
  public int numNulls() {
    return nullabilityHolder.numNulls();
  }

  /** 判断指定行是否为空。 */
  @Override
  public boolean isNullAt(int rowId) {
    return nullabilityHolder.isNullAt(rowId) == 1;
  }
  /** 返回 Boolean 属性。 */
  @Override
  public boolean getBoolean(int rowId) {
    return accessor.getBoolean(rowId);
  }
  /** 返回 Byte 属性。 */
  @Override
  public byte getByte(int rowId) {
    throw new UnsupportedOperationException("Unsupported type - byte");
  }
  /** 返回 Short 属性。 */
  @Override
  public short getShort(int rowId) {
    throw new UnsupportedOperationException("Unsupported type - short");
  }
  /** 返回 Int 属性。 */
  @Override
  public int getInt(int rowId) {
    return accessor.getInt(rowId);
  }
  /** 返回 Long 属性。 */
  @Override
  public long getLong(int rowId) {
    return accessor.getLong(rowId);
  }
  /** 返回 Float 属性。 */
  @Override
  public float getFloat(int rowId) {
    return accessor.getFloat(rowId);
  }
  /** 返回 Double 属性。 */
  @Override
  public double getDouble(int rowId) {
    return accessor.getDouble(rowId);
  }

  /** 返回指定行的数组，空值返回 null。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getArray(rowId);
  }
  /** 返回 Map 属性。 */
  @Override
  public ColumnarMap getMap(int rowId) {
    throw new UnsupportedOperationException("Unsupported type - map");
  }

  /** 返回指定行的 decimal，空值返回 null。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getDecimal(rowId, precision, scale);
  }

  /** 返回指定行的 UTF8 字符串，空值返回 null。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getUTF8String(rowId);
  }

  /** 返回指定行的二进制值，空值返回 null。 */
  @Override
  public byte[] getBinary(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getBinary(rowId);
  }

  /** 返回指定序号的子列向量。 */
  @Override
  public ArrowColumnVector getChild(int ordinal) {
    return accessor.childColumn(ordinal);
  }

  public ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector>
      vectorAccessor() {
    return accessor;
  }
}
