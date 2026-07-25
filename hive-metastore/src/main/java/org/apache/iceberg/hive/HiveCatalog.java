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
package org.apache.iceberg.hive;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.AlreadyExistsException;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.InvalidOperationException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.PrincipalType;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.api.UnknownDBException;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.ClientPool;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.LocationUtil;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Hive Metastore 的 Iceberg Catalog 实现。
 *
 * <p>所属模块：iceberg-hive-metastore（该模块的核心类，位于 Iceberg Catalog 实现层， 将 Hive Metastore 作为元数据存储后端）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link org.apache.iceberg.catalog.Catalog} 与 {@link SupportsNamespaces} 接口， 提供基于 Hive
 *       Metastore 的表与命名空间（database）的增删改查能力。
 *   <li>管理 Hive Metastore 客户端连接池（{@link CachedClientPool}），并持有 Hadoop 配置与 {@link FileIO}。
 *   <li>在 Hive Metastore 中将 Iceberg 表注册为 Hive 表（table_type=ICEBERG）， 并维护 Iceberg 元数据指针（metadata
 *       location）。
 *   <li>实现 {@link Configurable} 以支持 Hadoop 配置注入。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseMetastoreCatalog} 复用通用元数据 Catalog 逻辑（如表操作模板、 metadata location 提交协议），仅需实现
 *       Hive 特有的 HMS 调用。
 *   <li>命名空间映射为 Hive database（单层），故 namespace 仅支持一级。
 *   <li>listAllTables 开关：默认只列出 Iceberg 表，可配置为列出所有 Hive 表以提升性能 （跳过逐表 getTableObjectsByName 的过滤开销）。
 *   <li>owner 推断：建库时若未显式指定 owner，自动以当前 Hadoop 用户作为 owner。
 * </ul>
 *
 * <p>上下游关系：由 {@link CatalogUtil} 通过反射加载并初始化；内部委托 {@link CachedClientPool}/{@link HiveClientPool} 执行
 * Thrift 调用，委托 {@link HiveTableOperations} 做表级元数据读写；被 Spark/Flink 等引擎集成模块作为 Hive Catalog 使用。
 */
public class HiveCatalog extends BaseMetastoreCatalog implements SupportsNamespaces, Configurable {
  public static final String LIST_ALL_TABLES = "list-all-tables";
  public static final String LIST_ALL_TABLES_DEFAULT = "false";

  public static final String HMS_TABLE_OWNER = "hive.metastore.table.owner";
  public static final String HMS_DB_OWNER = "hive.metastore.database.owner";
  public static final String HMS_DB_OWNER_TYPE = "hive.metastore.database.owner-type";

  // MetastoreConf is not available with current Hive version
  static final String HIVE_CONF_CATALOG = "metastore.catalog.default";

  private static final Logger LOG = LoggerFactory.getLogger(HiveCatalog.class);

  private String name;
  private Configuration conf;
  private FileIO fileIO;
  private ClientPool<IMetaStoreClient, TException> clients;
  private boolean listAllTables = false;
  private Map<String, String> catalogProperties;

  public HiveCatalog() {}

  /**
   * 初始化 Catalog：解析属性、设置 HMS URI/warehouse、创建客户端池与 FileIO。
   *
   * <p>逻辑：将 properties 保存为不可变副本；若未注入 Configuration 则使用默认环境配置并告警； 将 Catalog 属性中的 URI/warehouse 映射到
   * HiveConf 对应项；解析 listAllTables 开关； 按配置加载 FileIO（默认 {@link HadoopFileIO}）；最后创建 {@link
   * CachedClientPool}。
   *
   * @param inputName Catalog 名称
   * @param properties Catalog 属性集合
   */
  @Override
  public void initialize(String inputName, Map<String, String> properties) {
    this.catalogProperties = ImmutableMap.copyOf(properties);
    this.name = inputName;
    if (conf == null) {
      LOG.warn("No Hadoop Configuration was set, using the default environment Configuration");
      this.conf = new Configuration();
    }

    if (properties.containsKey(CatalogProperties.URI)) {
      this.conf.set(HiveConf.ConfVars.METASTOREURIS.varname, properties.get(CatalogProperties.URI));
    }

    if (properties.containsKey(CatalogProperties.WAREHOUSE_LOCATION)) {
      this.conf.set(
          HiveConf.ConfVars.METASTOREWAREHOUSE.varname,
          LocationUtil.stripTrailingSlash(properties.get(CatalogProperties.WAREHOUSE_LOCATION)));
    }

    this.listAllTables =
        Boolean.parseBoolean(properties.getOrDefault(LIST_ALL_TABLES, LIST_ALL_TABLES_DEFAULT));

    String fileIOImpl = properties.get(CatalogProperties.FILE_IO_IMPL);
    this.fileIO =
        fileIOImpl == null
            ? new HadoopFileIO(conf)
            : CatalogUtil.loadFileIO(fileIOImpl, properties, conf);

    this.clients = new CachedClientPool(conf, properties);
  }

  /**
   * 列出指定命名空间（Hive database）下的表。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 namespace 为单层（对应 Hive database）。
   *   <li>通过 HMS {@code getAllTables} 获取所有表名。
   *   <li>若 listAllTables 为 true：直接返回所有表名（不区分是否 Iceberg 表）。
   *   <li>否则调用 {@code getTableObjectsByName} 获取表对象，过滤出 table_type=ICEBERG 的表。
   * </ol>
   *
   * @param namespace 命名空间（必须为单层，对应 Hive database 名）
   * @return 该 database 下的 Iceberg 表标识列表
   * @throws NoSuchNamespaceException 若 database 不存在
   */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    Preconditions.checkArgument(
        isValidateNamespace(namespace), "Missing database in namespace: %s", namespace);
    String database = namespace.level(0);

    try {
      List<String> tableNames = clients.run(client -> client.getAllTables(database));
      List<TableIdentifier> tableIdentifiers;

      if (listAllTables) {
        tableIdentifiers =
            tableNames.stream()
                .map(t -> TableIdentifier.of(namespace, t))
                .collect(Collectors.toList());
      } else {
        List<Table> tableObjects =
            clients.run(client -> client.getTableObjectsByName(database, tableNames));
        tableIdentifiers =
            tableObjects.stream()
                .filter(
                    table ->
                        table.getParameters() != null
                            && BaseMetastoreTableOperations.ICEBERG_TABLE_TYPE_VALUE
                                .equalsIgnoreCase(
                                    table
                                        .getParameters()
                                        .get(BaseMetastoreTableOperations.TABLE_TYPE_PROP)))
                .map(table -> TableIdentifier.of(namespace, table.getTableName()))
                .collect(Collectors.toList());
      }

      LOG.debug(
          "Listing of namespace: {} resulted in the following tables: {}",
          namespace,
          tableIdentifiers);
      return tableIdentifiers;

    } catch (UnknownDBException e) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);

    } catch (TException e) {
      throw new RuntimeException("Failed to list all tables under namespace " + namespace, e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted in call to listTables", e);
    }
  }

  /** 返回 Catalog 名称。 */
  @Override
  public String name() {
    return name;
  }

  /**
   * 从 Hive Metastore 删除表，可选清理数据文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验标识符合法；不合法则返回 false。
   *   <li>若 purge 为 true，先加载表当前 metadata 用于后续清理数据。
   *   <li>调用 HMS {@code dropTable}（不删数据、不存在则抛异常）。
   *   <li>若 purge 且拿到了 metadata，调用 {@link CatalogUtil#dropTableData} 删除数据文件。
   * </ol>
   *
   * @param identifier 表标识
   * @param purge 是否同时删除底层数据文件
   * @return true 表示已删除；false 表示表不存在
   */
  @Override
  public boolean dropTable(TableIdentifier identifier, boolean purge) {
    if (!isValidIdentifier(identifier)) {
      return false;
    }

    String database = identifier.namespace().level(0);

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

    try {
      clients.run(
          client -> {
            client.dropTable(
                database,
                identifier.name(),
                false /* do not delete data */,
                false /* throw NoSuchObjectException if the table doesn't exist */);
            return null;
          });

      if (purge && lastMetadata != null) {
        CatalogUtil.dropTableData(ops.io(), lastMetadata);
      }

      LOG.info("Dropped table: {}", identifier);
      return true;

    } catch (NoSuchTableException | NoSuchObjectException e) {
      LOG.info("Skipping drop, table does not exist: {}", identifier, e);
      return false;

    } catch (TException e) {
      throw new RuntimeException("Failed to drop " + identifier, e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted in call to dropTable", e);
    }
  }

  /**
   * 在 Hive Metastore 中重命名（或跨 database 移动）表。
   *
   * <p>逻辑：校验源标识符合法后去除目标标识符中可能携带的 catalog 名前缀； 通过 HMS {@code getTable} 获取表对象并校验其为 Iceberg 表；修改其
   * dbName/tableName 后 调用 {@link MetastoreUtil#alterTable} 完成重命名。
   *
   * @param from 源表标识
   * @param originalTo 目标表标识（可包含 catalog 名前缀，会被自动去除）
   * @throws NoSuchTableException 源表不存在
   * @throws org.apache.iceberg.exceptions.AlreadyExistsException 目标表已存在
   */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier originalTo) {
    if (!isValidIdentifier(from)) {
      throw new NoSuchTableException("Invalid identifier: %s", from);
    }

    TableIdentifier to = removeCatalogName(originalTo);
    Preconditions.checkArgument(isValidIdentifier(to), "Invalid identifier: %s", to);

    String toDatabase = to.namespace().level(0);
    String fromDatabase = from.namespace().level(0);
    String fromName = from.name();

    try {
      Table table = clients.run(client -> client.getTable(fromDatabase, fromName));
      HiveTableOperations.validateTableIsIceberg(table, fullTableName(name, from));

      table.setDbName(toDatabase);
      table.setTableName(to.name());

      clients.run(
          client -> {
            MetastoreUtil.alterTable(client, fromDatabase, fromName, table);
            return null;
          });

      LOG.info("Renamed table from {}, to {}", from, to);

    } catch (NoSuchObjectException e) {
      throw new NoSuchTableException("Table does not exist: %s", from);

    } catch (AlreadyExistsException e) {
      throw new org.apache.iceberg.exceptions.AlreadyExistsException(
          "Table already exists: %s", to);

    } catch (TException e) {
      throw new RuntimeException("Failed to rename " + from + " to " + to, e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted in call to rename", e);
    }
  }

  /**
   * 在 Hive Metastore 中创建 database（命名空间）。
   *
   * <p>逻辑：校验 namespace 非空且为单层；校验 owner-type 与 owner 配置成对出现； 通过 {@link #convertToDatabase} 构造 Hive
   * {@link Database} 对象后调用 HMS {@code createDatabase}。
   *
   * @param namespace 命名空间（单层，对应 database 名）
   * @param meta 命名空间属性（可含 comment、location、owner 等）
   * @throws org.apache.iceberg.exceptions.AlreadyExistsException database 已存在
   */
  @Override
  public void createNamespace(Namespace namespace, Map<String, String> meta) {
    Preconditions.checkArgument(
        !namespace.isEmpty(), "Cannot create namespace with invalid name: %s", namespace);
    Preconditions.checkArgument(
        isValidateNamespace(namespace),
        "Cannot support multi part namespace in Hive Metastore: %s",
        namespace);
    Preconditions.checkArgument(
        meta.get(HMS_DB_OWNER_TYPE) == null || meta.get(HMS_DB_OWNER) != null,
        "Create namespace setting %s without setting %s is not allowed",
        HMS_DB_OWNER_TYPE,
        HMS_DB_OWNER);
    try {
      clients.run(
          client -> {
            client.createDatabase(convertToDatabase(namespace, meta));
            return null;
          });

      LOG.info("Created namespace: {}", namespace);

    } catch (AlreadyExistsException e) {
      throw new org.apache.iceberg.exceptions.AlreadyExistsException(
          e, "Namespace '%s' already exists!", namespace);

    } catch (TException e) {
      throw new RuntimeException(
          "Failed to create namespace " + namespace + " in Hive Metastore", e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Interrupted in call to createDatabase(name) " + namespace + " in Hive Metastore", e);
    }
  }

  /**
   * 列出命名空间（Hive database）。
   *
   * <p>逻辑：Hive 仅支持单层命名空间，故仅在 namespace 为空时返回所有 database； 若传入非空且非法的 namespace 则抛
   * NoSuchNamespaceException；非空合法 namespace 返回空列表 （Hive 不支持子命名空间）。
   *
   * @param namespace 父命名空间（传空表示列出顶层）
   * @return database 对应的 Namespace 列表
   */
  @Override
  public List<Namespace> listNamespaces(Namespace namespace) {
    if (!isValidateNamespace(namespace) && !namespace.isEmpty()) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }
    if (!namespace.isEmpty()) {
      return ImmutableList.of();
    }
    try {
      List<Namespace> namespaces =
          clients.run(IMetaStoreClient::getAllDatabases).stream()
              .map(Namespace::of)
              .collect(Collectors.toList());

      LOG.debug("Listing namespace {} returned tables: {}", namespace, namespaces);
      return namespaces;

    } catch (TException e) {
      throw new RuntimeException(
          "Failed to list all namespace: " + namespace + " in Hive Metastore", e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Interrupted in call to getAllDatabases() " + namespace + " in Hive Metastore", e);
    }
  }

  /**
   * 删除 Hive database（命名空间）。
   *
   * <p>逻辑：校验 namespace 为单层后调用 HMS {@code dropDatabase}（不删数据、不忽略不存在、 不级联）。若 database 非空抛 {@link
   * NamespaceNotEmptyException}。
   *
   * @param namespace 命名空间
   * @return true 表示已删除；false 表示 database 不存在
   * @throws NamespaceNotEmptyException database 非空
   */
  @Override
  public boolean dropNamespace(Namespace namespace) {
    if (!isValidateNamespace(namespace)) {
      return false;
    }

    try {
      clients.run(
          client -> {
            client.dropDatabase(
                namespace.level(0),
                false /* deleteData */,
                false /* ignoreUnknownDb */,
                false /* cascade */);
            return null;
          });

      LOG.info("Dropped namespace: {}", namespace);
      return true;

    } catch (InvalidOperationException e) {
      throw new NamespaceNotEmptyException(
          e, "Namespace %s is not empty. One or more tables exist.", namespace);

    } catch (NoSuchObjectException e) {
      return false;

    } catch (TException e) {
      throw new RuntimeException("Failed to drop namespace " + namespace + " in Hive Metastore", e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Interrupted in call to drop dropDatabase(name) " + namespace + " in Hive Metastore", e);
    }
  }

  /**
   * 为命名空间设置属性。
   *
   * <p>逻辑：校验 owner-type 与 owner 成对设置后，加载现有属性合并新属性，构造 {@link Database} 并调用 {@link
   * #alterHiveDataBase} 更新。
   *
   * @param namespace 命名空间
   * @param properties 要设置的属性
   * @return 始终返回 true（失败时抛异常）
   */
  @Override
  public boolean setProperties(Namespace namespace, Map<String, String> properties) {
    Preconditions.checkArgument(
        (properties.get(HMS_DB_OWNER_TYPE) == null) == (properties.get(HMS_DB_OWNER) == null),
        "Setting %s and %s has to be performed together or not at all",
        HMS_DB_OWNER_TYPE,
        HMS_DB_OWNER);
    Map<String, String> parameter = Maps.newHashMap();

    parameter.putAll(loadNamespaceMetadata(namespace));
    parameter.putAll(properties);
    Database database = convertToDatabase(namespace, parameter);

    alterHiveDataBase(namespace, database);
    LOG.debug("Successfully set properties {} for {}", properties.keySet(), namespace);

    // Always successful, otherwise exception is thrown
    return true;
  }

  /**
   * 移除命名空间的指定属性。
   *
   * <p>逻辑：校验 owner-type 与 owner 成对移除后，加载现有属性，将待移除键值置 null， 构造 {@link Database} 并调用 {@link
   * #alterHiveDataBase} 更新。
   *
   * @param namespace 命名空间
   * @param properties 要移除的属性键集合
   * @return 始终返回 true（失败时抛异常）
   */
  @Override
  public boolean removeProperties(Namespace namespace, Set<String> properties) {
    Preconditions.checkArgument(
        properties.contains(HMS_DB_OWNER_TYPE) == properties.contains(HMS_DB_OWNER),
        "Removing %s and %s has to be performed together or not at all",
        HMS_DB_OWNER_TYPE,
        HMS_DB_OWNER);
    Map<String, String> parameter = Maps.newHashMap();

    parameter.putAll(loadNamespaceMetadata(namespace));
    properties.forEach(key -> parameter.put(key, null));
    Database database = convertToDatabase(namespace, parameter);

    alterHiveDataBase(namespace, database);
    LOG.debug("Successfully removed properties {} from {}", properties, namespace);

    // Always successful, otherwise exception is thrown
    return true;
  }

  /**
   * 调用 HMS {@code alterDatabase} 更新 database 元数据。
   *
   * <p>逻辑：在客户端池上执行 alterDatabase；database 不存在时抛 NoSuchNamespaceException。
   *
   * @param namespace 命名空间
   * @param database 已构造好的 Hive Database 对象
   */
  private void alterHiveDataBase(Namespace namespace, Database database) {
    try {
      clients.run(
          client -> {
            client.alterDatabase(namespace.level(0), database);
            return null;
          });

    } catch (NoSuchObjectException | UnknownDBException e) {
      throw new NoSuchNamespaceException(e, "Namespace does not exist: %s", namespace);

    } catch (TException e) {
      throw new RuntimeException(
          "Failed to list namespace under namespace: " + namespace + " in Hive Metastore", e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Interrupted in call to getDatabase(name) " + namespace + " in Hive Metastore", e);
    }
  }

  /**
   * 加载命名空间的元数据属性。
   *
   * <p>逻辑：校验 namespace 为单层后通过 HMS {@code getDatabase} 获取 Database， 再由 {@link #convertToMetadata}
   * 转为属性 Map。
   *
   * @param namespace 命名空间
   * @return database 的属性映射（含 location、comment、owner 等）
   * @throws NoSuchNamespaceException database 不存在
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace namespace) {
    if (!isValidateNamespace(namespace)) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }

    try {
      Database database = clients.run(client -> client.getDatabase(namespace.level(0)));
      Map<String, String> metadata = convertToMetadata(database);
      LOG.debug("Loaded metadata for namespace {} found {}", namespace, metadata.keySet());
      return metadata;

    } catch (NoSuchObjectException | UnknownDBException e) {
      throw new NoSuchNamespaceException(e, "Namespace does not exist: %s", namespace);

    } catch (TException e) {
      throw new RuntimeException(
          "Failed to list namespace under namespace: " + namespace + " in Hive Metastore", e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Interrupted in call to getDatabase(name) " + namespace + " in Hive Metastore", e);
    }
  }

  /**
   * 校验表标识符是否合法（namespace 必须恰好一层，对应 Hive database）。
   *
   * @param tableIdentifier 表标识
   * @return namespace 层级数为 1 时返回 true
   */
  @Override
  protected boolean isValidIdentifier(TableIdentifier tableIdentifier) {
    return tableIdentifier.namespace().levels().length == 1;
  }

  /**
   * 去除目标标识符中可能携带的 catalog 名前缀。
   *
   * <p>逻辑：若标识符已合法（单层 namespace）则原样返回；若 namespace 为两层且第一层等于 catalog 名，则去掉第一层；否则原样返回。
   *
   * @param to 原始目标标识符
   * @return 去除 catalog 名前缀后的标识符
   */
  private TableIdentifier removeCatalogName(TableIdentifier to) {
    if (isValidIdentifier(to)) {
      return to;
    }

    // check if the identifier includes the catalog name and remove it
    if (to.namespace().levels().length == 2 && name().equalsIgnoreCase(to.namespace().level(0))) {
      return TableIdentifier.of(Namespace.of(to.namespace().level(1)), to.name());
    }

    // return the original unmodified
    return to;
  }

  /** 判断 namespace 是否为单层（Hive database 要求）。 */
  private boolean isValidateNamespace(Namespace namespace) {
    return namespace.levels().length == 1;
  }

  /**
   * 为指定表创建 {@link HiveTableOperations}，用于读写该表的 Iceberg 元数据。
   *
   * @param tableIdentifier 表标识
   * @return 该表对应的 TableOperations 实例
   */
  @Override
  public TableOperations newTableOps(TableIdentifier tableIdentifier) {
    String dbName = tableIdentifier.namespace().level(0);
    String tableName = tableIdentifier.name();
    return new HiveTableOperations(conf, clients, fileIO, name, dbName, tableName);
  }

  /**
   * 计算新建表的默认存储路径。
   *
   * <p>逻辑：优先读取 database 自身设置的 location，拼接为 {dbLocation}/{tableName}； 若 database 未设置 location，则回退到
   * {warehouse}/{dbName}.db/{tableName}。
   *
   * <p>设计要点：此处复制了 HMS 的路径生成逻辑，是为了在创建 HMS 表元数据之前先生成 Iceberg 元数据文件，保证提交顺序为"先写元数据文件，再提交 HMS 表"。
   *
   * @param tableIdentifier 表标识
   * @return 默认 warehouse 路径
   */
  @Override
  protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
    // This is a little edgy since we basically duplicate the HMS location generation logic.
    // Sadly I do not see a good way around this if we want to keep the order of events, like:
    // - Create meta files
    // - Create the metadata in HMS, and this way committing the changes

    // Create a new location based on the namespace / database if it is set on database level
    try {
      Database databaseData =
          clients.run(client -> client.getDatabase(tableIdentifier.namespace().levels()[0]));
      if (databaseData.getLocationUri() != null) {
        // If the database location is set use it as a base.
        return String.format("%s/%s", databaseData.getLocationUri(), tableIdentifier.name());
      }

    } catch (TException e) {
      throw new RuntimeException(
          String.format("Metastore operation failed for %s", tableIdentifier), e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted during commit", e);
    }

    // Otherwise, stick to the {WAREHOUSE_DIR}/{DB_NAME}.db/{TABLE_NAME} path
    String databaseLocation = databaseLocation(tableIdentifier.namespace().levels()[0]);
    return String.format("%s/%s", databaseLocation, tableIdentifier.name());
  }

  /**
   * 计算指定 database 的默认存储路径（{warehouse}/{dbName}.db）。
   *
   * @param databaseName database 名
   * @return database 默认路径
   */
  private String databaseLocation(String databaseName) {
    String warehouseLocation = conf.get(HiveConf.ConfVars.METASTOREWAREHOUSE.varname);
    Preconditions.checkNotNull(
        warehouseLocation, "Warehouse location is not set: hive.metastore.warehouse.dir=null");
    warehouseLocation = LocationUtil.stripTrailingSlash(warehouseLocation);
    return String.format("%s/%s.db", warehouseLocation, databaseName);
  }

  /**
   * 将 Hive {@link Database} 转换为 Iceberg 命名空间属性 Map。
   *
   * <p>逻辑：把 database 的 parameters、location、comment、owner/ownerType 映射为属性键值对。
   *
   * @param database Hive Database 对象
   * @return 属性映射
   */
  private Map<String, String> convertToMetadata(Database database) {

    Map<String, String> meta = Maps.newHashMap();

    meta.putAll(database.getParameters());
    meta.put("location", database.getLocationUri());
    if (database.getDescription() != null) {
      meta.put("comment", database.getDescription());
    }
    if (database.getOwnerName() != null) {
      meta.put(HMS_DB_OWNER, database.getOwnerName());
      if (database.getOwnerType() != null) {
        meta.put(HMS_DB_OWNER_TYPE, database.getOwnerType().name());
      }
    }

    return meta;
  }

  /**
   * 将命名空间与属性转换为 Hive {@link Database} 对象。
   *
   * <p>逻辑：设置 database 名与默认 location；遍历属性，将 comment/location/owner/ownerType 分别映射到 Database
   * 对应字段，其余非空属性放入 parameters；若未指定 owner，则自动以 当前 Hadoop 用户作为 owner（类型 USER）。
   *
   * @param namespace 命名空间
   * @param meta 属性集合
   * @return 构造好的 Hive Database 对象
   */
  Database convertToDatabase(Namespace namespace, Map<String, String> meta) {
    if (!isValidateNamespace(namespace)) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }

    Database database = new Database();
    Map<String, String> parameter = Maps.newHashMap();

    database.setName(namespace.level(0));
    database.setLocationUri(databaseLocation(namespace.level(0)));

    meta.forEach(
        (key, value) -> {
          if (key.equals("comment")) {
            database.setDescription(value);
          } else if (key.equals("location")) {
            database.setLocationUri(value);
          } else if (key.equals(HMS_DB_OWNER)) {
            database.setOwnerName(value);
          } else if (key.equals(HMS_DB_OWNER_TYPE) && value != null) {
            database.setOwnerType(PrincipalType.valueOf(value));
          } else {
            if (value != null) {
              parameter.put(key, value);
            }
          }
        });

    if (database.getOwnerName() == null) {
      database.setOwnerName(HiveHadoopUtil.currentUser());
      database.setOwnerType(PrincipalType.USER);
    }

    database.setParameters(parameter);

    return database;
  }

  /** 返回包含 name 与 metastore URI 的字符串表示。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("uri", this.conf == null ? "" : this.conf.get(HiveConf.ConfVars.METASTOREURIS.varname))
        .toString();
  }

  /**
   * 注入 Hadoop 配置（{@link Configurable} 接口实现）。
   *
   * <p>设计要点：拷贝一份 Configuration 而非直接持有引用，避免外部修改影响本 Catalog。
   *
   * @param conf Hadoop 配置
   */
  @Override
  public void setConf(Configuration conf) {
    this.conf = new Configuration(conf);
  }

  /** 返回当前 Hadoop 配置（{@link Configurable} 接口实现）。 */
  @Override
  public Configuration getConf() {
    return conf;
  }

  /** 返回 Catalog 属性集合（供父类读取，未初始化时返回空 Map）。 */
  @Override
  protected Map<String, String> properties() {
    return catalogProperties == null ? ImmutableMap.of() : catalogProperties;
  }

  /** 设置是否列出所有 Hive 表（仅测试用）。 */
  @VisibleForTesting
  void setListAllTables(boolean listAllTables) {
    this.listAllTables = listAllTables;
  }

  /** 返回底层客户端池（仅测试用）。 */
  @VisibleForTesting
  ClientPool<IMetaStoreClient, TException> clientPool() {
    return clients;
  }
}
