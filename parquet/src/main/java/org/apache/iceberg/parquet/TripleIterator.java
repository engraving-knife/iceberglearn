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

import java.util.Iterator;
import org.apache.parquet.io.api.Binary;

/**
 * 文件级说明：Parquet 三元组（value + definition level + repetition level）迭代器接口。
 *
 * <p>所属模块：iceberg-parquet（Parquet 列式读取底座，定义列值读取的统一抽象）。
 *
 * <p>职责：在 {@link Iterator} 之上扩展 Parquet 列式存储所需的语义：
 *
 * <ul>
 *   <li>提供当前三元组的定义级别与重复级别（不推进迭代器）。
 *   <li>按原始类型提供拆箱后的 nextXxx 取值方法，避免装箱开销。
 *   <li>提供 nextNull 占位读取，用于跳过 null 值时仍推进 def/rep level。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>默认抛出异常：nextBoolean/nextInteger 等方法默认抛 {@link
 *       UnsupportedOperationException}，由具体实现按列类型重写对应方法， 调用方按需调用即可。
 *   <li>拆箱读取：直接返回 boolean/int/long 等原始类型，减少自动装箱开销。
 * </ul>
 *
 * <p>上下游关系：被 {@link ColumnIterator} 实现；上层由 {@link ParquetValueReader} 持有并消费。
 */
interface TripleIterator<T> extends Iterator<T> {
  /**
   * 返回当前三元组的定义级别（definition level），不推进迭代器。
   *
   * <p>定义级别表示该值在 schema 可选嵌套路径上的深度，用于解码 null。
   *
   * @return 当前三元组的定义级别
   * @throws java.util.NoSuchElementException 没有更多元素时
   */
  int currentDefinitionLevel();

  /**
   * 返回当前三元组的重复级别（repetition level），不推进迭代器。
   *
   * <p>重复级别表示当前值在重复嵌套结构中的位置，用于解码 list/map。 没有当前元素时返回 0。
   *
   * @return 当前三元组的重复级别，无当前元素时为 0
   * @throws java.util.NoSuchElementException 没有更多元素时
   */
  int currentRepetitionLevel();

  /**
   * 返回下一个值并拆箱为 boolean，同时推进迭代器。
   *
   * @return 下一个 boolean 值
   * @throws java.util.NoSuchElementException 没有更多元素时
   * @throws UnsupportedOperationException 当前列非 boolean 类型
   */
  default boolean nextBoolean() {
    throw new UnsupportedOperationException("Not a boolean column");
  }

  /**
   * 返回下一个值并拆箱为 int，同时推进迭代器。
   *
   * @return 下一个 int 值
   * @throws java.util.NoSuchElementException 没有更多元素时
   * @throws UnsupportedOperationException 当前列非 int 类型
   */
  default int nextInteger() {
    throw new UnsupportedOperationException("Not an integer column");
  }

  /**
   * 返回下一个值并拆箱为 long，同时推进迭代器。
   *
   * @return 下一个 long 值
   * @throws java.util.NoSuchElementException 没有更多元素时
   * @throws UnsupportedOperationException 当前列非 long 类型
   */
  default long nextLong() {
    throw new UnsupportedOperationException("Not a long column");
  }

  /**
   * 返回下一个值并拆箱为 float，同时推进迭代器。
   *
   * @return 下一个 float 值
   * @throws java.util.NoSuchElementException 没有更多元素时
   * @throws UnsupportedOperationException 当前列非 float 类型
   */
  default float nextFloat() {
    throw new UnsupportedOperationException("Not a float column");
  }

  /**
   * 返回下一个值并拆箱为 double，同时推进迭代器。
   *
   * @return 下一个 double 值
   * @throws java.util.NoSuchElementException 没有更多元素时
   * @throws UnsupportedOperationException 当前列非 double 类型
   */
  default double nextDouble() {
    throw new UnsupportedOperationException("Not a double column");
  }

  /**
   * 返回下一个值并包装为 {@link Binary}，同时推进迭代器。
   *
   * @return 下一个 Binary 值
   * @throws java.util.NoSuchElementException 没有更多元素时
   * @throws UnsupportedOperationException 当前列非 binary 类型
   */
  default Binary nextBinary() {
    throw new UnsupportedOperationException("Not a binary column");
  }

  /**
   * 读取一个 null 占位值并推进迭代器，仅推进 def/rep level 状态，不产生实际数据。
   *
   * @param <N> 形式类型，恒返回 null
   * @return null
   * @throws java.util.NoSuchElementException 没有更多元素时
   */
  <N> N nextNull();
}
