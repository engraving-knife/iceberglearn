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
package org.apache.iceberg.mapping;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.JsonUtil;

/**
 * 将外部名称映射在 JSON 与 {@link NameMapping} 之间相互转换的解析器。
 *
 * <p>所属模块：iceberg-core（mapping 子包），提供名称映射的序列化/反序列化能力。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #toJson(NameMapping)}：将 {@link NameMapping} 序列化为紧凑 JSON。
 *   <li>{@link #fromJson(String)}：将 JSON 反序列化为 {@link NameMapping}。
 *   <li>递归处理嵌套 {@link MappedFields}，支持多级结构。
 * </ul>
 *
 * <p>设计意图：工具类，私有构造器；基于 Jackson {@link JsonGenerator}/{@link JsonNode} 流式读写， JSON 字段键固定为
 * field-id/names/fields，便于跨语言互通。JSON 示例如下：
 *
 * <pre>
 * [ { "field-id": 1, "names": ["id", "record_id"] },
 *   { "field-id": 2, "names": ["data"] },
 *   { "field-id": 3, "names": ["location"], "fields": [
 *       { "field-id": 4, "names": ["latitude", "lat"] },
 *       { "field-id": 5, "names": ["longitude", "long"] }
 *     ] } ]
 * </pre>
 *
 * <p>上下游关系：被读写流程调用以加载/保存名称映射；产出/消费 {@link NameMapping}。
 */
public class NameMappingParser {

  /** 私有构造器，禁止实例化。 */
  private NameMappingParser() {}

  private static final String FIELD_ID = "field-id";
  private static final String NAMES = "names";
  private static final String FIELDS = "fields";

  /**
   * 将名称映射序列化为 JSON 字符串。
   *
   * @param mapping 名称映射
   * @return JSON 字符串
   */
  public static String toJson(NameMapping mapping) {
    return JsonUtil.generate(gen -> toJson(mapping, gen), true);
  }

  /**
   * 将名称映射写入 JSON 生成器。
   *
   * @param nameMapping 名称映射
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  static void toJson(NameMapping nameMapping, JsonGenerator generator) throws IOException {
    toJson(nameMapping.asMappedFields(), generator);
  }

  /**
   * 将映射字段集写入 JSON 数组。
   *
   * @param mapping 映射字段集
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  private static void toJson(MappedFields mapping, JsonGenerator generator) throws IOException {
    generator.writeStartArray();

    for (MappedField field : mapping.fields()) {
      toJson(field, generator);
    }

    generator.writeEndArray();
  }

  /**
   * 将单个映射字段写入 JSON 对象，含 field-id、names 及可选嵌套 fields。
   *
   * @param field 映射字段
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  private static void toJson(MappedField field, JsonGenerator generator) throws IOException {
    generator.writeStartObject();

    generator.writeNumberField(FIELD_ID, field.id());

    JsonUtil.writeStringArray(NAMES, field.names(), generator);

    MappedFields nested = field.nestedMapping();
    if (nested != null) {
      generator.writeFieldName(FIELDS);
      toJson(nested, generator);
    }

    generator.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析名称映射。
   *
   * @param json JSON 字符串
   * @return 名称映射
   */
  public static NameMapping fromJson(String json) {
    return JsonUtil.parse(json, NameMappingParser::fromJson);
  }

  /**
   * 从 JSON 节点解析名称映射。
   *
   * @param node JSON 节点
   * @return 名称映射
   */
  static NameMapping fromJson(JsonNode node) {
    return new NameMapping(fieldsFromJson(node));
  }

  /**
   * 从 JSON 节点解析映射字段集。
   *
   * <p>逻辑：校验节点为数组，遍历每个元素解析为 {@link MappedField} 后组装为 {@link MappedFields}。
   *
   * @param node JSON 节点
   * @return 映射字段集
   */
  private static MappedFields fieldsFromJson(JsonNode node) {
    Preconditions.checkArgument(node.isArray(), "Cannot parse non-array mapping fields: %s", node);

    List<MappedField> fields = Lists.newArrayList();
    node.elements().forEachRemaining(fieldNode -> fields.add(fieldFromJson(fieldNode)));

    return MappedFields.of(fields);
  }

  /**
   * 从 JSON 节点解析单个映射字段，读取 field-id、names 及可选嵌套 fields。
   *
   * <p>逻辑：校验节点为对象；读取 field-id（可能为 null）、names（缺失则为空集）、 嵌套 fields（缺失则为 null），组装为 {@link
   * MappedField}。
   *
   * @param node JSON 节点
   * @return 映射字段
   */
  private static MappedField fieldFromJson(JsonNode node) {
    Preconditions.checkArgument(
        node != null && !node.isNull() && node.isObject(),
        "Cannot parse non-object mapping field: %s",
        node);

    Integer id = JsonUtil.getIntOrNull(FIELD_ID, node);

    Set<String> names;
    if (node.has(NAMES)) {
      names = ImmutableSet.copyOf(JsonUtil.getStringList(NAMES, node));
    } else {
      names = ImmutableSet.of();
    }

    MappedFields nested;
    if (node.has(FIELDS)) {
      nested = fieldsFromJson(node.get(FIELDS));
    } else {
      nested = null;
    }

    return MappedField.of(id, names, nested);
  }
}
