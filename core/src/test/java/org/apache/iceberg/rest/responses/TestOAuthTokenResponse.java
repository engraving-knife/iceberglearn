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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.apache.iceberg.rest.auth.OAuth2Util;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestOAuthTokenResponse，用于验证 O Auth Token Response 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 O Auth Token Response 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestOAuthTokenResponse extends RequestResponseTestBase<OAuthTokenResponse> {
  /** 辅助方法：all fields from spec。 */
  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"access_token", "token_type", "issued_token_type", "expires_in", "scope"};
  }

  /** 辅助方法：create example instance。 */
  @Override
  public OAuthTokenResponse createExampleInstance() {
    return OAuthTokenResponse.builder()
        .setExpirationInSeconds(600)
        .withToken("test-token")
        .withIssuedTokenType("urn:ietf:params:oauth:token-type:access_token")
        .withTokenType("Bearer")
        .addScope("catalog")
        .build();
  }

  /** 辅助方法：assert equals。 */
  @Override
  public void assertEquals(OAuthTokenResponse actual, OAuthTokenResponse expected) {
    Assertions.assertThat(actual.token()).as("Token should match").isEqualTo(expected.token());
    Assertions.assertThat(actual.tokenType())
        .as("Token type should match")
        .isEqualTo(expected.tokenType());
    Assertions.assertThat(actual.issuedTokenType())
        .as("Issued token type should match")
        .isEqualTo(expected.issuedTokenType());
    Assertions.assertThat(actual.expiresInSeconds())
        .as("Expiration should match")
        .isEqualTo(expected.expiresInSeconds());
    Assertions.assertThat(actual.scopes()).as("Scope should match").isEqualTo(expected.scopes());
  }

  /** 辅助方法：deserialize。 */
  @Override
  public OAuthTokenResponse deserialize(String json) throws JsonProcessingException {
    return OAuth2Util.tokenResponseFromJson(json);
  }

  /** 辅助方法：serialize。 */
  @Override
  public String serialize(OAuthTokenResponse response) throws JsonProcessingException {
    return OAuth2Util.tokenResponseToJson(response);
  }

  /**
   * 测试场景：round trip。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRoundTrip() throws Exception {
    assertRoundTripSerializesEquallyFrom(
        "{\"access_token\":\"bearer-token\",\"token_type\":\"bearer\"}",
        OAuthTokenResponse.builder().withToken("bearer-token").withTokenType("bearer").build());

    assertRoundTripSerializesEquallyFrom(
        "{\"access_token\":\"bearer-token\",\"token_type\":\"bearer\","
            + "\"issued_token_type\":\"urn:ietf:params:oauth:token-type:access_token\"}",
        OAuthTokenResponse.builder()
            .withToken("bearer-token")
            .withTokenType("bearer")
            .withIssuedTokenType("urn:ietf:params:oauth:token-type:access_token")
            .build());

    assertRoundTripSerializesEquallyFrom(
        "{\"access_token\":\"bearer-token\",\"token_type\":\"bearer\",\"expires_in\":600}",
        OAuthTokenResponse.builder()
            .withToken("bearer-token")
            .withTokenType("bearer")
            .setExpirationInSeconds(600)
            .build());

    assertRoundTripSerializesEquallyFrom(
        "{\"access_token\":\"bearer-token\",\"token_type\":\"bearer\",\"scope\":\"a b\"}",
        OAuthTokenResponse.builder()
            .withToken("bearer-token")
            .withTokenType("bearer")
            .addScope("a")
            .addScope("b")
            .build());

    assertRoundTripSerializesEquallyFrom(
        "{\"access_token\":\"bearer-token\",\"token_type\":\"bearer\","
            + "\"issued_token_type\":\"urn:ietf:params:oauth:token-type:access_token\","
            + "\"expires_in\":600,\"scope\":\"a b\"}",
        OAuthTokenResponse.builder()
            .withToken("bearer-token")
            .withTokenType("bearer")
            .withIssuedTokenType("urn:ietf:params:oauth:token-type:access_token")
            .setExpirationInSeconds(600)
            .addScope("a")
            .addScope("b")
            .build());
  }

  /**
   * 测试场景：failures。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFailures() {
    Assertions.assertThatThrownBy(() -> deserialize("{\"token_type\":\"bearer\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing string: access_token");

    Assertions.assertThatThrownBy(
            () -> deserialize("{\"access_token\":34,\"token_type\":\"bearer\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot parse to a string value: access_token: 34");

    Assertions.assertThatThrownBy(() -> deserialize("{\"access_token\":\"bearer-token\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing string: token_type");

    Assertions.assertThatThrownBy(
            () -> deserialize("{\"access_token\":\"bearer-token\",\"token_type\":34}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot parse to a string value: token_type: 34");
  }
}
