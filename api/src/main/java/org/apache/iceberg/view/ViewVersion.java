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
import org.apache.iceberg.catalog.Namespace;

/**
 * 视图在某一时刻的版本。
 *
 * <p>所属模块：iceberg-api。是 view 模块的不可变版本单元，一个版本对应一个视图元数据文件。
 *
 * <p>职责：记录视图某次状态的内容，包括版本 ID、时间戳、summary、各方言 SQL 表示、操作类型、 schema ID、默认 catalog 与默认 namespace。
 *
 * <p>设计意图：版本由视图操作（如 Create、Replace）创建，不可变，便于时间旅行与并发控制。 operation 通过 summary 中的 "operation"
 * 键获取，避免新增独立字段。defaultCatalog 默认返回 null， 兼容早期未设置默认 catalog 的版本。
 *
 * <p>上下游关系：由 {@link View#versions()}、{@link View#currentVersion()} 返回；包含若干 {@link
 * ViewRepresentation}。
 */
public interface ViewVersion {

  /**
   * 返回本版本 ID。版本 ID 单调递增。
   *
   * @return 版本 ID
   */
  int versionId();

  /**
   * 返回本版本的时间戳。
   *
   * <p>该时间戳与 {@link System#currentTimeMillis()} 产生的格式一致。
   *
   * @return 毫秒时间戳
   */
  long timestampMillis();

  /**
   * 返回本版本的摘要信息。
   *
   * @return 版本摘要键值对
   */
  Map<String, String> summary();

  /**
   * 返回本版本的视图表示列表。
   *
   * <p>可能包含多种方言的 SQL 视图表示。
   *
   * @return 视图表示列表
   */
  List<ViewRepresentation> representations();

  /**
   * 返回产生本视图版本的操作类型。
   *
   * <p>逻辑：从 {@link #summary()} 中以 "operation" 为键取出对应值。
   *
   * @return 产生本版本的操作字符串
   */
  default String operation() {
    return summary().get("operation");
  }

  /**
   * 返回版本创建时查询输出的 schema ID（不含别名）。
   *
   * @return schema ID
   */
  int schemaId();

  /**
   * 返回视图创建时使用的默认 catalog。默认返回 null，兼容未设置默认 catalog 的版本。
   *
   * @return 默认 catalog，可能为 null
   */
  default String defaultCatalog() {
    return null;
  }

  /**
   * 返回当 SQL 中未包含 namespace 时使用的默认 namespace。
   *
   * @return 默认 namespace
   */
  Namespace defaultNamespace();
}
