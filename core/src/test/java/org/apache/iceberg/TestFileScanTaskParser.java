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

import org.apache.iceberg.expressions.ExpressionUtil;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 测试类：TestFileScanTaskParser，用于验证 File Scan Task Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 File Scan Task Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestFileScanTaskParser {
  /**
   * 测试场景：null arguments。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNullArguments() {
    Assertions.assertThatThrownBy(() -> FileScanTaskParser.toJson(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid file scan task: null");

    Assertions.assertThatThrownBy(() -> FileScanTaskParser.fromJson(null, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid JSON string for file scan task: null");
  }

  /**
   * 测试场景：parser。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testParser(boolean caseSensitive) {
    PartitionSpec spec = TableTestBase.SPEC;
    FileScanTask fileScanTask = createScanTask(spec, caseSensitive);
    String jsonStr = FileScanTaskParser.toJson(fileScanTask);
    Assertions.assertThat(jsonStr).isEqualTo(expectedFileScanTaskJson());
    FileScanTask deserializedTask = FileScanTaskParser.fromJson(jsonStr, caseSensitive);
    assertFileScanTaskEquals(fileScanTask, deserializedTask, spec, caseSensitive);
  }

  /** 辅助方法：create scan task。 */
  private FileScanTask createScanTask(PartitionSpec spec, boolean caseSensitive) {
    ResidualEvaluator residualEvaluator;
    if (spec.isUnpartitioned()) {
      residualEvaluator = ResidualEvaluator.unpartitioned(Expressions.alwaysTrue());
    } else {
      residualEvaluator = ResidualEvaluator.of(spec, Expressions.equal("id", 1), caseSensitive);
    }

    return new BaseFileScanTask(
        TableTestBase.FILE_A,
        new DeleteFile[] {TableTestBase.FILE_A_DELETES, TableTestBase.FILE_A2_DELETES},
        SchemaParser.toJson(TableTestBase.SCHEMA),
        PartitionSpecParser.toJson(spec),
        residualEvaluator);
  }

  /** 辅助方法：expected file scan task json。 */
  private String expectedFileScanTaskJson() {
    return "{\"schema\":{\"type\":\"struct\",\"schema-id\":0,\"fields\":["
        + "{\"id\":3,\"name\":\"id\",\"required\":true,\"type\":\"int\"},"
        + "{\"id\":4,\"name\":\"data\",\"required\":true,\"type\":\"string\"}]},"
        + "\"spec\":{\"spec-id\":0,\"fields\":[{\"name\":\"data_bucket\","
        + "\"transform\":\"bucket[16]\",\"source-id\":4,\"field-id\":1000}]},"
        + "\"data-file\":{\"spec-id\":0,\"content\":\"DATA\",\"file-path\":\"/path/to/data-a.parquet\","
        + "\"file-format\":\"PARQUET\",\"partition\":{\"1000\":0},"
        + "\"file-size-in-bytes\":10,\"record-count\":1,\"sort-order-id\":0},"
        + "\"start\":0,\"length\":10,"
        + "\"delete-files\":[{\"spec-id\":0,\"content\":\"POSITION_DELETES\","
        + "\"file-path\":\"/path/to/data-a-deletes.parquet\",\"file-format\":\"PARQUET\","
        + "\"partition\":{\"1000\":0},\"file-size-in-bytes\":10,\"record-count\":1},"
        + "{\"spec-id\":0,\"content\":\"EQUALITY_DELETES\",\"file-path\":\"/path/to/data-a2-deletes.parquet\","
        + "\"file-format\":\"PARQUET\",\"partition\":{\"1000\":0},\"file-size-in-bytes\":10,"
        + "\"record-count\":1,\"equality-ids\":[1],\"sort-order-id\":0}],"
        + "\"residual-filter\":{\"type\":\"eq\",\"term\":\"id\",\"value\":1}}";
  }

  /** 辅助方法：assert file scan task equals。 */
  private static void assertFileScanTaskEquals(
      FileScanTask expected, FileScanTask actual, PartitionSpec spec, boolean caseSensitive) {
    TestContentFileParser.assertContentFileEquals(expected.file(), actual.file(), spec);
    Assertions.assertThat(actual.deletes()).hasSameSizeAs(expected.deletes());
    for (int pos = 0; pos < expected.deletes().size(); ++pos) {
      TestContentFileParser.assertContentFileEquals(
          expected.deletes().get(pos), actual.deletes().get(pos), spec);
    }

    Assertions.assertThat(expected.schema().sameSchema(actual.schema()))
        .as("Schema should match")
        .isTrue();
    Assertions.assertThat(actual.spec()).isEqualTo(expected.spec());
    Assertions.assertThat(
            ExpressionUtil.equivalent(
                expected.residual(),
                actual.residual(),
                TableTestBase.SCHEMA.asStruct(),
                caseSensitive))
        .as("Residual expression should match")
        .isTrue();
  }
}
