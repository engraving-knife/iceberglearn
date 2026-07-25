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
import java.util.List;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionParser;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * {@link ScanReport} 的 JSON 序列化/反序列化器。
 *
 * <p>所属模块：iceberg-core，度量包内负责扫描报告对象与 JSON 之间的双向转换， 供 REST 度量上报及本地持久化使用。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link ScanReport} 序列化为 JSON（含表名、快照 ID、filter、schema、投影字段、metrics、metadata）。
 *   <li>从 JSON 还原 {@link ScanReport}，filter 委托 {@link ExpressionParser}、 metrics 委托 {@link
 *       ScanMetricsResultParser} 处理。
 *   <li>提供 {@link #toJsonWithoutStartEnd} 以便嵌入到更大的 JSON 结构中（如 REST 上报请求体）。
 * </ul>
 *
 * <p>设计意图：无状态工具类，构造私有，仅暴露静态方法；metadata 为空时不写该字段以精简输出。
 *
 * <p>上下游关系：被 REST 模块 {@code ReportMetricsRequestParser} 调用；也被测试与上报流程直接使用。
 */
public class ScanReportParser {
  private static final String TABLE_NAME = "table-name";
  private static final String SNAPSHOT_ID = "snapshot-id";
  private static final String FILTER = "filter";
  private static final String SCHEMA_ID = "schema-id";
  private static final String PROJECTED_FIELD_IDS = "projected-field-ids";
  private static final String PROJECTED_FIELD_NAMES = "projected-field-names";
  private static final String METRICS = "metrics";
  private static final String METADATA = "metadata";

  private ScanReportParser() {}

  /**
   * 将扫描报告序列化为紧凑 JSON 字符串。
   *
   * @param scanReport 扫描报告
   * @return JSON 字符串
   */
  public static String toJson(ScanReport scanReport) {
    return toJson(scanReport, false);
  }

  /**
   * 将扫描报告序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param scanReport 扫描报告
   * @param pretty 是否美化（缩进）输出
   * @return JSON 字符串
   */
  public static String toJson(ScanReport scanReport, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(scanReport, gen), pretty);
  }

  /**
   * 将扫描报告写入 {@link JsonGenerator}，包含起始/结束对象边界。
   *
   * @param scanReport 扫描报告，不能为 null
   * @param gen JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  public static void toJson(ScanReport scanReport, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != scanReport, "Invalid scan report: null");

    gen.writeStartObject();
    toJsonWithoutStartEnd(scanReport, gen);
    gen.writeEndObject();
  }

  /**
   * 将扫描报告写入 {@link JsonGenerator}，但不写起始/结束对象边界。
   *
   * <p>设计意图：主要供 {@link org.apache.iceberg.rest.requests.ReportMetricsRequestParser}
   * 使用——该解析器需要把报告内容嵌入到外层请求体 JSON 中，故此处仅输出字段本身。
   *
   * @param scanReport 扫描报告，不能为 null
   * @param gen JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  public static void toJsonWithoutStartEnd(ScanReport scanReport, JsonGenerator gen)
      throws IOException {
    Preconditions.checkArgument(null != scanReport, "Invalid scan report: null");

    gen.writeStringField(TABLE_NAME, scanReport.tableName());
    gen.writeNumberField(SNAPSHOT_ID, scanReport.snapshotId());

    gen.writeFieldName(FILTER);
    ExpressionParser.toJson(scanReport.filter(), gen);

    gen.writeNumberField(SCHEMA_ID, scanReport.schemaId());

    JsonUtil.writeIntegerArray(PROJECTED_FIELD_IDS, scanReport.projectedFieldIds(), gen);
    JsonUtil.writeStringArray(PROJECTED_FIELD_NAMES, scanReport.projectedFieldNames(), gen);

    gen.writeFieldName(METRICS);
    ScanMetricsResultParser.toJson(scanReport.scanMetrics(), gen);

    if (!scanReport.metadata().isEmpty()) {
      JsonUtil.writeStringMap(METADATA, scanReport.metadata(), gen);
    }
  }

  /**
   * 从 JSON 字符串解析 {@link ScanReport}。
   *
   * @param json JSON 字符串
   * @return 扫描报告
   */
  public static ScanReport fromJson(String json) {
    return JsonUtil.parse(json, ScanReportParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link ScanReport}。
   *
   * <p>逻辑：校验为对象后，依次读取表名、快照 ID、schema ID、投影字段 ID 与名称列表； filter 委托 {@link ExpressionParser}
   * 解析、metrics 委托 {@link ScanMetricsResultParser} 解析； metadata 字段可选，存在时读取为字符串映射。
   *
   * @param json JSON 节点，不能为 null 且必须为对象
   * @return 扫描报告
   */
  public static ScanReport fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse scan report from null object");
    Preconditions.checkArgument(
        json.isObject(), "Cannot parse scan report from non-object: %s", json);

    String tableName = JsonUtil.getString(TABLE_NAME, json);
    long snapshotId = JsonUtil.getLong(SNAPSHOT_ID, json);
    Expression filter = ExpressionParser.fromJson(JsonUtil.get(FILTER, json));
    int schemaId = JsonUtil.getInt(SCHEMA_ID, json);
    List<Integer> projectedFieldIds = JsonUtil.getIntegerList(PROJECTED_FIELD_IDS, json);
    List<String> projectedFieldNames = JsonUtil.getStringList(PROJECTED_FIELD_NAMES, json);
    ScanMetricsResult scanMetricsResult =
        ScanMetricsResultParser.fromJson(JsonUtil.get(METRICS, json));
    ImmutableScanReport.Builder builder =
        ImmutableScanReport.builder()
            .tableName(tableName)
            .snapshotId(snapshotId)
            .schemaId(schemaId)
            .projectedFieldIds(projectedFieldIds)
            .projectedFieldNames(projectedFieldNames)
            .filter(filter)
            .scanMetrics(scanMetricsResult);

    if (json.has(METADATA)) {
      builder.metadata(JsonUtil.getStringMap(METADATA, json));
    }

    return builder.build();
  }
}
