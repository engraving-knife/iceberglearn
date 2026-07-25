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
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.aws.dynamodb.DynamoDbCatalog;
import org.apache.iceberg.aws.lakeformation.LakeFormationAwsClientFactory;
import org.apache.iceberg.common.DynClasses;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SerializableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.glue.GlueClientBuilder;

/**
 * 文件级说明：AWS 集成的核心配置属性持有者。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中定义并解析 Glue、DynamoDB、AssumeRole、REST SigV4 等场景的全部配置键与默认值。
 *   <li>持有运行期所需的可变状态（区域、端点、凭证提供者类名、是否跳过归档等）， 并提供 setter 以便 catalog 在运行中覆盖。
 *   <li>提供将端点、凭证、Glue/DynamoDB 端点注入到 AWS 客户端 builder 的辅助方法。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>历史演进：本类原本承载 HTTP、区域、凭证等所有 AWS 相关配置；后续为避免膨胀， HTTP/区域/凭证相关配置已迁移至 {@link HttpClientProperties}
 *       与 {@link AwsClientProperties}， 旧 API 保留为 @Deprecated 以保持向后兼容。
 *   <li>可序列化：实现 Serializable，便于在 Spark/Flink 等分布式引擎中分发。
 *   <li>解析即校验：构造时从 properties 抽取并缓存所有相关字段，运行期 getter 直接返回缓存值。
 * </ul>
 *
 * <p>上下游关系：由 {@link AwsClientFactory} 实现（如 {@link AssumeRoleAwsClientFactory}、 {@link
 * LakeFormationAwsClientFactory}）持有；被 {@code GlueCatalog}、 {@link DynamoDbCatalog}、{@link
 * RESTSigV4Signer} 等组件读取具体配置。
 */
public class AwsProperties implements Serializable {

  private static final Logger LOG = LoggerFactory.getLogger(AwsProperties.class);

  /**
   * Glue 数据目录的 Catalog ID（即 AWS 账号 ID）。未提供时 Glue 默认使用调用方所在账号。
   *
   * <p>详见 https://docs.aws.amazon.com/glue/latest/dg/aws-glue-api-catalog-databases.html
   */
  public static final String GLUE_CATALOG_ID = "glue.id";

  /** Glue 资源 ARN 中使用的账号 ID，例如 arn:aws:glue:us-east-1:1000000000000:table/db1/table1。 */
  public static final String GLUE_ACCOUNT_ID = "glue.account-id";

  /**
   * 是否在 Glue 创建新表版本时跳过旧版本的归档。默认 Glue 会在 UpdateTable 后归档所有旧版本， 但归档版本数量有上限（可申请提升）。对于频繁提交的流式场景，建议设为
   * true 以避免触及上限。
   */
  public static final String GLUE_CATALOG_SKIP_ARCHIVE = "glue.skip-archive";

  /** {@link #GLUE_CATALOG_SKIP_ARCHIVE} 的默认值：true。 */
  public static final boolean GLUE_CATALOG_SKIP_ARCHIVE_DEFAULT = true;

  /**
   * 是否跳过 Glue 对库名/表名的合法性校验。建议遵循 Glue 最佳实践
   * （https://docs.aws.amazon.com/athena/latest/ug/glue-best-practices.html）以保证 Hive 兼容性。
   * 仅为已有非标准命名约定的用户提供；跳过后无法保证下游系统均能识别这些名称。
   */
  public static final String GLUE_CATALOG_SKIP_NAME_VALIDATION = "glue.skip-name-validation";

  /** {@link #GLUE_CATALOG_SKIP_NAME_VALIDATION} 的默认值：false。 */
  public static final boolean GLUE_CATALOG_SKIP_NAME_VALIDATION_DEFAULT = false;

  /**
   * 是否让 GlueCatalog 通过 Lake Formation 进行访问控制与凭证下发。 详见
   * https://docs.aws.amazon.com/lake-formation/latest/dg/api-overview.html。 启用时，{@link
   * AwsClientFactory} 实现必须是 {@link LakeFormationAwsClientFactory} 或其子类。
   */
  public static final String GLUE_LAKEFORMATION_ENABLED = "glue.lakeformation-enabled";

  /** {@link #GLUE_LAKEFORMATION_ENABLED} 的默认值：false。 */
  public static final boolean GLUE_LAKEFORMATION_ENABLED_DEFAULT = false;

  /** 配置 GlueCatalog 访问 Glue 服务时使用的自定义端点。 可用于对接任意 Glue 兼容的元数据服务。 */
  public static final String GLUE_CATALOG_ENDPOINT = "glue.endpoint";

  /** 配置访问 DynamoDB 服务时使用的自定义端点。 */
  public static final String DYNAMODB_ENDPOINT = "dynamodb.endpoint";

  /** {@link DynamoDbCatalog} 使用的 DynamoDB 表名。 */
  public static final String DYNAMODB_TABLE_NAME = "dynamodb.table-name";

  /** {@link #DYNAMODB_TABLE_NAME} 的默认值：iceberg。 */
  public static final String DYNAMODB_TABLE_NAME_DEFAULT = "iceberg";

  /**
   * {@link AwsClientFactory} 实现类的全限定类名，用于自定义 AWS 客户端构造。 未设置时使用 {@link
   * AwsClientFactories#defaultFactory()} 作为默认工厂。
   */
  public static final String CLIENT_FACTORY = "client.factory";

  /** 供 {@link AssumeRoleAwsClientFactory} 使用：设置要扮演的 IAM 角色 ARN， 所有 AWS 客户端将使用该角色的临时凭证而非默认凭证链。 */
  public static final String CLIENT_ASSUME_ROLE_ARN = "client.assume-role.arn";

  /**
   * 供 {@link AssumeRoleAwsClientFactory} 使用：传递给 STS 会话的标签列表， 每个标签以“键名=值”形式给出，前缀为
   * client.assume-role.tags.。
   */
  public static final String CLIENT_ASSUME_ROLE_TAGS_PREFIX = "client.assume-role.tags.";

  /**
   * 供 {@link AssumeRoleAwsClientFactory} 使用：AssumeRole 会话超时（秒），默认 1 小时。 超时后会通过 STS 客户端重新获取新的会话凭证。
   */
  public static final String CLIENT_ASSUME_ROLE_TIMEOUT_SEC = "client.assume-role.timeout-sec";

  /** {@link #CLIENT_ASSUME_ROLE_TIMEOUT_SEC} 的默认值：3600 秒（1 小时）。 */
  public static final int CLIENT_ASSUME_ROLE_TIMEOUT_SEC_DEFAULT = 3600;

  /**
   * 供 {@link AssumeRoleAwsClientFactory} 使用的可选外部 ID，用于扮演 IAM 角色。
   *
   * <p>详见 https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-user_externalid.html
   */
  public static final String CLIENT_ASSUME_ROLE_EXTERNAL_ID = "client.assume-role.external-id";

  /**
   * 供 {@link AssumeRoleAwsClientFactory} 使用：除 STS 外所有客户端使用的目标区域， 替代默认区域链。取值必须是 {@link
   * software.amazon.awssdk.regions.Region} 之一，如 us-east-1。
   *
   * <p>详见 https://docs.aws.amazon.com/general/latest/gr/rande.html
   */
  public static final String CLIENT_ASSUME_ROLE_REGION = "client.assume-role.region";

  /**
   * 供 {@link AssumeRoleAwsClientFactory} 使用的可选会话名，用于扮演 IAM 角色。
   *
   * <p>详见
   * https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_iam-condition-keys.html#ck_rolesessionname
   */
  public static final String CLIENT_ASSUME_ROLE_SESSION_NAME = "client.assume-role.session-name";

  /** 供 {@link LakeFormationAwsClientFactory} 使用：Lake Formation 凭证请求中使用的表名。 */
  public static final String LAKE_FORMATION_TABLE_NAME = "lakeformation.table-name";

  /** 供 {@link LakeFormationAwsClientFactory} 使用：Lake Formation 凭证请求中使用的库名。 */
  public static final String LAKE_FORMATION_DB_NAME = "lakeformation.db-name";

  /** SigV4 签名请求时使用的区域。 */
  public static final String REST_SIGNER_REGION = "rest.signing-region";

  /** SigV4 签名请求时使用的服务名。 */
  public static final String REST_SIGNING_NAME = "rest.signing-name";

  /** SigV4 签名默认服务名（API Gateway 与 Lambda）：execute-api。 */
  public static final String REST_SIGNING_NAME_DEFAULT = "execute-api";

  /**
   * 配置 SigV4 签名使用的静态 access key ID。
   *
   * <p>设置后默认工厂将使用提供的 basic 或 session 凭证，而非默认凭证链。 若同时设置了 {@link #REST_SESSION_TOKEN}，则使用 session
   * 凭证，否则使用 basic 凭证。
   */
  public static final String REST_ACCESS_KEY_ID = "rest.access-key-id";

  /**
   * 配置 SigV4 签名使用的静态 secret access key。
   *
   * <p>设置后默认工厂将使用提供的 basic 或 session 凭证，而非默认凭证链。 若同时设置了 {@link #REST_SESSION_TOKEN}，则使用 session
   * 凭证，否则使用 basic 凭证。
   */
  public static final String REST_SECRET_ACCESS_KEY = "rest.secret-access-key";

  /**
   * 配置 SigV4 签名使用的静态 session token。
   *
   * <p>设置后默认工厂将使用提供的 session 凭证，而非默认凭证链。
   */
  public static final String REST_SESSION_TOKEN = "rest.session-token";

  private static final String HTTP_CLIENT_PREFIX = "http-client.";
  private final Map<String, String> httpClientProperties;
  private final Set<software.amazon.awssdk.services.sts.model.Tag> stsClientAssumeRoleTags;

  private String clientAssumeRoleArn;
  private String clientAssumeRoleExternalId;
  private int clientAssumeRoleTimeoutSec;
  private String clientAssumeRoleRegion;
  private String clientAssumeRoleSessionName;
  private String clientRegion;
  private String clientCredentialsProvider;
  private final Map<String, String> clientCredentialsProviderProperties;

  private String glueEndpoint;
  private String glueCatalogId;
  private boolean glueCatalogSkipArchive;
  private boolean glueCatalogSkipNameValidation;
  private boolean glueLakeFormationEnabled;

  private String dynamoDbTableName;
  private String dynamoDbEndpoint;
  private final Map<String, String> allProperties;

  private String restSigningRegion;
  private String restSigningName;
  private String restAccessKeyId;
  private String restSecretAccessKey;
  private String restSessionToken;

  /** 构造默认实例：所有字段使用默认值，无任何用户配置。 */
  public AwsProperties() {
    this.httpClientProperties = Collections.emptyMap();
    this.stsClientAssumeRoleTags = Sets.newHashSet();

    this.clientAssumeRoleArn = null;
    this.clientAssumeRoleTimeoutSec = CLIENT_ASSUME_ROLE_TIMEOUT_SEC_DEFAULT;
    this.clientAssumeRoleExternalId = null;
    this.clientAssumeRoleRegion = null;
    this.clientAssumeRoleSessionName = null;
    this.clientRegion = null;
    this.clientCredentialsProvider = null;
    this.clientCredentialsProviderProperties = null;

    this.glueCatalogId = null;
    this.glueEndpoint = null;
    this.glueCatalogSkipArchive = GLUE_CATALOG_SKIP_ARCHIVE_DEFAULT;
    this.glueCatalogSkipNameValidation = GLUE_CATALOG_SKIP_NAME_VALIDATION_DEFAULT;
    this.glueLakeFormationEnabled = GLUE_LAKEFORMATION_ENABLED_DEFAULT;

    this.dynamoDbEndpoint = null;
    this.dynamoDbTableName = DYNAMODB_TABLE_NAME_DEFAULT;

    this.allProperties = Maps.newHashMap();

    this.restSigningName = REST_SIGNING_NAME_DEFAULT;
  }

  /**
   * 从 catalog/FileIO properties 解析并缓存全部 AWS 相关配置字段。
   *
   * <p>逻辑：分别抽取 HTTP 客户端配置子集、AssumeRole 标签、客户端区域、凭证提供者类名、 Glue/DynamoDB 端点与表名、REST SigV4
   * 凭证等，按默认值兜底。allProperties 持有原始 properties 的可序列化副本，供需要全量配置的场景使用。
   *
   * @param properties catalog/FileIO 全部配置键值
   */
  @SuppressWarnings("MethodLength")
  public AwsProperties(Map<String, String> properties) {
    this.httpClientProperties =
        PropertyUtil.filterProperties(properties, key -> key.startsWith(HTTP_CLIENT_PREFIX));
    this.stsClientAssumeRoleTags = toStsTags(properties, CLIENT_ASSUME_ROLE_TAGS_PREFIX);
    this.clientAssumeRoleArn = properties.get(CLIENT_ASSUME_ROLE_ARN);
    this.clientAssumeRoleTimeoutSec =
        PropertyUtil.propertyAsInt(
            properties, CLIENT_ASSUME_ROLE_TIMEOUT_SEC, CLIENT_ASSUME_ROLE_TIMEOUT_SEC_DEFAULT);
    this.clientAssumeRoleExternalId = properties.get(CLIENT_ASSUME_ROLE_EXTERNAL_ID);
    this.clientAssumeRoleRegion = properties.get(CLIENT_ASSUME_ROLE_REGION);
    this.clientAssumeRoleSessionName = properties.get(CLIENT_ASSUME_ROLE_SESSION_NAME);
    this.clientRegion = properties.get(AwsClientProperties.CLIENT_REGION);
    this.clientCredentialsProvider =
        properties.get(AwsClientProperties.CLIENT_CREDENTIALS_PROVIDER);
    this.clientCredentialsProviderProperties =
        PropertyUtil.propertiesWithPrefix(
            properties, AwsClientProperties.CLIENT_CREDENTIAL_PROVIDER_PREFIX);

    this.glueEndpoint = properties.get(GLUE_CATALOG_ENDPOINT);
    this.glueCatalogId = properties.get(GLUE_CATALOG_ID);
    this.glueCatalogSkipArchive =
        PropertyUtil.propertyAsBoolean(
            properties, GLUE_CATALOG_SKIP_ARCHIVE, GLUE_CATALOG_SKIP_ARCHIVE_DEFAULT);
    this.glueCatalogSkipNameValidation =
        PropertyUtil.propertyAsBoolean(
            properties,
            GLUE_CATALOG_SKIP_NAME_VALIDATION,
            GLUE_CATALOG_SKIP_NAME_VALIDATION_DEFAULT);
    this.glueLakeFormationEnabled =
        PropertyUtil.propertyAsBoolean(
            properties, GLUE_LAKEFORMATION_ENABLED, GLUE_LAKEFORMATION_ENABLED_DEFAULT);

    this.dynamoDbEndpoint = properties.get(DYNAMODB_ENDPOINT);
    this.dynamoDbTableName =
        PropertyUtil.propertyAsString(properties, DYNAMODB_TABLE_NAME, DYNAMODB_TABLE_NAME_DEFAULT);

    this.allProperties = SerializableMap.copyOf(properties);

    this.restSigningRegion = properties.get(REST_SIGNER_REGION);
    this.restSigningName = properties.getOrDefault(REST_SIGNING_NAME, REST_SIGNING_NAME_DEFAULT);
    this.restAccessKeyId = properties.get(REST_ACCESS_KEY_ID);
    this.restSecretAccessKey = properties.get(REST_SECRET_ACCESS_KEY);
    this.restSessionToken = properties.get(REST_SESSION_TOKEN);
  }

  /** 返回 AssumeRole 会话标签集合。 */
  public Set<software.amazon.awssdk.services.sts.model.Tag> stsClientAssumeRoleTags() {
    return stsClientAssumeRoleTags;
  }

  /** 返回 AssumeRole 角色 ARN。 */
  public String clientAssumeRoleArn() {
    return clientAssumeRoleArn;
  }

  /** 返回 AssumeRole 会话超时（秒）。 */
  public int clientAssumeRoleTimeoutSec() {
    return clientAssumeRoleTimeoutSec;
  }

  /** 返回 AssumeRole 外部 ID。 */
  public String clientAssumeRoleExternalId() {
    return clientAssumeRoleExternalId;
  }

  /** 返回 AssumeRole 目标区域。 */
  public String clientAssumeRoleRegion() {
    return clientAssumeRoleRegion;
  }

  /** 返回 AssumeRole 会话名。 */
  public String clientAssumeRoleSessionName() {
    return clientAssumeRoleSessionName;
  }

  /** 返回 Glue Catalog ID（账号 ID）。 */
  public String glueCatalogId() {
    return glueCatalogId;
  }

  /** 设置 Glue Catalog ID。 */
  public void setGlueCatalogId(String id) {
    this.glueCatalogId = id;
  }

  /** 返回是否跳过 Glue 旧版本归档。 */
  public boolean glueCatalogSkipArchive() {
    return glueCatalogSkipArchive;
  }

  /** 设置是否跳过 Glue 旧版本归档。 */
  public void setGlueCatalogSkipArchive(boolean skipArchive) {
    this.glueCatalogSkipArchive = skipArchive;
  }

  /** 返回是否跳过 Glue 库名/表名校验。 */
  public boolean glueCatalogSkipNameValidation() {
    return glueCatalogSkipNameValidation;
  }

  /** 设置是否跳过 Glue 库名/表名校验。 */
  public void setGlueCatalogSkipNameValidation(boolean glueCatalogSkipNameValidation) {
    this.glueCatalogSkipNameValidation = glueCatalogSkipNameValidation;
  }

  /** 返回是否启用 Lake Formation。 */
  public boolean glueLakeFormationEnabled() {
    return glueLakeFormationEnabled;
  }

  /** 设置是否启用 Lake Formation。 */
  public void setGlueLakeFormationEnabled(boolean glueLakeFormationEnabled) {
    this.glueLakeFormationEnabled = glueLakeFormationEnabled;
  }

  /** 返回 DynamoDB 表名。 */
  public String dynamoDbTableName() {
    return dynamoDbTableName;
  }

  /** 设置 DynamoDB 表名。 */
  public void setDynamoDbTableName(String name) {
    this.dynamoDbTableName = name;
  }

  /** @deprecated 将在 1.5.0 移除，请改用 {@link HttpClientProperties} */
  @Deprecated
  public Map<String, String> httpClientProperties() {
    return httpClientProperties;
  }

  /** @deprecated 将在 1.5.0 移除，请改用 {@link AwsClientProperties#clientRegion()} */
  @Deprecated
  public String clientRegion() {
    return clientRegion;
  }

  /** @deprecated 将在 1.5.0 移除，请改用 {@link AwsClientProperties#setClientRegion(String)} */
  @Deprecated
  public void setClientRegion(String clientRegion) {
    this.clientRegion = clientRegion;
  }

  /**
   * 为客户端 builder 注入自定义凭证提供者（若配置）。
   *
   * <p>典型用法：
   *
   * <pre>
   *     GlueClient.builder().applyMutation(awsProperties::applyS3EndpointConfigurations)
   * </pre>
   *
   * @param builder 待配置的 AWS 客户端 builder
   * @param <T> builder 类型
   * @deprecated 将在 1.5.0 移除，请改用 {@link
   *     AwsClientProperties#applyClientCredentialConfigurations(AwsClientBuilder)}
   */
  @Deprecated
  public <T extends AwsClientBuilder> void applyClientCredentialConfigurations(T builder) {
    if (!Strings.isNullOrEmpty(this.clientCredentialsProvider)) {
      builder.credentialsProvider(credentialsProvider(this.clientCredentialsProvider));
    }
  }

  /**
   * 为 Glue 客户端 builder 注入自定义端点。
   *
   * <p>典型用法：
   *
   * <pre>
   *     GlueClient.builder().applyMutation(awsProperties::applyS3EndpointConfigurations)
   * </pre>
   *
   * @param builder Glue 客户端 builder
   * @param <T> builder 类型
   */
  public <T extends GlueClientBuilder> void applyGlueEndpointConfigurations(T builder) {
    configureEndpoint(builder, glueEndpoint);
  }

  /**
   * 为 DynamoDB 客户端 builder 注入自定义端点。
   *
   * <p>典型用法：
   *
   * <pre>
   *     DynamoDbClient.builder().applyMutation(awsProperties::applyDynamoDbEndpointConfigurations)
   * </pre>
   *
   * @param builder DynamoDB 客户端 builder
   * @param <T> builder 类型
   */
  public <T extends DynamoDbClientBuilder> void applyDynamoDbEndpointConfigurations(T builder) {
    configureEndpoint(builder, dynamoDbEndpoint);
  }

  /**
   * 返回 REST SigV4 签名使用的区域。未配置时通过默认区域链解析并缓存。
   *
   * @return 签名区域
   */
  public Region restSigningRegion() {
    if (restSigningRegion == null) {
      this.restSigningRegion = DefaultAwsRegionProviderChain.builder().build().getRegion().id();
    }

    return Region.of(restSigningRegion);
  }

  /** 返回 REST SigV4 签名使用的服务名。 */
  public String restSigningName() {
    return restSigningName;
  }

  /**
   * 构造 REST SigV4 签名使用的凭证提供者。
   *
   * <p>逻辑：若配置了静态 access key，则按是否带 session token 构造 basic 或 session
   * 静态凭证；否则若配置了自定义凭证提供者类，则反射加载；最后回退到默认凭证链。
   *
   * @return 凭证提供者
   */
  public AwsCredentialsProvider restCredentialsProvider() {
    return credentialsProvider(
        this.restAccessKeyId, this.restSecretAccessKey, this.restSessionToken);
  }

  /**
   * 将前缀下的 properties 转换为 STS Tag 集合。
   *
   * @param properties 全部配置
   * @param prefix 标签前缀
   * @return STS Tag 集合
   */
  private Set<software.amazon.awssdk.services.sts.model.Tag> toStsTags(
      Map<String, String> properties, String prefix) {
    return PropertyUtil.propertiesWithPrefix(properties, prefix).entrySet().stream()
        .map(
            e ->
                software.amazon.awssdk.services.sts.model.Tag.builder()
                    .key(e.getKey())
                    .value(e.getValue())
                    .build())
        .collect(Collectors.toSet());
  }

  /**
   * 根据静态 access key/secret/session token 选择并构造凭证提供者。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>access key 非空：有 session token 构造 session 凭证，否则 basic 凭证。
   *   <li>access key 为空但配置了自定义凭证提供者类：反射加载。
   *   <li>否则：返回新的默认凭证链实例。
   * </ul>
   *
   * @param accessKeyId 静态 access key（可空）
   * @param secretAccessKey 静态 secret（可空）
   * @param sessionToken 静态 session token（可空）
   * @return 凭证提供者
   */
  private AwsCredentialsProvider credentialsProvider(
      String accessKeyId, String secretAccessKey, String sessionToken) {
    if (accessKeyId != null) {
      if (sessionToken == null) {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey));
      } else {
        return StaticCredentialsProvider.create(
            AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken));
      }
    }

    if (!Strings.isNullOrEmpty(this.clientCredentialsProvider)) {
      return credentialsProvider(this.clientCredentialsProvider);
    }

    // Create a new credential provider for each client
    return DefaultCredentialsProvider.builder().build();
  }

  /**
   * 通过反射加载并实例化自定义凭证提供者类。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>反射加载类，校验其实现 {@link AwsCredentialsProvider}。
   *   <li>优先尝试静态 create(Map) 方法并传入凭证提供者配置；若不存在则尝试无参 create()。
   *   <li>两者均无则抛 IllegalArgumentException。
   * </ol>
   *
   * @param credentialsProviderClass 凭证提供者全限定类名
   * @return 凭证提供者实例
   * @throws IllegalArgumentException 当类不存在、不实现接口或缺少 create 方法时
   */
  private AwsCredentialsProvider credentialsProvider(String credentialsProviderClass) {
    Class<?> providerClass;
    try {
      providerClass = DynClasses.builder().impl(credentialsProviderClass).buildChecked();
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot load class %s, it does not exist in the classpath", credentialsProviderClass),
          e);
    }

    Preconditions.checkArgument(
        AwsCredentialsProvider.class.isAssignableFrom(providerClass),
        String.format(
            "Cannot initialize %s, it does not implement %s.",
            credentialsProviderClass, AwsCredentialsProvider.class.getName()));

    AwsCredentialsProvider provider;
    try {
      try {
        provider =
            DynMethods.builder("create")
                .hiddenImpl(providerClass, Map.class)
                .buildStaticChecked()
                .invoke(clientCredentialsProviderProperties);
      } catch (NoSuchMethodException e) {
        provider =
            DynMethods.builder("create").hiddenImpl(providerClass).buildStaticChecked().invoke();
      }

      return provider;
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot create an instance of %s, it does not contain a static 'create' or 'create(Map<String, String>)' method",
              credentialsProviderClass),
          e);
    }
  }

  /**
   * 为客户端 builder 设置端点覆盖。
   *
   * @param builder SDK 客户端 builder
   * @param endpoint 端点 URL（为 null 则不设置）
   * @param <T> builder 类型
   */
  private <T extends SdkClientBuilder> void configureEndpoint(T builder, String endpoint) {
    if (endpoint != null) {
      builder.endpointOverride(URI.create(endpoint));
    }
  }
}
