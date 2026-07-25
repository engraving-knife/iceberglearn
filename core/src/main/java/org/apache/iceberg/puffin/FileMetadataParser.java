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
package org.apache.iceberg.puffin;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：Puffin 文件 footer 元数据的 JSON 序列化/反序列化器。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link FileMetadata}（含其 {@link BlobMetadata} 列表与文件级属性） 序列化为 footer JSON 文本。
 *   <li>将 footer JSON 解析回 {@link FileMetadata} 内存对象。
 * </ul>
 *
 * <p>设计意图：footer 采用 JSON 编码，字段命名采用 Iceberg 规范的 kebab-case （如 {@code snapshot-id}）；属性仅在非空时输出，减小
 * footer 体积。 通过 {@link JsonUtil} 统一处理 Jackson 的 IO 与字段取值，避免重复样板代码。
 *
 * <p>上下游关系：被 {@link PuffinWriter} 在写 footer 时调用 {@link #toJson}， 被 {@link PuffinReader} 在读 footer
 * 时调用 {@link #fromJson}。
 */
public final class FileMetadataParser {

  private FileMetadataParser() {}

  private static final String BLOBS = "blobs";
  private static final String PROPERTIES = "properties";

  private static final String TYPE = "type";
  private static final String FIELDS = "fields";
  private static final String SNAPSHOT_ID = "snapshot-id";
  private static final String SEQUENCE_NUMBER = "sequence-number";
  private static final String OFFSET = "offset";
  private static final String LENGTH = "length";
  private static final String COMPRESSION_CODEC = "compression-codec";

  /**
   * 将 {@link FileMetadata} 序列化为 JSON 字符串。
   *
   * @param fileMetadata 待序列化的文件元数据
   * @param pretty 是否美化输出
   * @return footer JSON 文本
   */
  public static String toJson(FileMetadata fileMetadata, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(fileMetadata, gen), pretty);
  }

  /**
   * 从 JSON 字符串解析 {@link FileMetadata}。
   *
   * @param json footer JSON 文本
   * @return 解析得到的文件元数据
   */
  public static FileMetadata fromJson(String json) {
    return JsonUtil.parse(json, FileMetadataParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link FileMetadata}。
   *
   * @param json 已解析的 JSON 节点
   * @return 文件元数据
   */
  static FileMetadata fromJson(JsonNode json) {
    return fileMetadataFromJson(json);
  }

  /**
   * 将 {@link FileMetadata} 写入 {@link JsonGenerator}。
   *
   * <p>逻辑：写起始对象 -> 输出 blobs 数组（逐个序列化 {@link BlobMetadata}） -> 属性非空时输出 properties -> 写结束对象。
   *
   * @param fileMetadata 待序列化的文件元数据
   * @param generator Jackson 生成器
   * @throws IOException 写入失败
   */
  static void toJson(FileMetadata fileMetadata, JsonGenerator generator) throws IOException {
    generator.writeStartObject();

    generator.writeArrayFieldStart(BLOBS);
    for (BlobMetadata blobMetadata : fileMetadata.blobs()) {
      toJson(blobMetadata, generator);
    }
    generator.writeEndArray();

    if (!fileMetadata.properties().isEmpty()) {
      JsonUtil.writeStringMap(PROPERTIES, fileMetadata.properties(), generator);
    }

    generator.writeEndObject();
  }

  /**
   * 从 {@link JsonNode} 解析 {@link FileMetadata}。
   *
   * <p>逻辑：校验 blobs 为数组并逐个解析为 {@link BlobMetadata}；properties 节点存在时 读取为字符串 map，否则置空 map；最终组装为 {@link
   * FileMetadata}。
   *
   * @param json 已解析的 JSON 节点
   * @return 文件元数据
   */
  static FileMetadata fileMetadataFromJson(JsonNode json) {

    ImmutableList.Builder<BlobMetadata> blobs = ImmutableList.builder();
    JsonNode blobsJson = JsonUtil.get(BLOBS, json);
    Preconditions.checkArgument(
        blobsJson.isArray(), "Cannot parse blobs from non-array: %s", blobsJson);
    for (JsonNode blobJson : blobsJson) {
      blobs.add(blobMetadataFromJson(blobJson));
    }

    Map<String, String> properties = ImmutableMap.of();
    JsonNode propertiesJson = json.get(PROPERTIES);
    if (propertiesJson != null) {
      properties = JsonUtil.getStringMap(PROPERTIES, json);
    }

    return new FileMetadata(blobs.build(), properties);
  }

  /**
   * 将单个 {@link BlobMetadata} 写入 {@link JsonGenerator}。
   *
   * <p>逻辑：写起始对象 -> 写 type、fields 数组、snapshot-id、sequence-number、offset、length -> 压缩编码非空时写出 ->
   * 属性非空时输出 properties -> 写结束对象。仅在字段非空时输出可选字段， 以减小 footer 体积。
   *
   * @param blobMetadata 待序列化的 Blob 元信息
   * @param generator Jackson 生成器
   * @throws IOException 写入失败
   */
  static void toJson(BlobMetadata blobMetadata, JsonGenerator generator) throws IOException {
    generator.writeStartObject();

    generator.writeStringField(TYPE, blobMetadata.type());

    JsonUtil.writeIntegerArray(FIELDS, blobMetadata.inputFields(), generator);
    generator.writeNumberField(SNAPSHOT_ID, blobMetadata.snapshotId());
    generator.writeNumberField(SEQUENCE_NUMBER, blobMetadata.sequenceNumber());

    generator.writeNumberField(OFFSET, blobMetadata.offset());
    generator.writeNumberField(LENGTH, blobMetadata.length());

    if (blobMetadata.compressionCodec() != null) {
      generator.writeStringField(COMPRESSION_CODEC, blobMetadata.compressionCodec());
    }

    if (!blobMetadata.properties().isEmpty()) {
      JsonUtil.writeStringMap(PROPERTIES, blobMetadata.properties(), generator);
    }

    generator.writeEndObject();
  }

  /**
   * 从 {@link JsonNode} 解析单个 {@link BlobMetadata}。
   *
   * <p>逻辑：按字段名逐一取出 type、fields、snapshot-id、sequence-number、offset、length、 compression-codec（可选）与
   * properties（可选），组装为 {@link BlobMetadata}。
   *
   * @param json 已解析的 JSON 节点
   * @return Blob 元信息
   */
  static BlobMetadata blobMetadataFromJson(JsonNode json) {
    String type = JsonUtil.getString(TYPE, json);
    List<Integer> fields = JsonUtil.getIntegerList(FIELDS, json);
    long snapshotId = JsonUtil.getLong(SNAPSHOT_ID, json);
    long sequenceNumber = JsonUtil.getLong(SEQUENCE_NUMBER, json);
    long offset = JsonUtil.getLong(OFFSET, json);
    long length = JsonUtil.getLong(LENGTH, json);
    String compressionCodec = JsonUtil.getStringOrNull(COMPRESSION_CODEC, json);
    Map<String, String> properties = ImmutableMap.of();
    JsonNode propertiesJson = json.get(PROPERTIES);
    if (propertiesJson != null) {
      properties = JsonUtil.getStringMap(PROPERTIES, json);
    }

    return new BlobMetadata(
        type, fields, snapshotId, sequenceNumber, offset, length, compressionCodec, properties);
  }
}
