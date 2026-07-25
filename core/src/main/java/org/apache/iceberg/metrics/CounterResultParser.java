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
package org.apache.iceberg.metrics;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * {@link CounterResult} 的 JSON 序列化/反序列化器（包级可见）。
 *
 * <p>所属模块：iceberg-core，度量包内负责计数结果在对象与 JSON 之间转换。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link CounterResult} 序列化为含 {@code unit}/{@code value} 字段的 JSON 对象。
 *   <li>提供两种反序列化入口：独立 JSON 对象，以及在父对象中按计数器名定位的子节点。
 * </ul>
 *
 * <p>设计意图：单位序列化时使用其 displayName（人类可读），反序列化时通过 {@link Unit#fromDisplayName(String)} 还原，保证 JSON
 * 的可读性与互操作性。 第二种 {@code fromJson(String, JsonNode)} 用于父级已持有计数器名的场景（如扫描度量结果）， 避免重复读写名称字段。
 *
 * <p>上下游关系：被 {@link ScanMetricsResultParser}、{@link CommitMetricsResultParser} 调用。
 */
class CounterResultParser {

  private static final String MISSING_FIELD_ERROR_MSG =
      "Cannot parse counter from '%s': Missing field '%s'";

  private static final String UNIT = "unit";
  private static final String VALUE = "value";

  private CounterResultParser() {}

  /**
   * 将计数结果序列化为紧凑 JSON 字符串。
   *
   * @param counter 计数结果
   * @return JSON 字符串
   */
  static String toJson(CounterResult counter) {
    return toJson(counter, false);
  }

  /**
   * 将计数结果序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param counter 计数结果
   * @param pretty 是否美化（缩进）输出
   * @return JSON 字符串
   */
  static String toJson(CounterResult counter, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(counter, gen), pretty);
  }

  /**
   * 将计数结果写入 {@link JsonGenerator}，输出 unit（displayName）与 value 两个字段。
   *
   * @param counter 计数结果，不能为 null
   * @param gen JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  static void toJson(CounterResult counter, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != counter, "Invalid counter: null");

    gen.writeStartObject();
    gen.writeStringField(UNIT, counter.unit().displayName());
    gen.writeNumberField(VALUE, counter.value());
    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析 {@link CounterResult}。
   *
   * @param json JSON 字符串
   * @return 计数结果
   */
  static CounterResult fromJson(String json) {
    return JsonUtil.parse(json, CounterResultParser::fromJson);
  }

  /**
   * 从独立 {@link JsonNode} 解析 {@link CounterResult}。
   *
   * <p>逻辑：校验为对象后读取 unit（displayName）与 value，通过 {@link Unit#fromDisplayName(String)} 还原单位并构造结果。
   *
   * @param json JSON 节点，不能为 null 且必须为对象
   * @return 计数结果
   */
  static CounterResult fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse counter from null object");
    Preconditions.checkArgument(json.isObject(), "Cannot parse counter from non-object: %s", json);

    String unit = JsonUtil.getString(UNIT, json);
    long value = JsonUtil.getLong(VALUE, json);
    return CounterResult.of(Unit.fromDisplayName(unit), value);
  }

  /**
   * 在父 {@link JsonNode} 中按计数器名定位并解析 {@link CounterResult}。
   *
   * <p>设计意图：主要供 {@link ScanMetricsResultParser} 使用——在该场景下计数器名已是父对象的 字段名，故此处无需再读写名称，仅解析子节点中的 unit 与
   * value。父对象中不存在该名称时返回 null。
   *
   * @param counterName 计数器名称（父对象中的字段名）
   * @param json 包含所有计数器信息的父 {@link JsonNode}
   * @return 计数结果；若父对象无该字段则返回 null
   */
  static CounterResult fromJson(String counterName, JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse counter from null object");
    Preconditions.checkArgument(json.isObject(), "Cannot parse counter from non-object: %s", json);

    if (!json.has(counterName)) {
      return null;
    }

    JsonNode counter = json.get(counterName);
    Preconditions.checkArgument(counter.has(UNIT), MISSING_FIELD_ERROR_MSG, counterName, UNIT);
    Preconditions.checkArgument(counter.has(VALUE), MISSING_FIELD_ERROR_MSG, counterName, VALUE);

    String unit = JsonUtil.getString(UNIT, counter);
    long value = JsonUtil.getLong(VALUE, counter);
    return CounterResult.of(Unit.fromDisplayName(unit), value);
  }
}
