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

import org.immutables.value.Value;

/**
 * 文件级说明：基于 SQL 的视图表示（Immutables 不可变接口）。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：以 SQL 文本 + SQL 方言（dialect）的形式描述视图查询，是 {@link ViewRepresentation} 的标准 SQL 实现。
 *
 * <p>设计意图：通过 Immutables 生成 {@code ImmutableSQLViewRepresentation}； {@link #type()} 固定返回 {@link
 * ViewRepresentation.Type#SQL}，用于 JSON 序列化时区分 表示类型。
 *
 * <p>上下游关系：被 {@link ViewVersion} 持有的表示列表包含；由 {@link SQLViewRepresentationParser} 序列化/反序列化。
 */
@Value.Immutable
public interface SQLViewRepresentation extends ViewRepresentation {

  /** 返回表示类型标识，固定为 {@link ViewRepresentation.Type#SQL}。 */
  @Override
  default String type() {
    return Type.SQL;
  }

  /** 返回视图查询的 SQL 文本。 */
  String sql();

  /** 返回视图查询的 SQL 方言（如 spark、trino 等）。 */
  String dialect();
}
