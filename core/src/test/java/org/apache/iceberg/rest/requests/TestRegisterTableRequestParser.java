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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestRegisterTableRequestParser，用于验证 Register Table Request Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Register Table Request Parser
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestRegisterTableRequestParser {

  /**
   * 测试场景：null check。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void nullCheck() {
    Assertions.assertThatThrownBy(() -> RegisterTableRequestParser.toJson(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid register table request: null");

    Assertions.assertThatThrownBy(() -> RegisterTableRequestParser.fromJson((JsonNode) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse register table request from null object");
  }

  /**
   * 测试场景：missing fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void missingFields() {
    Assertions.assertThatThrownBy(() -> RegisterTableRequestParser.fromJson("{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: name");

    Assertions.assertThatThrownBy(
            () -> RegisterTableRequestParser.fromJson("{\"name\" : \"test_tbl\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: metadata-location");

    Assertions.assertThatThrownBy(
            () ->
                RegisterTableRequestParser.fromJson(
                    "{\"metadata-location\" : \"file://tmp/NS/test_tbl/metadata/00000-d4f60d2f-2ad2-408b-8832-0ed7fbd851ee.metadata.json\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: name");
  }

  /**
   * 测试场景：round trip serde。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void roundTripSerde() {
    RegisterTableRequest request =
        ImmutableRegisterTableRequest.builder()
            .name("table_1")
            .metadataLocation(
                "file://tmp/NS/test_tbl/metadata/00000-d4f60d2f-2ad2-408b-8832-0ed7fbd851ee.metadata.json")
            .build();

    String expectedJson =
        "{\n"
            + "  \"name\" : \"table_1\",\n"
            + "  \"metadata-location\" : \"file://tmp/NS/test_tbl/metadata/00000-d4f60d2f-2ad2-408b-8832-0ed7fbd851ee.metadata.json\"\n"
            + "}";

    String json = RegisterTableRequestParser.toJson(request, true);
    assertThat(json).isEqualTo(expectedJson);

    assertThat(RegisterTableRequestParser.toJson(RegisterTableRequestParser.fromJson(json), true))
        .isEqualTo(expectedJson);
  }
}
