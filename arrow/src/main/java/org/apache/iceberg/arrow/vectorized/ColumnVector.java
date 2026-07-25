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

import java.math.BigDecimal;
import org.apache.arrow.vector.FieldVector;
import org.apache.iceberg.arrow.DictEncodedArrowConverter;

/**
 * 文件级说明：单列数据的访问器，包装 Arrow {@link FieldVector} 并提供按行读取能力（借鉴 Spark 的 ColumnVector）。
 *
 * <p>所属模块：iceberg-arrow（向量化读取结果中对单列的对外接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 {@link VectorHolder}、对应的 {@link ArrowVectorAccessor} 与 {@link NullabilityHolder}。
 *   <li>提供布尔、整型、长整型、浮点、双精度、字符串、二进制、Decimal 的按行读取， 并处理空值（空值返回 null）。
 *   <li>支持获取原始（可能字典编码）向量或解码后的 Arrow 向量。
 * </ul>
 *
 * <p>设计意图：通过 accessor 委托屏蔽底层向量类型差异；空值判断由 NullabilityHolder 提供， 避免重复扫描有效性向量。支持的 Iceberg 类型包括
 * Boolean/Integer/Long/Float/Double/String/ Binary/Timestamp/Date/Time/UUID/Decimal。
 *
 * <p>上下游关系：由 {@link ColumnarBatch} 持有；下游被引擎按行消费； 解码委托给 {@link DictEncodedArrowConverter}。
 */
public class ColumnVector implements AutoCloseable {
  private final VectorHolder vectorHolder;
  private final ArrowVectorAccessor<?, String, ?, ?> accessor;
  private final NullabilityHolder nullabilityHolder;

  /**
   * 构造列向量，从持有者获取空值信息与访问器。
   *
   * @param vectorHolder 向量持有者
   */
  ColumnVector(VectorHolder vectorHolder) {
    this.vectorHolder = vectorHolder;
    this.nullabilityHolder = vectorHolder.nullabilityHolder();
    this.accessor = getVectorAccessor(vectorHolder);
  }

  /**
   * 返回可能字典编码的原始 {@link FieldVector}。
   *
   * @return 原始 FieldVector 实例
   */
  public FieldVector getFieldVector() {
    return vectorHolder.vector();
  }

  /**
   * 解码字典编码向量并返回实际的 Arrow 向量。
   *
   * @return 解码后的 FieldVector 实例
   */
  public FieldVector getArrowVector() {
    return DictEncodedArrowConverter.toArrowVector(vectorHolder, accessor);
  }

  /**
   * 该列是否包含空值。
   *
   * @return 含空值返回 true
   */
  public boolean hasNull() {
    return nullabilityHolder.hasNulls();
  }

  /**
   * 返回该列的空值数量。
   *
   * @return 空值数
   */
  public int numNulls() {
    return nullabilityHolder.numNulls();
  }

  @Override
  /** 关闭底层访问器及其向量资源。 */
  public void close() {
    accessor.close();
  }

  /**
   * 判断指定行是否为空。
   *
   * @param rowId 行下标
   * @return 为空返回 true
   */
  public boolean isNullAt(int rowId) {
    return nullabilityHolder.isNullAt(rowId) == 1;
  }

  /**
   * 读取指定行的布尔值。
   *
   * @param rowId 行下标
   * @return 布尔值
   */
  public boolean getBoolean(int rowId) {
    return accessor.getBoolean(rowId);
  }

  /**
   * 读取指定行的 int 值。
   *
   * @param rowId 行下标
   * @return int 值
   */
  public int getInt(int rowId) {
    return accessor.getInt(rowId);
  }

  /**
   * 读取指定行的 long 值。
   *
   * @param rowId 行下标
   * @return long 值
   */
  public long getLong(int rowId) {
    return accessor.getLong(rowId);
  }

  /**
   * 读取指定行的 float 值。
   *
   * @param rowId 行下标
   * @return float 值
   */
  public float getFloat(int rowId) {
    return accessor.getFloat(rowId);
  }

  /**
   * 读取指定行的 double 值。
   *
   * @param rowId 行下标
   * @return double 值
   */
  public double getDouble(int rowId) {
    return accessor.getDouble(rowId);
  }

  /**
   * 读取指定行的字符串，空值返回 null。
   *
   * @param rowId 行下标
   * @return 字符串，或 null
   */
  public String getString(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getUTF8String(rowId);
  }

  /**
   * 读取指定行的二进制数据，空值返回 null。
   *
   * @param rowId 行下标
   * @return 字节数组，或 null
   */
  public byte[] getBinary(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor.getBinary(rowId);
  }

  /**
   * 读取指定行的 Decimal 值，空值返回 null。
   *
   * @param rowId 行下标
   * @param precision 精度
   * @param scale 标度
   * @return BigDecimal，或 null
   */
  public BigDecimal getDecimal(int rowId, int precision, int scale) {
    if (isNullAt(rowId)) {
      return null;
    }
    return (BigDecimal) accessor.getDecimal(rowId, precision, scale);
  }

  /**
   * 通过 {@link ArrowVectorAccessors} 构造向量访问器。
   *
   * @param holder 向量持有者
   * @return 访问器实例
   */
  private static ArrowVectorAccessor<?, String, ?, ?> getVectorAccessor(VectorHolder holder) {
    return ArrowVectorAccessors.getVectorAccessor(holder);
  }
}
