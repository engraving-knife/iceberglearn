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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：{@link SQLViewRepresentation} 的 JSON 序列化/反序列化器。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：将 SQL 视图表示在 JSON 与 {@link SQLViewRepresentation} 之间互转， 字段包括 type、sql、dialect。
 *
 * <p>设计意图：包级别私有，统一由 {@link ViewRepresentationParser} 按类型分派调用； 复用 {@link JsonUtil} 处理 Jackson
 * IO，避免样板代码。
 *
 * <p>上下游关系：被 {@link ViewRepresentationParser} 在按 type 路由时调用。
 */
class SQLViewRepresentationParser {
  private static final String SQL = "sql";
  private static final String DIALECT = "dialect";

  private SQLViewRepresentationParser() {}

  /**
   * 将 {@link SQLViewRepresentation} 序列化为 JSON 字符串（紧凑格式）。
   *
   * @param sqlViewRepresentation 待序列化的 SQL 视图表示
   * @return JSON 文本
   */
  static String toJson(SQLViewRepresentation sqlViewRepresentation) {
    return JsonUtil.generate(gen -> toJson(sqlViewRepresentation, gen), false);
  }

  /**
   * 将 {@link SQLViewRepresentation} 写入 {@link JsonGenerator}。
   *
   * <p>逻辑：写起始对象 -> 写 type、sql、dialect 字段 -> 写结束对象。
   *
   * @param view 待序列化的 SQL 视图表示
   * @param generator Jackson 生成器
   * @throws IOException 写入失败
   */
  static void toJson(SQLViewRepresentation view, JsonGenerator generator) throws IOException {
    Preconditions.checkArgument(view != null, "Invalid SQL view representation: null");
    generator.writeStartObject();
    generator.writeStringField(ViewRepresentationParser.TYPE, view.type());
    generator.writeStringField(SQL, view.sql());
    generator.writeStringField(DIALECT, view.dialect());

    generator.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析 {@link SQLViewRepresentation}。
   *
   * @param json JSON 文本
   * @return SQL 视图表示
   */
  static SQLViewRepresentation fromJson(String json) {
    return JsonUtil.parse(json, SQLViewRepresentationParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link SQLViewRepresentation}。
   *
   * <p>逻辑：校验节点非空且为对象 -> 取出 sql、dialect 字段 -> 通过 ImmutableSQLViewRepresentation.Builder 构建。
   *
   * @param node 已解析的 JSON 节点
   * @return SQL 视图表示
   */
  static SQLViewRepresentation fromJson(JsonNode node) {
    Preconditions.checkArgument(
        node != null, "Cannot parse SQL view representation from null object");
    Preconditions.checkArgument(
        node.isObject(), "Cannot parse SQL view representation from non-object: %s", node);
    ImmutableSQLViewRepresentation.Builder builder =
        ImmutableSQLViewRepresentation.builder()
            .sql(JsonUtil.getString(SQL, node))
            .dialect(JsonUtil.getString(DIALECT, node));

    return builder.build();
  }
}
