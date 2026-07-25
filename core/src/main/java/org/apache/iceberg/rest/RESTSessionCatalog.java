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
package org.apache.iceberg.rest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.MetadataTableType;
import org.apache.iceberg.MetadataTableUtils;
import org.apache.iceberg.MetadataUpdate;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.Transaction;
import org.apache.iceberg.Transactions;
import org.apache.iceberg.catalog.BaseSessionCatalog;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableCommit;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.hadoop.Configurable;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.MetricsReporters;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.rest.auth.OAuth2Properties;
import org.apache.iceberg.rest.auth.OAuth2Util;
import org.apache.iceberg.rest.auth.OAuth2Util.AuthSession;
import org.apache.iceberg.rest.requests.CommitTransactionRequest;
import org.apache.iceberg.rest.requests.CreateNamespaceRequest;
import org.apache.iceberg.rest.requests.CreateTableRequest;
import org.apache.iceberg.rest.requests.ImmutableRegisterTableRequest;
import org.apache.iceberg.rest.requests.RegisterTableRequest;
import org.apache.iceberg.rest.requests.RenameTableRequest;
import org.apache.iceberg.rest.requests.UpdateNamespacePropertiesRequest;
import org.apache.iceberg.rest.requests.UpdateTableRequest;
import org.apache.iceberg.rest.responses.ConfigResponse;
import org.apache.iceberg.rest.responses.CreateNamespaceResponse;
import org.apache.iceberg.rest.responses.GetNamespaceResponse;
import org.apache.iceberg.rest.responses.ListNamespacesResponse;
import org.apache.iceberg.rest.responses.ListTablesResponse;
import org.apache.iceberg.rest.responses.LoadTableResponse;
import org.apache.iceberg.rest.responses.OAuthTokenResponse;
import org.apache.iceberg.rest.responses.UpdateNamespacePropertiesResponse;
import org.apache.iceberg.util.EnvironmentUtil;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：基于 REST 协议的会话级 Catalog 实现，是 REST Catalog 客户端的核心。
 *
 * <p>所属模块：iceberg-core（REST Catalog 客户端核心层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link BaseSessionCatalog}，在会话语境（{@link SessionContext}）下执行表与命名空间操作。
 *   <li>管理 OAuth2 认证会话（catalog 级与表级），支持 token 刷新与多会话缓存。
 *   <li>加载表时构造 {@link RESTTableOperations}，并管理表级 {@link FileIO} 的生命周期。
 *   <li>支持指标通过 REST 上报、快照加载模式（ALL/REFS）、多表事务提交等高级特性。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>认证会话使用 Caffeine 缓存按 sessionId 隔离，过期自动停止刷新，避免连接泄漏。
 *   <li>token 刷新线程池采用 volatile + 双重检查锁的惰性初始化，减少空闲资源占用。
 *   <li>表级 FileIO 通过弱引用缓存跟踪，表对象回收时自动关闭对应 FileIO。
 *   <li>快照 REFS 模式下采用懒加载快照列表，减少大表加载开销。
 * </ul>
 *
 * <p>上下游关系：被 {@link RESTCatalog} 委托调用；依赖 {@link RESTClient}、{@link AuthSession}、 {@link
 * ResourcePaths}、{@link FileIO} 与 {@link MetricsReporter}。
 */
public class RESTSessionCatalog extends BaseSessionCatalog
    implements Configurable<Object>, Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(RESTSessionCatalog.class);
  private static final String DEFAULT_FILE_IO_IMPL = "org.apache.iceberg.io.ResolvingFileIO";
  private static final String REST_METRICS_REPORTING_ENABLED = "rest-metrics-reporting-enabled";
  private static final String REST_SNAPSHOT_LOADING_MODE = "snapshot-loading-mode";
  private static final List<String> TOKEN_PREFERENCE_ORDER =
      ImmutableList.of(
          OAuth2Properties.ID_TOKEN_TYPE,
          OAuth2Properties.ACCESS_TOKEN_TYPE,
          OAuth2Properties.JWT_TOKEN_TYPE,
          OAuth2Properties.SAML2_TOKEN_TYPE,
          OAuth2Properties.SAML1_TOKEN_TYPE);

  private final Function<Map<String, String>, RESTClient> clientBuilder;
  private final BiFunction<SessionContext, Map<String, String>, FileIO> ioBuilder;
  private Cache<String, AuthSession> sessions = null;
  private Cache<TableOperations, FileIO> fileIOCloser;
  private AuthSession catalogAuth = null;
  private boolean keepTokenRefreshed = true;
  private RESTClient client = null;
  private ResourcePaths paths = null;
  private SnapshotMode snapshotMode = null;
  private Object conf = null;
  private FileIO io = null;
  private MetricsReporter reporter = null;
  private boolean reportingViaRestEnabled;
  private CloseableGroup closeables = null;

  // a lazy thread pool for token refresh
  private volatile ScheduledExecutorService refreshExecutor = null;

  /** 快照加载模式枚举：ALL 加载全部快照，REFS 仅加载引用并按需懒加载快照列表。 */
  enum SnapshotMode {
    ALL,
    REFS;

    /**
     * 返回该模式对应的查询参数，用于在请求 load table 时告诉服务端快照返回策略。
     *
     * @return 包含 snapshots 参数的不可变 map
     */
    Map<String, String> params() {
      return ImmutableMap.of("snapshots", this.name().toLowerCase(Locale.US));
    }
  }

  /** 默认构造函数，使用默认 HTTP 客户端构建器且不提供自定义 FileIO 构建器。 */
  public RESTSessionCatalog() {
    this(config -> HTTPClient.builder(config).uri(config.get(CatalogProperties.URI)).build(), null);
  }

  /**
   * 使用指定的客户端构建器与 FileIO 构建器构造。
   *
   * @param clientBuilder 根据配置构建 {@link RESTClient} 的函数，不能为 null
   * @param ioBuilder 根据会话语境与配置构建表级 {@link FileIO} 的函数，可为 null（使用默认实现）
   */
  public RESTSessionCatalog(
      Function<Map<String, String>, RESTClient> clientBuilder,
      BiFunction<SessionContext, Map<String, String>, FileIO> ioBuilder) {
    Preconditions.checkNotNull(clientBuilder, "Invalid client builder: null");
    this.clientBuilder = clientBuilder;
    this.ioBuilder = ioBuilder;
  }

  /**
   * 初始化 REST Session Catalog，完成配置解析、认证获取、客户端与会话缓存构建。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>通过 {@link EnvironmentUtil#resolveAll} 解析环境变量占位符。
   *   <li>使用临时客户端获取 OAuth2 token（若提供 credential）与服务端配置 {@link ConfigResponse}。
   *   <li>合并服务端与本地配置，构建会话缓存、HTTP 客户端、资源路径。
   *   <li>初始化 catalog 级认证会话（来自 token 响应、access token 或无认证）。
   *   <li>构建默认 {@link FileIO}、FileIO 跟踪缓存与 {@link CloseableGroup}。
   *   <li>解析快照加载模式与指标上报开关，加载指标上报器。
   *   <li>调用父类 {@link BaseSessionCatalog#initialize} 完成最终初始化。
   * </ol>
   *
   * @param name Catalog 名称
   * @param unresolved 原始配置属性，不能为 null
   */
  @Override
  public void initialize(String name, Map<String, String> unresolved) {
    Preconditions.checkArgument(unresolved != null, "Invalid configuration: null");
    // resolve any configuration that is supplied by environment variables
    // note that this is only done for local config properties and not for properties from the
    // catalog service
    Map<String, String> props = EnvironmentUtil.resolveAll(unresolved);

    long startTimeMillis =
        System.currentTimeMillis(); // keep track of the init start time for token refresh
    String initToken = props.get(OAuth2Properties.TOKEN);

    // fetch auth and config to complete initialization
    ConfigResponse config;
    OAuthTokenResponse authResponse;
    String credential = props.get(OAuth2Properties.CREDENTIAL);
    String scope = props.getOrDefault(OAuth2Properties.SCOPE, OAuth2Properties.CATALOG_SCOPE);
    try (RESTClient initClient = clientBuilder.apply(props)) {
      Map<String, String> initHeaders =
          RESTUtil.merge(configHeaders(props), OAuth2Util.authHeaders(initToken));
      if (credential != null && !credential.isEmpty()) {
        authResponse = OAuth2Util.fetchToken(initClient, initHeaders, credential, scope);
        Map<String, String> authHeaders =
            RESTUtil.merge(initHeaders, OAuth2Util.authHeaders(authResponse.token()));
        config = fetchConfig(initClient, authHeaders, props);
      } else {
        authResponse = null;
        config = fetchConfig(initClient, initHeaders, props);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to close HTTP client", e);
    }

    // build the final configuration and set up the catalog's auth
    Map<String, String> mergedProps = config.merge(props);
    Map<String, String> baseHeaders = configHeaders(mergedProps);

    this.sessions = newSessionCache(mergedProps);
    this.keepTokenRefreshed =
        PropertyUtil.propertyAsBoolean(
            mergedProps,
            OAuth2Properties.TOKEN_REFRESH_ENABLED,
            OAuth2Properties.TOKEN_REFRESH_ENABLED_DEFAULT);
    this.client = clientBuilder.apply(mergedProps);
    this.paths = ResourcePaths.forCatalogProperties(mergedProps);

    String token = mergedProps.get(OAuth2Properties.TOKEN);
    this.catalogAuth = new AuthSession(baseHeaders, null, null, credential, scope);
    if (authResponse != null) {
      this.catalogAuth =
          AuthSession.fromTokenResponse(
              client, tokenRefreshExecutor(), authResponse, startTimeMillis, catalogAuth);
    } else if (token != null) {
      this.catalogAuth =
          AuthSession.fromAccessToken(
              client, tokenRefreshExecutor(), token, expiresAtMillis(mergedProps), catalogAuth);
    }

    this.io = newFileIO(SessionContext.createEmpty(), mergedProps);

    this.fileIOCloser = newFileIOCloser();
    this.closeables = new CloseableGroup();
    this.closeables.addCloseable(this.io);
    this.closeables.addCloseable(this.client);
    this.closeables.setSuppressCloseFailure(true);

    this.snapshotMode =
        SnapshotMode.valueOf(
            PropertyUtil.propertyAsString(
                    mergedProps, REST_SNAPSHOT_LOADING_MODE, SnapshotMode.ALL.name())
                .toUpperCase(Locale.US));

    this.reporter = CatalogUtil.loadMetricsReporter(mergedProps);

    this.reportingViaRestEnabled =
        PropertyUtil.propertyAsBoolean(mergedProps, REST_METRICS_REPORTING_ENABLED, true);
    super.initialize(name, mergedProps);
  }

  /**
   * 获取指定会话语境对应的认证会话，若缓存未命中则基于 catalog 级认证新建并缓存。
   *
   * @param context 会话语境
   * @return 会话级 {@link AuthSession}，新建失败时回退到 catalog 级认证
   */
  private AuthSession session(SessionContext context) {
    AuthSession session =
        sessions.get(
            context.sessionId(),
            id -> newSession(context.credentials(), context.properties(), catalogAuth));

    return session != null ? session : catalogAuth;
  }

  /**
   * 返回会话语境对应的请求头供应器（绑定到会话认证头）。
   *
   * @param context 会话语境
   * @return 请求头 map 的供应器
   */
  private Supplier<Map<String, String>> headers(SessionContext context) {
    return session(context)::headers;
  }

  /** 设置底层 Hadoop 配置，用于 FileIO 加载。 */
  @Override
  public void setConf(Object newConf) {
    this.conf = newConf;
  }

  /**
   * 列出指定命名空间下的所有表。
   *
   * @param context 会话语境
   * @param ns 命名空间，不能为空
   * @return 表标识符列表
   */
  @Override
  public List<TableIdentifier> listTables(SessionContext context, Namespace ns) {
    checkNamespaceIsValid(ns);

    ListTablesResponse response =
        client.get(
            paths.tables(ns),
            ListTablesResponse.class,
            headers(context),
            ErrorHandlers.namespaceErrorHandler());
    return response.identifiers();
  }

  /**
   * 删除指定表。表不存在时返回 false 而非抛异常。
   *
   * @param context 会话语境
   * @param identifier 表标识符
   * @return 删除成功返回 true，表不存在返回 false
   */
  @Override
  public boolean dropTable(SessionContext context, TableIdentifier identifier) {
    checkIdentifierIsValid(identifier);

    try {
      client.delete(
          paths.table(identifier), null, headers(context), ErrorHandlers.tableErrorHandler());
      return true;
    } catch (NoSuchTableException e) {
      return false;
    }
  }

  /**
   * 删除表并请求服务端清除底层数据文件。表不存在时返回 false。
   *
   * @param context 会话语境
   * @param identifier 表标识符
   * @return 删除成功返回 true，表不存在返回 false
   */
  @Override
  public boolean purgeTable(SessionContext context, TableIdentifier identifier) {
    checkIdentifierIsValid(identifier);

    try {
      client.delete(
          paths.table(identifier),
          ImmutableMap.of("purgeRequested", "true"),
          null,
          headers(context),
          ErrorHandlers.tableErrorHandler());
      return true;
    } catch (NoSuchTableException e) {
      return false;
    }
  }

  /**
   * 重命名表，通过 POST 请求发送 {@link RenameTableRequest}。
   *
   * @param context 会话语境
   * @param from 源表标识符
   * @param to 目标表标识符
   */
  @Override
  public void renameTable(SessionContext context, TableIdentifier from, TableIdentifier to) {
    checkIdentifierIsValid(from);
    checkIdentifierIsValid(to);

    RenameTableRequest request =
        RenameTableRequest.builder().withSource(from).withDestination(to).build();

    // for now, ignore the response because there is no way to return it
    client.post(paths.rename(), request, null, headers(context), ErrorHandlers.tableErrorHandler());
  }

  /**
   * 内部加载表方法，按指定快照模式发起 GET 请求获取 {@link LoadTableResponse}。
   *
   * @param context 会话语境
   * @param identifier 表标识符
   * @param mode 快照加载模式
   * @return 加载表响应
   */
  private LoadTableResponse loadInternal(
      SessionContext context, TableIdentifier identifier, SnapshotMode mode) {
    return client.get(
        paths.table(identifier),
        mode.params(),
        LoadTableResponse.class,
        headers(context),
        ErrorHandlers.tableErrorHandler());
  }

  /**
   * 加载表，支持元数据表（如 snapshots、history 等）自动解析与 REFS 模式懒加载快照。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>尝试按普通表加载；若抛 {@link NoSuchTableException}，则尝试把标识符末段解析为 {@link
   *       MetadataTableType}，以其命名空间作为基础表重新加载。
   *   <li>构造表级认证会话与表级 {@link FileIO}。
   *   <li>若为 REFS 模式，构建带快照懒加载供应器的 {@link TableMetadata}；否则直接使用响应元数据。
   *   <li>构造 {@link RESTTableOperations} 与 {@link BaseTable}，跟踪 FileIO 生命周期。
   *   <li>若为元数据表，通过 {@link MetadataTableUtils#createMetadataTableInstance} 返回对应视图。
   * </ol>
   *
   * @param context 会话语境
   * @param identifier 表标识符
   * @return 加载的 {@link Table}
   */
  @Override
  public Table loadTable(SessionContext context, TableIdentifier identifier) {
    checkIdentifierIsValid(identifier);

    MetadataTableType metadataType;
    LoadTableResponse response;
    TableIdentifier loadedIdent;
    try {
      response = loadInternal(context, identifier, snapshotMode);
      loadedIdent = identifier;
      metadataType = null;

    } catch (NoSuchTableException original) {
      metadataType = MetadataTableType.from(identifier.name());
      if (metadataType != null) {
        // attempt to load a metadata table using the identifier's namespace as the base table
        TableIdentifier baseIdent = TableIdentifier.of(identifier.namespace().levels());
        try {
          response = loadInternal(context, baseIdent, snapshotMode);
          loadedIdent = baseIdent;
        } catch (NoSuchTableException ignored) {
          // the base table does not exist
          throw original;
        }
      } else {
        // name is not a metadata table
        throw original;
      }
    }

    TableIdentifier finalIdentifier = loadedIdent;
    AuthSession session = tableSession(response.config(), session(context));
    TableMetadata tableMetadata;

    if (snapshotMode == SnapshotMode.REFS) {
      tableMetadata =
          TableMetadata.buildFrom(response.tableMetadata())
              .withMetadataLocation(response.metadataLocation())
              .setPreviousFileLocation(null)
              .setSnapshotsSupplier(
                  () ->
                      loadInternal(context, finalIdentifier, SnapshotMode.ALL)
                          .tableMetadata()
                          .snapshots())
              .discardChanges()
              .build();
    } else {
      tableMetadata = response.tableMetadata();
    }

    RESTTableOperations ops =
        new RESTTableOperations(
            client,
            paths.table(finalIdentifier),
            session::headers,
            tableFileIO(context, response.config()),
            tableMetadata);

    trackFileIO(ops);

    BaseTable table =
        new BaseTable(
            ops,
            fullTableName(finalIdentifier),
            metricsReporter(paths.metrics(finalIdentifier), session::headers));
    if (metadataType != null) {
      return MetadataTableUtils.createMetadataTableInstance(table, metadataType);
    }

    return table;
  }

  /**
   * 跟踪表级 FileIO 生命周期。当 ops 使用的 FileIO 与 catalog 默认 io 不同时， 将其加入弱引用缓存，以便表对象回收时自动关闭对应 FileIO。
   *
   * @param ops 表操作对象
   */
  private void trackFileIO(RESTTableOperations ops) {
    if (io != ops.io()) {
      fileIOCloser.put(ops, ops.io());
    }
  }

  /**
   * 构造指标上报器。若启用 REST 上报，则将用户自定义上报器与 {@link RESTMetricsReporter} 组合； 否则仅返回用户自定义上报器。
   *
   * @param metricsEndpoint 指标上报 REST 路径
   * @param headers 请求头供应器
   * @return 组合后的指标上报器
   */
  private MetricsReporter metricsReporter(
      String metricsEndpoint, Supplier<Map<String, String>> headers) {
    if (reportingViaRestEnabled) {
      RESTMetricsReporter restMetricsReporter =
          new RESTMetricsReporter(client, metricsEndpoint, headers);
      return MetricsReporters.combine(reporter, restMetricsReporter);
    } else {
      return this.reporter;
    }
  }

  /**
   * 创建表构建器，返回内部 {@link Builder} 实例。
   *
   * @param context 会话语境
   * @param identifier 表标识符
   * @param schema 表 schema
   * @return 表构建器
   */
  @Override
  public Catalog.TableBuilder buildTable(
      SessionContext context, TableIdentifier identifier, Schema schema) {
    return new Builder(identifier, schema, context);
  }

  /**
   * 失效表缓存。当前实现为空，因为 REST Catalog 不缓存表元数据。
   *
   * @param context 会话语境
   * @param ident 表标识符
   */
  @Override
  public void invalidateTable(SessionContext context, TableIdentifier ident) {}

  /**
   * 通过已有的元数据文件位置注册表到 REST Catalog。
   *
   * <p>逻辑：校验标识符与元数据文件位置，发送 {@link RegisterTableRequest} 到 register 端点， 然后构造表级会话、{@link
   * RESTTableOperations} 与 {@link BaseTable}。
   *
   * @param context 会话语境
   * @param ident 表标识符
   * @param metadataFileLocation 元数据文件位置
   * @return 注册后的 {@link Table}
   */
  @Override
  public Table registerTable(
      SessionContext context, TableIdentifier ident, String metadataFileLocation) {
    checkIdentifierIsValid(ident);

    Preconditions.checkArgument(
        metadataFileLocation != null && !metadataFileLocation.isEmpty(),
        "Invalid metadata file location: %s",
        metadataFileLocation);

    RegisterTableRequest request =
        ImmutableRegisterTableRequest.builder()
            .name(ident.name())
            .metadataLocation(metadataFileLocation)
            .build();

    LoadTableResponse response =
        client.post(
            paths.register(ident.namespace()),
            request,
            LoadTableResponse.class,
            headers(context),
            ErrorHandlers.tableErrorHandler());

    AuthSession session = tableSession(response.config(), session(context));
    RESTTableOperations ops =
        new RESTTableOperations(
            client,
            paths.table(ident),
            session::headers,
            tableFileIO(context, response.config()),
            response.tableMetadata());

    trackFileIO(ops);

    return new BaseTable(
        ops, fullTableName(ident), metricsReporter(paths.metrics(ident), session::headers));
  }

  /**
   * 创建命名空间并设置元数据属性。
   *
   * @param context 会话语境
   * @param namespace 命名空间
   * @param metadata 命名空间元数据属性
   */
  @Override
  public void createNamespace(
      SessionContext context, Namespace namespace, Map<String, String> metadata) {
    CreateNamespaceRequest request =
        CreateNamespaceRequest.builder().withNamespace(namespace).setProperties(metadata).build();

    // for now, ignore the response because there is no way to return it
    client.post(
        paths.namespaces(),
        request,
        CreateNamespaceResponse.class,
        headers(context),
        ErrorHandlers.namespaceErrorHandler());
  }

  /**
   * 列出指定父命名空间下的子命名空间。父命名空间为空时列出顶层命名空间。
   *
   * @param context 会话语境
   * @param namespace 父命名空间
   * @return 子命名空间列表
   */
  @Override
  public List<Namespace> listNamespaces(SessionContext context, Namespace namespace) {
    Map<String, String> queryParams;
    if (namespace.isEmpty()) {
      queryParams = ImmutableMap.of();
    } else {
      // query params should be unescaped
      queryParams = ImmutableMap.of("parent", RESTUtil.NAMESPACE_JOINER.join(namespace.levels()));
    }

    ListNamespacesResponse response =
        client.get(
            paths.namespaces(),
            queryParams,
            ListNamespacesResponse.class,
            headers(context),
            ErrorHandlers.namespaceErrorHandler());
    return response.namespaces();
  }

  /**
   * 加载命名空间元数据属性。
   *
   * @param context 会话语境
   * @param ns 命名空间，不能为空
   * @return 命名空间属性 map
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(SessionContext context, Namespace ns) {
    checkNamespaceIsValid(ns);

    // TODO: rename to LoadNamespaceResponse?
    GetNamespaceResponse response =
        client.get(
            paths.namespace(ns),
            GetNamespaceResponse.class,
            headers(context),
            ErrorHandlers.namespaceErrorHandler());
    return response.properties();
  }

  /**
   * 删除命名空间。命名空间不存在时返回 false。
   *
   * @param context 会话语境
   * @param ns 命名空间，不能为空
   * @return 删除成功返回 true，不存在返回 false
   */
  @Override
  public boolean dropNamespace(SessionContext context, Namespace ns) {
    checkNamespaceIsValid(ns);

    try {
      client.delete(
          paths.namespace(ns), null, headers(context), ErrorHandlers.namespaceErrorHandler());
      return true;
    } catch (NoSuchNamespaceException e) {
      return false;
    }
  }

  /**
   * 更新命名空间元数据属性，包括设置与移除。
   *
   * @param context 会话语境
   * @param ns 命名空间
   * @param updates 待设置的键值对
   * @param removals 待移除的键集合
   * @return 是否有属性被更新
   */
  @Override
  public boolean updateNamespaceMetadata(
      SessionContext context, Namespace ns, Map<String, String> updates, Set<String> removals) {
    checkNamespaceIsValid(ns);

    UpdateNamespacePropertiesRequest request =
        UpdateNamespacePropertiesRequest.builder().updateAll(updates).removeAll(removals).build();

    UpdateNamespacePropertiesResponse response =
        client.post(
            paths.namespaceProperties(ns),
            request,
            UpdateNamespacePropertiesResponse.class,
            headers(context),
            ErrorHandlers.namespaceErrorHandler());

    return !response.updated().isEmpty();
  }

  /**
   * 获取 token 刷新线程池，采用双重检查锁惰性初始化。若未启用 token 刷新则返回 null。
   *
   * @return 调度线程池，禁用刷新时为 null
   */
  private ScheduledExecutorService tokenRefreshExecutor() {
    if (!keepTokenRefreshed) {
      return null;
    }

    if (refreshExecutor == null) {
      synchronized (this) {
        if (refreshExecutor == null) {
          this.refreshExecutor = ThreadPools.newScheduledPool(name() + "-token-refresh", 1);
        }
      }
    }

    return refreshExecutor;
  }

  /**
   * 关闭 Catalog，依次停止刷新线程池、关闭 closeables（客户端与默认 IO）、失效并清理 FileIO 缓存。
   *
   * @throws IOException 关闭过程中发生的 IO 异常
   */
  @Override
  public void close() throws IOException {
    shutdownRefreshExecutor();

    if (closeables != null) {
      closeables.close();
    }

    if (fileIOCloser != null) {
      fileIOCloser.invalidateAll();
      fileIOCloser.cleanUp();
    }
  }

  /**
   * 关闭 token 刷新线程池，取消待执行任务并等待最多 1 分钟终止。
   *
   * <p>逻辑：先将引用置 null 避免重复关闭，调用 shutdownNow 取消任务，对 Future 任务显式 cancel， 然后等待终止，超时或中断时打印告警日志。
   */
  private void shutdownRefreshExecutor() {
    if (refreshExecutor != null) {
      ScheduledExecutorService service = refreshExecutor;
      this.refreshExecutor = null;

      List<Runnable> tasks = service.shutdownNow();
      tasks.forEach(
          task -> {
            if (task instanceof Future) {
              ((Future<?>) task).cancel(true);
            }
          });

      try {
        if (service.awaitTermination(1, TimeUnit.MINUTES)) {
          LOG.warn("Timed out waiting for refresh executor to terminate");
        }
      } catch (InterruptedException e) {
        LOG.warn("Interrupted while waiting for refresh executor to terminate", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  /** 表构建器内部实现，用于收集建表/替换表参数并创建表或对应事务。 */
  private class Builder implements Catalog.TableBuilder {
    private final TableIdentifier ident;
    private final Schema schema;
    private final SessionContext context;
    private final ImmutableMap.Builder<String, String> propertiesBuilder = ImmutableMap.builder();
    private PartitionSpec spec = null;
    private SortOrder writeOrder = null;
    private String location = null;

    /**
     * 构造表构建器，校验标识符有效性后保存基本参数。
     *
     * @param ident 表标识符
     * @param schema 表 schema
     * @param context 会话语境
     */
    private Builder(TableIdentifier ident, Schema schema, SessionContext context) {
      checkIdentifierIsValid(ident);

      this.ident = ident;
      this.schema = schema;
      this.context = context;
    }

    /** 设置分区规格。 */
    @Override
    public Builder withPartitionSpec(PartitionSpec tableSpec) {
      this.spec = tableSpec;
      return this;
    }

    /** 设置排序顺序。 */
    @Override
    public Builder withSortOrder(SortOrder tableWriteOrder) {
      this.writeOrder = tableWriteOrder;
      return this;
    }

    /** 设置表存储位置。 */
    @Override
    public Builder withLocation(String tableLocation) {
      this.location = tableLocation;
      return this;
    }

    /** 批量设置表属性。 */
    @Override
    public Builder withProperties(Map<String, String> props) {
      if (props != null) {
        this.propertiesBuilder.putAll(props);
      }
      return this;
    }

    /** 设置单个表属性。 */
    @Override
    public Builder withProperty(String key, String value) {
      this.propertiesBuilder.put(key, value);
      return this;
    }

    /**
     * 创建表，发送 {@link CreateTableRequest} 到服务端并返回 {@link BaseTable}。
     *
     * <p>逻辑：构建请求并 POST 到 tables 端点，获取响应后构造表级会话、 {@link RESTTableOperations} 与 {@link BaseTable}，并跟踪
     * FileIO。
     *
     * @return 新创建的表
     */
    @Override
    public Table create() {
      CreateTableRequest request =
          CreateTableRequest.builder()
              .withName(ident.name())
              .withSchema(schema)
              .withPartitionSpec(spec)
              .withWriteOrder(writeOrder)
              .withLocation(location)
              .setProperties(propertiesBuilder.build())
              .build();

      LoadTableResponse response =
          client.post(
              paths.tables(ident.namespace()),
              request,
              LoadTableResponse.class,
              headers(context),
              ErrorHandlers.tableErrorHandler());

      AuthSession session = tableSession(response.config(), session(context));
      RESTTableOperations ops =
          new RESTTableOperations(
              client,
              paths.table(ident),
              session::headers,
              tableFileIO(context, response.config()),
              response.tableMetadata());

      trackFileIO(ops);

      return new BaseTable(
          ops, fullTableName(ident), metricsReporter(paths.metrics(ident), session::headers));
    }

    /**
     * 创建建表事务，先通过 stageCreate 在服务端暂存表，再构造 CREATE 类型的事务。
     *
     * <p>逻辑：调用 {@link #stageCreate} 获取暂存响应，构造 {@link RESTTableOperations}（CREATE 类型） 与变更列表 {@link
     * #createChanges}，最终返回 {@link Transactions#createTableTransaction}。
     *
     * @return 建表事务
     */
    @Override
    public Transaction createTransaction() {
      LoadTableResponse response = stageCreate();
      String fullName = fullTableName(ident);

      AuthSession session = tableSession(response.config(), session(context));
      TableMetadata meta = response.tableMetadata();

      RESTTableOperations ops =
          new RESTTableOperations(
              client,
              paths.table(ident),
              session::headers,
              tableFileIO(context, response.config()),
              RESTTableOperations.UpdateType.CREATE,
              createChanges(meta),
              meta);

      trackFileIO(ops);

      return Transactions.createTableTransaction(
          fullName, ops, meta, metricsReporter(paths.metrics(ident), session::headers));
    }

    /**
     * 创建替换表事务，加载现有表后构建替换元数据与变更列表。
     *
     * <p>逻辑：加载基础表，构建替换元数据 {@link TableMetadata#buildReplacement}， 补齐
     * SetCurrentSchema/SetDefaultPartitionSpec/SetDefaultSortOrder 变更（若缺失）， 构造 REPLACE 类型的 {@link
     * RESTTableOperations}，返回 {@link Transactions#replaceTableTransaction}。
     *
     * @return 替换表事务
     */
    @Override
    public Transaction replaceTransaction() {
      LoadTableResponse response = loadInternal(context, ident, snapshotMode);
      String fullName = fullTableName(ident);

      AuthSession session = tableSession(response.config(), session(context));
      TableMetadata base = response.tableMetadata();

      Map<String, String> tableProperties = propertiesBuilder.build();
      TableMetadata replacement =
          base.buildReplacement(
              schema,
              spec != null ? spec : PartitionSpec.unpartitioned(),
              writeOrder != null ? writeOrder : SortOrder.unsorted(),
              location != null ? location : base.location(),
              tableProperties);

      ImmutableList.Builder<MetadataUpdate> changes = ImmutableList.builder();

      if (replacement.changes().stream()
          .noneMatch(MetadataUpdate.SetCurrentSchema.class::isInstance)) {
        // ensure there is a change to set the current schema
        changes.add(new MetadataUpdate.SetCurrentSchema(replacement.currentSchemaId()));
      }

      if (replacement.changes().stream()
          .noneMatch(MetadataUpdate.SetDefaultPartitionSpec.class::isInstance)) {
        // ensure there is a change to set the default spec
        changes.add(new MetadataUpdate.SetDefaultPartitionSpec(replacement.defaultSpecId()));
      }

      if (replacement.changes().stream()
          .noneMatch(MetadataUpdate.SetDefaultSortOrder.class::isInstance)) {
        // ensure there is a change to set the default sort order
        changes.add(new MetadataUpdate.SetDefaultSortOrder(replacement.defaultSortOrderId()));
      }

      RESTTableOperations ops =
          new RESTTableOperations(
              client,
              paths.table(ident),
              session::headers,
              tableFileIO(context, response.config()),
              RESTTableOperations.UpdateType.REPLACE,
              changes.build(),
              base);

      trackFileIO(ops);

      return Transactions.replaceTableTransaction(
          fullName, ops, replacement, metricsReporter(paths.metrics(ident), session::headers));
    }

    /**
     * 创建或替换表事务。先尝试替换，表不存在则改为创建。
     *
     * <p>设计要点：create 与 replace 在 schema field ID 分配上不同，必须在写入前确定， 因此在客户端判断表是否存在后再决定走哪种事务。
     *
     * @return 建表或替换表事务
     */
    @Override
    public Transaction createOrReplaceTransaction() {
      // return a create or a replace transaction, depending on whether the table exists
      // deciding whether to create or replace can't be determined on the service because schema
      // field IDs are assigned
      // at this point and then used in data and metadata files. because create and replace will
      // assign different
      // field IDs, they must be determined before any writes occur
      try {
        return replaceTransaction();
      } catch (NoSuchTableException e) {
        return createTransaction();
      }
    }

    /**
     * 在服务端暂存建表请求（stageCreate），返回服务端分配了 schema field ID 的表响应。
     *
     * <p>逻辑：构建带 stageCreate 标志的 {@link CreateTableRequest} 并 POST 到 tables 端点。
     *
     * @return 暂存的加载表响应
     */
    private LoadTableResponse stageCreate() {
      Map<String, String> tableProperties = propertiesBuilder.build();

      CreateTableRequest request =
          CreateTableRequest.builder()
              .stageCreate()
              .withName(ident.name())
              .withSchema(schema)
              .withPartitionSpec(spec)
              .withWriteOrder(writeOrder)
              .withLocation(location)
              .setProperties(tableProperties)
              .build();

      return client.post(
          paths.tables(ident.namespace()),
          request,
          LoadTableResponse.class,
          headers(context),
          ErrorHandlers.tableErrorHandler());
    }
  }

  /**
   * 根据暂存表元数据生成建表变更列表，包含 UUID、格式版本、schema、分区规格、排序顺序、位置与属性。
   *
   * @param meta 暂存的表元数据
   * @return 建表变更列表
   */
  private static List<MetadataUpdate> createChanges(TableMetadata meta) {
    ImmutableList.Builder<MetadataUpdate> changes = ImmutableList.builder();

    changes.add(new MetadataUpdate.AssignUUID(meta.uuid()));
    changes.add(new MetadataUpdate.UpgradeFormatVersion(meta.formatVersion()));

    Schema schema = meta.schema();
    changes.add(new MetadataUpdate.AddSchema(schema, schema.highestFieldId()));
    changes.add(new MetadataUpdate.SetCurrentSchema(-1));

    PartitionSpec spec = meta.spec();
    if (spec != null && spec.isPartitioned()) {
      changes.add(new MetadataUpdate.AddPartitionSpec(spec));
    } else {
      changes.add(new MetadataUpdate.AddPartitionSpec(PartitionSpec.unpartitioned()));
    }
    changes.add(new MetadataUpdate.SetDefaultPartitionSpec(-1));

    SortOrder order = meta.sortOrder();
    if (order != null && order.isSorted()) {
      changes.add(new MetadataUpdate.AddSortOrder(order));
    } else {
      changes.add(new MetadataUpdate.AddSortOrder(SortOrder.unsorted()));
    }
    changes.add(new MetadataUpdate.SetDefaultSortOrder(-1));

    String location = meta.location();
    if (location != null) {
      changes.add(new MetadataUpdate.SetLocation(location));
    }

    Map<String, String> properties = meta.properties();
    if (properties != null && !properties.isEmpty()) {
      changes.add(new MetadataUpdate.SetProperties(properties));
    }

    return changes.build();
  }

  /**
   * 拼接全限定表名（catalog名.表标识符）。
   *
   * @param ident 表标识符
   * @return 全限定表名字符串
   */
  private String fullTableName(TableIdentifier ident) {
    return String.format("%s.%s", name(), ident);
  }

  /**
   * 创建 FileIO 实例。优先使用自定义 ioBuilder，否则按配置加载默认实现（如 ResolvingFileIO）。
   *
   * @param context 会话语境
   * @param properties 配置属性
   * @return FileIO 实例
   */
  private FileIO newFileIO(SessionContext context, Map<String, String> properties) {
    if (null != ioBuilder) {
      return ioBuilder.apply(context, properties);
    } else {
      String ioImpl = properties.getOrDefault(CatalogProperties.FILE_IO_IMPL, DEFAULT_FILE_IO_IMPL);
      return CatalogUtil.loadFileIO(ioImpl, properties, conf);
    }
  }

  /**
   * 获取表级 FileIO。若表配置为空且无自定义 ioBuilder，复用 catalog 默认 io；否则合并配置后新建。
   *
   * @param context 会话语境
   * @param config 表级配置
   * @return 表级 FileIO
   */
  private FileIO tableFileIO(SessionContext context, Map<String, String> config) {
    if (config.isEmpty() && ioBuilder == null) {
      return io; // reuse client and io since config is the same
    }

    Map<String, String> fullConf = RESTUtil.merge(properties(), config);

    return newFileIO(context, fullConf);
  }

  /**
   * 构造表级认证会话。基于表配置与父会话新建会话，新建失败时回退到父会话。
   *
   * @param tableConf 表级配置（同时作为凭证与属性来源）
   * @param parent 父认证会话
   * @return 表级认证会话
   */
  private AuthSession tableSession(Map<String, String> tableConf, AuthSession parent) {
    AuthSession session = newSession(tableConf, tableConf, parent);

    return session != null ? session : parent;
  }

  /**
   * 从服务端获取 Catalog 配置 {@link ConfigResponse}，并将客户端 warehouse 位置作为查询参数同步给服务端。
   *
   * @param client HTTP 客户端
   * @param headers 请求头
   * @param properties 配置属性
   * @return 校验后的配置响应
   */
  private static ConfigResponse fetchConfig(
      RESTClient client, Map<String, String> headers, Map<String, String> properties) {
    // send the client's warehouse location to the service to keep in sync
    // this is needed for cases where the warehouse is configured client side, but may be used on
    // the server side,
    // like the Hive Metastore, where both client and service hive-site.xml may have a warehouse
    // location.
    ImmutableMap.Builder<String, String> queryParams = ImmutableMap.builder();
    if (properties.containsKey(CatalogProperties.WAREHOUSE_LOCATION)) {
      queryParams.put(
          CatalogProperties.WAREHOUSE_LOCATION,
          properties.get(CatalogProperties.WAREHOUSE_LOCATION));
    }

    ConfigResponse configResponse =
        client.get(
            ResourcePaths.config(),
            queryParams.build(),
            ConfigResponse.class,
            headers,
            ErrorHandlers.defaultErrorHandler());
    configResponse.validate();
    return configResponse;
  }

  /**
   * 根据凭证创建认证会话，支持三种方式并按优先级选择：
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若含 bearer token，直接用 access token 方式（不交换）。
   *   <li>若含 credential，用 client credentials 流程获取 token。
   *   <li>否则按 {@link #TOKEN_PREFERENCE_ORDER} 顺序尝试 token exchange。
   *   <li>以上都不满足时返回 null。
   * </ol>
   *
   * @param credentials 凭证 map
   * @param properties 属性 map（用于读取 token 过期时间）
   * @param parent 父认证会话
   * @return 新建的认证会话，无凭证时返回 null
   */
  private AuthSession newSession(
      Map<String, String> credentials, Map<String, String> properties, AuthSession parent) {
    if (credentials != null) {
      // use the bearer token without exchanging
      if (credentials.containsKey(OAuth2Properties.TOKEN)) {
        return AuthSession.fromAccessToken(
            client,
            tokenRefreshExecutor(),
            credentials.get(OAuth2Properties.TOKEN),
            expiresAtMillis(properties),
            parent);
      }

      if (credentials.containsKey(OAuth2Properties.CREDENTIAL)) {
        // fetch a token using the client credentials flow
        return AuthSession.fromCredential(
            client, tokenRefreshExecutor(), credentials.get(OAuth2Properties.CREDENTIAL), parent);
      }

      for (String tokenType : TOKEN_PREFERENCE_ORDER) {
        if (credentials.containsKey(tokenType)) {
          // exchange the token for an access token using the token exchange flow
          return AuthSession.fromTokenExchange(
              client, tokenRefreshExecutor(), credentials.get(tokenType), tokenType, parent);
        }
      }
    }

    return null;
  }

  /**
   * 计算 token 过期时间戳。若配置了过期时长，返回当前时间加上该时长；否则返回 null。
   *
   * @param properties 配置属性
   * @return 过期时间戳（毫秒），未配置时为 null
   */
  private Long expiresAtMillis(Map<String, String> properties) {
    if (properties.containsKey(OAuth2Properties.TOKEN_EXPIRES_IN_MS)) {
      long expiresInMillis =
          PropertyUtil.propertyAsLong(
              properties,
              OAuth2Properties.TOKEN_EXPIRES_IN_MS,
              OAuth2Properties.TOKEN_EXPIRES_IN_MS_DEFAULT);
      return System.currentTimeMillis() + expiresInMillis;
    } else {
      return null;
    }
  }

  /**
   * 校验表标识符有效性，命名空间不能为空。
   *
   * @param tableIdentifier 表标识符
   * @throws NoSuchTableException 命名空间为空时抛出
   */
  private void checkIdentifierIsValid(TableIdentifier tableIdentifier) {
    if (tableIdentifier.namespace().isEmpty()) {
      throw new NoSuchTableException("Invalid table identifier: %s", tableIdentifier);
    }
  }

  /**
   * 校验命名空间有效性，不能为空。
   *
   * @param namespace 命名空间
   * @throws NoSuchNamespaceException 命名空间为空时抛出
   */
  private void checkNamespaceIsValid(Namespace namespace) {
    if (namespace.isEmpty()) {
      throw new NoSuchNamespaceException("Invalid namespace: %s", namespace);
    }
  }

  /**
   * 从配置属性中提取以 "header." 为前缀的请求头 map，并去掉前缀。
   *
   * @param properties 配置属性
   * @return 请求头 map
   */
  private static Map<String, String> configHeaders(Map<String, String> properties) {
    return RESTUtil.extractPrefixMap(properties, "header.");
  }

  /**
   * 创建认证会话缓存，按访问时间过期，过期或移除时停止 token 刷新。
   *
   * @param properties 配置属性
   * @return Caffeine 缓存
   */
  private static Cache<String, AuthSession> newSessionCache(Map<String, String> properties) {
    long expirationIntervalMs =
        PropertyUtil.propertyAsLong(
            properties,
            CatalogProperties.AUTH_SESSION_TIMEOUT_MS,
            CatalogProperties.AUTH_SESSION_TIMEOUT_MS_DEFAULT);

    return Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMillis(expirationIntervalMs))
        .removalListener(
            (RemovalListener<String, AuthSession>) (id, auth, cause) -> auth.stopRefreshing())
        .build();
  }

  /**
   * 创建表级 FileIO 跟踪缓存，使用弱引用 key，表对象回收时自动关闭对应 FileIO。
   *
   * @return Caffeine 缓存
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

  /**
   * 提交多表事务，将多个 {@link TableCommit} 转换为 {@link UpdateTableRequest} 列表后 通过 {@link
   * CommitTransactionRequest} 一次性 POST 到服务端。
   *
   * @param context 会话语境
   * @param commits 多表提交变更列表
   */
  public void commitTransaction(SessionContext context, List<TableCommit> commits) {
    List<UpdateTableRequest> tableChanges = Lists.newArrayListWithCapacity(commits.size());

    for (TableCommit commit : commits) {
      tableChanges.add(
          UpdateTableRequest.create(commit.identifier(), commit.requirements(), commit.updates()));
    }

    client.post(
        paths.commitTransaction(),
        new CommitTransactionRequest(tableChanges),
        null,
        headers(context),
        ErrorHandlers.tableCommitHandler());
  }
}
