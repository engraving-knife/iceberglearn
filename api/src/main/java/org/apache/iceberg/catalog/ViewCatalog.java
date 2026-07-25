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
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchViewException;
import org.apache.iceberg.view.View;
import org.apache.iceberg.view.ViewBuilder;

/**
 * 文件级说明：Iceberg 视图（View）目录服务接口。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义视图的创建（buildView）、加载（loadView）、删除（dropView）、重命名 （renameView）、列举（listViews）等目录管理操作。
 *   <li>提供视图存在性判断（viewExists）与缓存失效（invalidateView）等辅助能力。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>与 {@link Catalog} 平行：视图与表是两类对象，独立接口避免方法堆砌，且允许 实现侧仅支持其中一类。
 *   <li>构建器模式：通过 {@link ViewBuilder}（由 {@link #buildView(TableIdentifier)} 获取）
 *       配置视图定义、Schema、属性等，统一创建与替换流程。
 *   <li>两阶段初始化：实现类需有无参构造，引擎先实例化再调用 {@link #initialize(String, Map)} 注入配置。
 * </ul>
 *
 * <p>上下游关系：被 Spark/Flink 等引擎的视图管理模块调用；实现侧依赖 iceberg-api 中的 {@link View}、{@link ViewBuilder} 抽象。
 */
public interface ViewCatalog {

  /**
   * 返回本 catalog 的名称。
   *
   * @return catalog 名称
   */
  String name();

  /**
   * 列举指定命名空间下的所有视图标识符。
   *
   * @param namespace 命名空间
   * @return 该命名空间下的视图标识符列表
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出
   */
  List<TableIdentifier> listViews(Namespace namespace);

  /**
   * 加载视图。
   *
   * @param identifier 视图标识符
   * @return 该标识符对应的 {@link View} 实现实例
   * @throws NoSuchViewException 当视图不存在时抛出
   */
  View loadView(TableIdentifier identifier);

  /**
   * 判断视图是否存在。
   *
   * <p>逻辑：默认实现尝试 {@link #loadView(TableIdentifier)}，捕获 {@link NoSuchViewException} 时返回 false。
   *
   * @param identifier 视图标识符
   * @return 视图存在返回 true，否则 false
   */
  default boolean viewExists(TableIdentifier identifier) {
    try {
      loadView(identifier);
      return true;
    } catch (NoSuchViewException e) {
      return false;
    }
  }

  /**
   * 实例化一个 {@link ViewBuilder}，用于创建或替换 SQL 视图。
   *
   * @param identifier 视图标识符
   * @return 视图构建器
   */
  ViewBuilder buildView(TableIdentifier identifier);

  /**
   * 删除视图。
   *
   * @param identifier 视图标识符
   * @return 视图存在并已删除返回 true，视图不存在返回 false
   */
  boolean dropView(TableIdentifier identifier);

  /**
   * 重命名视图。
   *
   * @param from 原视图标识符
   * @param to 新视图标识符
   * @throws NoSuchViewException 当 from 视图不存在时抛出
   * @throws AlreadyExistsException 当 to 视图已存在时抛出
   */
  void renameView(TableIdentifier from, TableIdentifier to);

  /**
   * 使本 catalog 中缓存的视图元数据失效。
   *
   * <p>若视图已被加载或缓存，则丢弃缓存数据；若视图不存在或未被缓存，则不做任何操作。 默认实现为空，子类按需覆盖。
   *
   * @param identifier 视图标识符
   */
  default void invalidateView(TableIdentifier identifier) {}

  /**
   * 使用自定义名称和属性映射初始化视图 catalog。
   *
   * <p>计算引擎（如 Spark/Flink）会先以无参构造实例化 ViewCatalog，再调用本方法注入 引擎传入的 catalog 配置属性。默认实现为空，子类按需覆盖。
   *
   * @param name catalog 自定义名称
   * @param properties catalog 配置属性
   */
  default void initialize(String name, Map<String, String> properties) {}
}
