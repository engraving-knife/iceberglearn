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

import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Namespace;

/**
 * 视图版本构建器，用于配置一个视图版本的内容。
 *
 * <p>所属模块：iceberg-api。是 view 模块构建/替换视图版本时的通用配置接口，被 {@link ReplaceViewVersion} 与 {@link ViewBuilder}
 * 继承。
 *
 * <p>职责：设置视图 schema、按方言添加 SQL 表示、设置默认 catalog 与默认 namespace。
 *
 * <p>设计意图：将视图版本的内容配置能力抽象为独立接口，使"替换版本"与"新建/替换视图"两类操作 复用同一套配置方法。泛型 T 指向具体构建器子类型，保证链式调用类型安全。
 *
 * @param <T> 具体构建器子类型，用于链式调用返回类型
 */
public interface VersionBuilder<T> {
  /**
   * 设置视图的 schema。
   *
   * @param schema 本视图版本使用的 schema
   * @return this，便于链式调用
   */
  T withSchema(Schema schema);

  /**
   * 为指定方言添加一条视图 SQL 表示（representation）。
   *
   * @param dialect 视图表示的 SQL 方言
   * @param sql 视图表示的 SQL 文本
   * @return this，便于链式调用
   */
  T withQuery(String dialect, String sql);

  /**
   * 设置视图的默认 catalog。
   *
   * @param catalog 当 SQL 中未包含 catalog 时使用的默认 catalog
   * @return this，便于链式调用
   */
  T withDefaultCatalog(String catalog);

  /**
   * 设置视图的默认 namespace。
   *
   * @param namespace 当 SQL 中未包含 namespace 时使用的默认 namespace
   * @return this，便于链式调用
   */
  T withDefaultNamespace(Namespace namespace);
}
