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

import static org.apache.iceberg.NullOrder.NULLS_FIRST;
import static org.apache.iceberg.SortDirection.DESC;

import org.apache.iceberg.transforms.UnknownTransform;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 测试类：TestSortOrderParser，用于验证 Sort Order Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Sort Order Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestSortOrderParser extends TableTestBase {
  /** 辅助方法：sort order parser。 */
  public TestSortOrderParser() {
    super(1);
  }

  /**
   * 测试场景：unknown transforms。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testUnknownTransforms() {
    String jsonString =
        "{\n"
            + "  \"order-id\" : 10,\n"
            + "  \"fields\" : [ {\n"
            + "    \"transform\" : \"custom_transform\",\n"
            + "    \"source-id\" : 2,\n"
            + "    \"direction\" : \"desc\",\n"
            + "    \"null-order\" : \"nulls-first\"\n"
            + "  } ]\n"
            + "}";

    SortOrder order = SortOrderParser.fromJson(table.schema(), jsonString);

    Assert.assertEquals(10, order.orderId());
    Assert.assertEquals(1, order.fields().size());
    Assertions.assertThat(order.fields().get(0).transform()).isInstanceOf(UnknownTransform.class);
    Assert.assertEquals("custom_transform", order.fields().get(0).transform().toString());
    Assert.assertEquals(2, order.fields().get(0).sourceId());
    Assert.assertEquals(DESC, order.fields().get(0).direction());
    Assert.assertEquals(NULLS_FIRST, order.fields().get(0).nullOrder());
  }

  /**
   * 测试场景：invalid sort direction。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void invalidSortDirection() {
    String jsonString =
        "{\n"
            + "  \"order-id\" : 10,\n"
            + "  \"fields\" : [ {\n"
            + "    \"transform\" : \"custom_transform\",\n"
            + "    \"source-id\" : 2,\n"
            + "    \"direction\" : \"invalid\",\n"
            + "    \"null-order\" : \"nulls-first\"\n"
            + "  } ]\n"
            + "}";

    Assertions.assertThatThrownBy(() -> SortOrderParser.fromJson(table.schema(), jsonString))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid sort direction: invalid");
  }
}
