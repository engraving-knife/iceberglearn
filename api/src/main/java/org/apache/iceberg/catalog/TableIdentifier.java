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
import java.util.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;

/**
 * 文件级说明：Iceberg catalog 中表的唯一标识符。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>由 {@link Namespace}（命名空间）和表名两部分组成，唯一标识 catalog 中的一张表。
 *   <li>提供工厂方法构造标识符，以及解析点号分隔字符串（"a.b.t"）的能力。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不可变值对象：内部字段为 final，equals/hashCode 基于 namespace 与 name 内容， 可安全作为 Map key 与序列化载体。
 *   <li>构造时强制校验：表名非空、namespace 非 null，防止非法标识符进入系统。
 *   <li>解析与序列化对称：{@link #parse(String)} 与 {@link #toString()} 均以点号分隔， 便于与 SQL 中 "db.table" 形式互转。
 * </ul>
 *
 * <p>上下游关系：被 {@link Catalog}、{@link SessionCatalog}、{@link ViewCatalog} 等接口 作为表/视图定位的标准参数；被
 * iceberg-core 及引擎模块广泛使用。
 */
public class TableIdentifier {

  private static final Splitter DOT = Splitter.on('.');

  private final Namespace namespace;
  private final String name;

  /**
   * 根据可变名称数组构造表标识符。
   *
   * <p>逻辑：将最后一个元素作为表名，其余元素组成命名空间。 例如 ("a","b","t") → namespace=["a","b"]、name="t"。
   *
   * @param names 命名空间各级 + 表名（至少一个元素）
   * @return 表标识符
   * @throws IllegalArgumentException 当 names 为 null 或长度为 0 时抛出
   */
  public static TableIdentifier of(String... names) {
    Preconditions.checkArgument(names != null, "Cannot create table identifier from null array");
    Preconditions.checkArgument(
        names.length > 0, "Cannot create table identifier without a table name");
    return new TableIdentifier(
        Namespace.of(Arrays.copyOf(names, names.length - 1)), names[names.length - 1]);
  }

  /**
   * 根据命名空间和表名构造表标识符。
   *
   * @param namespace 命名空间
   * @param name 表名
   * @return 表标识符
   */
  public static TableIdentifier of(Namespace namespace, String name) {
    return new TableIdentifier(namespace, name);
  }

  /**
   * 解析点号分隔的标识符字符串。
   *
   * <p>逻辑：以 '.' 分割字符串为多段，委托给 {@link #of(String...)} 构造。 例如 "a.b.t" → namespace=["a","b"]、name="t"。
   *
   * @param identifier 点号分隔的标识符字符串
   * @return 表标识符
   * @throws IllegalArgumentException 当 identifier 为 null 时抛出
   */
  public static TableIdentifier parse(String identifier) {
    Preconditions.checkArgument(identifier != null, "Cannot parse table identifier: null");
    Iterable<String> parts = DOT.split(identifier);
    return TableIdentifier.of(Iterables.toArray(parts, String.class));
  }

  private TableIdentifier(Namespace namespace, String name) {
    Preconditions.checkArgument(
        name != null && !name.isEmpty(), "Invalid table name: null or empty");
    Preconditions.checkArgument(namespace != null, "Invalid Namespace: null");
    this.namespace = namespace;
    this.name = name;
  }

  /**
   * 判断是否具有非空命名空间。
   *
   * @return 命名空间非空返回 true，否则 false
   */
  public boolean hasNamespace() {
    return !namespace.isEmpty();
  }

  /**
   * 返回所属命名空间。
   *
   * @return 命名空间
   */
  public Namespace namespace() {
    return namespace;
  }

  /**
   * 返回表名。
   *
   * @return 表名
   */
  public String name() {
    return name;
  }

  /**
   * 返回所有组成部分转为小写后的新标识符。
   *
   * <p>用于在不区分大小写的 catalog（如 Hive）中规范化标识符。
   *
   * @return 全小写的新标识符
   */
  public TableIdentifier toLowerCase() {
    String[] newLevels =
        Arrays.stream(namespace().levels()).map(String::toLowerCase).toArray(String[]::new);
    String newName = name().toLowerCase();
    return TableIdentifier.of(Namespace.of(newLevels), newName);
  }

  /**
   * 基于命名空间与表名判断相等。
   *
   * @param other 另一对象
   * @return 同类型且 namespace 与 name 都相等返回 true
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    TableIdentifier that = (TableIdentifier) other;
    return namespace.equals(that.namespace) && name.equals(that.name);
  }

  /**
   * 基于 namespace 与 name 计算 hashCode。
   *
   * @return 哈希值
   */
  @Override
  public int hashCode() {
    return Objects.hash(namespace, name);
  }

  /**
   * 返回点号连接的标识符字符串，如 "a.b.t"；无命名空间时仅返回表名。
   *
   * @return 标识符字符串
   */
  @Override
  public String toString() {
    if (hasNamespace()) {
      return namespace.toString() + "." + name;
    } else {
      return name;
    }
  }
}
