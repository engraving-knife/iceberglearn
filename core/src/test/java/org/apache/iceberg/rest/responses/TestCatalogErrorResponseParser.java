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
package org.apache.iceberg.rest.responses;

import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestCatalogErrorResponseParser，用于验证 Catalog Error Response Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Catalog Error Response Parser
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestCatalogErrorResponseParser {

  /**
   * 测试场景：error response to json。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testErrorResponseToJson() {
    String message = "The given namespace does not exist";
    String type = "NoSuchNamespaceException";
    Integer code = 404;
    String errorModelJson =
        String.format("{\"message\":\"%s\",\"type\":\"%s\",\"code\":%d}", message, type, code);
    String json = "{\"error\":" + errorModelJson + "}";
    ErrorResponse response =
        ErrorResponse.builder().withMessage(message).withType(type).responseCode(code).build();
    Assertions.assertThat(ErrorResponseParser.toJson(response))
        .as("Should be able to serialize an error response as json")
        .isEqualTo(json);
  }

  /**
   * 测试场景：error response to json with stack。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testErrorResponseToJsonWithStack() {
    String message = "The given namespace does not exist";
    String type = "NoSuchNamespaceException";
    Integer code = 404;
    List<String> stack = Arrays.asList("a", "b");
    String errorModelJson =
        String.format(
            "{\"message\":\"%s\",\"type\":\"%s\",\"code\":%d,\"stack\":[\"a\",\"b\"]}",
            message, type, code);
    String json = "{\"error\":" + errorModelJson + "}";
    ErrorResponse response =
        ErrorResponse.builder()
            .withMessage(message)
            .withType(type)
            .responseCode(code)
            .withStackTrace(stack)
            .build();
    Assertions.assertThat(ErrorResponseParser.toJson(response))
        .as("Should be able to serialize an error response as json")
        .isEqualTo(json);
  }

  /**
   * 测试场景：error response from json。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testErrorResponseFromJson() {
    String message = "The given namespace does not exist";
    String type = "NoSuchNamespaceException";
    Integer code = 404;
    String errorModelJson =
        String.format("{\"message\":\"%s\",\"type\":\"%s\",\"code\":%d}", message, type, code);
    String json = "{\"error\":" + errorModelJson + "}";

    ErrorResponse expected =
        ErrorResponse.builder().withMessage(message).withType(type).responseCode(code).build();
    assertEquals(expected, ErrorResponseParser.fromJson(json));
  }

  /**
   * 测试场景：error response from json with stack。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testErrorResponseFromJsonWithStack() {
    String message = "The given namespace does not exist";
    String type = "NoSuchNamespaceException";
    Integer code = 404;
    List<String> stack = Arrays.asList("a", "b");
    String errorModelJson =
        String.format(
            "{\"message\":\"%s\",\"type\":\"%s\",\"code\":%d,\"stack\":[\"a\",\"b\"]}",
            message, type, code);
    String json = "{\"error\":" + errorModelJson + "}";

    ErrorResponse expected =
        ErrorResponse.builder()
            .withMessage(message)
            .withType(type)
            .responseCode(code)
            .withStackTrace(stack)
            .build();
    assertEquals(expected, ErrorResponseParser.fromJson(json));
  }

  /**
   * 测试场景：error response from json with explicit null stack。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testErrorResponseFromJsonWithExplicitNullStack() {
    String message = "The given namespace does not exist";
    String type = "NoSuchNamespaceException";
    Integer code = 404;
    List<String> stack = null;
    String errorModelJson =
        String.format(
            "{\"message\":\"%s\",\"type\":\"%s\",\"code\":%d,\"stack\":null}", message, type, code);
    String json = "{\"error\":" + errorModelJson + "}";

    ErrorResponse expected =
        ErrorResponse.builder()
            .withMessage(message)
            .withType(type)
            .responseCode(code)
            .withStackTrace(stack)
            .build();
    assertEquals(expected, ErrorResponseParser.fromJson(json));
  }

  /** 辅助方法：assert equals。 */
  public void assertEquals(ErrorResponse expected, ErrorResponse actual) {
    Assertions.assertThat(actual.message()).isEqualTo(expected.message());
    Assertions.assertThat(actual.type()).isEqualTo(expected.type());
    Assertions.assertThat(actual.code()).isEqualTo(expected.code());
    Assertions.assertThat(actual.stack()).isEqualTo(expected.stack());
  }
}
