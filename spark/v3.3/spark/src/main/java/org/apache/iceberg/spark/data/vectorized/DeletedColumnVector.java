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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.types.Type;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ColumnVector;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.sql.vectorized.ColumnarMap;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark 向量化读取 Iceberg 数据的列式访问组件，实现 DELETE 行级操作。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 DeletedColumnVector。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class DeletedColumnVector extends ColumnVector {
  private final boolean[] isDeleted;

  /** 构造 DeletedColumnVector 实例。 */
  public DeletedColumnVector(Type type, boolean[] isDeleted) {
    super(SparkSchemaUtil.convert(type));
    Preconditions.checkArgument(isDeleted != null, "Boolean array isDeleted cannot be null");
    this.isDeleted = isDeleted;
  }

  /** 释放底层资源。 */
  @Override
  public void close() {}

  /** 判断是否包含null。 */
  @Override
  public boolean hasNull() {
    return false;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public int numNulls() {
    return 0;
  }

  /** 判断是否nullat。 */
  @Override
  public boolean isNullAt(int rowId) {
    return false;
  }

  /** 返回boolean。 */
  @Override
  public boolean getBoolean(int rowId) {
    return isDeleted[rowId];
  }

  /** 返回byte。 */
  @Override
  public byte getByte(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回short。 */
  @Override
  public short getShort(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回int。 */
  @Override
  public int getInt(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回long。 */
  @Override
  public long getLong(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回float。 */
  @Override
  public float getFloat(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回double。 */
  @Override
  public double getDouble(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回array。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回map。 */
  @Override
  public ColumnarMap getMap(int ordinal) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回decimal。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回utf8string。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回binary。 */
  @Override
  public byte[] getBinary(int rowId) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }

  /** 返回child。 */
  @Override
  public ColumnVector getChild(int ordinal) {
    /** 执行该方法的具体逻辑。 */
    throw new UnsupportedOperationException();
  }
}
