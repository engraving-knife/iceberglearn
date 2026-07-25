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
package org.apache.iceberg.aws.glue;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.LockManager;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.aws.AwsClientFactories;
import org.apache.iceberg.aws.AwsClientFactory;
import org.apache.iceberg.aws.AwsProperties;
import org.apache.iceberg.aws.lakeformation.LakeFormationAwsClientFactory;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.hadoop.Configurable;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.LocationUtil;
import org.apache.iceberg.util.LockManagers;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.CreateDatabaseRequest;
import software.amazon.awssdk.services.glue.model.CreateTableRequest;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.DeleteDatabaseRequest;
import software.amazon.awssdk.services.glue.model.DeleteTableRequest;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.glue.model.GetDatabaseResponse;
import software.amazon.awssdk.services.glue.model.GetDatabasesRequest;
import software.amazon.awssdk.services.glue.model.GetDatabasesResponse;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.GetTableResponse;
import software.amazon.awssdk.services.glue.model.GetTablesRequest;
import software.amazon.awssdk.services.glue.model.GetTablesResponse;
import software.amazon.awssdk.services.glue.model.InvalidInputException;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;
import software.amazon.awssdk.services.glue.model.UpdateDatabaseRequest;

/**
 * 文件级说明：基于 AWS Glue Data Catalog 实现的 Iceberg Catalog。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 Iceberg {@link org.apache.iceberg.catalog.Catalog} 与 {@link SupportsNamespaces} SPI， 把
 *       Glue 数据库/表映射为 Iceberg Namespace/TableIdentifier。
 *   <li>通过 {@link GlueTableOperations} 把表元数据 commit 到 Glue（含乐观锁/分布式锁）， 并以 S3 为底层数据与元数据文件存储。
 *   <li>支持 Lake Formation 凭证下发、表/库标签透传、warehouse 默认路径推导等 AWS 特有能力。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseMetastoreCatalog}：复用 Iceberg 通用的元数据存储 catalog 抽象， 仅实现 Glue 特有的 CRUD 与命名空间操作。
 *   <li>乐观锁优先：若运行环境 SDK 支持 UpdateTableRequest.versionId，则优先使用 Glue 乐观锁； 否则回退到分布式锁管理器（推荐
 *       DynamoDB），保证 commit 原子性。
 *   <li>表级 FileIO 缓存：每个 {@link GlueTableOperations} 持有自己的 FileIO（可能基于表级 Lake Formation 凭证或标签配置），用
 *       Caffeine weakKey 缓存并在 GC 时关闭 FileIO， 避免内存泄漏。
 *   <li>Configurable：实现 Hadoop Configurable 以接收引擎传入的 Hadoop 配置。
 * </ul>
 *
 * <p>上下游关系：由 Iceberg 引擎层（Spark/Flink 等）通过 CatalogUtil 加载；依赖 {@link AwsClientFactories} 构造
 * GlueClient、{@link AwsProperties}/{@link S3FileIOProperties} 解析配置、{@link LockManagers}
 * 构造锁管理器；底层数据通过 S3FileIO 读写。
 */
public class GlueCatalog extends BaseMetastoreCatalog
    implements Closeable, SupportsNamespaces, Configurable<Configuration> {

  private static final Logger LOG = LoggerFactory.getLogger(GlueCatalog.class);

  private GlueClient glue;
  private Object hadoopConf;
  private String catalogName;
  private String warehousePath;
  private AwsProperties awsProperties;
  private S3FileIOProperties s3FileIOProperties;
  private LockManager lockManager;
  private CloseableGroup closeableGroup;
  private Map<String, String> catalogProperties;
  private Cache<TableOperations, FileIO> fileIOCloser;

  // Attempt to set versionId if available on the path
  private static final DynMethods.UnboundMethod SET_VERSION_ID =
      DynMethods.builder("versionId")
          .hiddenImpl(
              "software.amazon.awssdk.services.glue.model.UpdateTableRequest$Builder", String.class)
          .orNoop()
          .build();

  /**
   * 无参构造器，供 Iceberg 动态加载 catalog 时反射调用。
   *
   * <p>所有字段需通过后续 {@link #initialize(String, Map)} 完成初始化。
   */
  public GlueCatalog() {}

  /**
   * 初始化 GlueCatalog：根据 properties 构造 GlueClient、AwsProperties、S3FileIOProperties 与锁管理器。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若启用 Lake Formation：校验/补齐 client.factory 为 LakeFormationAwsClientFactory， 构造工厂并断言其类型。
   *   <li>否则直接从 properties 构造 AwsClientFactory 并获取 GlueClient。
   *   <li>委托给 {@link #initialize(String, String, AwsProperties, S3FileIOProperties, GlueClient,
   *       LockManager)} 完成字段装配。
   * </ol>
   *
   * @param name catalog 名称
   * @param properties catalog 配置键值
   */
  @Override
  public void initialize(String name, Map<String, String> properties) {
    this.catalogProperties = ImmutableMap.copyOf(properties);
    AwsClientFactory awsClientFactory;
    if (PropertyUtil.propertyAsBoolean(
        properties,
        AwsProperties.GLUE_LAKEFORMATION_ENABLED,
        AwsProperties.GLUE_LAKEFORMATION_ENABLED_DEFAULT)) {
      String factoryImpl =
          PropertyUtil.propertyAsString(properties, AwsProperties.CLIENT_FACTORY, null);
      ImmutableMap.Builder<String, String> builder =
          ImmutableMap.<String, String>builder().putAll(properties);
      if (factoryImpl == null) {
        builder.put(AwsProperties.CLIENT_FACTORY, LakeFormationAwsClientFactory.class.getName());
      }

      this.catalogProperties = builder.buildOrThrow();
      awsClientFactory = AwsClientFactories.from(catalogProperties);
      Preconditions.checkArgument(
          awsClientFactory instanceof LakeFormationAwsClientFactory,
          "Detected LakeFormation enabled for Glue catalog, should use a client factory that extends %s, but found %s",
          LakeFormationAwsClientFactory.class.getName(),
          factoryImpl);
    } else {
      awsClientFactory = AwsClientFactories.from(properties);
    }

    initialize(
        name,
        properties.get(CatalogProperties.WAREHOUSE_LOCATION),
        new AwsProperties(properties),
        new S3FileIOProperties(properties),
        awsClientFactory.glue(),
        initializeLockManager(properties));
  }

  /**
   * 根据运行环境与配置选择锁管理器：用户显式配置优先；否则若 SDK 不支持乐观锁则回退到 内存锁（仅单机安全）；若支持乐观锁则返回 null（不使用外部锁）。
   *
   * @param properties catalog 配置
   * @return 锁管理器实例，可能为 null
   */
  private LockManager initializeLockManager(Map<String, String> properties) {
    if (properties.containsKey(CatalogProperties.LOCK_IMPL)) {
      return LockManagers.from(properties);
    } else if (SET_VERSION_ID.isNoop()) {
      LOG.warn(
          "Optimistic locking is not available in the environment. Using in-memory lock manager."
              + " To ensure atomic transaction, please configure a distributed lock manager"
              + " such as the DynamoDB lock manager.");
      return LockManagers.defaultLockManager();
    } else {
      LOG.debug("Using optimistic locking for Glue Data Catalog tables.");
    }
    return null;
  }

  /** 供测试使用的初始化入口，允许传入额外 catalogProps。 */
  @VisibleForTesting
  void initialize(
      String name,
      String path,
      AwsProperties properties,
      S3FileIOProperties s3Properties,
      GlueClient client,
      LockManager lock,
      Map<String, String> catalogProps) {
    this.catalogProperties = catalogProps;
    initialize(name, path, properties, s3Properties, client, lock);
  }

  /**
   * 装配 GlueCatalog 各字段并构造资源关闭组与 FileIO 缓存。
   *
   * @param name catalog 名称
   * @param path warehouse 路径
   * @param properties AWS 配置
   * @param s3Properties S3 FileIO 配置
   * @param client Glue 客户端
   * @param lock 锁管理器（可为 null）
   */
  @VisibleForTesting
  void initialize(
      String name,
      String path,
      AwsProperties properties,
      S3FileIOProperties s3Properties,
      GlueClient client,
      LockManager lock) {
    this.catalogName = name;
    this.awsProperties = properties;
    this.s3FileIOProperties = s3Properties;
    this.warehousePath =
        (path != null && path.length() > 0) ? LocationUtil.stripTrailingSlash(path) : null;
    this.glue = client;
    this.lockManager = lock;

    this.closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(glue);
    closeableGroup.addCloseable(lockManager);
    closeableGroup.setSuppressCloseFailure(true);
    this.fileIOCloser = newFileIOCloser();
  }

  /**
   * 为指定表构造 {@link GlueTableOperations}，必要时叠加表级标签与 Lake Formation 配置。
   *
   * <p>逻辑：基于 catalogProperties 复制一份表级配置，按需追加 Iceberg 表/命名空间 S3 标签 与 Lake Formation 库表名参数，再以此构造
   * GlueTableOperations 并把其 FileIO 注册到 fileIOCloser 缓存，确保表操作 GC 时关闭对应 FileIO。
   *
   * @param tableIdentifier 表标识
   * @return 表操作实例
   */
  @Override
  protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
    if (catalogProperties != null) {
      ImmutableMap.Builder<String, String> tableSpecificCatalogPropertiesBuilder =
          ImmutableMap.<String, String>builder().putAll(catalogProperties);
      boolean skipNameValidation = awsProperties.glueCatalogSkipNameValidation();

      if (s3FileIOProperties.writeTableTagEnabled()) {
        tableSpecificCatalogPropertiesBuilder.put(
            S3FileIOProperties.WRITE_TAGS_PREFIX.concat(S3FileIOProperties.S3_TAG_ICEBERG_TABLE),
            IcebergToGlueConverter.getTableName(tableIdentifier, skipNameValidation));
      }

      if (s3FileIOProperties.isWriteNamespaceTagEnabled()) {
        tableSpecificCatalogPropertiesBuilder.put(
            S3FileIOProperties.WRITE_TAGS_PREFIX.concat(
                S3FileIOProperties.S3_TAG_ICEBERG_NAMESPACE),
            IcebergToGlueConverter.getDatabaseName(tableIdentifier, skipNameValidation));
      }

      if (awsProperties.glueLakeFormationEnabled()) {
        tableSpecificCatalogPropertiesBuilder
            .put(
                AwsProperties.LAKE_FORMATION_DB_NAME,
                IcebergToGlueConverter.getDatabaseName(tableIdentifier, skipNameValidation))
            .put(
                AwsProperties.LAKE_FORMATION_TABLE_NAME,
                IcebergToGlueConverter.getTableName(tableIdentifier, skipNameValidation))
            .put(S3FileIOProperties.PRELOAD_CLIENT_ENABLED, String.valueOf(true));
      }

      // FileIO initialization depends on tableSpecificCatalogProperties, so a new FileIO is
      // initialized each time
      GlueTableOperations glueTableOperations =
          new GlueTableOperations(
              glue,
              lockManager,
              catalogName,
              awsProperties,
              tableSpecificCatalogPropertiesBuilder.buildOrThrow(),
              hadoopConf,
              tableIdentifier);
      fileIOCloser.put(glueTableOperations, glueTableOperations.io());
      return glueTableOperations;
    }

    GlueTableOperations glueTableOperations =
        new GlueTableOperations(
            glue,
            lockManager,
            catalogName,
            awsProperties,
            catalogProperties,
            hadoopConf,
            tableIdentifier);
    fileIOCloser.put(glueTableOperations, glueTableOperations.io());
    return glueTableOperations;
  }

  /**
   * 推导表的默认 warehouse 路径，与 HiveCatalog 行为一致。
   *
   * <p>逻辑：若 Glue 数据库已设置 locationUri，则使用 locationUri/tableName； 否则使用
   * warehousePath/databaseName.db/tableName，并校验 warehousePath 非空。
   *
   * @param tableIdentifier table id
   * @return default warehouse path
   */
  @Override
  protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
    // check if value is set in database
    GetDatabaseResponse response =
        glue.getDatabase(
            GetDatabaseRequest.builder()
                .catalogId(awsProperties.glueCatalogId())
                .name(
                    IcebergToGlueConverter.getDatabaseName(
                        tableIdentifier, awsProperties.glueCatalogSkipNameValidation()))
                .build());
    String dbLocationUri = response.database().locationUri();
    if (dbLocationUri != null) {
      return String.format("%s/%s", dbLocationUri, tableIdentifier.name());
    }

    ValidationException.check(
        warehousePath != null && warehousePath.length() > 0,
        "Cannot derive default warehouse location, warehouse path must not be null or empty");

    return String.format(
        "%s/%s.db/%s",
        warehousePath,
        IcebergToGlueConverter.getDatabaseName(
            tableIdentifier, awsProperties.glueCatalogSkipNameValidation()),
        tableIdentifier.name());
  }

  /**
   * 列举指定命名空间下的所有 Iceberg 表，按 Glue 分页 token 循环拉取。
   *
   * <p>逻辑：循环调用 getTables 直到 nextToken 为空；过滤出 {@link #isGlueIcebergTable} 为真的表，转换为 Iceberg
   * TableIdentifier。
   *
   * @param namespace 命名空间
   * @return 表标识列表
   */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    namespaceExists(namespace);
    // should be safe to list all before returning the list, instead of dynamically load the list.
    String nextToken = null;
    List<TableIdentifier> results = Lists.newArrayList();
    do {
      GetTablesResponse response =
          glue.getTables(
              GetTablesRequest.builder()
                  .catalogId(awsProperties.glueCatalogId())
                  .databaseName(
                      IcebergToGlueConverter.toDatabaseName(
                          namespace, awsProperties.glueCatalogSkipNameValidation()))
                  .nextToken(nextToken)
                  .build());
      nextToken = response.nextToken();
      if (response.hasTableList()) {
        results.addAll(
            response.tableList().stream()
                .filter(this::isGlueIcebergTable)
                .map(GlueToIcebergConverter::toTableId)
                .collect(Collectors.toList()));
      }
    } while (nextToken != null);

    LOG.debug("Listing of namespace: {} resulted in the following tables: {}", namespace, results);
    return results;
  }

  /**
   * 判断 Glue 表是否为 Iceberg 表：检查 parameters 中 table_type 是否为 ICEBERG。
   *
   * @param table Glue 表
   * @return 是 Iceberg 表返回 true
   */
  private boolean isGlueIcebergTable(Table table) {
    return table.parameters() != null
        && BaseMetastoreTableOperations.ICEBERG_TABLE_TYPE_VALUE.equalsIgnoreCase(
            table.parameters().get(BaseMetastoreTableOperations.TABLE_TYPE_PROP));
  }

  /**
   * 从 Glue 删除表，可选地清理底层 S3 数据。
   *
   * <p>逻辑：若 purge 为 true 则先加载最新元数据；调用 deleteTable 删除 Glue 表项； 再用 {@link CatalogUtil#dropTableData}
   * 删除数据文件。表不存在视为失败返回 false。
   *
   * @param identifier 表标识
   * @param purge 是否清理数据文件
   * @return 删除成功返回 true
   */
  @Override
  public boolean dropTable(TableIdentifier identifier, boolean purge) {
    try {
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
      glue.deleteTable(
          DeleteTableRequest.builder()
              .catalogId(awsProperties.glueCatalogId())
              .databaseName(
                  IcebergToGlueConverter.getDatabaseName(
                      identifier, awsProperties.glueCatalogSkipNameValidation()))
              .name(identifier.name())
              .build());
      LOG.info("Successfully dropped table {} from Glue", identifier);
      if (purge && lastMetadata != null) {
        CatalogUtil.dropTableData(ops.io(), lastMetadata);
        LOG.info("Glue table {} data purged", identifier);
      }
      LOG.info("Dropped table: {}", identifier);
      return true;
    } catch (EntityNotFoundException e) {
      LOG.error("Cannot drop table {} because table not found or not accessible", identifier, e);
      return false;
    } catch (Exception e) {
      LOG.error(
          "Cannot complete drop table operation for {} due to unexpected exception", identifier, e);
      throw e;
    }
  }

  /**
   * 重命名表：Glue 不支持原子 rename，这里通过“建新表 + 删旧表”实现，并保留原元数据指针。
   *
   * <p>逻辑：校验目标命名空间存在；读取源表信息；用相同的 owner/tableType/parameters/ storageDescriptor
   * 创建目标表；删除源表；若删除失败则回滚删除目标表。
   *
   * @param from identifier of the table to rename
   * @param to new table name
   */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    // check new namespace exists
    if (!namespaceExists(to.namespace())) {
      throw new NoSuchNamespaceException(
          "Cannot rename %s to %s because namespace %s does not exist", from, to, to.namespace());
    }
    // keep metadata
    Table fromTable = null;
    String fromTableDbName =
        IcebergToGlueConverter.getDatabaseName(from, awsProperties.glueCatalogSkipNameValidation());
    String fromTableName =
        IcebergToGlueConverter.getTableName(from, awsProperties.glueCatalogSkipNameValidation());
    String toTableDbName =
        IcebergToGlueConverter.getDatabaseName(to, awsProperties.glueCatalogSkipNameValidation());
    String toTableName =
        IcebergToGlueConverter.getTableName(to, awsProperties.glueCatalogSkipNameValidation());
    try {
      GetTableResponse response =
          glue.getTable(
              GetTableRequest.builder()
                  .catalogId(awsProperties.glueCatalogId())
                  .databaseName(fromTableDbName)
                  .name(fromTableName)
                  .build());
      fromTable = response.table();
    } catch (EntityNotFoundException e) {
      throw new NoSuchTableException(
          e, "Cannot rename %s because the table does not exist in Glue", from);
    }

    // use the same Glue info to create the new table, pointing to the old metadata
    TableInput.Builder tableInputBuilder =
        TableInput.builder()
            .owner(fromTable.owner())
            .tableType(fromTable.tableType())
            .parameters(fromTable.parameters())
            .storageDescriptor(fromTable.storageDescriptor());

    glue.createTable(
        CreateTableRequest.builder()
            .catalogId(awsProperties.glueCatalogId())
            .databaseName(toTableDbName)
            .tableInput(tableInputBuilder.name(toTableName).build())
            .build());
    LOG.info("created rename destination table {}", to);

    try {
      dropTable(from, false);
    } catch (Exception e) {
      // rollback, delete renamed table
      LOG.error(
          "Fail to drop old table {} after renaming to {}, rollback to use the old table",
          from,
          to,
          e);
      glue.deleteTable(
          DeleteTableRequest.builder()
              .catalogId(awsProperties.glueCatalogId())
              .databaseName(toTableDbName)
              .name(toTableName)
              .build());
      throw e;
    }

    LOG.info("Successfully renamed table from {} to {}", from, to);
  }

  /** 在 Glue 中创建数据库（命名空间），已存在则抛 AlreadyExistsException。 */
  @Override
  public void createNamespace(Namespace namespace, Map<String, String> metadata) {
    try {
      glue.createDatabase(
          CreateDatabaseRequest.builder()
              .catalogId(awsProperties.glueCatalogId())
              .databaseInput(
                  IcebergToGlueConverter.toDatabaseInput(
                      namespace, metadata, awsProperties.glueCatalogSkipNameValidation()))
              .build());
      LOG.info("Created namespace: {}", namespace);
    } catch (software.amazon.awssdk.services.glue.model.AlreadyExistsException e) {
      throw new AlreadyExistsException(
          "Cannot create namespace %s because it already exists in Glue", namespace);
    }
  }

  /**
   * 列举 Glue 中所有数据库（命名空间），按分页 token 循环拉取。
   *
   * <p>Glue 不支持嵌套命名空间，传入非空 namespace 时仅做存在性校验。
   *
   * @param namespace 命名空间，空表示列举所有
   * @return 命名空间列表
   * @throws NoSuchNamespaceException 当传入非空且不存在时
   */
  @Override
  public List<Namespace> listNamespaces(Namespace namespace) throws NoSuchNamespaceException {
    if (!namespace.isEmpty()) {
      // if it is not a list all op, just check if the namespace exists and return empty.
      if (namespaceExists(namespace)) {
        return Lists.newArrayList();
      }
      throw new NoSuchNamespaceException(
          "Glue does not support nested namespace, cannot list namespaces under %s", namespace);
    }

    // should be safe to list all before returning the list, instead of dynamically load the list.
    String nextToken = null;
    List<Namespace> results = Lists.newArrayList();
    do {
      GetDatabasesResponse response =
          glue.getDatabases(
              GetDatabasesRequest.builder()
                  .catalogId(awsProperties.glueCatalogId())
                  .nextToken(nextToken)
                  .build());
      nextToken = response.nextToken();
      if (response.hasDatabaseList()) {
        results.addAll(
            response.databaseList().stream()
                .map(GlueToIcebergConverter::toNamespace)
                .collect(Collectors.toList()));
      }
    } while (nextToken != null);

    LOG.debug("Listing namespace {} returned namespaces: {}", namespace, results);
    return results;
  }

  /**
   * 加载 Glue 数据库的元数据，并补充 locationUri 与 description 到返回 Map。
   *
   * @param namespace 命名空间
   * @return 元数据键值
   * @throws NoSuchNamespaceException 数据库不存在时
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace namespace)
      throws NoSuchNamespaceException {
    String databaseName =
        IcebergToGlueConverter.toDatabaseName(
            namespace, awsProperties.glueCatalogSkipNameValidation());
    try {
      Database database =
          glue.getDatabase(
                  GetDatabaseRequest.builder()
                      .catalogId(awsProperties.glueCatalogId())
                      .name(databaseName)
                      .build())
              .database();
      Map<String, String> result = Maps.newHashMap(database.parameters());

      if (database.locationUri() != null) {
        result.put(IcebergToGlueConverter.GLUE_DB_LOCATION_KEY, database.locationUri());
      }

      if (database.description() != null) {
        result.put(IcebergToGlueConverter.GLUE_DB_DESCRIPTION_KEY, database.description());
      }

      LOG.debug("Loaded metadata for namespace {} found {}", namespace, result);
      return result;
    } catch (InvalidInputException e) {
      throw new NoSuchNamespaceException(
          "invalid input for namespace %s, error message: %s", namespace, e.getMessage());
    } catch (EntityNotFoundException e) {
      throw new NoSuchNamespaceException(
          "fail to find Glue database for namespace %s, error message: %s",
          databaseName, e.getMessage());
    }
  }

  /**
   * 删除 Glue 数据库，若库内仍有表则抛 NamespaceNotEmptyException。
   *
   * @param namespace 命名空间
   * @return 删除成功返回 true
   * @throws NamespaceNotEmptyException 库非空时
   */
  @Override
  public boolean dropNamespace(Namespace namespace) throws NamespaceNotEmptyException {
    namespaceExists(namespace);

    GetTablesResponse response =
        glue.getTables(
            GetTablesRequest.builder()
                .catalogId(awsProperties.glueCatalogId())
                .databaseName(
                    IcebergToGlueConverter.toDatabaseName(
                        namespace, awsProperties.glueCatalogSkipNameValidation()))
                .build());

    if (response.hasTableList() && !response.tableList().isEmpty()) {
      Table table = response.tableList().get(0);
      if (isGlueIcebergTable(table)) {
        throw new NamespaceNotEmptyException(
            "Cannot drop namespace %s because it still contains Iceberg tables", namespace);
      } else {
        throw new NamespaceNotEmptyException(
            "Cannot drop namespace %s because it still contains non-Iceberg tables", namespace);
      }
    }

    glue.deleteDatabase(
        DeleteDatabaseRequest.builder()
            .catalogId(awsProperties.glueCatalogId())
            .name(
                IcebergToGlueConverter.toDatabaseName(
                    namespace, awsProperties.glueCatalogSkipNameValidation()))
            .build());
    LOG.info("Dropped namespace: {}", namespace);
    // Always successful, otherwise exception is thrown
    return true;
  }

  /**
   * 为 Glue 数据库设置属性：合并现有元数据与新属性后整体更新。
   *
   * @param namespace 命名空间
   * @param properties 待设置属性
   * @return 成功返回 true
   * @throws NoSuchNamespaceException 数据库不存在时
   */
  @Override
  public boolean setProperties(Namespace namespace, Map<String, String> properties)
      throws NoSuchNamespaceException {
    Map<String, String> newProperties = Maps.newHashMap();
    newProperties.putAll(loadNamespaceMetadata(namespace));
    newProperties.putAll(properties);
    glue.updateDatabase(
        UpdateDatabaseRequest.builder()
            .catalogId(awsProperties.glueCatalogId())
            .name(
                IcebergToGlueConverter.toDatabaseName(
                    namespace, awsProperties.glueCatalogSkipNameValidation()))
            .databaseInput(
                IcebergToGlueConverter.toDatabaseInput(
                    namespace, newProperties, awsProperties.glueCatalogSkipNameValidation()))
            .build());
    LOG.debug("Successfully set properties {} for {}", properties.keySet(), namespace);
    // Always successful, otherwise exception is thrown
    return true;
  }

  /**
   * 从 Glue 数据库移除属性：加载现有元数据、删除指定键后整体更新。
   *
   * @param namespace 命名空间
   * @param properties 待移除属性键集合
   * @return 成功返回 true
   * @throws NoSuchNamespaceException 数据库不存在时
   */
  @Override
  public boolean removeProperties(Namespace namespace, Set<String> properties)
      throws NoSuchNamespaceException {
    Map<String, String> metadata = Maps.newHashMap(loadNamespaceMetadata(namespace));
    for (String property : properties) {
      metadata.remove(property);
    }

    glue.updateDatabase(
        UpdateDatabaseRequest.builder()
            .catalogId(awsProperties.glueCatalogId())
            .name(
                IcebergToGlueConverter.toDatabaseName(
                    namespace, awsProperties.glueCatalogSkipNameValidation()))
            .databaseInput(
                IcebergToGlueConverter.toDatabaseInput(
                    namespace, metadata, awsProperties.glueCatalogSkipNameValidation()))
            .build());
    LOG.debug("Successfully removed properties {} from {}", properties, namespace);
    // Always successful, otherwise exception is thrown
    return true;
  }

  /**
   * 校验表标识是否合法：若开启跳过校验直接返回 true，否则校验命名空间与表名格式。
   *
   * @param tableIdentifier 表标识
   * @return 合法返回 true
   */
  @Override
  protected boolean isValidIdentifier(TableIdentifier tableIdentifier) {
    if (awsProperties.glueCatalogSkipNameValidation()) {
      return true;
    }

    return IcebergToGlueConverter.isValidNamespace(tableIdentifier.namespace())
        && IcebergToGlueConverter.isValidTableName(tableIdentifier.name());
  }

  /** 返回 catalog 名称。 */
  @Override
  public String name() {
    return catalogName;
  }

  /**
   * 关闭 catalog：关闭 GlueClient、锁管理器与所有缓存的 FileIO。
   *
   * @throws IOException 关闭异常
   */
  @Override
  public void close() throws IOException {
    closeableGroup.close();
    if (fileIOCloser != null) {
      fileIOCloser.invalidateAll();
      fileIOCloser.cleanUp();
    }
  }

  /** 接收 Hadoop Configuration，供 FileIO 等组件使用。 */
  @Override
  public void setConf(Configuration conf) {
    this.hadoopConf = conf;
  }

  /** 返回 catalog 配置（不可变），未初始化时返回空 Map。 */
  @Override
  protected Map<String, String> properties() {
    return catalogProperties == null ? ImmutableMap.of() : catalogProperties;
  }

  /**
   * 构造 FileIO 关闭缓存：以 TableOperations 弱引用为 key，当 TableOperations 被 GC 时 自动关闭对应的 FileIO，避免表操作生命周期与
   * FileIO 不一致导致的资源泄漏。
   */
  private Cache<TableOperations, FileIO> newFileIOCloser() {
    return Caffeine.newBuilder()
        .weakKeys()
        .removalListener(
            (RemovalListener<TableOperations, FileIO>)
                (ops, fileIO, cause) -> {
                  if (null != fileIO) {
                    fileIO.close();
                  }
                })
        .build();
  }
}
