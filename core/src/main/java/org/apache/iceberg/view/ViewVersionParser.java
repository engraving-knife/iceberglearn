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
import java.util.Arrays;
import java.util.Map;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：{@link ViewVersion} 的 JSON 序列化/反序列化器。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：将视图版本（版本 ID、时间戳、schema-id、summary、默认 catalog/namespace、 representations 列表）在 JSON 与 {@link
 * ViewVersion} 之间互转。
 *
 * <p>设计意图：字段命名采用 Iceberg 规范的 kebab-case；representations 列表中的每个元素 由 {@link ViewRepresentationParser}
 * 按 type 分派处理；default-catalog 为可选字段，仅在 非空时输出。
 *
 * <p>上下游关系：被 {@link ViewMetadataParser} 在序列化/反序列化视图元数据的 versions 字段时调用。
 */
public class ViewVersionParser {

  private static final String VERSION_ID = "version-id";
  private static final String TIMESTAMP_MS = "timestamp-ms";
  private static final String SUMMARY = "summary";
  private static final String REPRESENTATIONS = "representations";
  private static final String SCHEMA_ID = "schema-id";
  private static final String DEFAULT_CATALOG = "default-catalog";
  private static final String DEFAULT_NAMESPACE = "default-namespace";

  private ViewVersionParser() {}

  /**
   * 将 {@link ViewVersion} 写入 {@link JsonGenerator}。
   *
   * <p>逻辑：写起始对象 -> 写 version-id、timestamp-ms、schema-id、summary -> default-catalog 非空时写出 -> 写
   * default-namespace（levels 数组） -> 写 representations 数组（逐个委托 {@link ViewRepresentationParser}） ->
   * 写结束对象。
   *
   * @param version 视图版本
   * @param generator Jackson 生成器
   * @throws IOException 写入失败
   */
  public static void toJson(ViewVersion version, JsonGenerator generator) throws IOException {
    Preconditions.checkArgument(version != null, "Cannot serialize null view version");
    generator.writeStartObject();

    generator.writeNumberField(VERSION_ID, version.versionId());
    generator.writeNumberField(TIMESTAMP_MS, version.timestampMillis());
    generator.writeNumberField(SCHEMA_ID, version.schemaId());
    JsonUtil.writeStringMap(SUMMARY, version.summary(), generator);

    if (version.defaultCatalog() != null) {
      generator.writeStringField(DEFAULT_CATALOG, version.defaultCatalog());
    }

    JsonUtil.writeStringArray(
        DEFAULT_NAMESPACE, Arrays.asList(version.defaultNamespace().levels()), generator);

    generator.writeArrayFieldStart(REPRESENTATIONS);
    for (ViewRepresentation representation : version.representations()) {
      ViewRepresentationParser.toJson(representation, generator);
    }
    generator.writeEndArray();

    generator.writeEndObject();
  }

  /**
   * 将 {@link ViewVersion} 序列化为 JSON 字符串（紧凑格式）。
   *
   * @param version 视图版本
   * @return JSON 文本
   */
  static String toJson(ViewVersion version) {
    return JsonUtil.generate(gen -> toJson(version, gen), false);
  }

  /**
   * 从 JSON 字符串解析 {@link ViewVersion}。
   *
   * @param json JSON 文本
   * @return 视图版本
   */
  static ViewVersion fromJson(String json) {
    Preconditions.checkArgument(json != null, "Cannot parse view version from null string");
    return JsonUtil.parse(json, ViewVersionParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link ViewVersion}。
   *
   * <p>逻辑：校验节点非空且为对象 -> 逐一取出 version-id、schema-id、timestamp-ms、summary -> 遍历 representations 数组委托
   * {@link ViewRepresentationParser} 解析 -> 取出 default-catalog（可选）与 default-namespace -> 组装
   * ImmutableViewVersion。
   *
   * @param node 已解析的 JSON 节点
   * @return 视图版本
   */
  public static ViewVersion fromJson(JsonNode node) {
    Preconditions.checkArgument(node != null, "Cannot parse view version from null object");
    Preconditions.checkArgument(
        node.isObject(), "Cannot parse view version from a non-object: %s", node);

    int versionId = JsonUtil.getInt(VERSION_ID, node);
    int schemaId = JsonUtil.getInt(SCHEMA_ID, node);
    long timestamp = JsonUtil.getLong(TIMESTAMP_MS, node);
    Map<String, String> summary = JsonUtil.getStringMap(SUMMARY, node);

    JsonNode serializedRepresentations = node.get(REPRESENTATIONS);
    ImmutableList.Builder<ViewRepresentation> representations = ImmutableList.builder();
    for (JsonNode serializedRepresentation : serializedRepresentations) {
      ViewRepresentation representation =
          ViewRepresentationParser.fromJson(serializedRepresentation);
      representations.add(representation);
    }

    String defaultCatalog = JsonUtil.getStringOrNull(DEFAULT_CATALOG, node);

    Namespace defaultNamespace =
        Namespace.of(JsonUtil.getStringArray(JsonUtil.get(DEFAULT_NAMESPACE, node)));

    return ImmutableViewVersion.builder()
        .versionId(versionId)
        .timestampMillis(timestamp)
        .schemaId(schemaId)
        .summary(summary)
        .defaultNamespace(defaultNamespace)
        .defaultCatalog(defaultCatalog)
        .representations(representations.build())
        .build();
  }
}
