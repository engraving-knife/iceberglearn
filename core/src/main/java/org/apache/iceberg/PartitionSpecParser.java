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
package org.apache.iceberg;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.util.Iterator;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.JsonUtil;
import org.apache.iceberg.util.Pair;

/**
 * 文件级说明：分区规范（PartitionSpec）JSON 序列化/反序列化工具。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 {@link PartitionSpec} / {@link UnboundPartitionSpec} 序列化为 metadata.json 中的
 *       partition-specs 节点；
 *   <li>从 JSON 节点反序列化为 {@link UnboundPartitionSpec}（不绑定 schema）或 {@link PartitionSpec}（绑定 schema）；
 *   <li>提供基于 Caffeine 的弱引用缓存，避免重复解析相同 JSON。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>先解析为 Unbound 形式再 {@code bind(schema)}，把 schema 信息与 spec 文本解耦， 便于跨 metadata 复用与延迟绑定。
 *   <li>对老版本 metadata 中缺失 field-id 的 spec 做兼容：所有 field-id 缺失时按 PARTITION_DATA_ID_START 自增；部分缺失则报错。
 *   <li>弱值缓存：缓存 PartitionSpec 实例避免热路径重复解析，但弱引用避免长期占用堆。
 * </ul>
 *
 * <p>上下游关系：被 {@link TableMetadataParser} 在读写 metadata.json 时调用； 依赖 {@link JsonUtil}、{@link
 * UnboundPartitionSpec}。
 */
public class PartitionSpecParser {

  private PartitionSpecParser() {}

  private static final String SPEC_ID = "spec-id";
  private static final String FIELDS = "fields";
  private static final String SOURCE_ID = "source-id";
  private static final String FIELD_ID = "field-id";
  private static final String TRANSFORM = "transform";
  private static final String NAME = "name";

  /**
   * 把 PartitionSpec 写入 JSON 生成器（先转为 Unbound 形式）。
   *
   * @param spec 分区规范
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  public static void toJson(PartitionSpec spec, JsonGenerator generator) throws IOException {
    toJson(spec.toUnbound(), generator);
  }

  /**
   * 把 PartitionSpec 序列化为 JSON 字符串（紧凑形式）。
   *
   * @param spec 分区规范
   * @return JSON 字符串
   */
  public static String toJson(PartitionSpec spec) {
    return toJson(spec, false);
  }

  /**
   * 把 PartitionSpec 序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param spec 分区规范
   * @param pretty 是否美化输出
   * @return JSON 字符串
   */
  public static String toJson(PartitionSpec spec, boolean pretty) {
    return toJson(spec.toUnbound(), pretty);
  }

  /**
   * 把 UnboundPartitionSpec 写入 JSON 生成器。
   *
   * @param spec 未绑定 schema 的分区规范
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  public static void toJson(UnboundPartitionSpec spec, JsonGenerator generator) throws IOException {
    generator.writeStartObject();
    generator.writeNumberField(SPEC_ID, spec.specId());
    generator.writeFieldName(FIELDS);
    toJsonFields(spec, generator);
    generator.writeEndObject();
  }

  /**
   * 把 UnboundPartitionSpec 序列化为 JSON 字符串（紧凑形式）。
   *
   * @param spec 未绑定 schema 的分区规范
   * @return JSON 字符串
   */
  public static String toJson(UnboundPartitionSpec spec) {
    return toJson(spec, false);
  }

  /**
   * 把 UnboundPartitionSpec 序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param spec 未绑定 schema 的分区规范
   * @param pretty 是否美化输出
   * @return JSON 字符串
   */
  public static String toJson(UnboundPartitionSpec spec, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(spec, gen), pretty);
  }

  /**
   * 从 JSON 节点解析出已绑定 schema 的 PartitionSpec。
   *
   * @param schema 表 schema，用于绑定字段类型
   * @param json JSON 节点
   * @return 绑定后的分区规范
   */
  public static PartitionSpec fromJson(Schema schema, JsonNode json) {
    return fromJson(json).bind(schema);
  }

  /**
   * 从 JSON 节点解析出 UnboundPartitionSpec（未绑定 schema）。
   *
   * @param json JSON 节点
   * @return 未绑定的分区规范
   */
  public static UnboundPartitionSpec fromJson(JsonNode json) {
    Preconditions.checkArgument(json.isObject(), "Cannot parse spec from non-object: %s", json);
    int specId = JsonUtil.getInt(SPEC_ID, json);
    UnboundPartitionSpec.Builder builder = UnboundPartitionSpec.builder().withSpecId(specId);
    buildFromJsonFields(builder, JsonUtil.get(FIELDS, json));
    return builder.build();
  }

  /** 基于 (schema, json 字符串) 的反序列化结果缓存。键为 (StructType, json 字符串)， 值为弱引用 PartitionSpec，避免热路径重复解析。 */
  private static final Cache<Pair<Types.StructType, String>, PartitionSpec> SPEC_CACHE =
      Caffeine.newBuilder().weakValues().build();

  /**
   * 从 JSON 字符串解析 PartitionSpec（带缓存）。
   *
   * <p>缓存键为 (schema.asStruct(), json 字符串)；缓存未命中时调用 {@link #fromJson(Schema, JsonNode)} 进行实际解析。
   *
   * @param schema 表 schema
   * @param json JSON 字符串
   * @return 绑定后的分区规范（可能来自缓存）
   */
  public static PartitionSpec fromJson(Schema schema, String json) {
    return SPEC_CACHE.get(
        Pair.of(schema.asStruct(), json),
        schemaJsonPair -> JsonUtil.parse(json, node -> PartitionSpecParser.fromJson(schema, node)));
  }

  /**
   * 把 PartitionSpec 的 fields 节点写入 JSON 生成器。
   *
   * @param spec 分区规范
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  static void toJsonFields(PartitionSpec spec, JsonGenerator generator) throws IOException {
    toJsonFields(spec.toUnbound(), generator);
  }

  /**
   * 把 UnboundPartitionSpec 的 fields 节点写入 JSON 生成器。
   *
   * <p>每个字段写为 {name, transform, source-id, field-id} 的对象，整体放在数组中。
   *
   * @param spec 未绑定 schema 的分区规范
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  static void toJsonFields(UnboundPartitionSpec spec, JsonGenerator generator) throws IOException {
    generator.writeStartArray();
    for (UnboundPartitionSpec.UnboundPartitionField field : spec.fields()) {
      generator.writeStartObject();
      generator.writeStringField(NAME, field.name());
      generator.writeStringField(TRANSFORM, field.transformAsString());
      generator.writeNumberField(SOURCE_ID, field.sourceId());
      generator.writeNumberField(FIELD_ID, field.partitionId());
      generator.writeEndObject();
    }
    generator.writeEndArray();
  }

  /**
   * 把 PartitionSpec 的 fields 节点序列化为 JSON 字符串。
   *
   * @param spec 分区规范
   * @return fields 节点对应的 JSON 字符串
   */
  static String toJsonFields(PartitionSpec spec) {
    return JsonUtil.generate(gen -> toJsonFields(spec, gen), false);
  }

  /**
   * 从 JSON 节点解析 fields 数组并绑定 schema，构造 PartitionSpec。
   *
   * @param schema 表 schema
   * @param specId 分区规范 id
   * @param json fields 节点
   * @return 绑定后的分区规范
   */
  static PartitionSpec fromJsonFields(Schema schema, int specId, JsonNode json) {
    UnboundPartitionSpec.Builder builder = UnboundPartitionSpec.builder().withSpecId(specId);
    buildFromJsonFields(builder, json);
    return builder.build().bind(schema);
  }

  /**
   * 从 JSON 字符串解析 fields 并绑定 schema，构造 PartitionSpec。
   *
   * @param schema 表 schema
   * @param specId 分区规范 id
   * @param json fields 节点对应的 JSON 字符串
   * @return 绑定后的分区规范
   */
  static PartitionSpec fromJsonFields(Schema schema, int specId, String json) {
    return JsonUtil.parse(json, node -> PartitionSpecParser.fromJsonFields(schema, specId, node));
  }

  /**
   * 把 JSON 数组形式的 fields 解析并逐个加入 builder。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>校验输入必须是数组；
   *   <li>遍历每个元素，取 name / transform / source-id；
   *   <li>对老版本兼容：若元素含 field-id 则用三参 {@code addField}，否则用两参让框架自动分配；
   *   <li>校验 field-id 要么全部存在要么全部缺失，禁止部分缺失。
   * </ol>
   *
   * @param builder 分区规范 builder
   * @param json fields 数组节点
   */
  private static void buildFromJsonFields(UnboundPartitionSpec.Builder builder, JsonNode json) {
    Preconditions.checkArgument(
        json.isArray(), "Cannot parse partition spec fields, not an array: %s", json);

    Iterator<JsonNode> elements = json.elements();
    int fieldIdCount = 0;
    while (elements.hasNext()) {
      JsonNode element = elements.next();
      Preconditions.checkArgument(
          element.isObject(), "Cannot parse partition field, not an object: %s", element);

      String name = JsonUtil.getString(NAME, element);
      String transform = JsonUtil.getString(TRANSFORM, element);
      int sourceId = JsonUtil.getInt(SOURCE_ID, element);

      // partition field ids are missing in old PartitionSpec, they always auto-increment from
      // PARTITION_DATA_ID_START
      if (element.has(FIELD_ID)) {
        builder.addField(transform, sourceId, JsonUtil.getInt(FIELD_ID, element), name);
        fieldIdCount++;
      } else {
        builder.addField(transform, sourceId, name);
      }
    }

    Preconditions.checkArgument(
        fieldIdCount == 0 || fieldIdCount == json.size(),
        "Cannot parse spec with missing field IDs: %s missing of %s fields.",
        json.size() - fieldIdCount,
        json.size());
  }
}
