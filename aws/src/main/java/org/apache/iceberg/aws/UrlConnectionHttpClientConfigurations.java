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

import java.time.Duration;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.util.PropertyUtil;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

/**
 * URLConnection HTTP 客户端配置承载类。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的模块，处于引擎层之下、AWS SDK 之上， 为 AWS 客户端提供 HTTP 传输层配置）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>解析并持有 AWS SDK v2 中 UrlConnectionHttpClient 所支持的连接超时与套接字超时参数。
 *   <li>把上述参数应用到 AwsSyncClientBuilder 上，使生成的 AWS 同步客户端 使用基于 JDK URLConnection 的轻量 HTTP 传输而非 SDK 默认的
 *       Apache HttpClient。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>URLConnection HttpClient 比 Apache HttpClient 更轻量、依赖更少，适合对依赖体积敏感 的场景（如 serverless /
 *       Lambda）。但功能较简，仅暴露连接超时与套接字超时两项可配置项。
 *   <li>与 ApacheHttpClientConfigurations 保持结构一致：私有构造 + 静态工厂 + 可空包装类型字段， 仅在用户显式配置时覆盖默认值。
 * </ul>
 *
 * <p>上下游关系：由 HttpClientProperties 调用 create(Map) 创建，再被 AwsClientFactories 及各 AWS 客户端工厂用于配置底层 HTTP
 * 传输。
 */
class UrlConnectionHttpClientConfigurations {

  private Long httpClientUrlConnectionConnectionTimeoutMs;
  private Long httpClientUrlConnectionSocketTimeoutMs;

  private UrlConnectionHttpClientConfigurations() {}

  /**
   * 配置 AWS 同步客户端 Builder 使用本类持有的 URLConnection HTTP 客户端配置。
   *
   * <p>逻辑：创建一个新的 UrlConnectionHttpClient.Builder，调用 {@link
   * #configureUrlConnectionHttpClientBuilder(UrlConnectionHttpClient.Builder)} 应用各项参数， 然后通过
   * httpClientBuilder 注入到 AWS 客户端构建器中。
   *
   * @param awsClientBuilder 待配置的 AWS 同步客户端构建器
   * @param <T> 客户端构建器类型
   */
  public <T extends AwsSyncClientBuilder> void configureHttpClientBuilder(T awsClientBuilder) {
    UrlConnectionHttpClient.Builder urlConnectionHttpClientBuilder =
        UrlConnectionHttpClient.builder();
    configureUrlConnectionHttpClientBuilder(urlConnectionHttpClientBuilder);
    awsClientBuilder.httpClientBuilder(urlConnectionHttpClientBuilder);
  }

  /**
   * 从配置 Map 读取 URLConnection HTTP 客户端相关属性并写入本实例字段。
   *
   * <p>逻辑：通过 PropertyUtil 把以毫秒为单位的连接超时、套接字超时解析为可空 Long， 未配置的属性保持为 null（应用阶段不会覆盖 SDK 默认值）。
   *
   * @param httpClientProperties HTTP 客户端配置 Map
   */
  private void initialize(Map<String, String> httpClientProperties) {
    this.httpClientUrlConnectionConnectionTimeoutMs =
        PropertyUtil.propertyAsNullableLong(
            httpClientProperties, HttpClientProperties.URLCONNECTION_CONNECTION_TIMEOUT_MS);
    this.httpClientUrlConnectionSocketTimeoutMs =
        PropertyUtil.propertyAsNullableLong(
            httpClientProperties, HttpClientProperties.URLCONNECTION_SOCKET_TIMEOUT_MS);
  }

  /**
   * 将已解析的参数应用到 URLConnection HTTP 客户端构建器上。
   *
   * <p>逻辑：逐项判断字段是否非空，非空则用 Duration.ofMillis(long) 转换并调用对应 setter。 该方法是包级可见以便单元测试验证。
   *
   * @param urlConnectionHttpClientBuilder 待配置的 URLConnection HTTP 客户端构建器
   */
  @VisibleForTesting
  void configureUrlConnectionHttpClientBuilder(
      UrlConnectionHttpClient.Builder urlConnectionHttpClientBuilder) {
    if (httpClientUrlConnectionConnectionTimeoutMs != null) {
      urlConnectionHttpClientBuilder.connectionTimeout(
          Duration.ofMillis(httpClientUrlConnectionConnectionTimeoutMs));
    }
    if (httpClientUrlConnectionSocketTimeoutMs != null) {
      urlConnectionHttpClientBuilder.socketTimeout(
          Duration.ofMillis(httpClientUrlConnectionSocketTimeoutMs));
    }
  }

  /**
   * 静态工厂方法：根据配置 Map 构造一个已初始化的 UrlConnectionHttpClientConfigurations 实例。
   *
   * @param httpClientProperties HTTP 客户端配置 Map
   * @return 已完成初始化的配置实例
   */
  public static UrlConnectionHttpClientConfigurations create(
      Map<String, String> httpClientProperties) {
    UrlConnectionHttpClientConfigurations configurations =
        new UrlConnectionHttpClientConfigurations();
    configurations.initialize(httpClientProperties);
    return configurations;
  }
}
