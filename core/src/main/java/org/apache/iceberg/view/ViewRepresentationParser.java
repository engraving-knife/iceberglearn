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
import java.util.Locale;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：{@link ViewRepresentation} 的 JSON 序列化/反序列化分派器。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：根据表示类型（type 字段）将序列化/反序列化请求分派到具体的子解析器 （如 {@link SQLViewRepresentationParser}），对未知类型做兜底处理。
 *
 * <p>设计意图：采用类型路由模式，新增表示类型只需扩展 switch 分支；反序列化时遇到未知 类型不报错，而是构造 {@link UnknownViewRepresentation}
 * 占位，保证前向兼容。
 *
 * <p>上下游关系：被 {@link ViewVersionParser} 在序列化/反序列化版本中的 representations 列表时调用；分派到 {@link
 * SQLViewRepresentationParser} 等具体解析器。
 */
class ViewRepresentationParser {
  static final String TYPE = "type";

  private ViewRepresentationParser() {}

  /**
   * 将 {@link ViewRepresentation} 写入 {@link JsonGenerator}，按 type 分派到子解析器。
   *
   * <p>逻辑：按 type（小写）路由，SQL 类型委托 {@link SQLViewRepresentationParser}； 未知类型抛出
   * UnsupportedOperationException。
   *
   * @param representation 视图表示
   * @param generator Jackson 生成器
   * @throws IOException 写入失败
   * @throws UnsupportedOperationException 遇到不支持的表示类型
   */
  static void toJson(ViewRepresentation representation, JsonGenerator generator)
      throws IOException {
    Preconditions.checkArgument(representation != null, "Invalid view representation: null");
    switch (representation.type().toLowerCase(Locale.ENGLISH)) {
      case ViewRepresentation.Type.SQL:
        SQLViewRepresentationParser.toJson((SQLViewRepresentation) representation, generator);
        break;

      default:
        throw new UnsupportedOperationException(
            String.format(
                "Cannot serialize unsupported view representation: %s", representation.type()));
    }
  }

  /**
   * 将 {@link ViewRepresentation} 序列化为 JSON 字符串（紧凑格式）。
   *
   * @param entry 视图表示
   * @return JSON 文本
   */
  static String toJson(ViewRepresentation entry) {
    return JsonUtil.generate(gen -> toJson(entry, gen), false);
  }

  /**
   * 从 JSON 字符串解析 {@link ViewRepresentation}。
   *
   * @param json JSON 文本
   * @return 视图表示
   */
  static ViewRepresentation fromJson(String json) {
    return JsonUtil.parse(json, ViewRepresentationParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link ViewRepresentation}，按 type 分派。
   *
   * <p>逻辑：校验节点非空且为对象 -> 读取 type 字段（小写） -> SQL 类型委托 {@link SQLViewRepresentationParser}；未知类型构造
   * {@link UnknownViewRepresentation} 占位。
   *
   * @param node 已解析的 JSON 节点
   * @return 视图表示
   */
  static ViewRepresentation fromJson(JsonNode node) {
    Preconditions.checkArgument(node != null, "Cannot parse view representation from null object");
    Preconditions.checkArgument(
        node.isObject(), "Cannot parse view representation from non-object: %s", node);
    String type = JsonUtil.getString(TYPE, node).toLowerCase(Locale.ENGLISH);
    switch (type) {
      case ViewRepresentation.Type.SQL:
        return SQLViewRepresentationParser.fromJson(node);

      default:
        return ImmutableUnknownViewRepresentation.builder().type(type).build();
    }
  }
}
