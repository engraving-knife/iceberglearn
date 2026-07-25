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
package org.apache.iceberg.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 测试类：TestExponentialHttpRequestRetryStrategy，用于验证 Exponential Http Request Retry Strategy 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Exponential Http Request Retry Strategy
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestExponentialHttpRequestRetryStrategy {

  private final HttpRequestRetryStrategy retryStrategy = new ExponentialHttpRequestRetryStrategy(5);

  /**
   * 测试场景：invalid retries。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  public void invalidRetries(int retries) {
    assertThatThrownBy(() -> new ExponentialHttpRequestRetryStrategy(retries))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(String.format("Cannot set retries to %s, the value must be positive", retries));
  }

  /**
   * 测试场景：exponential retry。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void exponentialRetry() {
    HttpRequestRetryStrategy strategy = new ExponentialHttpRequestRetryStrategy(10);
    BasicHttpResponse response = new BasicHttpResponse(503, "Oopsie");

    // note that the upper limit includes ~10% variability
    assertThat(strategy.getRetryInterval(response, 0, null).toMilliseconds()).isEqualTo(0);
    assertThat(strategy.getRetryInterval(response, 1, null).toMilliseconds())
        .isBetween(1000L, 2000L);
    assertThat(strategy.getRetryInterval(response, 2, null).toMilliseconds())
        .isBetween(2000L, 3000L);
    assertThat(strategy.getRetryInterval(response, 3, null).toMilliseconds())
        .isBetween(4000L, 5000L);
    assertThat(strategy.getRetryInterval(response, 4, null).toMilliseconds())
        .isBetween(8000L, 9000L);
    assertThat(strategy.getRetryInterval(response, 5, null).toMilliseconds())
        .isBetween(16000L, 18000L);
    assertThat(strategy.getRetryInterval(response, 6, null).toMilliseconds())
        .isBetween(32000L, 36000L);
    assertThat(strategy.getRetryInterval(response, 7, null).toMilliseconds())
        .isBetween(64000L, 72000L);
    assertThat(strategy.getRetryInterval(response, 10, null).toMilliseconds())
        .isBetween(64000L, 72000L);
  }

  /**
   * 测试场景：basic retry。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void basicRetry() {
    BasicHttpResponse response503 = new BasicHttpResponse(503, "Oopsie");
    assertThat(retryStrategy.retryRequest(response503, 3, null)).isTrue();

    BasicHttpResponse response429 = new BasicHttpResponse(429, "Oopsie");
    assertThat(retryStrategy.retryRequest(response429, 3, null)).isTrue();

    BasicHttpResponse response404 = new BasicHttpResponse(404, "Oopsie");
    assertThat(retryStrategy.retryRequest(response404, 3, null)).isFalse();
  }

  /**
   * 测试场景：no retry on connect timeout。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void noRetryOnConnectTimeout() {
    HttpGet request = new HttpGet("/");

    assertThat(retryStrategy.retryRequest(request, new SocketTimeoutException(), 1, null))
        .isFalse();
  }

  /**
   * 测试场景：no retry on connect。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void noRetryOnConnect() {
    HttpGet request = new HttpGet("/");

    assertThat(retryStrategy.retryRequest(request, new ConnectException(), 1, null)).isFalse();
  }

  /**
   * 测试场景：no retry on connection closed。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void noRetryOnConnectionClosed() {
    HttpGet request = new HttpGet("/");

    assertThat(retryStrategy.retryRequest(request, new ConnectionClosedException(), 1, null))
        .isFalse();
  }

  /**
   * 测试场景：no retry for no route to host exception。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void noRetryForNoRouteToHostException() {
    HttpGet request = new HttpGet("/");

    assertThat(retryStrategy.retryRequest(request, new NoRouteToHostException(), 1, null))
        .isFalse();
  }

  /**
   * 测试场景：no retry on ssl failure。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void noRetryOnSSLFailure() {
    HttpGet request = new HttpGet("/");

    assertThat(retryStrategy.retryRequest(request, new SSLException("encryption failed"), 1, null))
        .isFalse();
  }

  /**
   * 测试场景：no retry on unknown host。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void noRetryOnUnknownHost() {
    HttpGet request = new HttpGet("/");

    assertThat(retryStrategy.retryRequest(request, new UnknownHostException(), 1, null)).isFalse();
  }

  /**
   * 测试场景：no retry on interrupted failure。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void noRetryOnInterruptedFailure() {
    HttpGet request = new HttpGet("/");

    assertThat(retryStrategy.retryRequest(request, new InterruptedIOException(), 1, null))
        .isFalse();
  }

  /**
   * 测试场景：no retry on aborted requests。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void noRetryOnAbortedRequests() {
    HttpGet request = new HttpGet("/");
    request.cancel();

    assertThat(retryStrategy.retryRequest(request, new IOException(), 1, null)).isFalse();
  }

  /**
   * 测试场景：retry on non aborted requests。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void retryOnNonAbortedRequests() {
    HttpGet request = new HttpGet("/");

    assertThat(retryStrategy.retryRequest(request, new IOException(), 1, null)).isTrue();
  }

  /**
   * 测试场景：retry after header as long。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void retryAfterHeaderAsLong() {
    HttpResponse response = new BasicHttpResponse(503, "Oopsie");
    response.setHeader(HttpHeaders.RETRY_AFTER, "321");

    assertThat(retryStrategy.getRetryInterval(response, 3, null).toSeconds()).isEqualTo(321L);
  }

  /**
   * 测试场景：retry after header as date。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void retryAfterHeaderAsDate() {
    HttpResponse response = new BasicHttpResponse(503, "Oopsie");
    response.setHeader(
        HttpHeaders.RETRY_AFTER,
        DateUtils.formatStandardDate(Instant.now().plus(100, ChronoUnit.SECONDS)));

    assertThat(retryStrategy.getRetryInterval(response, 3, null).toSeconds()).isBetween(0L, 100L);
  }

  /**
   * 测试场景：retry after header as past date。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void retryAfterHeaderAsPastDate() {
    HttpResponse response = new BasicHttpResponse(503, "Oopsie");
    response.setHeader(
        HttpHeaders.RETRY_AFTER,
        DateUtils.formatStandardDate(Instant.now().minus(100, ChronoUnit.SECONDS)));

    assertThat(retryStrategy.getRetryInterval(response, 3, null).toMilliseconds())
        .isBetween(4000L, 5000L);
  }

  /**
   * 测试场景：invalid retry after header。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void invalidRetryAfterHeader() {
    HttpResponse response = new BasicHttpResponse(503, "Oopsie");
    response.setHeader(HttpHeaders.RETRY_AFTER, "Stuff");

    assertThat(retryStrategy.getRetryInterval(response, 3, null).toMilliseconds())
        .isBetween(4000L, 5000L);
  }
}
