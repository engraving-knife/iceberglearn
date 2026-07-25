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
 * {@link CommitMetricsResult} 的 JSON 序列化/反序列化器（包级可见）。
 *
 * <p>所属模块：iceberg-core，度量包内负责把提交度量结果在对象与 JSON 之间转换， 供 REST 上报、日志输出及跨进程传输使用。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link CommitMetricsResult} 的各非空字段写入 JSON 对象，字段名为对应度量常量。
 *   <li>从 JSON 对象按字段名还原 {@link CommitMetricsResult}，委托 {@link CounterResultParser} 与 {@link
 *       TimerResultParser} 处理子结构。
 * </ul>
 *
 * <p>设计意图：仅序列化非 null 字段以精简输出；反序列化时字段缺失视为该指标未采集， 与可空语义保持一致。无状态，故构造为私有且仅提供静态方法。
 *
 * <p>上下游关系：被 {@link CommitReportParser} 调用以序列化提交报告中的 metrics 字段； 也被 REST 模块（如 {@code
 * ReportMetricsRequestParser}）间接使用。
 */
class CommitMetricsResultParser {
  private CommitMetricsResultParser() {}

  /**
   * 将提交度量结果序列化为紧凑 JSON 字符串。
   *
   * @param metrics 提交度量结果
   * @return JSON 字符串
   */
  static String toJson(CommitMetricsResult metrics) {
    return toJson(metrics, false);
  }

  /**
   * 将提交度量结果序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param metrics 提交度量结果
   * @param pretty 是否美化（缩进）输出
   * @return JSON 字符串
   */
  static String toJson(CommitMetricsResult metrics, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(metrics, gen), pretty);
  }

  /**
   * 将提交度量结果写入 {@link JsonGenerator}。
   *
   * <p>逻辑：依次检查每个度量字段，非 null 时写入对应字段名并委托对应子解析器输出其值， null 字段被跳过以保持 JSON 精简。整体包裹在起始/结束对象之间。
   *
   * @param metrics 提交度量结果，不能为 null
   * @param gen JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  static void toJson(CommitMetricsResult metrics, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != metrics, "Invalid commit metrics: null");

    gen.writeStartObject();

    if (null != metrics.totalDuration()) {
      gen.writeFieldName(CommitMetrics.TOTAL_DURATION);
      TimerResultParser.toJson(metrics.totalDuration(), gen);
    }

    if (null != metrics.attempts()) {
      gen.writeFieldName(CommitMetrics.ATTEMPTS);
      CounterResultParser.toJson(metrics.attempts(), gen);
    }

    if (null != metrics.addedDataFiles()) {
      gen.writeFieldName(CommitMetricsResult.ADDED_DATA_FILES);
      CounterResultParser.toJson(metrics.addedDataFiles(), gen);
    }

    if (null != metrics.removedDataFiles()) {
      gen.writeFieldName(CommitMetricsResult.REMOVED_DATA_FILES);
      CounterResultParser.toJson(metrics.removedDataFiles(), gen);
    }

    if (null != metrics.totalDataFiles()) {
      gen.writeFieldName(CommitMetricsResult.TOTAL_DATA_FILES);
      CounterResultParser.toJson(metrics.totalDataFiles(), gen);
    }

    if (null != metrics.addedDeleteFiles()) {
      gen.writeFieldName(CommitMetricsResult.ADDED_DELETE_FILES);
      CounterResultParser.toJson(metrics.addedDeleteFiles(), gen);
    }

    if (null != metrics.addedEqualityDeleteFiles()) {
      gen.writeFieldName(CommitMetricsResult.ADDED_EQ_DELETE_FILES);
      CounterResultParser.toJson(metrics.addedEqualityDeleteFiles(), gen);
    }

    if (null != metrics.addedPositionalDeleteFiles()) {
      gen.writeFieldName(CommitMetricsResult.ADDED_POS_DELETE_FILES);
      CounterResultParser.toJson(metrics.addedPositionalDeleteFiles(), gen);
    }

    if (null != metrics.removedDeleteFiles()) {
      gen.writeFieldName(CommitMetricsResult.REMOVED_DELETE_FILES);
      CounterResultParser.toJson(metrics.removedDeleteFiles(), gen);
    }

    if (null != metrics.removedPositionalDeleteFiles()) {
      gen.writeFieldName(CommitMetricsResult.REMOVED_POS_DELETE_FILES);
      CounterResultParser.toJson(metrics.removedPositionalDeleteFiles(), gen);
    }

    if (null != metrics.removedEqualityDeleteFiles()) {
      gen.writeFieldName(CommitMetricsResult.REMOVED_EQ_DELETE_FILES);
      CounterResultParser.toJson(metrics.removedEqualityDeleteFiles(), gen);
    }

    if (null != metrics.totalDeleteFiles()) {
      gen.writeFieldName(CommitMetricsResult.TOTAL_DELETE_FILES);
      CounterResultParser.toJson(metrics.totalDeleteFiles(), gen);
    }

    if (null != metrics.addedRecords()) {
      gen.writeFieldName(CommitMetricsResult.ADDED_RECORDS);
      CounterResultParser.toJson(metrics.addedRecords(), gen);
    }

    if (null != metrics.removedRecords()) {
      gen.writeFieldName(CommitMetricsResult.REMOVED_RECORDS);
      CounterResultParser.toJson(metrics.removedRecords(), gen);
    }

    if (null != metrics.totalRecords()) {
      gen.writeFieldName(CommitMetricsResult.TOTAL_RECORDS);
      CounterResultParser.toJson(metrics.totalRecords(), gen);
    }

    if (null != metrics.addedFilesSizeInBytes()) {
      gen.writeFieldName(CommitMetricsResult.ADDED_FILE_SIZE_BYTES);
      CounterResultParser.toJson(metrics.addedFilesSizeInBytes(), gen);
    }

    if (null != metrics.removedFilesSizeInBytes()) {
      gen.writeFieldName(CommitMetricsResult.REMOVED_FILE_SIZE_BYTES);
      CounterResultParser.toJson(metrics.removedFilesSizeInBytes(), gen);
    }

    if (null != metrics.totalFilesSizeInBytes()) {
      gen.writeFieldName(CommitMetricsResult.TOTAL_FILE_SIZE_BYTES);
      CounterResultParser.toJson(metrics.totalFilesSizeInBytes(), gen);
    }

    if (null != metrics.addedPositionalDeletes()) {
      gen.writeFieldName(CommitMetricsResult.ADDED_POS_DELETES);
      CounterResultParser.toJson(metrics.addedPositionalDeletes(), gen);
    }

    if (null != metrics.removedPositionalDeletes()) {
      gen.writeFieldName(CommitMetricsResult.REMOVED_POS_DELETES);
      CounterResultParser.toJson(metrics.removedPositionalDeletes(), gen);
    }

    if (null != metrics.totalPositionalDeletes()) {
      gen.writeFieldName(CommitMetricsResult.TOTAL_POS_DELETES);
      CounterResultParser.toJson(metrics.totalPositionalDeletes(), gen);
    }

    if (null != metrics.addedEqualityDeletes()) {
      gen.writeFieldName(CommitMetricsResult.ADDED_EQ_DELETES);
      CounterResultParser.toJson(metrics.addedEqualityDeletes(), gen);
    }

    if (null != metrics.removedEqualityDeletes()) {
      gen.writeFieldName(CommitMetricsResult.REMOVED_EQ_DELETES);
      CounterResultParser.toJson(metrics.removedEqualityDeletes(), gen);
    }

    if (null != metrics.totalEqualityDeletes()) {
      gen.writeFieldName(CommitMetricsResult.TOTAL_EQ_DELETES);
      CounterResultParser.toJson(metrics.totalEqualityDeletes(), gen);
    }

    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析 {@link CommitMetricsResult}。
   *
   * @param json JSON 字符串
   * @return 提交度量结果
   */
  static CommitMetricsResult fromJson(String json) {
    return JsonUtil.parse(json, CommitMetricsResultParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析 {@link CommitMetricsResult}。
   *
   * <p>逻辑：校验节点为对象后，按各度量字段名委托 {@link CounterResultParser}/{@link TimerResultParser} 逐字段解析，
   * 缺失字段由子解析器返回 null，最终由 ImmutableBuilder 构建。
   *
   * @param json JSON 节点，不能为 null 且必须为对象
   * @return 提交度量结果
   */
  static CommitMetricsResult fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse commit metrics from null object");
    Preconditions.checkArgument(
        json.isObject(), "Cannot parse commit metrics from non-object: %s", json);

    return ImmutableCommitMetricsResult.builder()
        .attempts(CounterResultParser.fromJson(CommitMetrics.ATTEMPTS, json))
        .totalDuration(TimerResultParser.fromJson(CommitMetrics.TOTAL_DURATION, json))
        .addedDataFiles(CounterResultParser.fromJson(CommitMetricsResult.ADDED_DATA_FILES, json))
        .removedDataFiles(
            CounterResultParser.fromJson(CommitMetricsResult.REMOVED_DATA_FILES, json))
        .totalDataFiles(CounterResultParser.fromJson(CommitMetricsResult.TOTAL_DATA_FILES, json))
        .addedDeleteFiles(
            CounterResultParser.fromJson(CommitMetricsResult.ADDED_DELETE_FILES, json))
        .addedEqualityDeleteFiles(
            CounterResultParser.fromJson(CommitMetricsResult.ADDED_EQ_DELETE_FILES, json))
        .addedPositionalDeleteFiles(
            CounterResultParser.fromJson(CommitMetricsResult.ADDED_POS_DELETE_FILES, json))
        .removedEqualityDeleteFiles(
            CounterResultParser.fromJson(CommitMetricsResult.REMOVED_EQ_DELETE_FILES, json))
        .removedPositionalDeleteFiles(
            CounterResultParser.fromJson(CommitMetricsResult.REMOVED_POS_DELETE_FILES, json))
        .removedDeleteFiles(
            CounterResultParser.fromJson(CommitMetricsResult.REMOVED_DELETE_FILES, json))
        .totalDeleteFiles(
            CounterResultParser.fromJson(CommitMetricsResult.TOTAL_DELETE_FILES, json))
        .addedRecords(CounterResultParser.fromJson(CommitMetricsResult.ADDED_RECORDS, json))
        .removedRecords(CounterResultParser.fromJson(CommitMetricsResult.REMOVED_RECORDS, json))
        .totalRecords(CounterResultParser.fromJson(CommitMetricsResult.TOTAL_RECORDS, json))
        .addedFilesSizeInBytes(
            CounterResultParser.fromJson(CommitMetricsResult.ADDED_FILE_SIZE_BYTES, json))
        .removedFilesSizeInBytes(
            CounterResultParser.fromJson(CommitMetricsResult.REMOVED_FILE_SIZE_BYTES, json))
        .totalFilesSizeInBytes(
            CounterResultParser.fromJson(CommitMetricsResult.TOTAL_FILE_SIZE_BYTES, json))
        .addedPositionalDeletes(
            CounterResultParser.fromJson(CommitMetricsResult.ADDED_POS_DELETES, json))
        .removedPositionalDeletes(
            CounterResultParser.fromJson(CommitMetricsResult.REMOVED_POS_DELETES, json))
        .totalPositionalDeletes(
            CounterResultParser.fromJson(CommitMetricsResult.TOTAL_POS_DELETES, json))
        .addedEqualityDeletes(
            CounterResultParser.fromJson(CommitMetricsResult.ADDED_EQ_DELETES, json))
        .removedEqualityDeletes(
            CounterResultParser.fromJson(CommitMetricsResult.REMOVED_EQ_DELETES, json))
        .totalEqualityDeletes(
            CounterResultParser.fromJson(CommitMetricsResult.TOTAL_EQ_DELETES, json))
        .build();
  }
}
