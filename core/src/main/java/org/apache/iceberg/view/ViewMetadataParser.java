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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：{@link ViewMetadata} 的 JSON 序列化/反序列化器及文件读写工具。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link ViewMetadata} 序列化为 JSON 文本（含 view-uuid、format-version、location、
 *       properties、schemas、current-version-id、versions、version-log）。
 *   <li>将 JSON 解析回 {@link ViewMetadata} 内存对象。
 *   <li>提供 {@link #read(InputFile)} / {@link #write}/{@link #overwrite} 完成元数据文件的 读写。
 * </ul>
 *
 * <p>设计意图：schemas、versions、version-log 各字段委托 {@link SchemaParser}、 {@link ViewVersionParser}、{@link
 * ViewHistoryEntryParser} 处理；写入时使用美化输出便于 人工检视；解析时记录 metadataLocation 以支持后续变更追踪。
 *
 * <p>上下游关系：被视图 catalog（如 {@code ViewMetadataParser.read/write}）在加载/提交 视图元数据时调用。
 */
public class ViewMetadataParser {

  static final String VIEW_UUID = "view-uuid";
  static final String FORMAT_VERSION = "format-version";
  static final String LOCATION = "location";
  static final String CURRENT_VERSION_ID = "current-version-id";
  static final String VERSIONS = "versions";
  static final String VERSION_LOG = "version-log";
  static final String PROPERTIES = "properties";
  static final String SCHEMAS = "schemas";

  private ViewMetadataParser() {}

  /**
   * 将 {@link ViewMetadata} 序列化为 JSON 字符串（紧凑格式）。
   *
   * @param metadata 视图元数据
   * @return JSON 文本
   */
  public static String toJson(ViewMetadata metadata) {
    return toJson(metadata, false);
  }

  /**
   * 将 {@link ViewMetadata} 序列化为 JSON 字符串。
   *
   * @param metadata 视图元数据
   * @param pretty 是否美化输出
   * @return JSON 文本
   */
  public static String toJson(ViewMetadata metadata, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(metadata, gen), pretty);
  }

  /**
   * 将 {@link ViewMetadata} 写入 {@link JsonGenerator}。
   *
   * <p>逻辑：写起始对象 -> 写 view-uuid、format-version、location、properties -> 写 schemas 数组（委托 {@link
   * SchemaParser}） -> 写 current-version-id -> 写 versions 数组（委托 {@link ViewVersionParser}） -> 写
   * version-log 数组 （委托 {@link ViewHistoryEntryParser}） -> 写结束对象。
   *
   * @param metadata 视图元数据
   * @param gen Jackson 生成器
   * @throws IOException 写入失败
   */
  static void toJson(ViewMetadata metadata, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != metadata, "Invalid view metadata: null");

    gen.writeStartObject();

    gen.writeStringField(VIEW_UUID, metadata.uuid());
    gen.writeNumberField(FORMAT_VERSION, metadata.formatVersion());
    gen.writeStringField(LOCATION, metadata.location());
    JsonUtil.writeStringMap(PROPERTIES, metadata.properties(), gen);

    gen.writeArrayFieldStart(SCHEMAS);
    for (Schema schema : metadata.schemas()) {
      SchemaParser.toJson(schema, gen);
    }
    gen.writeEndArray();

    gen.writeNumberField(CURRENT_VERSION_ID, metadata.currentVersionId());
    gen.writeArrayFieldStart(VERSIONS);
    for (ViewVersion version : metadata.versions()) {
      ViewVersionParser.toJson(version, gen);
    }
    gen.writeEndArray();

    gen.writeArrayFieldStart(VERSION_LOG);
    for (ViewHistoryEntry viewHistoryEntry : metadata.history()) {
      ViewHistoryEntryParser.toJson(viewHistoryEntry, gen);
    }
    gen.writeEndArray();

    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析 {@link ViewMetadata}，并记录元数据文件位置。
   *
   * @param metadataLocation 元数据文件路径，可为 null
   * @param json JSON 文本
   * @return 视图元数据
   */
  public static ViewMetadata fromJson(String metadataLocation, String json) {
    return JsonUtil.parse(json, node -> ViewMetadataParser.fromJson(metadataLocation, node));
  }

  /**
   * 从 JSON 字符串解析 {@link ViewMetadata}（不记录元数据文件位置）。
   *
   * @param json JSON 文本
   * @return 视图元数据
   */
  public static ViewMetadata fromJson(String json) {
    Preconditions.checkArgument(json != null, "Cannot parse view metadata from null string");
    return JsonUtil.parse(json, ViewMetadataParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link ViewMetadata}（不记录元数据文件位置）。
   *
   * @param json 已解析的 JSON 节点
   * @return 视图元数据
   */
  public static ViewMetadata fromJson(JsonNode json) {
    return fromJson(null, json);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link ViewMetadata}，并记录元数据文件位置。
   *
   * <p>逻辑：校验节点非空且为对象 -> 逐一取出 view-uuid、format-version、location、properties -> 解析 schemas 数组（委托
   * {@link SchemaParser}） -> 解析 current-version-id 与 versions 数组 （委托 {@link ViewVersionParser}） ->
   * 解析 version-log 数组（委托 {@link ViewHistoryEntryParser}） -> 组装 ImmutableViewMetadata（changes
   * 为空，因从文件加载无待提交变更）。
   *
   * @param metadataLocation 元数据文件路径，可为 null
   * @param json 已解析的 JSON 节点
   * @return 视图元数据
   */
  public static ViewMetadata fromJson(String metadataLocation, JsonNode json) {
    Preconditions.checkArgument(json != null, "Cannot parse view metadata from null object");
    Preconditions.checkArgument(
        json.isObject(), "Cannot parse view metadata from non-object: %s", json);

    String uuid = JsonUtil.getString(VIEW_UUID, json);
    int formatVersion = JsonUtil.getInt(FORMAT_VERSION, json);
    String location = JsonUtil.getString(LOCATION, json);
    Map<String, String> properties = JsonUtil.getStringMap(PROPERTIES, json);

    JsonNode schemasNode = JsonUtil.get(SCHEMAS, json);

    Preconditions.checkArgument(
        schemasNode.isArray(), "Cannot parse schemas from non-array: %s", schemasNode);
    List<Schema> schemas = Lists.newArrayListWithExpectedSize(schemasNode.size());

    for (JsonNode schemaNode : schemasNode) {
      schemas.add(SchemaParser.fromJson(schemaNode));
    }

    int currentVersionId = JsonUtil.getInt(CURRENT_VERSION_ID, json);
    JsonNode versionsNode = JsonUtil.get(VERSIONS, json);
    Preconditions.checkArgument(
        versionsNode.isArray(), "Cannot parse versions from non-array: %s", versionsNode);
    List<ViewVersion> versions = Lists.newArrayListWithExpectedSize(versionsNode.size());
    for (JsonNode versionNode : versionsNode) {
      versions.add(ViewVersionParser.fromJson(versionNode));
    }

    JsonNode versionLogNode = JsonUtil.get(VERSION_LOG, json);
    Preconditions.checkArgument(
        versionLogNode.isArray(), "Cannot parse version-log from non-array: %s", versionLogNode);
    List<ViewHistoryEntry> historyEntries =
        Lists.newArrayListWithExpectedSize(versionLogNode.size());
    for (JsonNode vLog : versionLogNode) {
      historyEntries.add(ViewHistoryEntryParser.fromJson(vLog));
    }

    return ImmutableViewMetadata.of(
        uuid,
        formatVersion,
        location,
        schemas,
        currentVersionId,
        versions,
        historyEntries,
        properties,
        ImmutableList.of(),
        metadataLocation);
  }

  /**
   * 以覆盖方式将 {@link ViewMetadata} 写入文件（文件已存在则覆盖）。
   *
   * @param metadata 视图元数据
   * @param outputFile 目标输出文件
   */
  public static void overwrite(ViewMetadata metadata, OutputFile outputFile) {
    internalWrite(metadata, outputFile, true);
  }

  /**
   * 以新建方式将 {@link ViewMetadata} 写入文件（文件已存在则报错）。
   *
   * @param metadata 视图元数据
   * @param outputFile 目标输出文件
   */
  public static void write(ViewMetadata metadata, OutputFile outputFile) {
    internalWrite(metadata, outputFile, false);
  }

  /**
   * 从输入文件读取并解析 {@link ViewMetadata}。
   *
   * <p>逻辑：打开输入流 -> 用 Jackson 读取为 JsonNode -> 委托 {@link #fromJson(String, JsonNode)} 解析（携带文件位置）。
   *
   * @param file 元数据文件
   * @return 视图元数据
   * @throws UncheckedIOException 读取失败时包装 IOException 抛出
   */
  public static ViewMetadata read(InputFile file) {
    try (InputStream is = file.newStream()) {
      return fromJson(file.location(), JsonUtil.mapper().readValue(is, JsonNode.class));
    } catch (IOException e) {
      throw new UncheckedIOException(String.format("Failed to read json file: %s", file), e);
    }
  }

  /**
   * 内部写入实现：根据 overwrite 选择创建或覆盖输出流，以 UTF-8 美化格式写入 JSON。
   *
   * @param metadata 视图元数据
   * @param outputFile 目标输出文件
   * @param overwrite 是否覆盖已有文件
   * @throws UncheckedIOException 写入失败时包装 IOException 抛出
   */
  private static void internalWrite(
      ViewMetadata metadata, OutputFile outputFile, boolean overwrite) {
    OutputStream stream = overwrite ? outputFile.createOrOverwrite() : outputFile.create();
    try (OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
      JsonGenerator generator = JsonUtil.factory().createGenerator(writer);
      generator.useDefaultPrettyPrinter();
      toJson(metadata, generator);
      generator.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Failed to write json to file: %s", outputFile), e);
    }
  }
}
