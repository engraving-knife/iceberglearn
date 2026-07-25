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
package org.apache.iceberg.parquet;

import org.apache.parquet.io.api.Binary;

/**
 * 文件级说明：Parquet 三元组写入器接口。
 *
 * <p>所属模块：iceberg-parquet（写入底层接口，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：定义按列写入 Parquet 三元组（value + repetition level + definition level）的接口。
 * 提供类型化的写入方法（writeBoolean/Integer/Long/Float/Double/Binary）和 null 写入方法。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>接口隔离：将列写入抽象与 Parquet 底层 ColumnWriter 解耦，便于测试和适配。
 *   <li>默认抛异常：类型化方法默认抛 UnsupportedOperationException，由具体实现（如 {@link ColumnWriter}）
 *       覆写需要的方法，避免不支持的类型误写。
 * </ul>
 *
 * <p>上下游关系：被 {@link ColumnWriter} 实现；被 ParquetValueWriters 的列写入器使用。
 *
 * @param <T> 写入的值类型
 */
public interface TripleWriter<T> {
  // TODO: should definition level be included, or should it be part of the column?

  /**
   * 写入一个值。
   *
   * @param rl 重复级别
   * @param value 要写入的值
   */
  void write(int rl, T value);

  /**
   * 写入 boolean 值（默认抛异常，由支持 boolean 的列覆写）。
   *
   * @param rl 重复级别
   * @param value boolean 值
   */
  default void writeBoolean(int rl, boolean value) {
    throw new UnsupportedOperationException("Not a boolean column");
  }

  /**
   * 写入 int 值（默认抛异常，由支持 int 的列覆写）。
   *
   * @param rl 重复级别
   * @param value int 值
   */
  default void writeInteger(int rl, int value) {
    throw new UnsupportedOperationException("Not an integer column");
  }

  /**
   * 写入 long 值（默认抛异常，由支持 long 的列覆写）。
   *
   * @param rl 重复级别
   * @param value long 值
   */
  default void writeLong(int rl, long value) {
    throw new UnsupportedOperationException("Not an long column");
  }

  /**
   * 写入 float 值（默认抛异常，由支持 float 的列覆写）。
   *
   * @param rl 重复级别
   * @param value float 值
   */
  default void writeFloat(int rl, float value) {
    throw new UnsupportedOperationException("Not an float column");
  }

  /**
   * 写入 double 值（默认抛异常，由支持 double 的列覆写）。
   *
   * @param rl 重复级别
   * @param value double 值
   */
  default void writeDouble(int rl, double value) {
    throw new UnsupportedOperationException("Not an double column");
  }

  /**
   * 写入 Binary 值（默认抛异常，由支持 binary 的列覆写）。
   *
   * @param rl 重复级别
   * @param value Binary 值
   */
  default void writeBinary(int rl, Binary value) {
    throw new UnsupportedOperationException("Not an binary column");
  }

  /**
   * 写入 null 值的三元组。
   *
   * @param rl 重复级别
   * @param dl 定义级别（指示 null 在嵌套结构中的位置）
   */
  void writeNull(int rl, int dl);
}
