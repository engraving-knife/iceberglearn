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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;

/**
 * 文件级说明：指数退避 HTTP 请求重试策略。
 *
 * <p>所属模块：iceberg-core（REST Catalog 客户端 HTTP 传输层的重试策略，供 {@link HTTPClient} 使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在 I/O 异常或特定 HTTP 状态码（429、503）时决定是否重试请求。
 *   <li>计算重试间隔，采用指数退避 + 抖动（jitter）策略，避免重试风暴。
 *   <li>遵循服务端 Retry-After 响应头（如果存在）。
 * </ul>
 *
 * <p>设计意图：基于 Apache HttpClient 5 的 {@link HttpRequestRetryStrategy} 接口实现， 与 {@link
 * org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy} 行为基本一致， 主要差异在 {@link
 * #getRetryInterval(HttpResponse, int, HttpContext)} 中改为指数退避 （2^execCount 秒，上限 64
 * 秒）并附加随机抖动，以更好应对服务端限流场景。
 *
 * <p>不可重试的异常类：InterruptedIOException、UnknownHostException、ConnectException、
 * ConnectionClosedException、NoRouteToHostException、SSLException。
 *
 * <p>可重试的 HTTP 状态码：429（TOO_MANY_REQUESTS）、503（SERVICE_UNAVAILABLE）。
 *
 * <p>上下游关系：被 {@link HTTPClient} 在构建 HttpClient 时设置为重试策略。
 */
class ExponentialHttpRequestRetryStrategy implements HttpRequestRetryStrategy {
  private final int maxRetries;
  private final Set<Class<? extends IOException>> nonRetriableExceptions;
  private final Set<Integer> retriableCodes;

  /**
   * 构造指数退避重试策略。
   *
   * @param maximumRetries 最大重试次数，必须为正数
   * @throws IllegalArgumentException 若 maximumRetries <= 0
   */
  ExponentialHttpRequestRetryStrategy(int maximumRetries) {
    Preconditions.checkArgument(
        maximumRetries > 0, "Cannot set retries to %s, the value must be positive", maximumRetries);
    this.maxRetries = maximumRetries;
    this.retriableCodes =
        ImmutableSet.of(HttpStatus.SC_TOO_MANY_REQUESTS, HttpStatus.SC_SERVICE_UNAVAILABLE);
    this.nonRetriableExceptions =
        ImmutableSet.of(
            InterruptedIOException.class,
            UnknownHostException.class,
            ConnectException.class,
            ConnectionClosedException.class,
            NoRouteToHostException.class,
            SSLException.class);
  }

  /**
   * 判断 I/O 异常时是否应重试请求。
   *
   * <p>逻辑：超过最大重试次数则不重试；异常属于不可重试类则不重试；请求已取消则不重试； 仅当请求方法为幂等（GET/HEAD 等）时才重试。
   *
   * @param request HTTP 请求
   * @param exception 发生的 I/O 异常
   * @param execCount 已执行次数（含当前）
   * @param context HTTP 上下文
   * @return 允许重试返回 true
   */
  @Override
  public boolean retryRequest(
      HttpRequest request, IOException exception, int execCount, HttpContext context) {
    if (execCount > maxRetries) {
      // Do not retry if over max retries
      return false;
    }

    if (nonRetriableExceptions.contains(exception.getClass())) {
      return false;
    } else {
      for (Class<? extends IOException> rejectException : nonRetriableExceptions) {
        if (rejectException.isInstance(exception)) {
          return false;
        }
      }
    }

    if (request instanceof CancellableDependency
        && ((CancellableDependency) request).isCancelled()) {
      return false;
    }

    // Retry if the request is considered idempotent
    return Method.isIdempotent(request.getMethod());
  }

  /**
   * 判断 HTTP 响应状态码是否应重试。
   *
   * <p>逻辑：未超过最大重试次数且状态码为可重试码（429/503）时返回 true。
   *
   * @param response HTTP 响应
   * @param execCount 已执行次数（含当前）
   * @param context HTTP 上下文
   * @return 允许重试返回 true
   */
  @Override
  public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
    return execCount <= maxRetries && retriableCodes.contains(response.getCode());
  }

  /**
   * 计算重试等待间隔。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>优先解析 Retry-After 响应头：先尝试按秒数解析，失败则按 HTTP 日期解析。
   *   <li>若 Retry-After 有效（正值）则直接使用。
   *   <li>否则采用指数退避：delay = 1000 * 2^(execCount-1)，上限 64 秒。
   *   <li>附加随机抖动 jitter（delay 的 10%），避免重试同步风暴。
   * </ol>
   *
   * @param response HTTP 响应
   * @param execCount 已执行次数（含当前）
   * @param context HTTP 上下文
   * @return 重试等待时间
   */
  @Override
  public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
    // a server may send a 429 / 503 with a Retry-After header
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Retry-After
    Header header = response.getFirstHeader(HttpHeaders.RETRY_AFTER);
    TimeValue retryAfter = null;
    if (header != null) {
      String value = header.getValue();
      try {
        retryAfter = TimeValue.ofSeconds(Long.parseLong(value));
      } catch (NumberFormatException ignore) {
        Instant retryAfterDate = DateUtils.parseStandardDate(value);
        if (retryAfterDate != null) {
          retryAfter =
              TimeValue.ofMilliseconds(retryAfterDate.toEpochMilli() - System.currentTimeMillis());
        }
      }

      if (TimeValue.isPositive(retryAfter)) {
        return retryAfter;
      }
    }

    int delayMillis = 1000 * (int) Math.min(Math.pow(2.0, (long) execCount - 1), 64.0);
    int jitter = ThreadLocalRandom.current().nextInt(Math.max(1, (int) (delayMillis * 0.1)));

    return TimeValue.ofMilliseconds(delayMillis + jitter);
  }
}
