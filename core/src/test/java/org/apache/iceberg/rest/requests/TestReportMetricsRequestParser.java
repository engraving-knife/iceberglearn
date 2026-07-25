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
package org.apache.iceberg.rest.requests;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.metrics.CommitMetrics;
import org.apache.iceberg.metrics.CommitMetricsResult;
import org.apache.iceberg.metrics.CommitReport;
import org.apache.iceberg.metrics.ImmutableCommitReport;
import org.apache.iceberg.metrics.ImmutableScanReport;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.ScanMetrics;
import org.apache.iceberg.metrics.ScanMetricsResult;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestReportMetricsRequestParser，用于验证 Report Metrics Request Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Report Metrics Request Parser
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestReportMetricsRequestParser {

  /**
   * 测试场景：null check。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void nullCheck() {
    Assertions.assertThatThrownBy(() -> ReportMetricsRequestParser.toJson(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid metrics request: null");

    Assertions.assertThatThrownBy(() -> ReportMetricsRequestParser.fromJson((JsonNode) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse metrics request from null object");
  }

  /**
   * 测试场景：missing fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void missingFields() {
    Assertions.assertThatThrownBy(() -> ReportMetricsRequestParser.fromJson("{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: report-type");

    Assertions.assertThatThrownBy(
            () -> ReportMetricsRequestParser.fromJson("{\"report-type\":\"scan-report\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: table-name");

    Assertions.assertThatThrownBy(
            () ->
                ReportMetricsRequestParser.fromJson(
                    "{\"report-type\":\"scan-report\", \"table-name\" : \"x\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing long: snapshot-id");
  }

  /**
   * 测试场景：invalid report type。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void invalidReportType() {
    Assertions.assertThat(
            ReportMetricsRequestParser.fromJson("{\"report-type\":\"invalid\"}").reportType())
        .isEqualTo(ReportMetricsRequest.unknown().reportType());

    Assertions.assertThat(
            ReportMetricsRequestParser.fromJson(
                    ReportMetricsRequestParser.toJson(
                        ReportMetricsRequest.of(new MetricsReport() {})))
                .reportType())
        .isEqualTo(ReportMetricsRequest.unknown().reportType());

    // this is simulating a newer client sending a request to server running an older version (not
    // knowing the new report type). this should not fail parsing on the server
    String json =
        "{\n"
            + "  \"report-type\" : \"new-report-type\",\n"
            + "  \"table-name\" : \"roundTripTableName\",\n"
            + "  \"snapshot-id\" : 23,\n"
            + "  \"filter\" : true,\n"
            + "  \"schema-id\" : 4,\n"
            + "  \"projected-field-ids\" : [ 1, 2, 3 ],\n"
            + "  \"projected-field-names\" : [ \"c1\", \"c2\", \"c3\" ],\n"
            + "  \"metrics\" : { }\n"
            + "}";

    ReportMetricsRequest request = ReportMetricsRequestParser.fromJson(json);
    Assertions.assertThat(request.reportType())
        .isEqualTo(ReportMetricsRequest.unknown().reportType());
  }

  /**
   * 测试场景：round trip serde with scan report。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void roundTripSerdeWithScanReport() {
    String tableName = "roundTripTableName";
    ScanReport scanReport =
        ImmutableScanReport.builder()
            .tableName(tableName)
            .schemaId(4)
            .addProjectedFieldIds(1, 2, 3)
            .addProjectedFieldNames("c1", "c2", "c3")
            .snapshotId(23L)
            .filter(Expressions.alwaysTrue())
            .scanMetrics(ScanMetricsResult.fromScanMetrics(ScanMetrics.noop()))
            .build();

    String expectedJson =
        "{\n"
            + "  \"report-type\" : \"scan-report\",\n"
            + "  \"table-name\" : \"roundTripTableName\",\n"
            + "  \"snapshot-id\" : 23,\n"
            + "  \"filter\" : true,\n"
            + "  \"schema-id\" : 4,\n"
            + "  \"projected-field-ids\" : [ 1, 2, 3 ],\n"
            + "  \"projected-field-names\" : [ \"c1\", \"c2\", \"c3\" ],\n"
            + "  \"metrics\" : { }\n"
            + "}";

    ReportMetricsRequest metricsRequest = ReportMetricsRequest.of(scanReport);

    String json = ReportMetricsRequestParser.toJson(metricsRequest, true);
    Assertions.assertThat(json).isEqualTo(expectedJson);

    Assertions.assertThat(ReportMetricsRequestParser.fromJson(json).report())
        .isEqualTo(metricsRequest.report());
  }

  /**
   * 测试场景：round trip serde with commit report。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void roundTripSerdeWithCommitReport() {
    String tableName = "roundTripTableName";
    CommitReport commitReport =
        ImmutableCommitReport.builder()
            .tableName(tableName)
            .snapshotId(23L)
            .sequenceNumber(4L)
            .operation("DELETE")
            .commitMetrics(CommitMetricsResult.from(CommitMetrics.noop(), ImmutableMap.of()))
            .build();

    String expectedJson =
        "{\n"
            + "  \"report-type\" : \"commit-report\",\n"
            + "  \"table-name\" : \"roundTripTableName\",\n"
            + "  \"snapshot-id\" : 23,\n"
            + "  \"sequence-number\" : 4,\n"
            + "  \"operation\" : \"DELETE\",\n"
            + "  \"metrics\" : { }\n"
            + "}";

    ReportMetricsRequest metricsRequest = ReportMetricsRequest.of(commitReport);

    String json = ReportMetricsRequestParser.toJson(metricsRequest, true);
    Assertions.assertThat(json).isEqualTo(expectedJson);

    Assertions.assertThat(ReportMetricsRequestParser.fromJson(json).report())
        .isEqualTo(metricsRequest.report());
  }
}
