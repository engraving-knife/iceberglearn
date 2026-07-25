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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.util.JsonUtil;

/**
 * 统计文件（{@link StatisticsFile}）的 JSON 序列化/反序列化器。
 *
 * <p>所属模块：iceberg-core。职责：在 metadata.json 中持久化统计文件（含 blob 元数据）。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>分层结构：外层统计文件（路径/大小/快照 id/序列号），内层 blob-metadata 列表。
 *   <li>字段 id 列表：每个 blob 记录其关联的字段 id 集合。
 *   <li>常量键名：所有 JSON 键以常量定义。
 * </ul>
 *
 * <p>上下游关系：被 {@link TableMetadataParser} 调用；依赖 {@link JsonUtil}。
 */
public class StatisticsFileParser {

  private static final String SNAPSHOT_ID = "snapshot-id";
  private static final String STATISTICS_PATH = "statistics-path";
  private static final String FILE_SIZE_IN_BYTES = "file-size-in-bytes";
  private static final String FILE_FOOTER_SIZE_IN_BYTES = "file-footer-size-in-bytes";
  private static final String BLOB_METADATA = "blob-metadata";
  private static final String TYPE = "type";
  private static final String SEQUENCE_NUMBER = "sequence-number";
  private static final String FIELDS = "fields";
  private static final String PROPERTIES = "properties";

  /** 私有构造：工具类禁止实例化。 */
  private StatisticsFileParser() {}

  /**
   * 把统计文件序列化为 JSON 字符串（紧凑形式）。
   *
   * @param statisticsFile 统计文件
   * @return JSON 字符串
   */
  public static String toJson(StatisticsFile statisticsFile) {
    return toJson(statisticsFile, false);
  }

  /**
   * 把统计文件序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param statisticsFile 统计文件
   * @param pretty 是否美化输出
   * @return JSON 字符串
   */
  public static String toJson(StatisticsFile statisticsFile, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(statisticsFile, gen), pretty);
  }

  /**
   * 把统计文件写入 JSON 生成器。
   *
   * <p>字段：snapshot-id/statistics-path/file-size-in-bytes/file-footer-size-in-bytes/blob-metadata。
   *
   * @param statisticsFile 统计文件
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  public static void toJson(StatisticsFile statisticsFile, JsonGenerator generator)
      throws IOException {
    generator.writeStartObject();
    generator.writeNumberField(SNAPSHOT_ID, statisticsFile.snapshotId());
    generator.writeStringField(STATISTICS_PATH, statisticsFile.path());
    generator.writeNumberField(FILE_SIZE_IN_BYTES, statisticsFile.fileSizeInBytes());
    generator.writeNumberField(FILE_FOOTER_SIZE_IN_BYTES, statisticsFile.fileFooterSizeInBytes());
    generator.writeArrayFieldStart(BLOB_METADATA);
    for (BlobMetadata blobMetadata : statisticsFile.blobMetadata()) {
      toJson(blobMetadata, generator);
    }
    generator.writeEndArray();
    generator.writeEndObject();
  }

  /**
   * 从 JSON 节点解析统计文件。
   *
   * <p>字段：snapshot-id/statistics-path/file-size-in-bytes/file-footer-size-in-bytes 必填，
   * blob-metadata 为数组，逐个解析为 {@link BlobMetadata}。
   *
   * @param node JSON 节点
   * @return 解析得到的 {@link GenericStatisticsFile}
   */
  static StatisticsFile fromJson(JsonNode node) {
    long snapshotId = JsonUtil.getLong(SNAPSHOT_ID, node);
    String path = JsonUtil.getString(STATISTICS_PATH, node);
    long fileSizeInBytes = JsonUtil.getLong(FILE_SIZE_IN_BYTES, node);
    long fileFooterSizeInBytes = JsonUtil.getLong(FILE_FOOTER_SIZE_IN_BYTES, node);
    ImmutableList.Builder<BlobMetadata> blobMetadata = ImmutableList.builder();
    JsonNode blobsJson = node.get(BLOB_METADATA);
    Preconditions.checkArgument(
        blobsJson != null && blobsJson.isArray(),
        "Cannot parse blob metadata from non-array: %s",
        blobsJson);
    for (JsonNode blobJson : blobsJson) {
      blobMetadata.add(blobMetadataFromJson(blobJson));
    }
    return new GenericStatisticsFile(
        snapshotId, path, fileSizeInBytes, fileFooterSizeInBytes, blobMetadata.build());
  }

  /**
   * 把 blob 元数据写入 JSON 生成器。
   *
   * <p>字段：type/snapshot-id/sequence-number/fields（数组）/properties（可选）。
   *
   * @param blobMetadata blob 元数据
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  private static void toJson(BlobMetadata blobMetadata, JsonGenerator generator)
      throws IOException {
    generator.writeStartObject();
    generator.writeStringField(TYPE, blobMetadata.type());
    generator.writeNumberField(SNAPSHOT_ID, blobMetadata.sourceSnapshotId());
    generator.writeNumberField(SEQUENCE_NUMBER, blobMetadata.sourceSnapshotSequenceNumber());
    generator.writeArrayFieldStart(FIELDS);
    for (int field : blobMetadata.fields()) {
      generator.writeNumber(field);
    }
    generator.writeEndArray();

    if (!blobMetadata.properties().isEmpty()) {
      JsonUtil.writeStringMap(PROPERTIES, blobMetadata.properties(), generator);
    }
    generator.writeEndObject();
  }

  /**
   * 从 JSON 节点解析 blob 元数据。
   *
   * @param node JSON 节点
   * @return 解析得到的 {@link GenericBlobMetadata}
   */
  private static BlobMetadata blobMetadataFromJson(JsonNode node) {
    String type = JsonUtil.getString(TYPE, node);
    long sourceSnapshotId = JsonUtil.getLong(SNAPSHOT_ID, node);
    long sourceSnapshotSequenceNumber = JsonUtil.getLong(SEQUENCE_NUMBER, node);
    List<Integer> fields = JsonUtil.getIntegerList(FIELDS, node);
    Map<String, String> properties;
    if (node.has(PROPERTIES)) {
      properties = JsonUtil.getStringMap(PROPERTIES, node);
    } else {
      properties = ImmutableMap.of();
    }
    return new GenericBlobMetadata(
        type, sourceSnapshotId, sourceSnapshotSequenceNumber, fields, properties);
  }
}
