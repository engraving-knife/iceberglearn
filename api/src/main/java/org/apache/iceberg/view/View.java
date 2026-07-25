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

import java.util.List;
import java.util.Map;
import org.apache.iceberg.Schema;

/**
 * SQL 视图定义接口。
 *
 * <p>所属模块：iceberg-api。本接口是 view 模块的核心抽象，类似 {@link org.apache.iceberg.Table} 之于表，定义了一个 Iceberg
 * 视图的元数据视图与变更入口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供视图名称、schema、schema 集合、当前版本、版本列表、版本历史与属性等元数据查询。
 *   <li>提供变更入口：{@link #updateProperties()} 更新属性，{@link #replaceVersion()} 替换版本。
 * </ul>
 *
 * <p>设计意图：视图与表类似地采用"版本化元数据 + 不可变版本"模型，支持时间旅行。schema 以 Map&lt;Integer, Schema&gt; 形式保存多版本，便于不同版本引用不同
 * schema。replaceVersion 默认抛 {@link UnsupportedOperationException}，作为可选能力由具体实现按需提供。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.catalog.ViewCatalog} 加载/管理；变更操作产出 {@link
 * UpdateViewProperties} 与 {@link ReplaceViewVersion}。
 */
public interface View {

  /**
   * 返回视图名称。
   *
   * @return 视图名
   */
  String name();

  /**
   * 返回本视图当前使用的 {@link Schema}。
   *
   * @return 当前 schema
   */
  Schema schema();

  /**
   * 返回本视图全部 {@link Schema} 的映射（按 schema ID 索引）。
   *
   * @return schema 映射
   */
  Map<Integer, Schema> schemas();

  /**
   * 获取本视图的当前版本，若无任何版本则返回 null。
   *
   * @return 当前视图版本
   */
  ViewVersion currentVersion();

  /**
   * 获取本视图的全部版本。
   *
   * @return 版本可迭代集合
   */
  Iterable<ViewVersion> versions();

  /**
   * 按 ID 获取本视图的某个版本。
   *
   * @param versionId 版本 ID
   * @return 对应版本，找不到时返回 null
   */
  ViewVersion version(int versionId);

  /**
   * 获取本视图的版本历史。
   *
   * @return {@link ViewHistoryEntry} 列表
   */
  List<ViewHistoryEntry> history();

  /**
   * 返回本视图的字符串属性映射。
   *
   * @return 属性映射
   */
  Map<String, String> properties();

  /**
   * 创建一个新的 {@link UpdateViewProperties} 用于更新视图属性。
   *
   * @return 新的 {@link UpdateViewProperties}
   */
  UpdateViewProperties updateProperties();

  /**
   * 创建一个新的 {@link ReplaceViewVersion} 用于替换视图当前版本。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，表示不支持替换版本。
   *
   * @return 新的 {@link ReplaceViewVersion}
   */
  default ReplaceViewVersion replaceVersion() {
    throw new UnsupportedOperationException("Replacing a view's version is not supported");
  }
}
