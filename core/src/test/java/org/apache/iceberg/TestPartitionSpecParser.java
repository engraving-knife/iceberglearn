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

import org.junit.Assert;
import org.junit.Test;

/**
 * 测试类：TestPartitionSpecParser，用于验证 Partition Spec Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Partition Spec Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestPartitionSpecParser extends TableTestBase {
  /** 辅助方法：partition spec parser。 */
  public TestPartitionSpecParser() {
    super(1);
  }

  /**
   * 测试场景：to json for 1 table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testToJsonForV1Table() {
    String expected =
        "{\n"
            + "  \"spec-id\" : 0,\n"
            + "  \"fields\" : [ {\n"
            + "    \"name\" : \"data_bucket\",\n"
            + "    \"transform\" : \"bucket[16]\",\n"
            + "    \"source-id\" : 2,\n"
            + "    \"field-id\" : 1000\n"
            + "  } ]\n"
            + "}";
    Assert.assertEquals(expected, PartitionSpecParser.toJson(table.spec(), true));

    PartitionSpec spec =
        PartitionSpec.builderFor(table.schema()).bucket("id", 8).bucket("data", 16).build();

    table.ops().commit(table.ops().current(), table.ops().current().updatePartitionSpec(spec));

    expected =
        "{\n"
            + "  \"spec-id\" : 1,\n"
            + "  \"fields\" : [ {\n"
            + "    \"name\" : \"id_bucket\",\n"
            + "    \"transform\" : \"bucket[8]\",\n"
            + "    \"source-id\" : 1,\n"
            + "    \"field-id\" : 1000\n"
            + "  }, {\n"
            + "    \"name\" : \"data_bucket\",\n"
            + "    \"transform\" : \"bucket[16]\",\n"
            + "    \"source-id\" : 2,\n"
            + "    \"field-id\" : 1001\n"
            + "  } ]\n"
            + "}";
    Assert.assertEquals(expected, PartitionSpecParser.toJson(table.spec(), true));
  }

  /**
   * 测试场景：from json with field id。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFromJsonWithFieldId() {
    String specString =
        "{\n"
            + "  \"spec-id\" : 1,\n"
            + "  \"fields\" : [ {\n"
            + "    \"name\" : \"id_bucket\",\n"
            + "    \"transform\" : \"bucket[8]\",\n"
            + "    \"source-id\" : 1,\n"
            + "    \"field-id\" : 1001\n"
            + "  }, {\n"
            + "    \"name\" : \"data_bucket\",\n"
            + "    \"transform\" : \"bucket[16]\",\n"
            + "    \"source-id\" : 2,\n"
            + "    \"field-id\" : 1000\n"
            + "  } ]\n"
            + "}";

    PartitionSpec spec = PartitionSpecParser.fromJson(table.schema(), specString);

    Assert.assertEquals(2, spec.fields().size());
    // should be the field ids in the JSON
    Assert.assertEquals(1001, spec.fields().get(0).fieldId());
    Assert.assertEquals(1000, spec.fields().get(1).fieldId());
  }

  /**
   * 测试场景：from json without field id。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFromJsonWithoutFieldId() {
    String specString =
        "{\n"
            + "  \"spec-id\" : 1,\n"
            + "  \"fields\" : [ {\n"
            + "    \"name\" : \"id_bucket\",\n"
            + "    \"transform\" : \"bucket[8]\",\n"
            + "    \"source-id\" : 1\n"
            + "  }, {\n"
            + "    \"name\" : \"data_bucket\",\n"
            + "    \"transform\" : \"bucket[16]\",\n"
            + "    \"source-id\" : 2\n"
            + "  } ]\n"
            + "}";

    PartitionSpec spec = PartitionSpecParser.fromJson(table.schema(), specString);

    Assert.assertEquals(2, spec.fields().size());
    // should be the default assignment
    Assert.assertEquals(1000, spec.fields().get(0).fieldId());
    Assert.assertEquals(1001, spec.fields().get(1).fieldId());
  }

  /**
   * 测试场景：transforms。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTransforms() {
    for (PartitionSpec spec : PartitionSpecTestBase.SPECS) {
      Assert.assertEquals(
          "To/from JSON should produce equal partition spec", spec, roundTripJSON(spec));
    }
  }

  /** 辅助方法：round trip json。 */
  private static PartitionSpec roundTripJSON(PartitionSpec spec) {
    return PartitionSpecParser.fromJson(
        PartitionSpecTestBase.SCHEMA, PartitionSpecParser.toJson(spec));
  }
}
