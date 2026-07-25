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
package org.apache.iceberg.aws.lakeformation;

import java.util.Map;
import org.apache.iceberg.aws.AssumeRoleAwsClientFactory;
import org.apache.iceberg.aws.AwsProperties;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.PartitionMetadata;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.GetTableResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.lakeformation.LakeFormationClient;
import software.amazon.awssdk.services.lakeformation.model.GetTemporaryGlueTableCredentialsRequest;
import software.amazon.awssdk.services.lakeformation.model.GetTemporaryGlueTableCredentialsResponse;
import software.amazon.awssdk.services.lakeformation.model.PermissionType;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 模块：aws-lakeformation，AWS 客户端工厂实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>当 {@link org.apache.iceberg.aws.AwsProperties#GLUE_LAKEFORMATION_ENABLED} 为 true 时作为默认
 *       AwsClientFactory
 *   <li>使用默认凭证链 assume role；若表已注册到 LakeFormation，则为 S3/KMS 客户端使用 LakeFormation 下发的临时凭证
 *   <li>第三方引擎可继承本类以实现自定义凭证配置
 * </ul>
 *
 * <p>设计意图：继承 {@link AssumeRoleAwsClientFactory} 复用 assume-role 方式创建除 S3/KMS 外的所有客户端； 表未注册
 * LakeFormation 时回退到 AssumeRole 凭证，保证兼容性。
 *
 * <p>上下游关系：上游由 Iceberg catalog 配置加载；下游产出 S3/KMS/Glue 等客户端供 {@code S3FileIO}、 {@code GlueCatalog}
 * 使用。第三方查询引擎接入参考： https://docs.aws.amazon.com/lake-formation/latest/dg/register-query-engine.html
 */
public class LakeFormationAwsClientFactory extends AssumeRoleAwsClientFactory {

  public static final String LF_AUTHORIZED_CALLER = "LakeFormationAuthorizedCaller";

  private String dbName;
  private String tableName;
  private String glueCatalogId;
  private String glueAccountId;

  /** 默认构造方法。 */
  public LakeFormationAwsClientFactory() {}

  /**
   * 初始化工厂：校验 STS assume role 会话标签必须包含 {@link #LF_AUTHORIZED_CALLER}， 并读取 LakeFormation 数据库名、表名、Glue
   * catalog id 与账号 id 等配置。
   */
  @Override
  public void initialize(Map<String, String> catalogProperties) {
    super.initialize(catalogProperties);
    Preconditions.checkArgument(
        awsProperties().stsClientAssumeRoleTags().stream()
            .anyMatch(t -> LF_AUTHORIZED_CALLER.equals(t.key())),
        "STS assume role session tag %s must be set using %s to use LakeFormation client factory",
        LF_AUTHORIZED_CALLER,
        AwsProperties.CLIENT_ASSUME_ROLE_TAGS_PREFIX);
    this.dbName = catalogProperties.get(AwsProperties.LAKE_FORMATION_DB_NAME);
    this.tableName = catalogProperties.get(AwsProperties.LAKE_FORMATION_TABLE_NAME);
    this.glueCatalogId = catalogProperties.get(AwsProperties.GLUE_CATALOG_ID);
    this.glueAccountId = catalogProperties.get(AwsProperties.GLUE_ACCOUNT_ID);
  }

  /** 创建 S3 客户端：若表已注册 LakeFormation 则使用 LakeFormation 下发凭证，否则回退到父类 assume-role 凭证。 */
  @Override
  public S3Client s3() {
    if (isTableRegisteredWithLakeFormation()) {
      return S3Client.builder()
          .applyMutation(httpClientProperties()::applyHttpClientConfigurations)
          .applyMutation(s3FileIOProperties()::applyEndpointConfigurations)
          .applyMutation(s3FileIOProperties()::applyServiceConfigurations)
          .credentialsProvider(
              new LakeFormationCredentialsProvider(lakeFormation(), buildTableArn()))
          .region(Region.of(region()))
          .build();
    } else {
      return super.s3();
    }
  }

  /** 创建 KMS 客户端：若表已注册 LakeFormation 则使用 LakeFormation 下发凭证，否则回退到父类 assume-role 凭证。 */
  @Override
  public KmsClient kms() {
    if (isTableRegisteredWithLakeFormation()) {
      return KmsClient.builder()
          .applyMutation(httpClientProperties()::applyHttpClientConfigurations)
          .credentialsProvider(
              new LakeFormationCredentialsProvider(lakeFormation(), buildTableArn()))
          .region(Region.of(region()))
          .build();
    } else {
      return super.kms();
    }
  }

  /**
   * 通过 Glue 查询表是否已注册到 LakeFormation。
   *
   * <p>逻辑：校验数据库名与表名非空，调用 Glue GetTable 接口并返回注册状态。
   */
  private boolean isTableRegisteredWithLakeFormation() {
    Preconditions.checkArgument(
        dbName != null && !dbName.isEmpty(), "Database name can not be empty");
    Preconditions.checkArgument(
        tableName != null && !tableName.isEmpty(), "Table name can not be empty");

    GetTableResponse response =
        glue()
            .getTable(
                GetTableRequest.builder()
                    .catalogId(glueCatalogId)
                    .databaseName(dbName)
                    .name(tableName)
                    .build());
    return response.table().isRegisteredWithLakeFormation();
  }

  /** 构建 Glue 表的 ARN，格式为 arn:{partition}:glue:{region}:{accountId}:table/{db}/{table}。 */
  private String buildTableArn() {
    Preconditions.checkArgument(
        glueAccountId != null && !glueAccountId.isEmpty(),
        "%s can not be empty",
        AwsProperties.GLUE_ACCOUNT_ID);
    String partitionName = PartitionMetadata.of(Region.of(region())).id();
    return String.format(
        "arn:%s:glue:%s:%s:table/%s/%s", partitionName, region(), glueAccountId, dbName, tableName);
  }

  /** 创建 LakeFormation 客户端，应用 assume-role 与 HTTP 客户端配置。 */
  private LakeFormationClient lakeFormation() {
    return LakeFormationClient.builder()
        .applyMutation(this::applyAssumeRoleConfigurations)
        .applyMutation(httpClientProperties()::applyHttpClientConfigurations)
        .build();
  }

  /** LakeFormation 临时表凭证提供者，按需向 LakeFormation 请求表的临时凭证。 */
  static class LakeFormationCredentialsProvider implements AwsCredentialsProvider {
    private LakeFormationClient client;
    private String tableArn;

    LakeFormationCredentialsProvider(LakeFormationClient lakeFormationClient, String tableArn) {
      this.client = lakeFormationClient;
      this.tableArn = tableArn;
    }

    /**
     * 解析并返回 LakeFormation 临时表凭证。
     *
     * <p>逻辑：构建 GetTemporaryGlueTableCredentialsRequest（仅支持 COLUMN_PERMISSION）， 调用 LakeFormation
     * 获取临时凭证，包装为 AwsSessionCredentials 返回。
     *
     * @return AWS 会话凭证
     */
    @Override
    public AwsCredentials resolveCredentials() {
      GetTemporaryGlueTableCredentialsRequest getTemporaryGlueTableCredentialsRequest =
          GetTemporaryGlueTableCredentialsRequest.builder()
              .tableArn(tableArn)
              // Now only two permission types (COLUMN_PERMISSION and CELL_FILTER_PERMISSION) are
              // supported
              // and Iceberg only supports COLUMN_PERMISSION at this time
              .supportedPermissionTypes(PermissionType.COLUMN_PERMISSION)
              .build();
      GetTemporaryGlueTableCredentialsResponse response =
          client.getTemporaryGlueTableCredentials(getTemporaryGlueTableCredentialsRequest);
      return AwsSessionCredentials.create(
          response.accessKeyId(), response.secretAccessKey(), response.sessionToken());
    }
  }
}
