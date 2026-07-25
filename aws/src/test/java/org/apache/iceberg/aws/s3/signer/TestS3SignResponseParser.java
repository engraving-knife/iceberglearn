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
package org.apache.iceberg.aws.s3.signer;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Arrays;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestS3SignResponseParser 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestS3SignResponseParser 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestS3SignResponseParser {

  /**
   * 测试场景：null Response。
   *
   * <p>验证该方法在 null Response 条件下的行为是否符合预期。
   */
  @Test
  public void nullResponse() {
    Assertions.assertThatThrownBy(() -> S3SignResponseParser.fromJson((JsonNode) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse s3 sign response from null object");

    Assertions.assertThatThrownBy(() -> S3SignResponseParser.toJson(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid s3 sign response: null");
  }

  /**
   * 测试场景：missing Fields。
   *
   * <p>验证该方法在 missing Fields 条件下的行为是否符合预期。
   */
  @Test
  public void missingFields() {
    Assertions.assertThatThrownBy(() -> S3SignResponseParser.fromJson("{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: uri");

    Assertions.assertThatThrownBy(
            () ->
                S3SignResponseParser.fromJson(
                    "{\"uri\" : \"http://localhost:49208/iceberg-signer-test\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing field: headers");
  }

  /**
   * 测试场景：invalid Uri。
   *
   * <p>验证该方法在 invalid Uri 条件下的行为是否符合预期。
   */
  @Test
  public void invalidUri() {
    Assertions.assertThatThrownBy(
            () -> S3SignResponseParser.fromJson("{\"uri\" : 45, \"headers\" : {}}}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse to a string value: uri: 45");
  }

  @Test
  public void roundTripSerde() {
    S3SignResponse s3SignResponse =
        ImmutableS3SignResponse.builder()
            .uri(URI.create("http://localhost:49208/iceberg-signer-test"))
            .headers(
                ImmutableMap.of(
                    "amz-sdk-request",
                    Arrays.asList("attempt=1", "max=4"),
                    "Content-Length",
                    Arrays.asList("191"),
                    "Content-Type",
                    Arrays.asList("application/json"),
                    "User-Agent",
                    Arrays.asList("aws-sdk-java/2.20.18", "Linux/5.4.0-126")))
            .build();

    String json = S3SignResponseParser.toJson(s3SignResponse, true);
    Assertions.assertThat(S3SignResponseParser.fromJson(json)).isEqualTo(s3SignResponse);
    Assertions.assertThat(json)
        .isEqualTo(
            "{\n"
                + "  \"uri\" : \"http://localhost:49208/iceberg-signer-test\",\n"
                + "  \"headers\" : {\n"
                + "    \"amz-sdk-request\" : [ \"attempt=1\", \"max=4\" ],\n"
                + "    \"Content-Length\" : [ \"191\" ],\n"
                + "    \"Content-Type\" : [ \"application/json\" ],\n"
                + "    \"User-Agent\" : [ \"aws-sdk-java/2.20.18\", \"Linux/5.4.0-126\" ]\n"
                + "  }\n"
                + "}");
  }
}
