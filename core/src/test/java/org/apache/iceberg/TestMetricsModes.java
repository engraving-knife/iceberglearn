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

import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.iceberg.MetricsModes.Counts;
import org.apache.iceberg.MetricsModes.Full;
import org.apache.iceberg.MetricsModes.None;
import org.apache.iceberg.MetricsModes.Truncate;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestMetricsModes，用于验证 Metrics Modes 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Metrics Modes 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestMetricsModes {

  private final int formatVersion;

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：metrics modes。 */
  public TestMetricsModes(int formatVersion) {
    this.formatVersion = formatVersion;
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  /** 辅助方法：after。 */
  @After
  public void after() {
    TestTables.clearTables();
  }

  /**
   * 测试场景：metrics mode parsing。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMetricsModeParsing() {
    Assert.assertEquals(None.get(), MetricsModes.fromString("none"));
    Assert.assertEquals(None.get(), MetricsModes.fromString("nOnE"));
    Assert.assertEquals(Counts.get(), MetricsModes.fromString("counts"));
    Assert.assertEquals(Counts.get(), MetricsModes.fromString("coUntS"));
    Assert.assertEquals(Truncate.withLength(1), MetricsModes.fromString("truncate(1)"));
    Assert.assertEquals(Truncate.withLength(10), MetricsModes.fromString("truNcAte(10)"));
    Assert.assertEquals(Full.get(), MetricsModes.fromString("full"));
    Assert.assertEquals(Full.get(), MetricsModes.fromString("FULL"));
  }

  /**
   * 测试场景：invalid truncation length。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidTruncationLength() {
    Assertions.assertThatThrownBy(() -> MetricsModes.fromString("truncate(0)"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Truncate length should be positive");
  }

  /**
   * 测试场景：invalid column mode value。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidColumnModeValue() {
    Map<String, String> properties =
        ImmutableMap.of(
            TableProperties.DEFAULT_WRITE_METRICS_MODE,
            "full",
            TableProperties.METRICS_MODE_COLUMN_CONF_PREFIX + "col",
            "troncate(5)");

    MetricsConfig config = MetricsConfig.fromProperties(properties);
    Assert.assertEquals(
        "Invalid mode should be defaulted to table default (full)",
        MetricsModes.Full.get(),
        config.columnMode("col"));
  }

  /**
   * 测试场景：invalid default column mode value。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidDefaultColumnModeValue() {
    Map<String, String> properties =
        ImmutableMap.of(
            TableProperties.DEFAULT_WRITE_METRICS_MODE,
            "fuull",
            TableProperties.METRICS_MODE_COLUMN_CONF_PREFIX + "col",
            "troncate(5)");

    MetricsConfig config = MetricsConfig.fromProperties(properties);
    Assert.assertEquals(
        "Invalid mode should be defaulted to library default (truncate(16))",
        MetricsModes.Truncate.withLength(16),
        config.columnMode("col"));
  }

  /**
   * 测试场景：metrics config sorted cols default。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMetricsConfigSortedColsDefault() throws Exception {
    File tableDir = temp.newFolder();
    tableDir.delete(); // created by table create

    Schema schema =
        new Schema(
            required(1, "col1", Types.IntegerType.get()),
            required(2, "col2", Types.IntegerType.get()),
            required(3, "col3", Types.IntegerType.get()),
            required(4, "col4", Types.IntegerType.get()));
    SortOrder sortOrder = SortOrder.builderFor(schema).asc("col2").asc("col3").build();
    Table testTable =
        TestTables.create(
            tableDir, "test", schema, PartitionSpec.unpartitioned(), sortOrder, formatVersion);
    testTable
        .updateProperties()
        .set(TableProperties.DEFAULT_WRITE_METRICS_MODE, "counts")
        .set(TableProperties.METRICS_MODE_COLUMN_CONF_PREFIX + "col1", "counts")
        .set(TableProperties.METRICS_MODE_COLUMN_CONF_PREFIX + "col2", "none")
        .commit();

    MetricsConfig config = MetricsConfig.forTable(testTable);
    Assert.assertEquals(
        "Non-sorted existing column should not be overridden",
        Counts.get(),
        config.columnMode("col1"));
    Assert.assertEquals(
        "Sorted column defaults should not override user specified config",
        None.get(),
        config.columnMode("col2"));
    Assert.assertEquals(
        "Unspecified sorted column should use default",
        Truncate.withLength(16),
        config.columnMode("col3"));
    Assert.assertEquals(
        "Unspecified normal column should use default", Counts.get(), config.columnMode("col4"));
  }

  /**
   * 测试场景：metrics config sorted cols default by invalid。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMetricsConfigSortedColsDefaultByInvalid() throws Exception {
    File tableDir = temp.newFolder();
    tableDir.delete(); // created by table create

    Schema schema =
        new Schema(
            required(1, "col1", Types.IntegerType.get()),
            required(2, "col2", Types.IntegerType.get()),
            required(3, "col3", Types.IntegerType.get()));
    SortOrder sortOrder = SortOrder.builderFor(schema).asc("col2").asc("col3").build();
    Table testTable =
        TestTables.create(
            tableDir, "test", schema, PartitionSpec.unpartitioned(), sortOrder, formatVersion);
    testTable
        .updateProperties()
        .set(TableProperties.DEFAULT_WRITE_METRICS_MODE, "counts")
        .set(TableProperties.METRICS_MODE_COLUMN_CONF_PREFIX + "col1", "full")
        .set(TableProperties.METRICS_MODE_COLUMN_CONF_PREFIX + "col2", "invalid")
        .commit();

    MetricsConfig config = MetricsConfig.forTable(testTable);
    Assert.assertEquals(
        "Non-sorted existing column should not be overridden by sorted column",
        Full.get(),
        config.columnMode("col1"));
    Assert.assertEquals(
        "Original default applies as user entered invalid mode for sorted column",
        Counts.get(),
        config.columnMode("col2"));
  }

  /**
   * 测试场景：metrics config inferred default mode limit。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMetricsConfigInferredDefaultModeLimit() throws IOException {
    Schema schema =
        new Schema(
            required(1, "col1", Types.IntegerType.get()),
            required(2, "col2", Types.IntegerType.get()),
            required(3, "col3", Types.IntegerType.get()));

    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Table table =
        TestTables.create(
            tableDir,
            "test",
            schema,
            PartitionSpec.unpartitioned(),
            SortOrder.unsorted(),
            formatVersion);

    // only infer a default for the first two columns
    table
        .updateProperties()
        .set(TableProperties.METRICS_MAX_INFERRED_COLUMN_DEFAULTS, "2")
        .commit();

    MetricsConfig config = MetricsConfig.forTable(table);

    Assert.assertEquals(
        "Should use default mode for col1", Truncate.withLength(16), config.columnMode("col1"));
    Assert.assertEquals(
        "Should use default mode for col2", Truncate.withLength(16), config.columnMode("col2"));
    Assert.assertEquals("Should use None for col3", None.get(), config.columnMode("col3"));
  }
}
