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
package org.apache.iceberg.snowflake;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.IcebergBuild;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.hadoop.Configurable;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.apache.iceberg.jdbc.JdbcClientPool;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Snowflake 的 Iceberg Catalog 实现。
 *
 * <p>所属模块：iceberg-snowflake。职责：通过 Snowflake JDBC 连接查询 Snowflake 中管理的 Iceberg 表元数据，把 Iceberg 的
 * catalog 操作（列举表/命名空间、加载表元数据等）映射为对 Snowflake 的元数据查询；表数据文件的实际读写由 {@link FileIO} 负责。
 *
 * <p>设计意图：继承 {@link BaseMetastoreCatalog} 复用模板，仅实现 {@code newTableOps} 等少量抽象方法； 通过 {@link
 * SnowflakeClient} 封装与 Snowflake 的网络通信；每次 {@code newTableOps} 创建独立的 FileIO 实例（因部分 FileIO 如 S3FileIO
 * 会绑定单一 bucket）。当前为只读 catalog，dropTable/ renameTable/createNamespace 等写操作均抛 {@link
 * UnsupportedOperationException}。通过 {@code Configurable} 注入 Hadoop 配置，{@code CloseableGroup}
 * 统一管理资源关闭。
 *
 * <p>上下游关系：向上被引擎（Spark/Flink 等）通过 {@code Catalog} 接口调用；向下依赖 {@link SnowflakeClient}（默认 {@link
 * JdbcSnowflakeClient}）与 {@link FileIO}。
 */
public class SnowflakeCatalog extends BaseMetastoreCatalog
    implements Closeable, SupportsNamespaces, Configurable<Object> {
  private static final String DEFAULT_CATALOG_NAME = "snowflake_catalog";
  private static final String DEFAULT_FILE_IO_IMPL = "org.apache.iceberg.io.ResolvingFileIO";
  // Specifies the name of a Snowflake's partner application to connect through JDBC.
  // https://docs.snowflake.com/en/user-guide/jdbc-parameters.html#application
  private static final String JDBC_APPLICATION_PROPERTY = "application";
  // Add a suffix to user agent header for the web requests made by the jdbc driver.
  private static final String JDBC_USER_AGENT_SUFFIX_PROPERTY = "user_agent_suffix";
  private static final String APP_IDENTIFIER = "iceberg-snowflake-catalog";
  // Specifies the max length of unique id for each catalog initialized session.
  private static final int UNIQUE_ID_LENGTH = 20;
  /** FileIO 工厂，可注入以便测试替换。默认实现委托 {@link CatalogUtil#loadFileIO} 按 impl 类名加载。 */
  static class FileIOFactory {
    public FileIO newFileIO(String impl, Map<String, String> properties, Object hadoopConf) {
      return CatalogUtil.loadFileIO(impl, properties, hadoopConf);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(SnowflakeCatalog.class);

  private CloseableGroup closeableGroup;
  private Object conf;
  private String catalogName;
  private Map<String, String> catalogProperties;
  private FileIOFactory fileIOFactory;
  private SnowflakeClient snowflakeClient;

  /** 无参构造，用于动态加载 Catalog，字段由 {@link #initialize} 初始化。 */
  public SnowflakeCatalog() {}

  /**
   * 列出指定命名空间（schema 级别）下的所有 Iceberg 表。
   *
   * @param namespace 命名空间，必须解析到 SCHEMA 级别
   * @return 表标识符列表
   * @throws IllegalArgumentException namespace 未解析到 SCHEMA 级别
   */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    SnowflakeIdentifier scope = NamespaceHelpers.toSnowflakeIdentifier(namespace);
    Preconditions.checkArgument(
        scope.type() == SnowflakeIdentifier.Type.SCHEMA,
        "listTables must be at SCHEMA level; got %s from namespace %s",
        scope,
        namespace);

    List<SnowflakeIdentifier> sfTables = snowflakeClient.listIcebergTables(scope);

    return sfTables.stream()
        .map(NamespaceHelpers::toIcebergTableIdentifier)
        .collect(Collectors.toList());
  }

  /** 当前不支持删除表，抛出 {@link UnsupportedOperationException}。 */
  @Override
  public boolean dropTable(TableIdentifier identifier, boolean purge) {
    throw new UnsupportedOperationException(
        "SnowflakeCatalog does not currently support dropTable");
  }

  /** 当前不支持重命名表，抛出 {@link UnsupportedOperationException}。 */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    throw new UnsupportedOperationException(
        "SnowflakeCatalog does not currently support renameTable");
  }

  /**
   * 根据配置初始化 Catalog：校验 JDBC URI、加载 Snowflake JDBC 驱动、生成唯一应用标识符， 创建 JDBC 连接池后委托 {@link
   * #initialize(String, SnowflakeClient, FileIOFactory, Map)} 完成初始化。
   *
   * @param name Catalog 名称，为 null 时默认 "snowflake_catalog"
   * @param properties Catalog 配置，必须包含 JDBC 连接 URI
   */
  @Override
  public void initialize(String name, Map<String, String> properties) {
    String uri = properties.get(CatalogProperties.URI);
    Preconditions.checkArgument(null != uri, "JDBC connection URI is required");
    try {
      // We'll ensure the expected JDBC driver implementation class is initialized through
      // reflection regardless of which classloader ends up using this JdbcSnowflakeClient, but
      // we'll only warn if the expected driver fails to load, since users may use repackaged or
      // custom JDBC drivers for Snowflake communication.
      Class.forName(JdbcSnowflakeClient.EXPECTED_JDBC_IMPL);
    } catch (ClassNotFoundException cnfe) {
      LOG.warn(
          "Failed to load expected JDBC SnowflakeDriver - if queries fail by failing"
              + " to find a suitable driver for jdbc:snowflake:// URIs, you must add the Snowflake "
              + " JDBC driver to your jars/packages",
          cnfe);
    }

    // The uniqueAppIdentifier should be less than 50 characters, so trimming the guid.
    String uniqueId = UUID.randomUUID().toString().replace("-", "").substring(0, UNIQUE_ID_LENGTH);
    String uniqueAppIdentifier = APP_IDENTIFIER + "_" + uniqueId;
    String userAgentSuffix = IcebergBuild.fullVersion() + " " + uniqueAppIdentifier;
    // Populate application identifier in jdbc client
    properties.put(JdbcCatalog.PROPERTY_PREFIX + JDBC_APPLICATION_PROPERTY, uniqueAppIdentifier);
    // Adds application identifier to the user agent header of the JDBC requests.
    properties.put(JdbcCatalog.PROPERTY_PREFIX + JDBC_USER_AGENT_SUFFIX_PROPERTY, userAgentSuffix);

    JdbcClientPool connectionPool = new JdbcClientPool(uri, properties);

    initialize(name, new JdbcSnowflakeClient(connectionPool), new FileIOFactory(), properties);
  }

  /**
   * 使用调用方提供的 {@link SnowflakeClient} 与 {@link FileIOFactory} 初始化 Catalog 的替代入口， 主要用于测试注入。
   *
   * @param name Catalog 名称，为 null 时默认 "snowflake_catalog"
   * @param snowflakeClient 封装与 Snowflake 网络通信的客户端
   * @param fileIOFactory 用于为每个表操作创建 FileIO 的工厂
   * @param properties Catalog 配置选项
   */
  @SuppressWarnings("checkstyle:HiddenField")
  void initialize(
      String name,
      SnowflakeClient snowflakeClient,
      FileIOFactory fileIOFactory,
      Map<String, String> properties) {
    Preconditions.checkArgument(null != snowflakeClient, "snowflakeClient must be non-null");
    Preconditions.checkArgument(null != fileIOFactory, "fileIOFactory must be non-null");
    this.catalogName = name == null ? DEFAULT_CATALOG_NAME : name;
    this.snowflakeClient = snowflakeClient;
    this.fileIOFactory = fileIOFactory;
    this.catalogProperties = properties;
    this.closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(snowflakeClient);
    closeableGroup.setSuppressCloseFailure(true);
  }

  /** 关闭 Catalog 及其持有的 snowflakeClient 等资源。 */
  @Override
  public void close() throws IOException {
    if (null != closeableGroup) {
      closeableGroup.close();
    }
  }

  /** 当前不支持创建命名空间，抛出 {@link UnsupportedOperationException}。 */
  @Override
  public void createNamespace(Namespace namespace, Map<String, String> metadata) {
    throw new UnsupportedOperationException(
        "SnowflakeCatalog does not currently support createNamespace");
  }

  /**
   * 列出指定命名空间下的子命名空间：ROOT 级别列出所有 database，DATABASE 级别列出其下所有 schema。
   *
   * @param namespace 命名空间，必须解析到 ROOT 或 DATABASE 级别
   * @return 子命名空间列表
   * @throws IllegalArgumentException namespace 未解析到 ROOT 或 DATABASE 级别
   */
  @Override
  public List<Namespace> listNamespaces(Namespace namespace) {
    SnowflakeIdentifier scope = NamespaceHelpers.toSnowflakeIdentifier(namespace);
    List<SnowflakeIdentifier> results = null;
    switch (scope.type()) {
      case ROOT:
        results = snowflakeClient.listDatabases();
        break;
      case DATABASE:
        results = snowflakeClient.listSchemas(scope);
        break;
      default:
        throw new IllegalArgumentException(
            String.format(
                "listNamespaces must be at either ROOT or DATABASE level; got %s from namespace %s",
                scope, namespace));
    }

    return results.stream().map(NamespaceHelpers::toIcebergNamespace).collect(Collectors.toList());
  }

  /**
   * 加载命名空间元数据：检查 database 或 schema 是否存在，存在则返回空映射（Snowflake 不暴露额外属性）。
   *
   * @param namespace 命名空间，必须解析到 DATABASE 或 SCHEMA 级别
   * @return 空映射（当前不返回任何属性）
   * @throws NoSuchNamespaceException 命名空间不存在
   * @throws IllegalArgumentException namespace 未解析到 DATABASE 或 SCHEMA 级别
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace namespace)
      throws NoSuchNamespaceException {
    SnowflakeIdentifier id = NamespaceHelpers.toSnowflakeIdentifier(namespace);
    boolean namespaceExists;
    switch (id.type()) {
      case DATABASE:
        namespaceExists = snowflakeClient.databaseExists(id);
        break;
      case SCHEMA:
        namespaceExists = snowflakeClient.schemaExists(id);
        break;
      default:
        throw new IllegalArgumentException(
            String.format(
                "loadNamespaceMetadata must be at either DATABASE or SCHEMA level; got %s from namespace %s",
                id, namespace));
    }
    if (namespaceExists) {
      return ImmutableMap.of();
    } else {
      throw new NoSuchNamespaceException(
          "Namespace '%s' with snowflake identifier '%s' doesn't exist", namespace, id);
    }
  }

  /** 当前不支持删除命名空间，抛出 {@link UnsupportedOperationException}。 */
  @Override
  public boolean dropNamespace(Namespace namespace) {
    throw new UnsupportedOperationException(
        "SnowflakeCatalog does not currently support dropNamespace");
  }

  /** 当前不支持设置命名空间属性，抛出 {@link UnsupportedOperationException}。 */
  @Override
  public boolean setProperties(Namespace namespace, Map<String, String> properties) {
    throw new UnsupportedOperationException(
        "SnowflakeCatalog does not currently support setProperties");
  }

  /** 当前不支持移除命名空间属性，抛出 {@link UnsupportedOperationException}。 */
  @Override
  public boolean removeProperties(Namespace namespace, Set<String> properties) {
    throw new UnsupportedOperationException(
        "SnowflakeCatalog does not currently support removeProperties");
  }

  /**
   * 为指定表标识符构造 {@link SnowflakeTableOperations}。
   *
   * <p>逻辑：按配置选择 FileIO 实现（默认 ResolvingFileIO），每次创建独立 FileIO 实例 （因 S3FileIO 等会绑定单一 bucket），注册到
   * closeableGroup 统一关闭，最后构造 {@link SnowflakeTableOperations}。
   *
   * @param tableIdentifier 表标识符
   * @return 该表的 {@link TableOperations}
   */
  @Override
  protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
    String fileIOImpl = DEFAULT_FILE_IO_IMPL;
    if (catalogProperties.containsKey(CatalogProperties.FILE_IO_IMPL)) {
      fileIOImpl = catalogProperties.get(CatalogProperties.FILE_IO_IMPL);
    }

    // Initialize a fresh FileIO for each TableOperations created, because some FileIO
    // implementations such as S3FileIO can become bound to a single S3 bucket. Additionally,
    // FileIO implementations often support only a finite set of one or more URI schemes (i.e.
    // S3FileIO only supports s3/s3a/s3n, and even ResolvingFileIO only supports the combination
    // of schemes registered for S3FileIO and HadoopFileIO). Individual catalogs may need to
    // support tables across different cloud/storage providers with disjoint FileIO implementations.
    FileIO fileIO = fileIOFactory.newFileIO(fileIOImpl, catalogProperties, conf);
    closeableGroup.addCloseable(fileIO);
    return new SnowflakeTableOperations(snowflakeClient, fileIO, catalogName, tableIdentifier);
  }

  /** 当前不支持默认仓库路径，抛出 {@link UnsupportedOperationException}（表 location 由 Snowflake 管理）。 */
  @Override
  protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
    throw new UnsupportedOperationException(
        "SnowflakeCatalog does not currently support defaultWarehouseLocation");
  }

  /** 注入 Hadoop 配置（实现 {@link Configurable}），供 FileIO 加载时使用。 */
  @Override
  public void setConf(Object conf) {
    this.conf = conf;
  }
}
