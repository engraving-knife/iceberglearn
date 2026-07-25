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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：表标识（{@link TableIdentifier}）与 JSON 之间的序列化器。
 *
 * <p>所属模块：iceberg-core（catalog 包），为 REST Catalog 等场景提供表标识的 JSON 编解码， 位于序列化工具层。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link TableIdentifier} 序列化为 JSON（namespace 数组 + name 字段）。
 *   <li>将符合约定的 JSON 反序列化为 {@link TableIdentifier}。
 * </ul>
 *
 * <p>设计意图：采用 REST Catalog 通用的 JSON 表示。以 {@code TableIdentifier.of("dogs", "owners.and.handlers",
 * "food")} 为例，其 JSON 形式如下（namespace 中某一级若自身含点号， 该点号在 REST 协议中会被替换为单元分隔符，避免与命名空间层级分隔符冲突）：
 *
 * <pre>
 * {
 *   "namespace": ["dogs", "owners.and.handlers"],
 *   "name": "food"
 * }
 * </pre>
 *
 * <p>上下游关系：依赖 {@link JsonUtil} 做 JSON 读写；被 REST Catalog / 协议层在表标识编解码时调用。
 */
public class TableIdentifierParser {

  private static final String NAMESPACE = "namespace";
  private static final String NAME = "name";

  private TableIdentifierParser() {}

  /** 将表标识序列化为 JSON 字符串（紧凑格式）。 */
  public static String toJson(TableIdentifier identifier) {
    return toJson(identifier, false);
  }

  /**
   * 将表标识序列化为 JSON 字符串。
   *
   * @param identifier 表标识
   * @param pretty 是否美化输出
   * @return JSON 字符串
   */
  public static String toJson(TableIdentifier identifier, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(identifier, gen), pretty);
  }

  /**
   * 将表标识写入指定的 JSON 生成器。
   *
   * <p>逻辑：写起始对象 → 写 namespace 字段为数组（命名空间各层级） → 写 name 字段 → 写结束对象。
   *
   * @param identifier 表标识
   * @param generator Jackson JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  public static void toJson(TableIdentifier identifier, JsonGenerator generator)
      throws IOException {
    generator.writeStartObject();
    generator.writeFieldName(NAMESPACE);
    generator.writeArray(identifier.namespace().levels(), 0, identifier.namespace().length());
    generator.writeStringField(NAME, identifier.name());
    generator.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析表标识。
   *
   * <p>逻辑：先校验 json 非空非空串，再委托 {@link JsonUtil#parse} 解析并回调 {@link #fromJson(JsonNode)} 完成对象级解析。
   *
   * @param json JSON 字符串
   * @return 解析得到的 {@link TableIdentifier}
   */
  public static TableIdentifier fromJson(String json) {
    Preconditions.checkArgument(
        json != null, "Cannot parse table identifier from invalid JSON: null");
    Preconditions.checkArgument(
        !json.isEmpty(), "Cannot parse table identifier from invalid JSON: ''");
    return JsonUtil.parse(json, TableIdentifierParser::fromJson);
  }

  /**
   * 从 JSON 节点解析表标识。
   *
   * <p>逻辑：校验节点存在且为对象 → 读取 namespace 数组（可能为 null，表示空命名空间） → 读取 name → 组装为 {@link TableIdentifier}。
   *
   * @param node JSON 节点
   * @return 解析得到的 {@link TableIdentifier}
   */
  public static TableIdentifier fromJson(JsonNode node) {
    Preconditions.checkArgument(
        node != null && !node.isNull() && node.isObject(),
        "Cannot parse missing or non-object table identifier: %s",
        node);
    List<String> levels = JsonUtil.getStringListOrNull(NAMESPACE, node);
    String tableName = JsonUtil.getString(NAME, node);
    Namespace namespace =
        levels == null ? Namespace.empty() : Namespace.of(levels.toArray(new String[0]));
    return TableIdentifier.of(namespace, tableName);
  }
}
