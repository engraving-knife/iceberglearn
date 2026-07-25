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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 文件级说明：支持命名空间（Namespace）管理的 Catalog 扩展接口。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义命名空间的创建（createNamespace）、列举（listNamespaces）、删除（dropNamespace）、
 *       属性加载/设置/移除（loadNamespaceMetadata/setProperties/removeProperties）等操作。
 *   <li>为 {@link Catalog} 实现提供“是否支持命名空间”这一可选能力的契约。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>能力混合接口：Catalog 通过实现本接口声明支持命名空间管理，未实现则视为不支持， 避免在 Catalog 主接口中堆砌与命名空间相关的方法。
 *   <li>命名空间与对象存在性解耦：约定若表/视图/函数存在，则其各级父命名空间也必须 存在并能被发现；但实现可不维护独立于对象的命名空间，且允许在不抛 {@link
 *       NoSuchNamespaceException} 的情况下发现对象。
 *   <li>可选操作：createNamespace/setProperties 等可抛 {@link UnsupportedOperationException}
 *       以声明本实现不支持该能力（如基于反射的函数 catalog 以 Java 包为命名空间）。
 * </ul>
 *
 * <p>上下游关系：由支持命名空间的 Catalog 实现（如 HiveCatalog、JdbcCatalog、REST catalog 等） 实现；上层引擎通过 instanceof
 * 判断并调用以完成命名空间管理。
 */
public interface SupportsNamespaces {
  /**
   * 创建命名空间（不带属性）。
   *
   * @param namespace 命名空间
   * @throws AlreadyExistsException 当命名空间已存在时抛出
   * @throws UnsupportedOperationException 当实现不支持创建命名空间时抛出
   */
  default void createNamespace(Namespace namespace) {
    createNamespace(namespace, ImmutableMap.of());
  }

  /**
   * 创建命名空间（带属性）。
   *
   * @param namespace 多级命名空间
   * @param metadata 命名空间属性映射
   * @throws AlreadyExistsException 当命名空间已存在时抛出
   * @throws UnsupportedOperationException 当实现不支持创建命名空间时抛出
   */
  void createNamespace(Namespace namespace, Map<String, String> metadata);

  /**
   * 列举顶层命名空间。
   *
   * <p>若某对象（表/视图/函数）存在，其各级父命名空间也必须存在并被本方法返回。 例如存在表 a.b.t 时，本方法须返回 ["a"]。
   *
   * @return 顶层命名空间列表
   */
  default List<Namespace> listNamespaces() {
    return listNamespaces(Namespace.empty());
  }

  /**
   * 列举指定命名空间的子命名空间。
   *
   * <p>例如对已存在的表 a.b.c.table 与 a.b.d.table：
   *
   * <ul>
   *   <li>传入 {@code Namespace.empty()} 返回 {@code Namespace.of("a")}
   *   <li>传入 {@code Namespace.of("a")} 返回 {@code Namespace.of("a","b")}
   *   <li>传入 {@code Namespace.of("a","b")} 返回 {@code Namespace.of("a","b","c")} 与 {@code
   *       Namespace.of("a","b","d")}
   *   <li>传入 {@code Namespace.of("a","b","c")} 返回空列表（无子命名空间）
   * </ul>
   *
   * @param namespace 父命名空间
   * @return 子命名空间列表
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出（可选）
   */
  List<Namespace> listNamespaces(Namespace namespace) throws NoSuchNamespaceException;

  /**
   * 加载命名空间的元数据属性。
   *
   * @param namespace 命名空间
   * @return 命名空间属性映射
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出（可选）
   */
  Map<String, String> loadNamespaceMetadata(Namespace namespace) throws NoSuchNamespaceException;

  /**
   * 删除命名空间。命名空间存在并已删除返回 true。
   *
   * @param namespace 命名空间
   * @return 已删除返回 true，否则 false
   * @throws NamespaceNotEmptyException 当命名空间非空时抛出
   */
  boolean dropNamespace(Namespace namespace) throws NamespaceNotEmptyException;

  /**
   * 批量设置命名空间属性。
   *
   * <p>未出现在给定映射中的属性不会被修改或移除。
   *
   * @param namespace 命名空间
   * @param properties 待设置的属性
   * @return 成功设置返回 true
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出（可选）
   * @throws UnsupportedOperationException 当实现不支持命名空间属性时抛出
   */
  boolean setProperties(Namespace namespace, Map<String, String> properties)
      throws NoSuchNamespaceException;

  /**
   * 批量移除命名空间属性键。
   *
   * <p>未出现在给定集合中的属性不会被修改或移除。
   *
   * @param namespace 命名空间
   * @param properties 待移除的属性键集合
   * @return 成功移除返回 true
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出（可选）
   * @throws UnsupportedOperationException 当实现不支持命名空间属性时抛出
   */
  boolean removeProperties(Namespace namespace, Set<String> properties)
      throws NoSuchNamespaceException;

  /**
   * 判断命名空间是否存在。
   *
   * <p>逻辑：默认实现尝试 {@link #loadNamespaceMetadata(Namespace)}，捕获 {@link NoSuchNamespaceException} 时返回
   * false。
   *
   * @param namespace 命名空间
   * @return 存在返回 true，否则 false
   */
  default boolean namespaceExists(Namespace namespace) {
    try {
      loadNamespaceMetadata(namespace);
      return true;
    } catch (NoSuchNamespaceException e) {
      return false;
    }
  }
}
