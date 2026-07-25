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
 * 表示 Iceberg _deleted 元数据列的 Spark {@link ColumnVector}。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），data.vectorized 子包。
 *
 * <p>职责：把内部维护的 isDeleted 布尔数组暴露为 Spark boolean 列向量，供查询读取 _deleted 列。
 *
 * <p>设计意图：只实现 getBoolean，其余类型访问一律抛出 {@link UnsupportedOperationException}， 因为 _deleted 列在 Spark
 * 侧只表现为 boolean。hasNull/numNulls 恒为 false/0。
 *
 * <p>上下游关系：被 {@link ColumnarBatchReader} 通过 ColumnVectorBuilder 构造，作为 ColumnarBatch 的一列。
 */
public class DeletedColumnVector extends ColumnVector {
  private final boolean[] isDeleted;

  /** 构造 DeletedColumnVector，type 必须为 boolean 类型，isDeleted 不能为 null。 */
  public DeletedColumnVector(Type type, boolean[] isDeleted) {
    super(SparkSchemaUtil.convert(type));
    Preconditions.checkArgument(isDeleted != null, "Boolean array isDeleted cannot be null");
    this.isDeleted = isDeleted;
  }
  /** 关闭资源。 */
  @Override
  public void close() {}
  /** 判断是否存在 Null。 */
  @Override
  public boolean hasNull() {
    return false;
  }
  /** 执行 numNulls 相关操作。 */
  @Override
  public int numNulls() {
    return 0;
  }
  /** 判断是否 NullAt。 */
  @Override
  public boolean isNullAt(int rowId) {
    return false;
  }

  /** 返回指定行是否被删除。 */
  @Override
  public boolean getBoolean(int rowId) {
    return isDeleted[rowId];
  }
  /** 返回 Byte 属性。 */
  @Override
  public byte getByte(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Short 属性。 */
  @Override
  public short getShort(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Int 属性。 */
  @Override
  public int getInt(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Long 属性。 */
  @Override
  public long getLong(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Float 属性。 */
  @Override
  public float getFloat(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Double 属性。 */
  @Override
  public double getDouble(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Array 属性。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Map 属性。 */
  @Override
  public ColumnarMap getMap(int ordinal) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Decimal 属性。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    throw new UnsupportedOperationException();
  }
  /** 返回 UTF8String 属性。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Binary 属性。 */
  @Override
  public byte[] getBinary(int rowId) {
    throw new UnsupportedOperationException();
  }
  /** 返回 Child 属性。 */
  @Override
  public ColumnVector getChild(int ordinal) {
    throw new UnsupportedOperationException();
  }
}
