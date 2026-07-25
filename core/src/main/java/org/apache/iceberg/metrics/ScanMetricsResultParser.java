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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * {@link ScanMetricsResult} 的 JSON 序列化/反序列化器（包级可见）。
 *
 * <p>所属模块：iceberg-core，度量包内负责把扫描度量结果在对象与 JSON 之间转换， 供 REST 上报、日志输出及跨进程传输使用。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link ScanMetricsResult} 的各非空字段写入 JSON 对象，字段名为对应度量常量。
 *   <li>从 JSON 对象按字段名还原 {@link ScanMetricsResult}，委托 {@link CounterResultParser} 与 {@link
 *       TimerResultParser} 处理子结构。
 * </ul>
 *
 * <p>设计意图：仅序列化非 null 字段以精简输出；反序列化时字段缺失视为该指标未采集， 与可空语义保持一致。无状态，故构造为私有且仅提供静态方法。
 *
 * <p>上下游关系：被 {@link ScanReportParser} 调用以序列化扫描报告中的 metrics 字段； 也被 REST 模块（如 {@code
 * ReportMetricsRequestParser}）间接使用。
 */
class ScanMetricsResultParser {
  private ScanMetricsResultParser() {}

  /**
   * 将扫描度量结果序列化为紧凑 JSON 字符串。
   *
   * @param metrics 扫描度量结果
   * @return JSON 字符串
   */
  static String toJson(ScanMetricsResult metrics) {
    return toJson(metrics, false);
  }

  /**
   * 将扫描度量结果序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param metrics 扫描度量结果
   * @param pretty 是否美化（缩进）输出
   * @return JSON 字符串
   */
  static String toJson(ScanMetricsResult metrics, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(metrics, gen), pretty);
  }

  /**
   * 将扫描度量结果写入 {@link JsonGenerator}。
   *
   * <p>逻辑：依次检查每个度量字段，非 null 时写入对应字段名并委托对应子解析器输出其值， null 字段被跳过以保持 JSON 精简。整体包裹在起始/结束对象之间。
   *
   * @param metrics 扫描度量结果，不能为 null
   * @param gen JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  static void toJson(ScanMetricsResult metrics, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != metrics, "Invalid scan metrics: null");

    gen.writeStartObject();

    if (null != metrics.totalPlanningDuration()) {
      gen.writeFieldName(ScanMetrics.TOTAL_PLANNING_DURATION);
      TimerResultParser.toJson(metrics.totalPlanningDuration(), gen);
    }

    if (null != metrics.resultDataFiles()) {
      gen.writeFieldName(ScanMetrics.RESULT_DATA_FILES);
      CounterResultParser.toJson(metrics.resultDataFiles(), gen);
    }

    if (null != metrics.resultDeleteFiles()) {
      gen.writeFieldName(ScanMetrics.RESULT_DELETE_FILES);
      CounterResultParser.toJson(metrics.resultDeleteFiles(), gen);
    }

    if (null != metrics.totalDataManifests()) {
      gen.writeFieldName(ScanMetrics.TOTAL_DATA_MANIFESTS);
      CounterResultParser.toJson(metrics.totalDataManifests(), gen);
    }

    if (null != metrics.totalDeleteManifests()) {
      gen.writeFieldName(ScanMetrics.TOTAL_DELETE_MANIFESTS);
      CounterResultParser.toJson(metrics.totalDeleteManifests(), gen);
    }

    if (null != metrics.scannedDataManifests()) {
      gen.writeFieldName(ScanMetrics.SCANNED_DATA_MANIFESTS);
      CounterResultParser.toJson(metrics.scannedDataManifests(), gen);
    }

    if (null != metrics.skippedDataManifests()) {
      gen.writeFieldName(ScanMetrics.SKIPPED_DATA_MANIFESTS);
      CounterResultParser.toJson(metrics.skippedDataManifests(), gen);
    }

    if (null != metrics.totalFileSizeInBytes()) {
      gen.writeFieldName(ScanMetrics.TOTAL_FILE_SIZE_IN_BYTES);
      CounterResultParser.toJson(metrics.totalFileSizeInBytes(), gen);
    }

    if (null != metrics.totalDeleteFileSizeInBytes()) {
      gen.writeFieldName(ScanMetrics.TOTAL_DELETE_FILE_SIZE_IN_BYTES);
      CounterResultParser.toJson(metrics.totalDeleteFileSizeInBytes(), gen);
    }

    if (null != metrics.skippedDataFiles()) {
      gen.writeFieldName(ScanMetrics.SKIPPED_DATA_FILES);
      CounterResultParser.toJson(metrics.skippedDataFiles(), gen);
    }

    if (null != metrics.skippedDeleteFiles()) {
      gen.writeFieldName(ScanMetrics.SKIPPED_DELETE_FILES);
      CounterResultParser.toJson(metrics.skippedDeleteFiles(), gen);
    }

    if (null != metrics.scannedDeleteManifests()) {
      gen.writeFieldName(ScanMetrics.SCANNED_DELETE_MANIFESTS);
      CounterResultParser.toJson(metrics.scannedDeleteManifests(), gen);
    }

    if (null != metrics.skippedDeleteManifests()) {
      gen.writeFieldName(ScanMetrics.SKIPPED_DELETE_MANIFESTS);
      CounterResultParser.toJson(metrics.skippedDeleteManifests(), gen);
    }

    if (null != metrics.indexedDeleteFiles()) {
      gen.writeFieldName(ScanMetrics.INDEXED_DELETE_FILES);
      CounterResultParser.toJson(metrics.indexedDeleteFiles(), gen);
    }

    if (null != metrics.equalityDeleteFiles()) {
      gen.writeFieldName(ScanMetrics.EQUALITY_DELETE_FILES);
      CounterResultParser.toJson(metrics.equalityDeleteFiles(), gen);
    }

    if (null != metrics.positionalDeleteFiles()) {
      gen.writeFieldName(ScanMetrics.POSITIONAL_DELETE_FILES);
      CounterResultParser.toJson(metrics.positionalDeleteFiles(), gen);
    }

    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析 {@link ScanMetricsResult}。
   *
   * @param json JSON 字符串
   * @return 扫描度量结果
   */
  static ScanMetricsResult fromJson(String json) {
    return JsonUtil.parse(json, ScanMetricsResultParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link ScanMetricsResult}。
   *
   * <p>逻辑：校验节点为对象后，按各度量字段名委托 {@link CounterResultParser}/{@link TimerResultParser} 逐字段解析，
   * 缺失字段由子解析器返回 null，最终由 ImmutableBuilder 构建。
   *
   * @param json JSON 节点，不能为 null 且必须为对象
   * @return 扫描度量结果
   */
  static ScanMetricsResult fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse scan metrics from null object");
    Preconditions.checkArgument(
        json.isObject(), "Cannot parse scan metrics from non-object: %s", json);

    return ImmutableScanMetricsResult.builder()
        .totalPlanningDuration(
            TimerResultParser.fromJson(ScanMetrics.TOTAL_PLANNING_DURATION, json))
        .resultDataFiles(CounterResultParser.fromJson(ScanMetrics.RESULT_DATA_FILES, json))
        .resultDeleteFiles(CounterResultParser.fromJson(ScanMetrics.RESULT_DELETE_FILES, json))
        .totalDataManifests(CounterResultParser.fromJson(ScanMetrics.TOTAL_DATA_MANIFESTS, json))
        .totalDeleteManifests(
            CounterResultParser.fromJson(ScanMetrics.TOTAL_DELETE_MANIFESTS, json))
        .scannedDataManifests(
            CounterResultParser.fromJson(ScanMetrics.SCANNED_DATA_MANIFESTS, json))
        .skippedDataManifests(
            CounterResultParser.fromJson(ScanMetrics.SKIPPED_DATA_MANIFESTS, json))
        .totalFileSizeInBytes(
            CounterResultParser.fromJson(ScanMetrics.TOTAL_FILE_SIZE_IN_BYTES, json))
        .totalDeleteFileSizeInBytes(
            CounterResultParser.fromJson(ScanMetrics.TOTAL_DELETE_FILE_SIZE_IN_BYTES, json))
        .skippedDataFiles(CounterResultParser.fromJson(ScanMetrics.SKIPPED_DATA_FILES, json))
        .skippedDeleteFiles(CounterResultParser.fromJson(ScanMetrics.SKIPPED_DELETE_FILES, json))
        .scannedDeleteManifests(
            CounterResultParser.fromJson(ScanMetrics.SCANNED_DELETE_MANIFESTS, json))
        .skippedDeleteManifests(
            CounterResultParser.fromJson(ScanMetrics.SKIPPED_DELETE_MANIFESTS, json))
        .indexedDeleteFiles(CounterResultParser.fromJson(ScanMetrics.INDEXED_DELETE_FILES, json))
        .equalityDeleteFiles(CounterResultParser.fromJson(ScanMetrics.EQUALITY_DELETE_FILES, json))
        .positionalDeleteFiles(
            CounterResultParser.fromJson(ScanMetrics.POSITIONAL_DELETE_FILES, json))
        .build();
  }
}
