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

import java.util.Map;
import java.util.UUID;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * 文件级说明：基于 AWS STS AssumeRole 的客户端工厂实现。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上，为引擎层提供 针对 AWS S3/Glue/KMS/DynamoDB
 * 等服务的具体客户端实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link AwsClientFactory}，构造各 AWS 服务客户端（S3、Glue、KMS、DynamoDB）。
 *   <li>所有客户端统一通过 STS AssumeRole 获取临时凭证后再访问目标 AWS 服务， 而非直接使用调用方的长期凭证。
 *   <li>通过 {@link HttpClientProperties}、{@link S3FileIOProperties} 等 properties 对象， 将 HTTP/S3
 *       端点、签名、服务配置注入到客户端 builder。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>跨账号/跨角色访问：常见于多账号数据湖场景，引擎进程所在 IAM 主体无权直接访问 数据所在账号的资源，需要先扮演目标账号内的角色。
 *   <li>会话隔离：每次工厂初始化生成唯一 roleSessionName（UUID），便于 CloudTrail 审计与追踪。
 *   <li>构造时即注入 AssumeRole 凭证提供者，由 SDK 自行刷新，避免业务代码感知过期。
 * </ul>
 *
 * <p>上下游关系：由 {@link S3FileIOAwsClientFactories} 反射加载（配置 {@code client.factory}=本类名）；依赖
 * AwsProperties 解析角色 ARN、外部 ID、超时等参数， 产出的客户端被 S3FileIO、GlueCatalog、DynamoDbLockManager 等使用。
 */
public class AssumeRoleAwsClientFactory implements AwsClientFactory {
  private AwsProperties awsProperties;
  private HttpClientProperties httpClientProperties;
  private S3FileIOProperties s3FileIOProperties;
  private String roleSessionName;

  /**
   * 构造并返回一个使用 AssumeRole 临时凭证的 {@link S3Client}。
   *
   * <p>构建链路：先注入 AssumeRole 配置（凭证+区域），再叠加 HTTP 客户端、S3 端点、 S3 服务参数（如加速、路径风格）、签名器配置，最后由 SDK build()
   * 出实例。
   *
   * @return 已配置 AssumeRole 凭证的 S3 客户端
   */
  @Override
  public S3Client s3() {
    return S3Client.builder()
        .applyMutation(this::applyAssumeRoleConfigurations)
        .applyMutation(httpClientProperties::applyHttpClientConfigurations)
        .applyMutation(s3FileIOProperties::applyEndpointConfigurations)
        .applyMutation(s3FileIOProperties::applyServiceConfigurations)
        .applyMutation(s3FileIOProperties::applySignerConfiguration)
        .build();
  }

  /**
   * 构造并返回一个使用 AssumeRole 临时凭证的 {@link GlueClient}，用于 Glue 数据目录操作。
   *
   * @return 已配置 AssumeRole 凭证的 Glue 客户端
   */
  @Override
  public GlueClient glue() {
    return GlueClient.builder()
        .applyMutation(this::applyAssumeRoleConfigurations)
        .applyMutation(httpClientProperties::applyHttpClientConfigurations)
        .build();
  }

  /**
   * 构造并返回一个使用 AssumeRole 临时凭证的 {@link KmsClient}，用于 SSE-KMS 元数据加密。
   *
   * @return 已配置 AssumeRole 凭证的 KMS 客户端
   */
  @Override
  public KmsClient kms() {
    return KmsClient.builder()
        .applyMutation(this::applyAssumeRoleConfigurations)
        .applyMutation(httpClientProperties::applyHttpClientConfigurations)
        .build();
  }

  /**
   * 构造并返回一个使用 AssumeRole 临时凭证的 {@link DynamoDbClient}，用于 DynamoDB 锁管理。
   *
   * @return 已配置 AssumeRole 凭证的 DynamoDB 客户端
   */
  @Override
  public DynamoDbClient dynamo() {
    return DynamoDbClient.builder()
        .applyMutation(this::applyAssumeRoleConfigurations)
        .applyMutation(httpClientProperties::applyHttpClientConfigurations)
        .applyMutation(awsProperties::applyDynamoDbEndpointConfigurations)
        .build();
  }

  /**
   * 从 catalog/FileIO 配置 properties 初始化工厂：解析 AwsProperties、S3FileIOProperties、
   * HttpClientProperties，并生成 roleSessionName。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>构造三个 properties 持有者，分别承载 AWS 通用、S3 专属、HTTP 客户端配置。
   *   <li>生成会话名（用户配置优先，否则随机 UUID）。
   *   <li>校验角色 ARN 与区域不可为空，否则后续 AssumeRole 无法发起。
   * </ol>
   *
   * @param properties catalog/FileIO 全部配置键值
   */
  @Override
  public void initialize(Map<String, String> properties) {
    this.awsProperties = new AwsProperties(properties);
    this.s3FileIOProperties = new S3FileIOProperties(properties);
    this.httpClientProperties = new HttpClientProperties(properties);
    this.roleSessionName = genSessionName();
    Preconditions.checkNotNull(
        awsProperties.clientAssumeRoleArn(),
        "Cannot initialize AssumeRoleClientConfigFactory with null role ARN");
    Preconditions.checkNotNull(
        awsProperties.clientAssumeRoleRegion(),
        "Cannot initialize AssumeRoleClientConfigFactory with null region");
  }

  /**
   * 给定一个 AWS 客户端 builder，向其注入 AssumeRole 临时凭证与目标区域。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>基于 AwsProperties 构造 {@link AssumeRoleRequest}（角色 ARN、会话名、有效期、外部 ID、标签）。
   *   <li>构造 {@link StsAssumeRoleCredentialsProvider}，绑定一个独立的 STS 客户端用于刷新凭证。
   *   <li>设置 builder 的凭证提供者与区域，返回 builder 自身以支持链式调用。
   * </ol>
   *
   * @param clientBuilder 待配置的 AWS 同步客户端 builder
   * @param <T> builder 类型，需同时是 AwsClientBuilder 与 AwsSyncClientBuilder
   * @return 已注入凭证与区域的同一 builder
   */
  protected <T extends AwsClientBuilder & AwsSyncClientBuilder> T applyAssumeRoleConfigurations(
      T clientBuilder) {
    AssumeRoleRequest assumeRoleRequest =
        AssumeRoleRequest.builder()
            .roleArn(awsProperties.clientAssumeRoleArn())
            .roleSessionName(roleSessionName)
            .durationSeconds(awsProperties.clientAssumeRoleTimeoutSec())
            .externalId(awsProperties.clientAssumeRoleExternalId())
            .tags(awsProperties.stsClientAssumeRoleTags())
            .build();
    clientBuilder
        .credentialsProvider(
            StsAssumeRoleCredentialsProvider.builder()
                .stsClient(sts())
                .refreshRequest(assumeRoleRequest)
                .build())
        .region(Region.of(awsProperties.clientAssumeRoleRegion()));
    return clientBuilder;
  }

  /** 返回当前 AssumeRole 目标区域字符串。 */
  protected String region() {
    return awsProperties.clientAssumeRoleRegion();
  }

  /** 返回当前工厂持有的 AwsProperties，子类可复用其配置。 */
  protected AwsProperties awsProperties() {
    return awsProperties;
  }

  /** 返回当前工厂持有的 HttpClientProperties，子类可复用其配置。 */
  protected HttpClientProperties httpClientProperties() {
    return httpClientProperties;
  }

  /** 返回当前工厂持有的 S3FileIOProperties，子类可复用其配置。 */
  protected S3FileIOProperties s3FileIOProperties() {
    return s3FileIOProperties;
  }

  /**
   * 构造一个 STS 客户端，用于在 {@link #applyAssumeRoleConfigurations} 中刷新临时凭证。 STS 客户端本身不使用
   * AssumeRole，而是直接使用环境/默认凭证链。
   */
  private StsClient sts() {
    return StsClient.builder()
        .applyMutation(httpClientProperties::applyHttpClientConfigurations)
        .build();
  }

  /** 生成 AssumeRole 会话名：优先使用用户配置的 session name，否则生成 "iceberg-aws-&lt;UUID&gt;" 形式的随机名，便于审计追踪。 */
  private String genSessionName() {
    if (awsProperties.clientAssumeRoleSessionName() != null) {
      return awsProperties.clientAssumeRoleSessionName();
    }
    return String.format("iceberg-aws-%s", UUID.randomUUID());
  }
}
