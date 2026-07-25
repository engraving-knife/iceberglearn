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
 * 文件级说明：{@link ViewHistoryEntry} 的 JSON 序列化/反序列化器。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：将视图历史条目（时间戳 + 版本 ID）在 JSON 与 {@link ViewHistoryEntry} 之间互转。
 *
 * <p>设计意图：包级别私有，由 {@link ViewMetadataParser} 在序列化/反序列化视图元数据的 version-log 字段时调用；复用 {@link JsonUtil}
 * 处理 Jackson IO。
 *
 * <p>上下游关系：被 {@link ViewMetadataParser} 调用。
 */
class ViewHistoryEntryParser {

  private ViewHistoryEntryParser() {}

  static final String VERSION_ID = "version-id";
  static final String TIMESTAMP_MS = "timestamp-ms";

  /**
   * 将 {@link ViewHistoryEntry} 序列化为 JSON 字符串（紧凑格式）。
   *
   * @param entry 视图历史条目
   * @return JSON 文本
   */
  static String toJson(ViewHistoryEntry entry) {
    return JsonUtil.generate(gen -> toJson(entry, gen), false);
  }

  /**
   * 将 {@link ViewHistoryEntry} 写入 {@link JsonGenerator}。
   *
   * <p>逻辑：写起始对象 -> 写 timestamp-ms、version-id 字段 -> 写结束对象。
   *
   * @param entry 视图历史条目
   * @param generator Jackson 生成器
   * @throws IOException 写入失败
   */
  static void toJson(ViewHistoryEntry entry, JsonGenerator generator) throws IOException {
    Preconditions.checkArgument(entry != null, "Invalid view history entry: null");
    generator.writeStartObject();
    generator.writeNumberField(TIMESTAMP_MS, entry.timestampMillis());
    generator.writeNumberField(VERSION_ID, entry.versionId());
    generator.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析 {@link ViewHistoryEntry}。
   *
   * @param json JSON 文本
   * @return 视图历史条目
   */
  static ViewHistoryEntry fromJson(String json) {
    return JsonUtil.parse(json, ViewHistoryEntryParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link ViewHistoryEntry}。
   *
   * <p>逻辑：校验节点非空且为对象 -> 取出 version-id、timestamp-ms -> 构建 ImmutableViewHistoryEntry。
   *
   * @param node 已解析的 JSON 节点
   * @return 视图历史条目
   */
  static ViewHistoryEntry fromJson(JsonNode node) {
    Preconditions.checkArgument(node != null, "Cannot parse view history entry from null object");
    Preconditions.checkArgument(
        node.isObject(), "Cannot parse view history entry from non-object: %s", node);
    return ImmutableViewHistoryEntry.builder()
        .versionId(JsonUtil.getInt(VERSION_ID, node))
        .timestampMillis(JsonUtil.getLong(TIMESTAMP_MS, node))
        .build();
  }
}
