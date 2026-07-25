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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.aws.AwsClientFactories;
import org.apache.iceberg.aws.AwsProperties;
import org.apache.iceberg.aws.s3.S3FileIO;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.LocationUtil;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * 基于 AWS DynamoDB 的 Iceberg Catalog 实现。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成模块，处于引擎层之下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>使用 DynamoDB 表作为元数据存储，管理 Iceberg 表与命名空间的元信息。
 *   <li>实现 {@link org.apache.iceberg.catalog.SupportsNamespaces} 接口，支持命名空间的 创建、列表、加载属性、删除、属性设置与移除。
 *   <li>支持表的创建、列表、删除、重命名等 Catalog 操作，通过乐观锁（version 列）保证并发安全。
 *   <li>在初始化时自动创建所需的 DynamoDB 目录表（含主键索引与 GSI）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>DynamoDB 作为 Serverless 元数据存储，无需维护数据库实例，适合云原生场景。
 *   <li>使用"identifier + namespace"复合主键，通过 GSI（namespace-identifier）支持按命名空间 查询表列表，兼顾点查与范围查效率。
 *   <li>乐观锁：每条记录包含 version 字段（UUID），更新时通过条件表达式校验 version 不变， 避免并发写入冲突。renameTable 使用
 *       TransactWriteItems 保证原子性。
 *   <li>属性以 "p." 前缀存储在 DynamoDB 属性列中，与系统列（identifier/version 等）区分。
 * </ul>
 *
 * <p>上下游关系：继承 BaseMetastoreCatalog，被 iceberg-aws 模块及各引擎通过 CatalogUtil 加载； 表操作委托给 {@link
 * DynamoDbTableOperations}，文件 IO 默认使用 {@link org.apache.iceberg.aws.s3.S3FileIO}。
 */
public class DynamoDbCatalog extends BaseMetastoreCatalog
    implements Closeable, SupportsNamespaces, Configurable {

  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbCatalog.class);
  private static final int CATALOG_TABLE_CREATION_WAIT_ATTEMPTS_MAX = 5;
  static final Joiner COMMA = Joiner.on(',');

  private static final String GSI_NAMESPACE_IDENTIFIER = "namespace-identifier";
  private static final String COL_IDENTIFIER = "identifier";
  private static final String COL_IDENTIFIER_NAMESPACE = "NAMESPACE";
  private static final String COL_NAMESPACE = "namespace";
  private static final String PROPERTY_COL_PREFIX = "p.";
  private static final String PROPERTY_DEFAULT_LOCATION = "default_location";
  private static final String COL_CREATED_AT = "created_at";
  private static final String COL_UPDATED_AT = "updated_at";

  // field used for optimistic locking
  static final String COL_VERSION = "v";

  private DynamoDbClient dynamo;
  private Configuration hadoopConf;
  private String catalogName;
  private String warehousePath;
  private AwsProperties awsProperties;
  private FileIO fileIO;
  private CloseableGroup closeableGroup;
  private Map<String, String> catalogProperties;

  public DynamoDbCatalog() {}

  /**
   * {@inheritDoc}
   *
   * <p>逻辑：从 properties 读取 warehouse 路径、构建 AwsProperties、通过 AwsClientFactories 创建 DynamoDB 客户端、初始化
   * FileIO，最后委托给包级 initialize 完成实际初始化。
   */
  @Override
  public void initialize(String name, Map<String, String> properties) {
    this.catalogProperties = ImmutableMap.copyOf(properties);
    initialize(
        name,
        properties.get(CatalogProperties.WAREHOUSE_LOCATION),
        new AwsProperties(properties),
        AwsClientFactories.from(properties).dynamo(),
        initializeFileIO(properties));
  }

  /**
   * 包级初始化方法（供测试使用）：设置各字段并确保目录表存在。
   *
   * <p>逻辑：校验 warehousePath 非空，设置 catalogName/awsProperties/warehousePath/dynamo/fileIO， 将 dynamo 和
   * fileIO 注册到 CloseableGroup 以便统一关闭，最后调用 {@link #ensureCatalogTableExistsOrCreate()} 确保元数据表已创建。
   *
   * @param name catalog 名称
   * @param path warehouse 路径
   * @param properties AWS 属性
   * @param client DynamoDB 客户端
   * @param io 文件 IO
   */
  @VisibleForTesting
  void initialize(
      String name, String path, AwsProperties properties, DynamoDbClient client, FileIO io) {
    Preconditions.checkArgument(
        path != null && path.length() > 0,
        "Cannot initialize DynamoDbCatalog because warehousePath must not be null or empty");

    this.catalogName = name;
    this.awsProperties = properties;
    this.warehousePath = LocationUtil.stripTrailingSlash(path);
    this.dynamo = client;
    this.fileIO = io;

    this.closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(dynamo);
    closeableGroup.addCloseable(fileIO);
    closeableGroup.setSuppressCloseFailure(true);

    ensureCatalogTableExistsOrCreate();
  }

  @Override
  public String name() {
    return catalogName;
  }

  /**
   * 为指定表标识符创建 {@link DynamoDbTableOperations} 实例。
   *
   * @param tableIdentifier 表标识符
   * @return 表操作实例
   */
  @Override
  protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
    validateTableIdentifier(tableIdentifier);
    return new DynamoDbTableOperations(dynamo, awsProperties, catalogName, fileIO, tableIdentifier);
  }

  /**
   * 计算表的默认存储路径。
   *
   * <p>逻辑：查询命名空间记录，若命名空间设置了 default_location 属性则使用之拼接 {@code <default_location>/<table_name>}；否则使用
   * warehousePath 拼接 {@code <warehousePath>/<namespace>.db/<table_name>}。
   *
   * @param tableIdentifier 表标识符
   * @return 表的默认存储路径
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
    validateTableIdentifier(tableIdentifier);
    GetItemResponse response =
        dynamo.getItem(
            GetItemRequest.builder()
                .tableName(awsProperties.dynamoDbTableName())
                .consistentRead(true)
                .key(namespacePrimaryKey(tableIdentifier.namespace()))
                .build());

    if (!response.hasItem()) {
      throw new NoSuchNamespaceException(
          "Cannot find default warehouse location: namespace %s does not exist",
          tableIdentifier.namespace());
    }

    String defaultLocationCol = toPropertyCol(PROPERTY_DEFAULT_LOCATION);
    if (response.item().containsKey(defaultLocationCol)) {
      return String.format(
          "%s/%s", response.item().get(defaultLocationCol).s(), tableIdentifier.name());
    } else {
      return String.format(
          "%s/%s.db/%s", warehousePath, tableIdentifier.namespace(), tableIdentifier.name());
    }
  }

  /**
   * 在 DynamoDB 中创建命名空间记录。
   *
   * <p>逻辑：构建主键并设置创建时间/更新时间/version，将 metadata 以 "p." 前缀写入属性列， 通过条件表达式 attribute_not_exists(version)
   * 确保不存在时才创建，已存在则抛 AlreadyExistsException。
   *
   * @param namespace 命名空间
   * @param metadata 命名空间属性
   * @throws AlreadyExistsException 命名空间已存在
   */
  @Override
  public void createNamespace(Namespace namespace, Map<String, String> metadata) {
    validateNamespace(namespace);
    Map<String, AttributeValue> values = namespacePrimaryKey(namespace);
    setNewCatalogEntryMetadata(values);
    metadata.forEach(
        (key, value) -> values.put(toPropertyCol(key), AttributeValue.builder().s(value).build()));

    try {
      dynamo.putItem(
          PutItemRequest.builder()
              .tableName(awsProperties.dynamoDbTableName())
              .conditionExpression("attribute_not_exists(" + DynamoDbCatalog.COL_VERSION + ")")
              .item(values)
              .build());
    } catch (ConditionalCheckFailedException e) {
      throw new AlreadyExistsException("Cannot create namespace %s: already exists", namespace);
    }
  }

  /**
   * 列出指定命名空间下的子命名空间。
   *
   * <p>逻辑：以 identifier=NAMESPACE 为条件查询主键索引，若 namespace 非空则追加 begins_with 前缀条件；分页遍历直到
   * lastEvaluatedKey 为空，将结果按 "." 分割还原为 Namespace。
   *
   * @param namespace 父命名空间，为空时列出全部
   * @return 子命名空间列表
   */
  @Override
  public List<Namespace> listNamespaces(Namespace namespace) throws NoSuchNamespaceException {
    validateNamespace(namespace);
    List<Namespace> namespaces = Lists.newArrayList();
    Map<String, AttributeValue> lastEvaluatedKey = null;
    String condition = COL_IDENTIFIER + " = :identifier";
    Map<String, AttributeValue> conditionValues = Maps.newHashMap();
    conditionValues.put(
        ":identifier", AttributeValue.builder().s(COL_IDENTIFIER_NAMESPACE).build());
    if (!namespace.isEmpty()) {
      condition += " AND " + "begins_with(" + COL_NAMESPACE + ",:ns)";
      conditionValues.put(":ns", AttributeValue.builder().s(namespace.toString()).build());
    }

    do {
      QueryResponse response =
          dynamo.query(
              QueryRequest.builder()
                  .tableName(awsProperties.dynamoDbTableName())
                  .consistentRead(true)
                  .keyConditionExpression(condition)
                  .expressionAttributeValues(conditionValues)
                  .exclusiveStartKey(lastEvaluatedKey)
                  .build());

      if (response.hasItems()) {
        for (Map<String, AttributeValue> item : response.items()) {
          String ns = item.get(COL_NAMESPACE).s();
          namespaces.add(Namespace.of(ns.split("\\.")));
        }
      }

      lastEvaluatedKey = response.lastEvaluatedKey();
    } while (!lastEvaluatedKey.isEmpty());

    return namespaces;
  }

  /**
   * 加载命名空间的属性。
   *
   * <p>逻辑：通过主键点查获取命名空间记录，过滤出以 "p." 开头的属性列并还原为属性键值对。
   *
   * @param namespace 命名空间
   * @return 属性 Map
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace namespace)
      throws NoSuchNamespaceException {
    validateNamespace(namespace);
    GetItemResponse response =
        dynamo.getItem(
            GetItemRequest.builder()
                .tableName(awsProperties.dynamoDbTableName())
                .consistentRead(true)
                .key(namespacePrimaryKey(namespace))
                .build());

    if (!response.hasItem()) {
      throw new NoSuchNamespaceException("Cannot find namespace %s", namespace);
    }

    return response.item().entrySet().stream()
        .filter(e -> isProperty(e.getKey()))
        .collect(Collectors.toMap(e -> toPropertyKey(e.getKey()), e -> e.getValue().s()));
  }

  /**
   * 删除命名空间，要求命名空间为空。
   *
   * <p>逻辑：先检查命名空间下无表，再通过条件表达式 attribute_exists(namespace) 删除记录； 命名空间不存在时返回 false。
   *
   * @param namespace 命名空间
   * @return true 表示删除成功
   * @throws NamespaceNotEmptyException 命名空间下仍有表
   */
  @Override
  public boolean dropNamespace(Namespace namespace) throws NamespaceNotEmptyException {
    validateNamespace(namespace);
    if (!listTables(namespace).isEmpty()) {
      throw new NamespaceNotEmptyException("Cannot delete non-empty namespace %s", namespace);
    }

    try {
      dynamo.deleteItem(
          DeleteItemRequest.builder()
              .tableName(awsProperties.dynamoDbTableName())
              .key(namespacePrimaryKey(namespace))
              .conditionExpression("attribute_exists(" + COL_NAMESPACE + ")")
              .build());
      return true;
    } catch (ConditionalCheckFailedException e) {
      return false;
    }
  }

  /**
   * 为命名空间设置属性（已存在的属性会被覆盖）。
   *
   * <p>逻辑：将每个属性键加上 "p." 前缀，构建 SET 更新表达式，同时更新 updated_at 和 version， 通过乐观锁条件表达式保证并发安全。
   *
   * @param namespace 命名空间
   * @param properties 要设置的属性
   * @return true 表示成功
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public boolean setProperties(Namespace namespace, Map<String, String> properties)
      throws NoSuchNamespaceException {
    List<String> updateParts = Lists.newArrayList();
    Map<String, String> attributeNames = Maps.newHashMap();
    Map<String, AttributeValue> attributeValues = Maps.newHashMap();
    int idx = 0;
    for (Map.Entry<String, String> property : properties.entrySet()) {
      String attributeValue = ":v" + idx;
      String attributeKey = "#k" + idx;
      idx++;
      updateParts.add(attributeKey + " = " + attributeValue);
      attributeNames.put(attributeKey, toPropertyCol(property.getKey()));
      attributeValues.put(attributeValue, AttributeValue.builder().s(property.getValue()).build());
    }

    updateCatalogEntryMetadata(updateParts, attributeValues);
    String updateExpression = "SET " + COMMA.join(updateParts);
    return updateProperties(namespace, updateExpression, attributeValues, attributeNames);
  }

  /**
   * 移除命名空间的指定属性。
   *
   * <p>逻辑：构建 REMOVE 更新表达式移除以 "p." 前缀的属性列，同时更新 updated_at 和 version。
   *
   * @param namespace 命名空间
   * @param properties 要移除的属性键集合
   * @return true 表示成功
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public boolean removeProperties(Namespace namespace, Set<String> properties)
      throws NoSuchNamespaceException {
    List<String> removeParts = Lists.newArrayList(properties.iterator());
    Map<String, String> attributeNames = Maps.newHashMap();
    Map<String, AttributeValue> attributeValues = Maps.newHashMap();
    int idx = 0;
    for (String property : properties) {
      String attributeKey = "#k" + idx;
      idx++;
      removeParts.add(attributeKey);
      attributeNames.put(attributeKey, toPropertyCol(property));
    }

    List<String> updateParts = Lists.newArrayList();
    updateCatalogEntryMetadata(updateParts, attributeValues);
    String updateExpression =
        "REMOVE " + COMMA.join(removeParts) + " SET " + COMMA.join(updateParts);
    return updateProperties(namespace, updateExpression, attributeValues, attributeNames);
  }

  /**
   * 列出命名空间下的所有表。
   *
   * <p>逻辑：通过 GSI（namespace-identifier）以 namespace 为条件查询，分页遍历， 过滤掉 NAMESPACE 类型的记录，将 identifier 按
   * "." 分割还原为 TableIdentifier。
   *
   * @param namespace 命名空间
   * @return 表标识符列表
   */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    List<TableIdentifier> identifiers = Lists.newArrayList();
    Map<String, AttributeValue> lastEvaluatedKey = null;
    String condition = COL_NAMESPACE + " = :ns";
    Map<String, AttributeValue> conditionValues =
        ImmutableMap.of(":ns", AttributeValue.builder().s(namespace.toString()).build());
    do {
      QueryResponse response =
          dynamo.query(
              QueryRequest.builder()
                  .tableName(awsProperties.dynamoDbTableName())
                  .indexName(GSI_NAMESPACE_IDENTIFIER)
                  .keyConditionExpression(condition)
                  .expressionAttributeValues(conditionValues)
                  .exclusiveStartKey(lastEvaluatedKey)
                  .build());

      if (response.hasItems()) {
        for (Map<String, AttributeValue> item : response.items()) {
          String identifier = item.get(COL_IDENTIFIER).s();
          if (!COL_IDENTIFIER_NAMESPACE.equals(identifier)) {
            identifiers.add(TableIdentifier.of(identifier.split("\\.")));
          }
        }
      }

      lastEvaluatedKey = response.lastEvaluatedKey();
    } while (!lastEvaluatedKey.isEmpty());
    return identifiers;
  }

  /**
   * 从 DynamoDB 删除表记录，可选清除表数据文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>通过主键点查确认表存在，不存在则抛 NoSuchTableException。
   *   <li>若 purge 为 true，加载表元数据用于后续清理数据文件。
   *   <li>通过条件表达式（version 匹配）删除 DynamoDB 记录，并发冲突时返回 false。
   *   <li>若 purge 且元数据加载成功，调用 CatalogUtil.dropTableData 清除数据文件。
   * </ol>
   *
   * @param identifier 表标识符
   * @param purge 是否同时清除表数据文件
   * @return true 表示删除成功
   * @throws NoSuchTableException 表不存在
   */
  @Override
  public boolean dropTable(TableIdentifier identifier, boolean purge) {
    Map<String, AttributeValue> key = tablePrimaryKey(identifier);
    try {
      GetItemResponse response =
          dynamo.getItem(
              GetItemRequest.builder()
                  .tableName(awsProperties.dynamoDbTableName())
                  .consistentRead(true)
                  .key(key)
                  .build());

      if (!response.hasItem()) {
        throw new NoSuchTableException("Cannot find table %s to drop", identifier);
      }

      TableOperations ops = newTableOps(identifier);
      TableMetadata lastMetadata = null;
      if (purge) {
        try {
          lastMetadata = ops.current();
        } catch (NotFoundException e) {
          LOG.warn(
              "Failed to load table metadata for table: {}, continuing drop without purge",
              identifier,
              e);
        }
      }
      dynamo.deleteItem(
          DeleteItemRequest.builder()
              .tableName(awsProperties.dynamoDbTableName())
              .key(tablePrimaryKey(identifier))
              .conditionExpression(COL_VERSION + " = :v")
              .expressionAttributeValues(ImmutableMap.of(":v", response.item().get(COL_VERSION)))
              .build());
      LOG.info("Successfully dropped table {} from DynamoDb catalog", identifier);

      if (purge && lastMetadata != null) {
        CatalogUtil.dropTableData(ops.io(), lastMetadata);
        LOG.info("Table {} data purged", identifier);
      }

      LOG.info("Dropped table: {}", identifier);
      return true;
    } catch (ConditionalCheckFailedException e) {
      LOG.error("Cannot complete drop table operation for {}: commit conflict", identifier, e);
      return false;
    } catch (Exception e) {
      LOG.error("Cannot complete drop table operation for {}: unexpected exception", identifier, e);
      throw e;
    }
  }

  /**
   * 原子性地重命名表（从 from 改为 to）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>点查确认源表存在（否则抛 NoSuchTableException）、目标表不存在（否则抛 AlreadyExistsException）。
   *   <li>将源表的属性列复制到目标键，设置新的 version 和时间戳。
   *   <li>通过 TransactWriteItems 原子性地执行：删除源记录 + 写入目标记录， 删除时用条件表达式校验 version 未变。
   * </ol>
   *
   * @param from 源表标识符
   * @param to 目标表标识符
   * @throws NoSuchTableException 源表不存在
   * @throws AlreadyExistsException 目标表已存在
   */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    Map<String, AttributeValue> fromKey = tablePrimaryKey(from);
    Map<String, AttributeValue> toKey = tablePrimaryKey(to);

    GetItemResponse fromResponse =
        dynamo.getItem(
            GetItemRequest.builder()
                .tableName(awsProperties.dynamoDbTableName())
                .consistentRead(true)
                .key(fromKey)
                .build());

    if (!fromResponse.hasItem()) {
      throw new NoSuchTableException(
          "Cannot rename table %s to %s: %s does not exist", from, to, from);
    }

    GetItemResponse toResponse =
        dynamo.getItem(
            GetItemRequest.builder()
                .tableName(awsProperties.dynamoDbTableName())
                .consistentRead(true)
                .key(toKey)
                .build());

    if (toResponse.hasItem()) {
      throw new AlreadyExistsException(
          "Cannot rename table %s to %s: %s already exists", from, to, to);
    }

    fromResponse.item().entrySet().stream()
        .filter(e -> isProperty(e.getKey()))
        .forEach(e -> toKey.put(e.getKey(), e.getValue()));

    setNewCatalogEntryMetadata(toKey);

    dynamo.transactWriteItems(
        TransactWriteItemsRequest.builder()
            .transactItems(
                TransactWriteItem.builder()
                    .delete(
                        Delete.builder()
                            .tableName(awsProperties.dynamoDbTableName())
                            .key(fromKey)
                            .conditionExpression(COL_VERSION + " = :v")
                            .expressionAttributeValues(
                                ImmutableMap.of(":v", fromResponse.item().get(COL_VERSION)))
                            .build())
                    .build(),
                TransactWriteItem.builder()
                    .put(
                        Put.builder()
                            .tableName(awsProperties.dynamoDbTableName())
                            .item(toKey)
                            .conditionExpression("attribute_not_exists(" + COL_VERSION + ")")
                            .build())
                    .build())
            .build());

    LOG.info("Successfully renamed table from {} to {}", from, to);
  }

  @Override
  public void setConf(Configuration conf) {
    hadoopConf = conf;
  }

  @Override
  public Configuration getConf() {
    return hadoopConf;
  }

  @Override
  public void close() throws IOException {
    closeableGroup.close();
  }

  /**
   * 返回命名空间默认表存储路径的属性键。
   *
   * <p>通过 {@link #setProperties(Namespace, Map)} 设置此属性后，该命名空间下所有新表的 默认存储路径将基于该值生成。
   *
   * @return 默认路径属性键
   */
  public static String defaultLocationProperty() {
    return PROPERTY_DEFAULT_LOCATION;
  }

  static String toPropertyCol(String propertyKey) {
    return PROPERTY_COL_PREFIX + propertyKey;
  }

  static boolean isProperty(String dynamoCol) {
    return dynamoCol.startsWith(PROPERTY_COL_PREFIX);
  }

  static String toPropertyKey(String propertyCol) {
    return propertyCol.substring(PROPERTY_COL_PREFIX.length());
  }

  /** 构建命名空间记录的主键（identifier=NAMESPACE, namespace=命名空间字符串）。 */
  static Map<String, AttributeValue> namespacePrimaryKey(Namespace namespace) {
    Map<String, AttributeValue> key = Maps.newHashMap();
    key.put(COL_IDENTIFIER, AttributeValue.builder().s(COL_IDENTIFIER_NAMESPACE).build());
    key.put(COL_NAMESPACE, AttributeValue.builder().s(namespace.toString()).build());
    return key;
  }

  /** 构建表记录的主键（identifier=表标识符字符串, namespace=命名空间字符串）。 */
  static Map<String, AttributeValue> tablePrimaryKey(TableIdentifier identifier) {
    Map<String, AttributeValue> key = Maps.newHashMap();
    key.put(COL_IDENTIFIER, AttributeValue.builder().s(identifier.toString()).build());
    key.put(COL_NAMESPACE, AttributeValue.builder().s(identifier.namespace().toString()).build());
    return key;
  }

  /** 为新建记录设置 created_at、updated_at 时间戳和随机 version（UUID）。 */
  static void setNewCatalogEntryMetadata(Map<String, AttributeValue> values) {
    String current = Long.toString(System.currentTimeMillis());
    values.put(COL_CREATED_AT, AttributeValue.builder().n(current).build());
    values.put(COL_UPDATED_AT, AttributeValue.builder().n(current).build());
    values.put(COL_VERSION, AttributeValue.builder().s(UUID.randomUUID().toString()).build());
  }

  /** 为更新操作追加 updated_at 和新 version（UUID）到更新表达式片段中。 */
  static void updateCatalogEntryMetadata(
      List<String> updateParts, Map<String, AttributeValue> attributeValues) {
    updateParts.add(COL_UPDATED_AT + " = :uat");
    attributeValues.put(
        ":uat", AttributeValue.builder().n(Long.toString(System.currentTimeMillis())).build());
    updateParts.add(COL_VERSION + " = :uv");
    attributeValues.put(":uv", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
  }

  /**
   * 根据配置初始化 FileIO，未指定实现类时默认使用 S3FileIO。
   *
   * @param properties catalog 属性
   * @return FileIO 实例
   */
  private FileIO initializeFileIO(Map<String, String> properties) {
    String fileIOImpl = properties.get(CatalogProperties.FILE_IO_IMPL);
    if (fileIOImpl == null) {
      FileIO io = new S3FileIO();
      io.initialize(properties);
      return io;
    } else {
      return CatalogUtil.loadFileIO(fileIOImpl, properties, hadoopConf);
    }
  }

  /**
   * 校验命名空间各层级不为空且不含点号（点号用作层级分隔符）。
   *
   * @param namespace 命名空间
   * @throws ValidationException 校验失败
   */
  private void validateNamespace(Namespace namespace) {
    for (String level : namespace.levels()) {
      ValidationException.check(
          level != null && !level.isEmpty(), "Namespace level must not be empty: %s", namespace);
      ValidationException.check(
          !level.contains("."),
          "Namespace level must not contain dot, but found %s in %s",
          level,
          namespace);
    }
  }

  /**
   * 校验表标识符的命名空间合法且表名不含点号。
   *
   * @param identifier 表标识符
   * @throws ValidationException 校验失败
   */
  private void validateTableIdentifier(TableIdentifier identifier) {
    validateNamespace(identifier.namespace());
    ValidationException.check(
        identifier.hasNamespace(), "Table namespace must not be empty: %s", identifier);
    String tableName = identifier.name();
    ValidationException.check(
        !tableName.contains("."), "Table name must not contain dot: %s", tableName);
  }

  private boolean dynamoDbTableExists(String tableName) {
    try {
      dynamo.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
      return true;
    } catch (ResourceNotFoundException e) {
      return false;
    }
  }

  /**
   * 确保 DynamoDB 目录表存在，不存在则创建。
   *
   * <p>逻辑：若表已存在则直接返回；否则创建含复合主键（identifier HASH + namespace RANGE） 和 GSI（namespace-identifier）的表，使用
   * PAY_PER_REQUEST 计费模式，然后轮询等待表变为 ACTIVE 状态。
   */
  private void ensureCatalogTableExistsOrCreate() {
    if (dynamoDbTableExists(awsProperties.dynamoDbTableName())) {
      return;
    }

    LOG.info(
        "DynamoDb catalog table {} not found, trying to create", awsProperties.dynamoDbTableName());
    dynamo.createTable(
        CreateTableRequest.builder()
            .tableName(awsProperties.dynamoDbTableName())
            .keySchema(
                KeySchemaElement.builder()
                    .attributeName(COL_IDENTIFIER)
                    .keyType(KeyType.HASH)
                    .build(),
                KeySchemaElement.builder()
                    .attributeName(COL_NAMESPACE)
                    .keyType(KeyType.RANGE)
                    .build())
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName(COL_IDENTIFIER)
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName(COL_NAMESPACE)
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .globalSecondaryIndexes(
                GlobalSecondaryIndex.builder()
                    .indexName(GSI_NAMESPACE_IDENTIFIER)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName(COL_NAMESPACE)
                            .keyType(KeyType.HASH)
                            .build(),
                        KeySchemaElement.builder()
                            .attributeName(COL_IDENTIFIER)
                            .keyType(KeyType.RANGE)
                            .build())
                    .projection(
                        Projection.builder().projectionType(ProjectionType.KEYS_ONLY).build())
                    .build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());

    // wait for the dynamo table to complete provisioning, which takes around 10 seconds
    Tasks.foreach(awsProperties.dynamoDbTableName())
        .retry(CATALOG_TABLE_CREATION_WAIT_ATTEMPTS_MAX)
        .throwFailureWhenFinished()
        .onlyRetryOn(IllegalStateException.class)
        .run(this::checkTableActive);
  }

  private void checkTableActive(String tableName) {
    try {
      DescribeTableResponse response =
          dynamo.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
      TableStatus currentStatus = response.table().tableStatus();
      if (!currentStatus.equals(TableStatus.ACTIVE)) {
        throw new IllegalStateException(
            String.format(
                "Dynamo catalog table %s is not active, current status: %s",
                tableName, currentStatus));
      }
    } catch (ResourceNotFoundException e) {
      throw new IllegalStateException(
          String.format("Cannot find Dynamo catalog table %s", tableName));
    }
  }

  /**
   * 执行命名空间属性的更新操作（乐观锁保护）。
   *
   * <p>逻辑：点查获取当前 version，通过条件表达式 version = :v 执行 updateItem； 命名空间不存在抛
   * NoSuchNamespaceException，并发冲突返回 false。
   *
   * @param namespace 命名空间
   * @param updateExpression DynamoDB 更新表达式
   * @param attributeValues 表达式属性值
   * @param attributeNames 表达式属性名映射
   * @return true 表示成功
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  private boolean updateProperties(
      Namespace namespace,
      String updateExpression,
      Map<String, AttributeValue> attributeValues,
      Map<String, String> attributeNames) {
    validateNamespace(namespace);
    Map<String, AttributeValue> key = namespacePrimaryKey(namespace);
    try {
      GetItemResponse response =
          dynamo.getItem(
              GetItemRequest.builder()
                  .tableName(awsProperties.dynamoDbTableName())
                  .consistentRead(true)
                  .key(key)
                  .build());

      if (!response.hasItem()) {
        throw new NoSuchNamespaceException("Cannot find namespace %s", namespace);
      }

      attributeValues.put(":v", response.item().get(COL_VERSION));
      dynamo.updateItem(
          UpdateItemRequest.builder()
              .tableName(awsProperties.dynamoDbTableName())
              .key(key)
              .conditionExpression(COL_VERSION + " = :v")
              .updateExpression(updateExpression)
              .expressionAttributeValues(attributeValues)
              .expressionAttributeNames(attributeNames)
              .build());
      return true;
    } catch (ConditionalCheckFailedException e) {
      return false;
    }
  }

  @Override
  protected Map<String, String> properties() {
    return catalogProperties == null ? ImmutableMap.of() : catalogProperties;
  }
}
