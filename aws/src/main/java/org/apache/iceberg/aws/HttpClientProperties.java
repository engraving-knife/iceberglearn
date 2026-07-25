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

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.apache.iceberg.util.PropertyUtil;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;

/**
 * 文件级说明：AWS HTTP 客户端配置属性持有者。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中定义并解析所有与 AWS SDK HTTP 客户端相关的配置键（连接超时、socket 超时、 最大连接数、保活、空闲回收等），覆盖 apache 与 urlconnection
 *       两种实现。
 *   <li>提供 {@link #applyHttpClientConfigurations(AwsSyncClientBuilder)} 方法， 将配置注入到任意 AWS 同步客户端
 *       builder 上。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>动态加载：Apache 与 urlconnection 两种 HTTP 实现对应的 SDK 依赖并非强制， 因此通过 {@link DynMethods}
 *       反射调用配置类，避免在类加载期就硬依赖两者， 同时规避同时引入两者导致的 ServiceLoader 冲突（见 issue#6715）。
 *   <li>配置前缀分离：以 {@code http-client.} 前缀过滤出本类关心的属性， 便于从全局 catalog properties 中切片。
 *   <li>可序列化：实现 Serializable 以支持分布式引擎分发。
 * </ul>
 *
 * <p>上下游关系：由 {@link AwsClientFactory} 实现类（如 {@link AssumeRoleAwsClientFactory}） 持有并调用其 apply 方法；产出的
 * HTTP 客户端被 S3/Glue/KMS/DynamoDB 等客户端复用。
 */
public class HttpClientProperties implements Serializable {

  /**
   * HTTP 客户端类型配置键。指定后所有 AWS 客户端使用该类型 HTTP 实现；未设置时使用 {@link #CLIENT_TYPE_DEFAULT}。可选值见下方
   * CLIENT_TYPE_* 常量。
   */
  public static final String CLIENT_TYPE = "http-client.type";

  /**
   * 客户端类型取值：apache。设置后使用 {@link software.amazon.awssdk.http.apache.ApacheHttpClient} 作为 HTTP 客户端实现。
   */
  public static final String CLIENT_TYPE_APACHE = "apache";

  private static final String CLIENT_PREFIX = "http-client.";
  /**
   * 客户端类型取值：urlconnection。设置后使用 {@link
   * software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient} 作为 HTTP 客户端实现。
   */
  public static final String CLIENT_TYPE_URLCONNECTION = "urlconnection";

  /** 默认 HTTP 客户端类型：apache。 */
  public static final String CLIENT_TYPE_DEFAULT = CLIENT_TYPE_APACHE;
  /**
   * 配置 urlconnection 客户端的连接超时（毫秒）。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_URLCONNECTION} 时生效。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/urlconnection/UrlConnectionHttpClient.Builder.html
   */
  public static final String URLCONNECTION_CONNECTION_TIMEOUT_MS =
      "http-client.urlconnection.connection-timeout-ms";
  /**
   * 配置 urlconnection 客户端的 socket 超时（毫秒）。仅当 {@link #CLIENT_TYPE} 为 {@link
   * #CLIENT_TYPE_URLCONNECTION} 时生效。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/urlconnection/UrlConnectionHttpClient.Builder.html
   */
  public static final String URLCONNECTION_SOCKET_TIMEOUT_MS =
      "http-client.urlconnection.socket-timeout-ms";
  /**
   * 配置 apache 客户端的连接超时（毫秒）。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE} 时生效。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_CONNECTION_TIMEOUT_MS =
      "http-client.apache.connection-timeout-ms";
  /**
   * 配置 apache 客户端的 socket 超时（毫秒）。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE} 时生效。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_SOCKET_TIMEOUT_MS = "http-client.apache.socket-timeout-ms";
  /**
   * 配置 apache 客户端获取连接的等待超时（毫秒）。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE} 时生效。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_CONNECTION_ACQUISITION_TIMEOUT_MS =
      "http-client.apache.connection-acquisition-timeout-ms";
  /**
   * 配置 apache 客户端连接最大空闲时长（毫秒）。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE} 时生效。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_CONNECTION_MAX_IDLE_TIME_MS =
      "http-client.apache.connection-max-idle-time-ms";
  /**
   * 配置 apache 客户端连接 TTL（毫秒）。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE} 时生效。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_CONNECTION_TIME_TO_LIVE_MS =
      "http-client.apache.connection-time-to-live-ms";
  /**
   * 是否启用 apache 客户端的 expect-continue 行为。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE}
   * 时生效，默认关闭。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_EXPECT_CONTINUE_ENABLED =
      "http-client.apache.expect-continue-enabled";
  /**
   * 配置 apache 客户端最大连接数。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE} 时生效。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_MAX_CONNECTIONS = "http-client.apache.max-connections";
  /**
   * 是否启用 apache 客户端的 TCP keep-alive。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE} 时生效，默认关闭。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_TCP_KEEP_ALIVE_ENABLED =
      "http-client.apache.tcp-keep-alive-enabled";
  /**
   * 是否启用 apache 客户端的空闲连接回收线程。仅当 {@link #CLIENT_TYPE} 为 {@link #CLIENT_TYPE_APACHE} 时生效，默认开启。
   *
   * <p>详见
   * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/http/apache/ApacheHttpClient.Builder.html
   */
  public static final String APACHE_USE_IDLE_CONNECTION_REAPER_ENABLED =
      "http-client.apache.use-idle-connection-reaper-enabled";

  private String httpClientType;
  private final Map<String, String> httpClientProperties;

  /** 构造默认实例：HTTP 客户端类型为 apache，配置为空 Map。 */
  public HttpClientProperties() {
    this.httpClientType = CLIENT_TYPE_DEFAULT;
    this.httpClientProperties = Collections.emptyMap();
  }

  /**
   * 从 catalog/FileIO properties 解析 HTTP 客户端配置。
   *
   * <p>逻辑：读取 {@link #CLIENT_TYPE}，并以 {@code http-client.} 前缀过滤出相关属性子集。
   *
   * @param properties catalog/FileIO 全部配置键值
   */
  public HttpClientProperties(Map<String, String> properties) {
    this.httpClientType =
        PropertyUtil.propertyAsString(properties, CLIENT_TYPE, CLIENT_TYPE_DEFAULT);
    this.httpClientProperties =
        PropertyUtil.filterProperties(properties, key -> key.startsWith(CLIENT_PREFIX));
  }

  /**
   * 按当前配置的 HTTP 客户端类型，将相应配置应用到 AWS 客户端 builder 上。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>type 为空时回退到默认 apache。
   *   <li>urlconnection：动态加载 UrlConnectionHttpClientConfigurations 并配置 builder。
   *   <li>apache：动态加载 ApacheHttpClientConfigurations 并配置 builder。
   *   <li>其他：抛 IllegalArgumentException。
   * </ul>
   *
   * <p>典型用法：
   *
   * <pre>
   *     S3Client.builder().applyMutation(awsProperties::applyHttpClientConfigurations)
   * </pre>
   *
   * @param builder 待配置的 AWS 同步客户端 builder
   * @param <T> builder 类型
   */
  public <T extends AwsSyncClientBuilder> void applyHttpClientConfigurations(T builder) {
    if (Strings.isNullOrEmpty(httpClientType)) {
      httpClientType = CLIENT_TYPE_DEFAULT;
    }

    switch (httpClientType) {
      case CLIENT_TYPE_URLCONNECTION:
        UrlConnectionHttpClientConfigurations urlConnectionHttpClientConfigurations =
            loadHttpClientConfigurations(UrlConnectionHttpClientConfigurations.class.getName());
        urlConnectionHttpClientConfigurations.configureHttpClientBuilder(builder);
        break;
      case CLIENT_TYPE_APACHE:
        ApacheHttpClientConfigurations apacheHttpClientConfigurations =
            loadHttpClientConfigurations(ApacheHttpClientConfigurations.class.getName());
        apacheHttpClientConfigurations.configureHttpClientBuilder(builder);
        break;
      default:
        throw new IllegalArgumentException("Unrecognized HTTP client type " + httpClientType);
    }
  }

  /**
   * 通过反射动态加载 HTTP 客户端配置类，避免在运行期硬依赖 {@link
   * software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient} 和 {@link
   * software.amazon.awssdk.http.apache.ApacheHttpClient} 两个实现，因为同时引入两者会触发 <a
   * href="https://github.com/apache/iceberg/issues/6715">issue#6715</a> 描述的冲突。
   *
   * @param impl 配置实现类全限定名
   * @param <T> 返回类型
   * @return 配置实现实例
   * @throws IllegalArgumentException 当类不存在 create(Map) 静态方法时
   */
  private <T> T loadHttpClientConfigurations(String impl) {
    Object httpClientConfigurations;
    try {
      httpClientConfigurations =
          DynMethods.builder("create")
              .hiddenImpl(impl, Map.class)
              .buildStaticChecked()
              .invoke(httpClientProperties);
      return (T) httpClientConfigurations;
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format("Cannot create %s to generate and configure the http client builder", impl),
          e);
    }
  }
}
