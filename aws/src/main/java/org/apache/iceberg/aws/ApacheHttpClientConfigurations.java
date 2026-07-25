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
import software.amazon.awssdk.http.apache.ApacheHttpClient;

/**
 * Apache HTTP 客户端配置承载类。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的模块，处于引擎层之下、AWS SDK 之上， 为 S3/Glue/DynamoDB 等客户端提供统一的 HTTP
 * 传输层配置）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>解析并持有 AWS SDK v2 中 {@link software.amazon.awssdk.http.apache.ApacheHttpClient} 所支持的各类连接调优参数
 *       （连接/套接字超时、连接获取超时、最大连接数、空闲连接回收、TCP keep-alive 等）。
 *   <li>把上述参数应用到 {@link software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder} 上， 使生成的
 *       AWS 同步客户端使用自定义的 Apache HTTP 连接池而非 SDK 默认值。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>解耦配置解析与客户端构建：通过 {@link #create(Map)} 集中读取属性，再由 {@link
 *       #configureHttpClientBuilder(AwsSyncClientBuilder)} 应用到具体 Builder， 便于在多个 AWS 客户端复用同一套 HTTP
 *       配置。
 *   <li>采用可为 null 的包装类型（Long/Boolean/Integer）作为字段，仅在用户显式配置时 才覆盖默认值，避免误把 null 写成 0 而覆盖 SDK 合理默认值。
 *   <li>构造器私有 + 静态工厂方法，强制通过 {@link #create(Map)} 创建实例，保证初始化完整。
 * </ul>
 *
 * <p>上下游关系：由 HttpClientProperties 调用 {@link #create(Map)} 创建， 再被 AwsClientFactories 及各 AWS
 * 客户端工厂用于配置底层 HTTP 传输。
 */
class ApacheHttpClientConfigurations {
  private Long connectionTimeoutMs;
  private Long socketTimeoutMs;
  private Long acquisitionTimeoutMs;
  private Long connectionMaxIdleTimeMs;
  private Long connectionTimeToLiveMs;
  private Boolean expectContinueEnabled;
  private Integer maxConnections;
  private Boolean tcpKeepAliveEnabled;
  private Boolean useIdleConnectionReaperEnabled;

  private ApacheHttpClientConfigurations() {}

  /**
   * 配置 AWS 同步客户端 Builder 使用本类持有的 Apache HTTP 客户端配置。
   *
   * <p>逻辑：创建一个新的 ApacheHttpClient.Builder，调用 {@link
   * #configureApacheHttpClientBuilder(ApacheHttpClient.Builder)} 应用各项参数， 然后通过 httpClientBuilder 注入到
   * AWS 客户端构建器中。
   *
   * @param awsClientBuilder 待配置的 AWS 同步客户端构建器
   * @param <T> 客户端构建器类型
   */
  public <T extends AwsSyncClientBuilder> void configureHttpClientBuilder(T awsClientBuilder) {
    ApacheHttpClient.Builder apacheHttpClientBuilder = ApacheHttpClient.builder();
    configureApacheHttpClientBuilder(apacheHttpClientBuilder);
    awsClientBuilder.httpClientBuilder(apacheHttpClientBuilder);
  }

  /**
   * 从配置 Map 读取 Apache HTTP 客户端相关属性并写入本实例字段。
   *
   * <p>逻辑：依次通过 PropertyUtil 把以毫秒为单位的超时、布尔开关、最大连接数等 属性解析为可空包装类型，未配置的属性保持为 null（应用阶段不会覆盖 SDK 默认值）。
   *
   * @param httpClientProperties HTTP 客户端配置 Map
   */
  private void initialize(Map<String, String> httpClientProperties) {
    this.connectionTimeoutMs =
        PropertyUtil.propertyAsNullableLong(
            httpClientProperties, HttpClientProperties.APACHE_CONNECTION_TIMEOUT_MS);
    this.socketTimeoutMs =
        PropertyUtil.propertyAsNullableLong(
            httpClientProperties, HttpClientProperties.APACHE_SOCKET_TIMEOUT_MS);
    this.acquisitionTimeoutMs =
        PropertyUtil.propertyAsNullableLong(
            httpClientProperties, HttpClientProperties.APACHE_CONNECTION_ACQUISITION_TIMEOUT_MS);
    this.connectionMaxIdleTimeMs =
        PropertyUtil.propertyAsNullableLong(
            httpClientProperties, HttpClientProperties.APACHE_CONNECTION_MAX_IDLE_TIME_MS);
    this.connectionTimeToLiveMs =
        PropertyUtil.propertyAsNullableLong(
            httpClientProperties, HttpClientProperties.APACHE_CONNECTION_TIME_TO_LIVE_MS);
    this.expectContinueEnabled =
        PropertyUtil.propertyAsNullableBoolean(
            httpClientProperties, HttpClientProperties.APACHE_EXPECT_CONTINUE_ENABLED);
    this.maxConnections =
        PropertyUtil.propertyAsNullableInt(
            httpClientProperties, HttpClientProperties.APACHE_MAX_CONNECTIONS);
    this.tcpKeepAliveEnabled =
        PropertyUtil.propertyAsNullableBoolean(
            httpClientProperties, HttpClientProperties.APACHE_TCP_KEEP_ALIVE_ENABLED);
    this.useIdleConnectionReaperEnabled =
        PropertyUtil.propertyAsNullableBoolean(
            httpClientProperties, HttpClientProperties.APACHE_USE_IDLE_CONNECTION_REAPER_ENABLED);
  }

  /**
   * 将已解析的参数应用到 Apache HTTP 客户端构建器上。
   *
   * <p>逻辑：逐项判断字段是否非空，非空则用 Duration.ofMillis(long) 转换时间值 并调用对应 setter；布尔/整数字段直接传入。该方法是包级可见以便单元测试验证。
   *
   * @param apacheHttpClientBuilder 待配置的 Apache HTTP 客户端构建器
   */
  @VisibleForTesting
  void configureApacheHttpClientBuilder(ApacheHttpClient.Builder apacheHttpClientBuilder) {
    if (connectionTimeoutMs != null) {
      apacheHttpClientBuilder.connectionTimeout(Duration.ofMillis(connectionTimeoutMs));
    }
    if (socketTimeoutMs != null) {
      apacheHttpClientBuilder.socketTimeout(Duration.ofMillis(socketTimeoutMs));
    }
    if (acquisitionTimeoutMs != null) {
      apacheHttpClientBuilder.connectionAcquisitionTimeout(Duration.ofMillis(acquisitionTimeoutMs));
    }
    if (connectionMaxIdleTimeMs != null) {
      apacheHttpClientBuilder.connectionMaxIdleTime(Duration.ofMillis(connectionMaxIdleTimeMs));
    }
    if (connectionTimeToLiveMs != null) {
      apacheHttpClientBuilder.connectionTimeToLive(Duration.ofMillis(connectionTimeToLiveMs));
    }
    if (expectContinueEnabled != null) {
      apacheHttpClientBuilder.expectContinueEnabled(expectContinueEnabled);
    }
    if (maxConnections != null) {
      apacheHttpClientBuilder.maxConnections(maxConnections);
    }
    if (tcpKeepAliveEnabled != null) {
      apacheHttpClientBuilder.tcpKeepAlive(tcpKeepAliveEnabled);
    }
    if (useIdleConnectionReaperEnabled != null) {
      apacheHttpClientBuilder.useIdleConnectionReaper(useIdleConnectionReaperEnabled);
    }
  }

  /**
   * 静态工厂方法：根据配置 Map 构造一个已初始化的 ApacheHttpClientConfigurations 实例。
   *
   * @param properties HTTP 客户端配置 Map
   * @return 已完成初始化的配置实例
   */
  public static ApacheHttpClientConfigurations create(Map<String, String> properties) {
    ApacheHttpClientConfigurations configurations = new ApacheHttpClientConfigurations();
    configurations.initialize(properties);
    return configurations;
  }
}
