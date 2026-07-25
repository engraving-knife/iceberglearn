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
package org.apache.iceberg.aws.dynamodb;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.aws.AwsProperties;
import org.apache.iceberg.aws.util.RetryDetector;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * 基于 DynamoDB 的表操作实现：负责单个 Iceberg 表元数据的读取与提交。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成模块，处于引擎层之下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 DynamoDB 目录表读取表的当前元数据位置（metadata_location）并加载。
 *   <li>将新的表元数据位置写入 DynamoDB，通过乐观锁（version 列）保证提交的并发安全。
 *   <li>在提交失败时进行状态推断与回滚清理，处理 CommitStateUnknown 等边缘场景。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseMetastoreTableOperations}，复用其元数据加载、版本管理等通用逻辑， 仅实现 doRefresh/doCommit 两个钩子方法。
 *   <li>提交使用 DynamoDB 条件表达式实现 CAS：已有表用 version = :v 校验，新表用 attribute_not_exists(version)
 *       校验，从而实现乐观并发控制。
 *   <li>使用 RetryDetector 检测 AWS SDK 是否发生过重试，当 ConditionalCheckFailedException 伴随重试时，提交状态可能不确定，需调用
 *       checkCommitStatus 推断实际结果， 避免误判导致元数据文件泄漏或丢失。
 * </ul>
 *
 * <p>上下游关系：由 {@link DynamoDbCatalog#newTableOps} 创建，每个表标识符对应一个实例； 依赖 DynamoDbClient 进行读写，依赖
 * FileIO（通常为 S3FileIO）读写元数据文件。
 */
class DynamoDbTableOperations extends BaseMetastoreTableOperations {

  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbTableOperations.class);

  private final DynamoDbClient dynamo;
  private final AwsProperties awsProperties;
  private final TableIdentifier tableIdentifier;
  private final String fullTableName;
  private final FileIO fileIO;

  /**
   * 构造表操作实例。
   *
   * @param dynamo DynamoDB 客户端
   * @param awsProperties AWS 属性
   * @param catalogName catalog 名称
   * @param fileIO 文件 IO
   * @param tableIdentifier 表标识符
   */
  DynamoDbTableOperations(
      DynamoDbClient dynamo,
      AwsProperties awsProperties,
      String catalogName,
      FileIO fileIO,
      TableIdentifier tableIdentifier) {
    this.dynamo = dynamo;
    this.awsProperties = awsProperties;
    this.fullTableName = String.format("%s.%s", catalogName, tableIdentifier);
    this.tableIdentifier = tableIdentifier;
    this.fileIO = fileIO;
  }

  @Override
  protected String tableName() {
    return fullTableName;
  }

  @Override
  public FileIO io() {
    return fileIO;
  }

  /**
   * 从 DynamoDB 刷新表元数据位置。
   *
   * <p>逻辑：通过主键强一致读取表记录，若存在则提取 metadata_location 并调用 {@link #refreshFromMetadataLocation(String)}
   * 加载；若不存在且本地已有元数据， 则抛 NoSuchTableException（表可能已被其他进程删除）。
   */
  @Override
  protected void doRefresh() {
    String metadataLocation = null;
    GetItemResponse table =
        dynamo.getItem(
            GetItemRequest.builder()
                .tableName(awsProperties.dynamoDbTableName())
                .consistentRead(true)
                .key(DynamoDbCatalog.tablePrimaryKey(tableIdentifier))
                .build());
    if (table.hasItem()) {
      metadataLocation = getMetadataLocation(table);
    } else {
      if (currentMetadataLocation() != null) {
        throw new NoSuchTableException(
            "Cannot find table %s after refresh, "
                + "maybe another process deleted it or revoked your access permission",
            tableName());
      }
    }

    refreshFromMetadataLocation(metadataLocation);
  }

  /**
   * 将新元数据提交到 DynamoDB。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>写入新元数据文件到 S3，获取 newMetadataLocation。
   *   <li>强一致读取当前表记录，校验 base 元数据位置与 DynamoDB 中一致（乐观锁前置检查）。
   *   <li>准备属性（TABLE_TYPE、METADATA_LOCATION、PREVIOUS_METADATA_LOCATION）， 调用 {@link #persistTable}
   *       持久化，通过条件表达式实现 CAS。
   *   <li>异常处理：CommitFailedException 直接上抛；其他 RuntimeException 时结合 RetryDetector 判断是否需要推断提交状态，分别返回
   *       FAILURE/UNKNOWN。
   *   <li>finally 块中，若提交失败则删除已写入的元数据文件以避免泄漏。
   * </ol>
   *
   * @param base 当前元数据（null 表示新表）
   * @param metadata 新元数据
   * @throws CommitFailedException 并发冲突导致提交失败
   * @throws CommitStateUnknownException 提交状态无法确定
   */
  @Override
  protected void doCommit(TableMetadata base, TableMetadata metadata) {
    boolean newTable = base == null;
    String newMetadataLocation = writeNewMetadataIfRequired(newTable, metadata);
    CommitStatus commitStatus = CommitStatus.FAILURE;
    RetryDetector retryDetector = new RetryDetector();
    Map<String, AttributeValue> tableKey = DynamoDbCatalog.tablePrimaryKey(tableIdentifier);
    try {
      GetItemResponse table =
          dynamo.getItem(
              GetItemRequest.builder()
                  .tableName(awsProperties.dynamoDbTableName())
                  .consistentRead(true)
                  .key(tableKey)
                  .build());
      checkMetadataLocation(table, base);
      Map<String, String> properties = prepareProperties(table, newMetadataLocation);
      persistTable(tableKey, table, properties, retryDetector);
      commitStatus = CommitStatus.SUCCESS;
    } catch (CommitFailedException e) {
      // any explicit commit failures are passed up and out to the retry handler
      throw e;
    } catch (RuntimeException persistFailure) {
      boolean conditionCheckFailed = persistFailure instanceof ConditionalCheckFailedException;

      // If we got an exception we weren't expecting, or we got a ConditionalCheckFailedException
      // but retries were performed, attempt to reconcile the actual commit status.
      if (!conditionCheckFailed || retryDetector.retried()) {
        LOG.warn(
            "Received unexpected failure when committing to {}, validating if commit ended up succeeding.",
            fullTableName,
            persistFailure);
        commitStatus = checkCommitStatus(newMetadataLocation, metadata);
      }

      if (commitStatus != CommitStatus.SUCCESS && conditionCheckFailed) {
        throw new CommitFailedException(
            persistFailure, "Cannot commit %s: concurrent update detected", tableName());
      }

      switch (commitStatus) {
        case SUCCESS:
          break;
        case FAILURE:
          throw new CommitFailedException(
              persistFailure, "Cannot commit %s due to unexpected exception", tableName());
        case UNKNOWN:
          throw new CommitStateUnknownException(persistFailure);
      }
    } finally {
      try {
        if (commitStatus == CommitStatus.FAILURE) {
          // if anything went wrong, clean up the uncommitted metadata file
          io().deleteFile(newMetadataLocation);
        }
      } catch (RuntimeException e) {
        LOG.error("Failed to cleanup metadata file at {}", newMetadataLocation, e);
      }
    }
  }

  /**
   * 校验 DynamoDB 中的元数据位置与 base 一致，不一致则抛 CommitFailedException。
   *
   * @param table DynamoDB 表记录响应
   * @param base 当前元数据（null 表示新表）
   * @throws CommitFailedException 元数据位置不匹配
   */
  private void checkMetadataLocation(GetItemResponse table, TableMetadata base) {
    String dynamoMetadataLocation = table.hasItem() ? getMetadataLocation(table) : null;
    String baseMetadataLocation = base != null ? base.metadataFileLocation() : null;
    if (!Objects.equals(baseMetadataLocation, dynamoMetadataLocation)) {
      throw new CommitFailedException(
          "Cannot commit %s because base metadata location '%s' is not same as the current DynamoDb location '%s'",
          tableName(), baseMetadataLocation, dynamoMetadataLocation);
    }
  }

  /** 从 DynamoDB 表记录中提取 metadata_location 属性值。 */
  private String getMetadataLocation(GetItemResponse table) {
    return table.item().get(DynamoDbCatalog.toPropertyCol(METADATA_LOCATION_PROP)).s();
  }

  /**
   * 准备要写入 DynamoDB 的属性 Map。
   *
   * <p>逻辑：保留已有属性，设置 TABLE_TYPE 为 ICEBERG，更新 METADATA_LOCATION， 若存在当前元数据位置则设置
   * PREVIOUS_METADATA_LOCATION。
   *
   * @param response DynamoDB 表记录响应
   * @param newMetadataLocation 新元数据位置
   * @return 属性 Map
   */
  private Map<String, String> prepareProperties(
      GetItemResponse response, String newMetadataLocation) {
    Map<String, String> properties =
        response.hasItem() ? getProperties(response) : Maps.newHashMap();
    properties.put(TABLE_TYPE_PROP, ICEBERG_TABLE_TYPE_VALUE.toUpperCase(Locale.ENGLISH));
    properties.put(METADATA_LOCATION_PROP, newMetadataLocation);
    if (currentMetadataLocation() != null && !currentMetadataLocation().isEmpty()) {
      properties.put(PREVIOUS_METADATA_LOCATION_PROP, currentMetadataLocation());
    }

    return properties;
  }

  /** 从 DynamoDB 表记录中提取所有以 "p." 前缀的属性并还原为键值对。 */
  private Map<String, String> getProperties(GetItemResponse table) {
    return table.item().entrySet().stream()
        .filter(e -> DynamoDbCatalog.isProperty(e.getKey()))
        .collect(
            Collectors.toMap(
                e -> DynamoDbCatalog.toPropertyKey(e.getKey()), e -> e.getValue().s()));
  }

  /**
   * 将表属性持久化到 DynamoDB（CAS 乐观锁）。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>已有表：构建 SET 更新表达式（含属性 + updated_at + version），通过条件表达式 version = :v 实现 CAS 更新，附带
   *       RetryDetector 检测 SDK 重试。
   *   <li>新表：构建完整记录（主键 + 属性 + 时间戳 + version），通过条件表达式 attribute_not_exists(version) 确保不存在时才写入。
   * </ul>
   *
   * @param tableKey 表主键
   * @param table 当前表记录响应
   * @param parameters 要写入的属性
   * @param retryDetector 重试检测器
   * @throws ConditionalCheckFailedException CAS 条件不满足
   */
  void persistTable(
      Map<String, AttributeValue> tableKey,
      GetItemResponse table,
      Map<String, String> parameters,
      RetryDetector retryDetector) {
    if (table.hasItem()) {
      LOG.debug("Committing existing DynamoDb catalog table: {}", tableName());
      List<String> updateParts = Lists.newArrayList();
      Map<String, String> attributeNames = Maps.newHashMap();
      Map<String, AttributeValue> attributeValues = Maps.newHashMap();
      int idx = 0;
      for (Map.Entry<String, String> property : parameters.entrySet()) {
        String attributeValue = ":v" + idx;
        String attributeKey = "#k" + idx;
        idx++;
        updateParts.add(attributeKey + " = " + attributeValue);
        attributeNames.put(attributeKey, DynamoDbCatalog.toPropertyCol(property.getKey()));
        attributeValues.put(
            attributeValue, AttributeValue.builder().s(property.getValue()).build());
      }
      DynamoDbCatalog.updateCatalogEntryMetadata(updateParts, attributeValues);
      String updateExpression = "SET " + DynamoDbCatalog.COMMA.join(updateParts);
      attributeValues.put(":v", table.item().get(DynamoDbCatalog.COL_VERSION));
      dynamo.updateItem(
          UpdateItemRequest.builder()
              .overrideConfiguration(c -> c.addMetricPublisher(retryDetector))
              .tableName(awsProperties.dynamoDbTableName())
              .key(tableKey)
              .conditionExpression(DynamoDbCatalog.COL_VERSION + " = :v")
              .updateExpression(updateExpression)
              .expressionAttributeValues(attributeValues)
              .expressionAttributeNames(attributeNames)
              .build());
    } else {
      LOG.debug("Committing new DynamoDb catalog table: {}", tableName());
      Map<String, AttributeValue> values = Maps.newHashMap(tableKey);
      parameters.forEach(
          (k, v) ->
              values.put(DynamoDbCatalog.toPropertyCol(k), AttributeValue.builder().s(v).build()));
      DynamoDbCatalog.setNewCatalogEntryMetadata(values);

      dynamo.putItem(
          PutItemRequest.builder()
              .overrideConfiguration(c -> c.addMetricPublisher(retryDetector))
              .tableName(awsProperties.dynamoDbTableName())
              .item(values)
              .conditionExpression("attribute_not_exists(" + DynamoDbCatalog.COL_VERSION + ")")
              .build());
    }
  }
}
