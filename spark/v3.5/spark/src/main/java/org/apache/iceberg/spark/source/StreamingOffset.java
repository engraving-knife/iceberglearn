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
package org.apache.iceberg.spark.source;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;
import org.apache.spark.sql.connector.read.streaming.Offset;

/**
 * Iceberg 表的结构化流偏移量。
 *
 * <p>所属模块：iceberg-spark（source 子包）。继承 Spark {@link Offset}，跟踪结构化流读取 Iceberg 表的进度：当前处理的快照
 * ID、已扫描文件位置以及是否扫描全部文件。
 *
 * <p>设计意图：以快照 + 位置定位消费进度，支持增量与全量（启动时扫描全部文件）两种模式； 提供 JSON 序列化以便 Spark 持久化 checkpoint。
 *
 * <p>上下游关系：由 {@link SparkMicroBatchStream} 使用，Spark 通过其 JSON 持久化与恢复偏移。
 */
class StreamingOffset extends Offset {
  static final StreamingOffset START_OFFSET = new StreamingOffset(-1L, -1, false);

  private static final int CURR_VERSION = 1;
  private static final String VERSION = "version";
  private static final String SNAPSHOT_ID = "snapshot_id";
  private static final String POSITION = "position";
  private static final String SCAN_ALL_FILES = "scan_all_files";

  private final long snapshotId;
  private final long position;
  private final boolean scanAllFiles;

  /**
   * 构造偏移量。
   *
   * @param snapshotId 当前处理的快照 ID
   * @param position 快照内最后扫描文件的位置
   * @param scanAllFiles 是否扫描快照内全部文件（如启动流时读取全部数据）
   */
  StreamingOffset(long snapshotId, long position, boolean scanAllFiles) {
    this.snapshotId = snapshotId;
    this.position = position;
    this.scanAllFiles = scanAllFiles;
  }

  /** 从 JSON 字符串反序列化为偏移量。 */
  static StreamingOffset fromJson(String json) {
    Preconditions.checkNotNull(json, "Cannot parse StreamingOffset JSON: null");

    try {
      JsonNode node = JsonUtil.mapper().readValue(json, JsonNode.class);
      return fromJsonNode(node);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Failed to parse StreamingOffset from JSON string %s", json), e);
    }
  }

  /** 从输入流反序列化为偏移量。 */
  static StreamingOffset fromJson(InputStream inputStream) {
    Preconditions.checkNotNull(inputStream, "Cannot parse StreamingOffset from inputStream: null");

    JsonNode node;
    try {
      node = JsonUtil.mapper().readValue(inputStream, JsonNode.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read StreamingOffset from json", e);
    }

    return fromJsonNode(node);
  }

  /** 序列化为 JSON（含版本、快照 ID、位置、是否全扫描）。 */
  @Override
  public String json() {
    StringWriter writer = new StringWriter();
    try {
      JsonGenerator generator = JsonUtil.factory().createGenerator(writer);
      generator.writeStartObject();
      generator.writeNumberField(VERSION, CURR_VERSION);
      generator.writeNumberField(SNAPSHOT_ID, snapshotId);
      generator.writeNumberField(POSITION, position);
      generator.writeBooleanField(SCAN_ALL_FILES, scanAllFiles);
      generator.writeEndObject();
      generator.flush();

    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write StreamingOffset to json", e);
    }

    return writer.toString();
  }

  /** 返回快照 ID。 */
  long snapshotId() {
    return snapshotId;
  }

  /** 返回位置。 */
  long position() {
    return position;
  }

  /** 是否扫描全部文件。 */
  boolean shouldScanAllFiles() {
    return scanAllFiles;
  }
  /** 判断是否相等。 */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof StreamingOffset) {
      StreamingOffset offset = (StreamingOffset) obj;
      return offset.snapshotId == snapshotId
          && offset.position == position
          && offset.scanAllFiles == scanAllFiles;
    } else {
      return false;
    }
  }
  /** 返回哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hashCode(snapshotId, position, scanAllFiles);
  }
  /** 返回字符串表示。 */
  @Override
  public String toString() {
    return String.format(
        "Streaming Offset[%d: position (%d) scan_all_files (%b)]",
        snapshotId, position, scanAllFiles);
  }

  /** 由 JsonNode 解析偏移量，校验版本号一致。 */
  private static StreamingOffset fromJsonNode(JsonNode node) {
    // The version of StreamingOffset. The offset was created with a version number
    // used to validate when deserializing from json string.
    int version = JsonUtil.getInt(VERSION, node);
    Preconditions.checkArgument(
        version == CURR_VERSION,
        "This version of Iceberg source only supports version %s. Version %s is not supported.",
        CURR_VERSION,
        version);

    long snapshotId = JsonUtil.getLong(SNAPSHOT_ID, node);
    int position = JsonUtil.getInt(POSITION, node);
    boolean shouldScanAllFiles = JsonUtil.getBool(SCAN_ALL_FILES, node);

    return new StreamingOffset(snapshotId, position, shouldScanAllFiles);
  }
}
