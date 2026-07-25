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
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ColumnVector;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.sql.vectorized.ColumnarMap;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 行位置列向量：为每一行生成其在文件中的全局行号（long 类型）。
 *
 * <p>所属模块：iceberg-spark（data/vectorized 子包，用于向量化读取路径）。
 *
 * <p>职责：作为虚拟列向量，按 {@code 批次偏移 + 行内序号} 计算并返回每行的全局行位置， 供需要行号的下游（如位置删除匹配）使用。
 *
 * <p>设计意图：行位置不存储在数据文件中，而是由读取时动态计算，因此本类仅实现 {@code getLong} 返回 {@code batchOffsetInFile +
 * rowId}，其余类型访问一律抛出 UnsupportedOperationException。 hasNull/numNulls 恒为无空值。
 *
 * <p>上下游关系：继承 Spark {@link ColumnVector}，由向量化读取器在需要行位置列时构造。
 */
public class RowPositionColumnVector extends ColumnVector {

  private final long batchOffsetInFile;

  /**
   * 构造行位置列向量。
   *
   * @param batchOffsetInFile 当前批次在文件中的起始行偏移
   */
  RowPositionColumnVector(long batchOffsetInFile) {
    super(SparkSchemaUtil.convert(Types.LongType.get()));
    this.batchOffsetInFile = batchOffsetInFile;
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
  /** 返回 Boolean 属性。 */
  @Override
  public boolean getBoolean(int rowId) {
    throw new UnsupportedOperationException();
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

  /**
   * 返回指定行的全局行位置（批次偏移 + 行内序号）。
   *
   * @param rowId 行内序号
   * @return 该行在文件中的全局行号
   */
  @Override
  public long getLong(int rowId) {
    return batchOffsetInFile + rowId;
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
