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
package org.apache.iceberg.aws;

import java.io.IOException;
import java.util.Map;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.rest.HTTPClient;
import org.apache.iceberg.rest.auth.OAuth2Util;
import org.apache.iceberg.rest.responses.ConfigResponse;
import org.apache.iceberg.rest.responses.OAuthTokenResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import software.amazon.awssdk.auth.signer.internal.SignerConstant;

/**
 * 文件级说明：测试 TestRESTSigV4Signer 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestRESTSigV4Signer 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestRESTSigV4Signer {
  private static ClientAndServer mockServer;
  private static HTTPClient client;

  /** 辅助方法：beforeClass。 */
  @BeforeAll
  public static void beforeClass() {
    mockServer = ClientAndServer.startClientAndServer();

    Map<String, String> properties =
        ImmutableMap.of(
            "rest.sigv4-enabled",
            "true",
            // CI environment doesn't have credentials, but a value must be set for signing
            AwsProperties.REST_SIGNER_REGION,
            "us-west-2",
            AwsProperties.REST_ACCESS_KEY_ID,
            "id",
            AwsProperties.REST_SECRET_ACCESS_KEY,
            "secret");
    client =
        HTTPClient.builder(properties)
            .uri("http://localhost:" + mockServer.getLocalPort())
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer existing_token")
            .build();
  }

  /** 辅助方法：afterClass。 */
  @AfterAll
  public static void afterClass() throws IOException {
    mockServer.stop();
    client.close();
  }

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() {
    mockServer.reset();
  }

  /**
   * 测试场景：sign Request Without Body。
   *
   * <p>验证该方法在 sign Request Without Body 条件下的行为是否符合预期。
   */
  @Test
  public void signRequestWithoutBody() {
    HttpRequest request =
        HttpRequest.request()
            .withMethod("GET")
            .withPath("/v1/config")
            // Require SigV4 Authorization
            .withHeader(Header.header(HttpHeaders.AUTHORIZATION, "AWS4-HMAC-SHA256.*"))
            // Require that conflicting auth header is relocated
            .withHeader(
                Header.header(RESTSigV4Signer.RELOCATED_HEADER_PREFIX + HttpHeaders.AUTHORIZATION))
            // Require the empty body checksum
            .withHeader(
                Header.header(
                    SignerConstant.X_AMZ_CONTENT_SHA256, RESTSigV4Signer.EMPTY_BODY_SHA256));

    mockServer
        .when(request)
        .respond(HttpResponse.response().withStatusCode(HttpStatus.SC_OK).withBody("{}"));

    ConfigResponse response =
        client.get("v1/config", ConfigResponse.class, ImmutableMap.of(), e -> {});

    mockServer.verify(request, VerificationTimes.exactly(1));
    Assertions.assertThat(response).isNotNull();
  }

  /**
   * 测试场景：sign Request With Body。
   *
   * <p>验证该方法在 sign Request With Body 条件下的行为是否符合预期。
   */
  @Test
  public void signRequestWithBody() {
    HttpRequest request =
        HttpRequest.request()
            .withMethod("POST")
            .withPath("/v1/oauth/token")
            // Require SigV4 Authorization
            .withHeader(Header.header(HttpHeaders.AUTHORIZATION, "AWS4-HMAC-SHA256.*"))
            // Require that conflicting auth header is relocated
            .withHeader(
                Header.header(RESTSigV4Signer.RELOCATED_HEADER_PREFIX + HttpHeaders.AUTHORIZATION))
            // Require a body checksum is set
            .withHeader(Header.header(SignerConstant.X_AMZ_CONTENT_SHA256));

    mockServer
        .when(request)
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.SC_OK)
                .withBody(
                    OAuth2Util.tokenResponseToJson(
                        OAuthTokenResponse.builder()
                            .withToken("fake_token")
                            .withTokenType("bearer")
                            .withIssuedTokenType("n/a")
                            .build())));

    Map<String, String> formData = Maps.newHashMap();
    formData.put("client_id", "asdfasd");
    formData.put("client_secret", "asdfasdf");
    formData.put("scope", "catalog");

    OAuthTokenResponse response =
        client.postForm(
            "v1/oauth/token", formData, OAuthTokenResponse.class, ImmutableMap.of(), e -> {});

    mockServer.verify(request, VerificationTimes.exactly(1));
    Assertions.assertThat(response).isNotNull();
  }
}
