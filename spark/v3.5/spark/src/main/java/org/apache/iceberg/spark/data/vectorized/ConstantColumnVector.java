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
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.vectorized.ColumnVector;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.sql.vectorized.ColumnarMap;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 常量列向量：所有行返回相同的常量值。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，vectorized 子包负责 向量化读取时的列向量实现）。
 *
 * <p>职责：在向量化读取中为常量列（如隐藏分区字段、常量注入字段）提供 {@link ColumnVector} 实现，使所有行的读取都返回同一个预设的常量值，避免实际存储 重复数据。支持所有
 * Spark 基本类型（boolean/byte/short/int/long/float/double、 Decimal、UTF8String、binary）及嵌套 struct 的
 * getChild。
 *
 * <p>设计意图：Iceberg 表中某些列的值在整个文件或分区中是恒定的（如分区列）， 通过常量列向量在内存中只保存一份值，显著减少内存占用。当常量为 null 时， hasNull 返回
 * true 且 numNulls 等于 batchSize。array 和 map 操作不支持，抛出异常。
 *
 * <p>上下游关系：被 {@link VectorizedSparkOrcReaders.StructConverter} 和其他 向量化读取器用于处理常量列和元数据列；继承 Spark 的
 * {@link ColumnVector}。
 */
class ConstantColumnVector extends ColumnVector {

  private final Type icebergType;
  private final Object constant;
  private final int batchSize;

  /**
   * 构造常量列向量。
   *
   * @param icebergType Iceberg 类型（NULL 向量时可为 null）
   * @param batchSize 批次行数
   * @param constant 常量值（可为 null）
   */
  ConstantColumnVector(Type icebergType, int batchSize, Object constant) {
    // the type may be unknown for NULL vectors
    super(icebergType != null ? SparkSchemaUtil.convert(icebergType) : null);
    this.icebergType = icebergType;
    this.constant = constant;
    this.batchSize = batchSize;
  }
  /** 关闭资源。 */
  @Override
  public void close() {}
  /** 判断是否存在 Null。 */
  @Override
  public boolean hasNull() {
    return constant == null;
  }
  /** 执行 numNulls 相关操作。 */
  @Override
  public int numNulls() {
    return constant == null ? batchSize : 0;
  }
  /** 判断是否 NullAt。 */
  @Override
  public boolean isNullAt(int rowId) {
    return constant == null;
  }
  /** 返回 Boolean 属性。 */
  @Override
  public boolean getBoolean(int rowId) {
    return (boolean) constant;
  }
  /** 返回 Byte 属性。 */
  @Override
  public byte getByte(int rowId) {
    return (byte) constant;
  }
  /** 返回 Short 属性。 */
  @Override
  public short getShort(int rowId) {
    return (short) constant;
  }
  /** 返回 Int 属性。 */
  @Override
  public int getInt(int rowId) {
    return (int) constant;
  }
  /** 返回 Long 属性。 */
  @Override
  public long getLong(int rowId) {
    return (long) constant;
  }
  /** 返回 Float 属性。 */
  @Override
  public float getFloat(int rowId) {
    return (float) constant;
  }
  /** 返回 Double 属性。 */
  @Override
  public double getDouble(int rowId) {
    return (double) constant;
  }
  /** 返回 Array 属性。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    throw new UnsupportedOperationException(this.getClass() + " does not implement getArray");
  }
  /** 返回 Map 属性。 */
  @Override
  public ColumnarMap getMap(int ordinal) {
    throw new UnsupportedOperationException(this.getClass() + " does not implement getMap");
  }
  /** 返回 Decimal 属性。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    return (Decimal) constant;
  }
  /** 返回 UTF8String 属性。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    return (UTF8String) constant;
  }
  /** 返回 Binary 属性。 */
  @Override
  public byte[] getBinary(int rowId) {
    return (byte[]) constant;
  }

  /**
   * 获取 struct 类型常量的子列向量。
   *
   * <p>逻辑：将常量值视为 InternalRow，按 ordinal 取出子字段值， 递归创建 ConstantColumnVector 返回。
   *
   * @param ordinal 子字段序号
   * @return 子字段的常量列向量
   */
  @Override
  public ColumnVector getChild(int ordinal) {
    InternalRow constantAsRow = (InternalRow) constant;
    Object childConstant = constantAsRow.get(ordinal, childType(ordinal));
    return new ConstantColumnVector(childIcebergType(ordinal), batchSize, childConstant);
  }
  /** 执行 childIcebergType 相关操作。 */
  private Type childIcebergType(int ordinal) {
    Types.StructType icebergTypeAsStruct = (Types.StructType) icebergType;
    return icebergTypeAsStruct.fields().get(ordinal).type();
  }
  /** 执行 childType 相关操作。 */
  private DataType childType(int ordinal) {
    StructType typeAsStruct = (StructType) type;
    return typeAsStruct.fields()[ordinal].dataType();
  }
}
