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
package org.apache.iceberg.view;

import java.util.Map;
import org.apache.iceberg.catalog.ViewCatalog;

/**
 * 用于创建或替换 SQL {@link View} 的构建器。
 *
 * <p>所属模块：iceberg-api。继承自 {@link VersionBuilder}，在版本内容配置基础上增加属性设置与 创建/替换/创建或替换三种提交动作。
 *
 * <p>职责：链式配置视图 schema、SQL 表示、默认 catalog/namespace 及属性，然后通过 create、 replace、createOrReplace 之一提交。
 *
 * <p>设计意图：将视图的"构建"与"提交语义"分离——配置阶段不产生副作用，提交阶段才决定是新建、 替换还是 upsert。调用 {@link ViewCatalog#buildView}
 * 可创建一个新的构建器实例。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.catalog.ViewCatalog#buildView} 创建；提交后产出 {@link View}。
 */
public interface ViewBuilder extends VersionBuilder<ViewBuilder> {

  /**
   * 为视图添加一组键值对属性。
   *
   * @param properties 键值对属性
   * @return this，便于链式调用
   */
  ViewBuilder withProperties(Map<String, String> properties);

  /**
   * 为视图添加单个键值对属性。
   *
   * @param key 属性键
   * @param value 属性值
   * @return this，便于链式调用
   */
  ViewBuilder withProperty(String key, String value);

  /**
   * 创建视图。
   *
   * @return 创建出的视图
   */
  View create();

  /**
   * 替换视图。
   *
   * @return 替换后的 {@link View}
   */
  View replace();

  /**
   * 创建或替换视图（若存在则替换，否则创建）。
   *
   * @return 创建或替换后的 {@link View}
   */
  View createOrReplace();
}
