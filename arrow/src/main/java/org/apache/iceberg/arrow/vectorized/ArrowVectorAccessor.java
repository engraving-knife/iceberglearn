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

import org.apache.arrow.vector.ValueVector;

/**
 * 文件级说明：Arrow 向量值的统一访问器基类（模板方法模式）。
 *
 * <p>所属模块：iceberg-arrow（向量化读取链路中对外暴露的向量读取接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义按行读取各类 Arrow 向量值的统一接口：布尔、整型、长整型、浮点、双精度、 二进制、Decimal、UTF8 字符串、数组等。
 *   <li>持有底层 {@link ValueVector} 与可选的子列向量，并负责它们的关闭。
 * </ul>
 *
 * <p>设计意图：基类中各 {@code getXxx} 方法默认抛出 {@link UnsupportedOperationException}，
 * 由具体子类（按向量类型生成）只覆写其支持的方法，避免庞大的类型分支。泛型参数 {@code DecimalT/Utf8StringT/ArrayT/ChildVectorT}
 * 使访问器能适配不同引擎的 Decimal 与 字符串表示（如 Hive 的 HiveDecimal、Spark 的 UTF8String）。
 *
 * <p>上下游关系：由 {@link GenericArrowVectorAccessorFactory} 生成具体子类实例；被 {@link
 * DictEncodedArrowConverter}、{@link ColumnVector} 等用于读取向量中的值。
 */
public class ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
    implements AutoCloseable {
  private final ValueVector vector;
  private final ChildVectorT[] childColumns;

  /**
   * 仅包装单个向量的构造方法（无子列）。
   *
   * @param vector 被包装的 Arrow 向量
   */
  protected ArrowVectorAccessor(ValueVector vector) {
    this(vector, null);
  }

  /**
   * 包装向量及其子列的构造方法。
   *
   * @param vector 被包装的 Arrow 向量
   * @param children 子列向量数组（用于嵌套类型）
   */
  protected ArrowVectorAccessor(ValueVector vector, ChildVectorT[] children) {
    this.vector = vector;
    this.childColumns = children;
  }

  @Override
  /**
   * 关闭本访问器持有的向量及子列向量。
   *
   * <p>逻辑：先逐个关闭子列（捕获异常并包装为 RuntimeException），再关闭主向量。
   */
  public void close() {
    if (childColumns != null) {
      for (ChildVectorT column : childColumns) {
        try {
          // Closing an ArrowColumnVector is expected to not throw any exception
          column.close();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    vector.close();
  }

  /**
   * 读取指定行的布尔值，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @return 布尔值
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public boolean getBoolean(int rowId) {
    throw new UnsupportedOperationException("Unsupported type: boolean");
  }

  /**
   * 读取指定行的 int 值，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @return int 值
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public int getInt(int rowId) {
    throw new UnsupportedOperationException("Unsupported type: int");
  }

  /**
   * 读取指定行的 long 值，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @return long 值
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public long getLong(int rowId) {
    throw new UnsupportedOperationException("Unsupported type: long");
  }

  /**
   * 读取指定行的 float 值，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @return float 值
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public float getFloat(int rowId) {
    throw new UnsupportedOperationException("Unsupported type: float");
  }

  /**
   * 读取指定行的 double 值，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @return double 值
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public double getDouble(int rowId) {
    throw new UnsupportedOperationException("Unsupported type: double");
  }

  /**
   * 读取指定行的二进制字节数组，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @return 字节数组
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public byte[] getBinary(int rowId) {
    throw new UnsupportedOperationException("Unsupported type: binary");
  }

  /**
   * 读取指定行的 Decimal 值，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @param precision 精度
   * @param scale 标度
   * @return Decimal 值（类型由泛型 {@code DecimalT} 决定）
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public DecimalT getDecimal(int rowId, int precision, int scale) {
    throw new UnsupportedOperationException("Unsupported type: decimal");
  }

  /**
   * 读取指定行的 UTF8 字符串，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @return 字符串（类型由泛型 {@code Utf8StringT} 决定）
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public Utf8StringT getUTF8String(int rowId) {
    throw new UnsupportedOperationException("Unsupported type: UTF8String");
  }

  /**
   * 读取指定行的数组，默认不支持，由子类覆写。
   *
   * @param rowId 行下标
   * @return 数组（类型由泛型 {@code ArrayT} 决定）
   * @throws UnsupportedOperationException 若子类未覆写
   */
  public ArrayT getArray(int rowId) {
    throw new UnsupportedOperationException("Unsupported type: array");
  }

  /**
   * 获取指定位置的子列向量。
   *
   * @param pos 子列位置
   * @return 子列向量
   * @throws IndexOutOfBoundsException 若不存在子列
   */
  public ChildVectorT childColumn(int pos) {
    if (childColumns != null) {
      return childColumns[pos];
    } else {
      throw new IndexOutOfBoundsException("Child columns is null hence cannot find index: " + pos);
    }
  }

  /**
   * 返回底层 Arrow 向量。
   *
   * @return 被包装的 {@link ValueVector}
   */
  public final ValueVector getVector() {
    return vector;
  }
}
