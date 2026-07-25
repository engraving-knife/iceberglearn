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

import java.net.URI;
import java.util.Map;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.apache.iceberg.util.PropertyUtil;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.GlueClientBuilder;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

public class AwsClientFactories {

  private static final DefaultAwsClientFactory AWS_CLIENT_FACTORY_DEFAULT =
      new DefaultAwsClientFactory();

  private AwsClientFactories() {}

  public static AwsClientFactory defaultFactory() {
    return AWS_CLIENT_FACTORY_DEFAULT;
  }

  public static AwsClientFactory from(Map<String, String> properties) {
    String factoryImpl =
        PropertyUtil.propertyAsString(
            properties, AwsProperties.CLIENT_FACTORY, DefaultAwsClientFactory.class.getName());
    return loadClientFactory(factoryImpl, properties);
  }

  private static AwsClientFactory loadClientFactory(String impl, Map<String, String> properties) {
    DynConstructors.Ctor<AwsClientFactory> ctor;
    try {
      ctor =
          DynConstructors.builder(AwsClientFactory.class)
              .loader(AwsClientFactories.class.getClassLoader())
              .hiddenImpl(impl)
              .buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize AwsClientFactory, missing no-arg constructor: %s", impl),
          e);
    }

    AwsClientFactory factory;
    try {
      factory = ctor.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize AwsClientFactory, %s does not implement AwsClientFactory.", impl),
          e);
    }

    factory.initialize(properties);
    return factory;
  }

  static class DefaultAwsClientFactory implements AwsClientFactory {
    private AwsProperties awsProperties;
    private AwsClientProperties awsClientProperties;
    private S3FileIOProperties s3FileIOProperties;
    private HttpClientProperties httpClientProperties;

    DefaultAwsClientFactory() {
      awsProperties = new AwsProperties();
      awsClientProperties = new AwsClientProperties();
      s3FileIOProperties = new S3FileIOProperties();
      httpClientProperties = new HttpClientProperties();
    }

    @Override
    public S3Client s3() {
      return S3Client.builder()
          .applyMutation(awsClientProperties::applyClientRegionConfiguration)
          .applyMutation(httpClientProperties::applyHttpClientConfigurations)
          .applyMutation(s3FileIOProperties::applyEndpointConfigurations)
          .applyMutation(s3FileIOProperties::applyServiceConfigurations)
          .applyMutation(
              b -> s3FileIOProperties.applyCredentialConfigurations(awsClientProperties, b))
          .applyMutation(s3FileIOProperties::applySignerConfiguration)
          .build();
    }

    @Override
    public GlueClient glue() {
      return GlueClient.builder()
          .applyMutation(awsClientProperties::applyClientRegionConfiguration)
          .applyMutation(httpClientProperties::applyHttpClientConfigurations)
          .applyMutation(awsProperties::applyGlueEndpointConfigurations)
          .applyMutation(awsProperties::applyClientCredentialConfigurations)
          .build();
    }

    @Override
    public KmsClient kms() {
      return KmsClient.builder()
          .applyMutation(awsClientProperties::applyClientRegionConfiguration)
          .applyMutation(httpClientProperties::applyHttpClientConfigurations)
          .applyMutation(awsProperties::applyClientCredentialConfigurations)
          .build();
    }

    @Override
    public DynamoDbClient dynamo() {
      return DynamoDbClient.builder()
          .applyMutation(awsClientProperties::applyClientRegionConfiguration)
          .applyMutation(httpClientProperties::applyHttpClientConfigurations)
          .applyMutation(awsProperties::applyClientCredentialConfigurations)
          .applyMutation(awsProperties::applyDynamoDbEndpointConfigurations)
          .build();
    }

    @Override
    public void initialize(Map<String, String> properties) {
      this.awsProperties = new AwsProperties(properties);
      this.awsClientProperties = new AwsClientProperties(properties);
      this.s3FileIOProperties = new S3FileIOProperties(properties);
      this.httpClientProperties = new HttpClientProperties(properties);
    }
  }

  /**
   * 根据 HTTP 客户端类型创建对应的 httpClientBuilder。
   *
   * <p>逻辑：若 httpClientType 为空则使用默认类型，随后按类型返回 UrlConnectionHttpClient 或 ApacheHttpClient 的 Builder。
   *
   * @param httpClientType HTTP 客户端类型，可选 urlconnection / apache
   * @return 对应的 {@link software.amazon.awssdk.http.SdkHttpClient.Builder}
   * @throws IllegalArgumentException 未知的 HTTP 客户端类型
   * @deprecated 不再供外部使用，请改用 {@link
   *     HttpClientProperties#applyHttpClientConfigurations(AwsSyncClientBuilder)}， 将在 2.0.0 移除
   */
  @Deprecated
  public static SdkHttpClient.Builder configureHttpClientBuilder(String httpClientType) {
    String clientType = httpClientType;
    if (Strings.isNullOrEmpty(clientType)) {
      clientType = HttpClientProperties.CLIENT_TYPE_DEFAULT;
    }
    switch (clientType) {
      case HttpClientProperties.CLIENT_TYPE_URLCONNECTION:
        return UrlConnectionHttpClient.builder();
      case HttpClientProperties.CLIENT_TYPE_APACHE:
        return ApacheHttpClient.builder();
      default:
        throw new IllegalArgumentException("Unrecognized HTTP client type " + httpClientType);
    }
  }

  /**
   * 为客户端配置自定义端点覆盖地址。
   *
   * @param builder 客户端构建器
   * @param endpoint 端点 URL，为 null 时不设置
   * @param <T> 客户端构建器类型
   * @deprecated 不再供外部使用，请改用 {@link
   *     S3FileIOProperties#applyEndpointConfigurations(S3ClientBuilder)}、{@link
   *     AwsProperties#applyGlueEndpointConfigurations(GlueClientBuilder)} 或 {@link
   *     AwsProperties#applyDynamoDbEndpointConfigurations(DynamoDbClientBuilder)}， 将在 2.0.0 移除
   */
  @Deprecated
  public static <T extends SdkClientBuilder> void configureEndpoint(T builder, String endpoint) {
    if (endpoint != null) {
      builder.endpointOverride(URI.create(endpoint));
    }
  }

  /**
   * 构建 S3Configuration 对象，设置 path style 访问与 ARN 区域解析开关。
   *
   * @param pathStyleAccess 是否启用 path style 访问
   * @param s3UseArnRegionEnabled 是否允许使用 ARN 中的区域
   * @return 配置好的 {@link software.amazon.awssdk.services.s3.S3Configuration}
   * @deprecated 不再供外部使用，请直接使用 S3Configuration.builder()，将在 2.0.0 移除
   */
  @Deprecated
  public static S3Configuration s3Configuration(
      Boolean pathStyleAccess, Boolean s3UseArnRegionEnabled) {
    return S3Configuration.builder()
        .pathStyleAccessEnabled(pathStyleAccess)
        .useArnRegionEnabled(s3UseArnRegionEnabled)
        .build();
  }

  /**
   * 根据访问密钥构建静态凭证提供者；密钥为空时返回默认凭证提供者。
   *
   * <p>逻辑：若 accessKeyId 非空，有 sessionToken 则构建会话凭证，否则构建基本凭证； accessKeyId 为空时返回
   * DefaultCredentialsProvider，走 AWS 默认凭证链。
   *
   * @param accessKeyId AWS 访问密钥 ID
   * @param secretAccessKey AWS 秘密访问密钥
   * @param sessionToken AWS 会话令牌（临时凭证时使用）
   * @return 凭证提供者实例
   * @deprecated 不再供外部使用，请改用 {@link
   *     S3FileIOProperties#applyCredentialConfigurations(AwsClientProperties, S3ClientBuilder)}， 将在
   *     2.0.0 移除
   */
  @Deprecated
  static AwsCredentialsProvider credentialsProvider(
      String accessKeyId, String secretAccessKey, String sessionToken) {
    if (accessKeyId != null) {
      if (sessionToken == null) {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey));
      } else {
        return StaticCredentialsProvider.create(
            AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken));
      }
    } else {
      // Create a new credential provider for each client
      return DefaultCredentialsProvider.builder().build();
    }
  }
}
