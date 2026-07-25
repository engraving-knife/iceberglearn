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
package org.apache.iceberg.catalog;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：Iceberg 目录服务中的命名空间（Namespace）抽象。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层，被 core 与各引擎模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>表示 {@link Catalog} 中表/视图的层级命名空间，由多级字符串名称组成（如 "a.b.c"）。
 *   <li>作为 {@link TableIdentifier} 的组成部分，定位表在 catalog 中的逻辑位置。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不可变值对象：内部以 String[] 存储各级名称，构造后不再可变；通过 equals/hashCode 基于内容比较，可作为 Map key 安全使用。
 *   <li>空对象模式：预定义 {@link #EMPTY_NAMESPACE} 单例表示根命名空间，避免重复创建。
 *   <li>防御性校验：禁止 null 级别与包含 NULL 字符（\u0000）的级别，后者常被底层存储用作 字段分隔符，混入会破坏序列化与查询。
 * </ul>
 *
 * <p>上下游关系：被 {@link Catalog}、{@link SupportsNamespaces}、{@link TableIdentifier}
 * 等广泛使用，作为表/视图定位和命名空间管理的基本载体。
 */
public class Namespace {
  private static final Namespace EMPTY_NAMESPACE = new Namespace(new String[] {});
  private static final Joiner DOT = Joiner.on('.');
  private static final Predicate<String> CONTAINS_NULL_CHARACTER =
      Pattern.compile("\u0000", Pattern.UNICODE_CHARACTER_CLASS).asPredicate();

  /**
   * 返回根（空）命名空间单例。
   *
   * @return 空 {@link Namespace}
   */
  public static Namespace empty() {
    return EMPTY_NAMESPACE;
  }

  /**
   * 根据多级名称创建命名空间。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>校验 levels 数组非 null；长度为 0 时返回 {@link #empty()} 单例。
   *   <li>逐级校验：每级非 null，且不包含 NULL 字符（\u0000，底层存储字段分隔符）。
   *   <li>校验通过后构造新 {@link Namespace}。
   * </ul>
   *
   * @param levels 命名空间各级名称（可变参数）
   * @return 由给定级别组成的命名空间
   * @throws IllegalArgumentException 当 levels 为 null 或某级包含 NULL 字符时抛出
   * @throws NullPointerException 当某级为 null 时抛出
   */
  public static Namespace of(String... levels) {
    Preconditions.checkArgument(null != levels, "Cannot create Namespace from null array");
    if (levels.length == 0) {
      return empty();
    }

    for (String level : levels) {
      Preconditions.checkNotNull(level, "Cannot create a namespace with a null level");
      Preconditions.checkArgument(
          !CONTAINS_NULL_CHARACTER.test(level),
          "Cannot create a namespace with the null-byte character");
    }

    return new Namespace(levels);
  }

  private final String[] levels;

  private Namespace(String[] levels) {
    this.levels = levels;
  }

  /**
   * 返回命名空间所有级别名称数组。
   *
   * @return 各级名称数组
   */
  public String[] levels() {
    return levels;
  }

  /**
   * 返回指定位置的级别名称。
   *
   * @param pos 位置下标（从 0 起）
   * @return 该位置的级别名称
   */
  public String level(int pos) {
    return levels[pos];
  }

  /**
   * 判断是否为根（空）命名空间。
   *
   * @return 长度为 0 返回 true，否则 false
   */
  public boolean isEmpty() {
    return levels.length == 0;
  }

  /**
   * 返回命名空间级别数。
   *
   * @return 级别数量
   */
  public int length() {
    return levels.length;
  }

  /**
   * 基于级别数组内容判断相等。
   *
   * @param other 另一对象
   * @return 同类型且各级名称依次相等返回 true
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    Namespace namespace = (Namespace) other;
    return Arrays.equals(levels, namespace.levels);
  }

  /**
   * 基于级别数组计算 hashCode。
   *
   * @return 哈希值
   */
  @Override
  public int hashCode() {
    return Arrays.hashCode(levels);
  }

  /**
   * 以点号（.）连接各级名称形成字符串表示，如 "a.b.c"。
   *
   * @return 点号连接的命名空间字符串
   */
  @Override
  public String toString() {
    return DOT.join(levels);
  }
}
