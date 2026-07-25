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
package org.apache.iceberg;

/**
 * 文件级说明：按位置访问结构化数据的接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，是最基础的数据访问抽象之一）。
 *
 * <p>职责：提供按字段位置（而非字段名）读写顶层字段值的能力，是 Iceberg 内部数据传递的 核心抽象。
 *
 * <p>设计意图：按位置访问比按名访问更高效（避免哈希查找），适合高频内部数据流转。 本接口仅支持顶层字段，不支持嵌套字段访问。通过泛型方法 {@link #get(int, Class)} 保证
 * 类型安全。被广泛实现于 {@link PartitionKey}、{@link org.apache.iceberg.data.Record}、 各种投影/聚合中间结构等。
 *
 * <p>上下游关系：被几乎所有数据处理路径使用——分区计算、表达式求值、序列化/反序列化、 manifest 读写等。
 */
public interface StructLike {
  /**
   * 返回结构中字段的数量。
   *
   * @return 字段数量
   */
  int size();

  /**
   * 按位置获取字段值并强转为指定 Java 类型。
   *
   * @param pos 字段位置（从 0 开始）
   * @param javaClass 期望的 Java 类型
   * @return 字段值
   */
  <T> T get(int pos, Class<T> javaClass);

  /**
   * 按位置设置字段值。
   *
   * @param pos 字段位置（从 0 开始）
   * @param value 要设置的值
   */
  <T> void set(int pos, T value);
}
