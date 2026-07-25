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

import java.io.Serializable;

/**
 * 空结构对象：{@link StructLike} 的空对象模式实现，表示一个没有字段的分区/结构值。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为未分区表（无分区字段）的分区值的占位实例。
 *   <li>所有读写方法均抛出 {@link UnsupportedOperationException} 或返回 0，避免被误用。
 * </ul>
 *
 * <p>设计意图：采用单例 + 私有构造器，避免为无分区表反复创建空对象；实现 {@link Serializable} 以支持在分布式引擎间序列化传递。{@link #get(int,
 * Class)} 与 {@link #set(int, Object)} 直接抛异常，强制调用方在"零字段"语义下不应尝试读写。
 *
 * <p>上下游关系：通过 {@link #get()} 全局获取单例，被 core 模块用于未分区表的分区值占位。
 */
class EmptyStructLike implements StructLike, Serializable {

  private static final EmptyStructLike INSTANCE = new EmptyStructLike();

  private EmptyStructLike() {}

  /**
   * 返回全局唯一的 {@link EmptyStructLike} 单例。
   *
   * @return 空结构单例
   */
  static EmptyStructLike get() {
    return INSTANCE;
  }

  /** 返回 0，表示没有任何字段。 */
  @Override
  public int size() {
    return 0;
  }

  /**
   * 不支持读取：空结构没有任何字段可读。
   *
   * @param pos 字段位置（无意义）
   * @param javaClass 期望的 Java 类型（无意义）
   * @return 永不返回
   * @throws UnsupportedOperationException 总是抛出
   */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    throw new UnsupportedOperationException("Can't retrieve values from an empty struct");
  }

  /**
   * 不支持写入：空结构没有任何字段可写。
   *
   * @param pos 字段位置（无意义）
   * @param value 待写入的值（无意义）
   * @throws UnsupportedOperationException 总是抛出
   */
  @Override
  public <T> void set(int pos, T value) {
    throw new UnsupportedOperationException("Can't modify an empty struct");
  }

  /**
   * 相等判断：所有 {@link EmptyStructLike} 实例（含子类同型）视为相等。
   *
   * <p>逻辑：同一引用直接相等；否则要求对方非 null 且与自身属于同一个具体类。
   *
   * @param other 比较对象
   * @return 是否相等
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    return other != null && getClass() == other.getClass();
  }

  /** 返回固定的哈希值 0。 */
  @Override
  public int hashCode() {
    return 0;
  }

  /** 返回固定字符串 "StructLike{}"。 */
  @Override
  public String toString() {
    return "StructLike{}";
  }
}
