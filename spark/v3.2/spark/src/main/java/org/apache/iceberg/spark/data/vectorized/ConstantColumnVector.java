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

import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.types.Type;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ColumnVector;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.sql.vectorized.ColumnarMap;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark 向量化读取 Iceberg 数据的列式访问组件，封装列向量相关能力。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 ConstantColumnVector。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
class ConstantColumnVector extends ColumnVector {

  private final Object constant;
  private final int batchSize;

  ConstantColumnVector(Type type, int batchSize, Object constant) {
    super(SparkSchemaUtil.convert(type));
    this.constant = constant;
    this.batchSize = batchSize;
  }

  /** 释放底层资源。 */
  @Override
  public void close() {}

  /** 判断是否包含null。 */
  @Override
  public boolean hasNull() {
    return constant == null;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public int numNulls() {
    return constant == null ? batchSize : 0;
  }

  /** 判断是否nullat。 */
  @Override
  public boolean isNullAt(int rowId) {
    return constant == null;
  }

  /** 返回boolean。 */
  @Override
  public boolean getBoolean(int rowId) {
    return (boolean) constant;
  }

  /** 返回byte。 */
  @Override
  public byte getByte(int rowId) {
    return (byte) constant;
  }

  /** 返回short。 */
  @Override
  public short getShort(int rowId) {
    return (short) constant;
  }

  /** 返回int。 */
  @Override
  public int getInt(int rowId) {
    return (int) constant;
  }

  /** 返回long。 */
  @Override
  public long getLong(int rowId) {
    return (long) constant;
  }

  /** 返回float。 */
  @Override
  public float getFloat(int rowId) {
    return (float) constant;
  }

  /** 返回double。 */
  @Override
  public double getDouble(int rowId) {
    return (double) constant;
  }

  /** 返回array。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    throw new UnsupportedOperationException("ConstantColumnVector only supports primitives");
  }

  /** 返回map。 */
  @Override
  public ColumnarMap getMap(int ordinal) {
    throw new UnsupportedOperationException("ConstantColumnVector only supports primitives");
  }

  /** 返回decimal。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    return (Decimal) constant;
  }

  /** 返回utf8string。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    return (UTF8String) constant;
  }

  /** 返回binary。 */
  @Override
  public byte[] getBinary(int rowId) {
    return (byte[]) constant;
  }

  /** 返回child。 */
  @Override
  public ColumnVector getChild(int ordinal) {
    throw new UnsupportedOperationException("ConstantColumnVector only supports primitives");
  }
}
