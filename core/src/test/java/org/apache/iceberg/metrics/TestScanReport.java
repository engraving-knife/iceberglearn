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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.expressions.Expressions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestScanReport，用于验证 Scan Report 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Scan Report 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestScanReport {

  /**
   * 测试场景：missing fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void missingFields() {
    Assertions.assertThatThrownBy(() -> ImmutableScanReport.builder().build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Cannot build ScanReport, some of required attributes are not set [tableName, snapshotId, filter, schemaId, scanMetrics]");

    Assertions.assertThatThrownBy(() -> ImmutableScanReport.builder().tableName("x").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Cannot build ScanReport, some of required attributes are not set [snapshotId, filter, schemaId, scanMetrics]");

    Assertions.assertThatThrownBy(
            () ->
                ImmutableScanReport.builder()
                    .tableName("x")
                    .filter(Expressions.alwaysTrue())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Cannot build ScanReport, some of required attributes are not set [snapshotId, schemaId, scanMetrics]");

    Assertions.assertThatThrownBy(
            () ->
                ImmutableScanReport.builder()
                    .tableName("x")
                    .filter(Expressions.alwaysTrue())
                    .snapshotId(23L)
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Cannot build ScanReport, some of required attributes are not set [schemaId, scanMetrics]");

    Assertions.assertThatThrownBy(
            () ->
                ImmutableScanReport.builder()
                    .tableName("x")
                    .filter(Expressions.alwaysTrue())
                    .snapshotId(23L)
                    .schemaId(4)
                    .addProjectedFieldIds(1, 2)
                    .addProjectedFieldNames("c1", "c2")
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Cannot build ScanReport, some of required attributes are not set [scanMetrics]");
  }

  /**
   * 测试场景：from empty scan metrics。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void fromEmptyScanMetrics() {
    String tableName = "x";
    int schemaId = 4;
    List<Integer> fieldIds = Arrays.asList(1, 2);
    List<String> fieldNames = Arrays.asList("c1", "c2");
    ScanReport scanReport =
        ImmutableScanReport.builder()
            .tableName(tableName)
            .snapshotId(23L)
            .filter(Expressions.alwaysTrue())
            .schemaId(schemaId)
            .projectedFieldIds(fieldIds)
            .projectedFieldNames(fieldNames)
            .scanMetrics(ScanMetricsResult.fromScanMetrics(ScanMetrics.noop()))
            .build();

    Assertions.assertThat(scanReport.tableName()).isEqualTo(tableName);
    Assertions.assertThat(scanReport.schemaId()).isEqualTo(schemaId);
    Assertions.assertThat(scanReport.projectedFieldIds()).isEqualTo(fieldIds);
    Assertions.assertThat(scanReport.projectedFieldNames()).isEqualTo(fieldNames);
    Assertions.assertThat(scanReport.filter()).isEqualTo(Expressions.alwaysTrue());
    Assertions.assertThat(scanReport.snapshotId()).isEqualTo(23L);
    Assertions.assertThat(scanReport.scanMetrics().totalPlanningDuration()).isNull();
    Assertions.assertThat(scanReport.scanMetrics().resultDataFiles()).isNull();
    Assertions.assertThat(scanReport.scanMetrics().resultDeleteFiles()).isNull();
    Assertions.assertThat(scanReport.scanMetrics().totalDataManifests()).isNull();
    Assertions.assertThat(scanReport.scanMetrics().totalDeleteManifests()).isNull();
    Assertions.assertThat(scanReport.scanMetrics().scannedDataManifests()).isNull();
    Assertions.assertThat(scanReport.scanMetrics().skippedDataManifests()).isNull();
    Assertions.assertThat(scanReport.scanMetrics().totalFileSizeInBytes()).isNull();
    Assertions.assertThat(scanReport.scanMetrics().totalDeleteFileSizeInBytes()).isNull();
  }

  /**
   * 测试场景：from scan metrics。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void fromScanMetrics() {
    ScanMetrics scanMetrics = ScanMetrics.of(new DefaultMetricsContext());
    scanMetrics.totalPlanningDuration().record(10, TimeUnit.MINUTES);
    scanMetrics.resultDataFiles().increment(5L);
    scanMetrics.resultDeleteFiles().increment(5L);
    scanMetrics.scannedDataManifests().increment(5L);
    scanMetrics.totalFileSizeInBytes().increment(1024L);
    scanMetrics.totalDataManifests().increment(5L);

    String tableName = "x";
    int schemaId = 4;
    List<Integer> fieldIds = Arrays.asList(1, 2);
    List<String> fieldNames = Arrays.asList("c1", "c2");

    ScanReport scanReport =
        ImmutableScanReport.builder()
            .tableName(tableName)
            .snapshotId(23L)
            .filter(Expressions.alwaysTrue())
            .schemaId(schemaId)
            .projectedFieldIds(fieldIds)
            .projectedFieldNames(fieldNames)
            .scanMetrics(ScanMetricsResult.fromScanMetrics(scanMetrics))
            .build();

    Assertions.assertThat(scanReport.tableName()).isEqualTo(tableName);
    Assertions.assertThat(scanReport.schemaId()).isEqualTo(schemaId);
    Assertions.assertThat(scanReport.projectedFieldIds()).isEqualTo(fieldIds);
    Assertions.assertThat(scanReport.projectedFieldNames()).isEqualTo(fieldNames);
    Assertions.assertThat(scanReport.filter()).isEqualTo(Expressions.alwaysTrue());
    Assertions.assertThat(scanReport.snapshotId()).isEqualTo(23L);
    Assertions.assertThat(scanReport.scanMetrics().totalPlanningDuration().totalDuration())
        .isEqualTo(Duration.ofMinutes(10L));
    Assertions.assertThat(scanReport.scanMetrics().resultDataFiles().value()).isEqualTo(5);
    Assertions.assertThat(scanReport.scanMetrics().resultDeleteFiles().value()).isEqualTo(5);
    Assertions.assertThat(scanReport.scanMetrics().scannedDataManifests().value()).isEqualTo(5);
    Assertions.assertThat(scanReport.scanMetrics().totalDataManifests().value()).isEqualTo(5);
    Assertions.assertThat(scanReport.scanMetrics().totalFileSizeInBytes().value()).isEqualTo(1024L);
  }

  /**
   * 测试场景：null scan metrics。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void nullScanMetrics() {
    Assertions.assertThatThrownBy(() -> ScanMetrics.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("metricsContext");
  }
}
