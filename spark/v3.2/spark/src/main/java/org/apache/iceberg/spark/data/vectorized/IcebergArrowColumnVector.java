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
 * Spark 向量化读取 Iceberg 数据的列式访问组件，封装列向量相关能力。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 IcebergArrowColumnVector。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class IcebergArrowColumnVector extends ColumnVector {

  private final ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector> accessor;
  private final NullabilityHolder nullabilityHolder;

  /** 构造 IcebergArrowColumnVector 实例。 */
  public IcebergArrowColumnVector(VectorHolder holder) {
    super(SparkSchemaUtil.convert(holder.icebergType()));
    this.nullabilityHolder = holder.nullabilityHolder();
    this.accessor = ArrowVectorAccessors.getVectorAccessor(holder);
  }

  /** 执行该方法的具体逻辑。 */
  protected ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector> accessor() {
    return accessor;
  }

  /** 执行该方法的具体逻辑。 */
  protected NullabilityHolder nullabilityHolder() {
    return nullabilityHolder;
  }

  /** 释放底层资源。 */
  @Override
  public void close() {
    accessor.close();
  }

  /** 判断是否包含null。 */
  @Override
  public boolean hasNull() {
    return nullabilityHolder.hasNulls();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public int numNulls() {
    return nullabilityHolder.numNulls();
  }

  /** 判断是否nullat。 */
  @Override
  public boolean isNullAt(int rowId) {
    return nullabilityHolder.isNullAt(rowId) == 1;
  }

  /** 返回boolean。 */
  @Override
  public boolean getBoolean(int rowId) {
    return accessor.getBoolean(rowId);
  }

  /** 返回byte。 */
  @Override
  public byte getByte(int rowId) {
    throw new UnsupportedOperationException("Unsupported type - byte");
  }

  /** 返回short。 */
  @Override
  public short getShort(int rowId) {
    throw new UnsupportedOperationException("Unsupported type - short");
  }

  /** 返回int。 */
  @Override
  public int getInt(int rowId) {
    return accessor.getInt(rowId);
  }

  /** 返回long。 */
  @Override
  public long getLong(int rowId) {
    return accessor.getLong(rowId);
  }

  /** 返回float。 */
  @Override
  public float getFloat(int rowId) {
    return accessor.getFloat(rowId);
  }

  /** 返回double。 */
  @Override
  public double getDouble(int rowId) {
    return accessor.getDouble(rowId);
  }

  /** 返回array。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getArray(rowId);
  }

  /** 返回map。 */
  @Override
  public ColumnarMap getMap(int rowId) {
    throw new UnsupportedOperationException("Unsupported type - map");
  }

  /** 返回decimal。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getDecimal(rowId, precision, scale);
  }

  /** 返回utf8string。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getUTF8String(rowId);
  }

  /** 返回binary。 */
  @Override
  public byte[] getBinary(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getBinary(rowId);
  }

  /** 返回child。 */
  @Override
  public ArrowColumnVector getChild(int ordinal) {
    return accessor.childColumn(ordinal);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector>
      vectorAccessor() {
    return accessor;
  }
}
