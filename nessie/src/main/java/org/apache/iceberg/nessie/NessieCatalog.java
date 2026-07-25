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
package org.apache.iceberg.nessie;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.hadoop.Configurable;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.projectnessie.client.NessieClientBuilder;
import org.projectnessie.client.NessieConfigConstants;
import org.projectnessie.client.api.NessieApiV1;
import org.projectnessie.client.api.NessieApiV2;
import org.projectnessie.client.config.NessieClientConfigSource;
import org.projectnessie.client.config.NessieClientConfigSources;
import org.projectnessie.client.http.HttpClientBuilder;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.TableReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Nessie 的 Iceberg Catalog 实现。
 *
 * <p>所属模块：iceberg-nessie。职责：把 Iceberg 表元数据指针（metadata.json 的 location） 作为 Nessie 的 {@code
 * IcebergTable} 内容条目存储，借助 Nessie 的分支/标签（branch/tag）引用 模型实现"引用感知"的表管理——可在不同分支上独立提交、回滚与时间旅行。
 *
 * <p>设计意图：继承 {@link BaseMetastoreCatalog} 复用"元数据文件存 FS、指针存 Nessie"的模板， 仅实现 {@code
 * newTableOps}/{@code defaultWarehouseLocation} 等少量抽象方法；通过 Nessie API v1/v2 双协议适配、{@code
 * Configurable} 注入 Hadoop 配置、{@code CloseableGroup} 管理资源关闭， 兼容多运行环境。默认关闭 GC（{@code
 * gc.enabled=false}），因为 Nessie 自身管理文件生命周期。
 *
 * <p>上下游：向上被引擎（Spark/Flink 等）通过 {@code Catalog} 接口调用；向下依赖 Nessie Client 与 {@link FileIO}（读写元数据文件）。
 */
public class NessieCatalog extends BaseMetastoreCatalog
    implements AutoCloseable, SupportsNamespaces, Configurable<Object> {

  private static final Logger LOG = LoggerFactory.getLogger(NessieCatalog.class);
  private static final Joiner SLASH = Joiner.on("/");
  private static final String NAMESPACE_LOCATION_PROPS = "location";

  private static final Map<String, String> DEFAULT_CATALOG_OPTIONS =
      ImmutableMap.<String, String>builder()
          .put(CatalogProperties.TABLE_DEFAULT_PREFIX + TableProperties.GC_ENABLED, "false")
          .put(
              CatalogProperties.TABLE_DEFAULT_PREFIX
                  + TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED,
              "false") // just in case METADATA_DELETE_AFTER_COMMIT_ENABLED_DEFAULT changes
          .build();

  private NessieIcebergClient client;
  private String warehouseLocation;
  private Object config;
  private String name;
  private FileIO fileIO;
  private Map<String, String> catalogOptions = DEFAULT_CATALOG_OPTIONS;
  private CloseableGroup closeableGroup;

  /** 无参构造，用于动态加载 Catalog，字段由 {@link #initialize} 初始化。 */
  public NessieCatalog() {}

  /**
   * 根据配置初始化 Catalog：加载 FileIO、解析 Nessie 引用（ref/hash）、按 API 版本构建 Nessie 客户端。
   *
   * <p>逻辑：读取 fileIOImpl、剥离 nessie 前缀后解析 ref 与 hash；按 client-api-version（默认 1） 构建
   * NessieApiV1/V2；最后委托 {@link #initialize(String, NessieIcebergClient, FileIO, Map)} 完成初始化。
   */
  @SuppressWarnings("checkstyle:HiddenField")
  @Override
  public void initialize(String name, Map<String, String> options) {
    Map<String, String> catalogOptions = ImmutableMap.copyOf(options);
    String fileIOImpl =
        options.getOrDefault(
            CatalogProperties.FILE_IO_IMPL, "org.apache.iceberg.hadoop.HadoopFileIO");
    // remove nessie prefix
    final Function<String, String> removePrefix =
        x -> x.replace(NessieUtil.NESSIE_CONFIG_PREFIX, "");
    final String requestedRef =
        options.get(removePrefix.apply(NessieConfigConstants.CONF_NESSIE_REF));
    String requestedHash =
        options.get(removePrefix.apply(NessieConfigConstants.CONF_NESSIE_REF_HASH));

    NessieClientConfigSource configSource =
        NessieClientConfigSources.mapConfigSource(options)
            .fallbackTo(x -> options.get(removePrefix.apply(x)));
    NessieClientBuilder nessieClientBuilder =
        NessieClientBuilder.createClientBuilderFromSystemSettings(configSource);
    // default version is set to v1.
    final String apiVersion =
        options.getOrDefault(removePrefix.apply(NessieUtil.CLIENT_API_VERSION), "1");
    NessieApiV1 api;
    switch (apiVersion) {
      case "1":
        api = nessieClientBuilder.build(NessieApiV1.class);
        break;
      case "2":
        api = nessieClientBuilder.build(NessieApiV2.class);
        break;
      default:
        throw new IllegalArgumentException(
            String.format(
                "Unsupported %s: %s. Can only be 1 or 2",
                removePrefix.apply(NessieUtil.CLIENT_API_VERSION), apiVersion));
    }

    initialize(
        name,
        new NessieIcebergClient(api, requestedRef, requestedHash, catalogOptions),
        CatalogUtil.loadFileIO(fileIOImpl, options, config),
        catalogOptions);
  }

  /**
   * 使用预配置的 {@link NessieIcebergClient} 与 {@link FileIO} 初始化 Catalog 的替代入口。
   *
   * @param name Catalog 名称，为 null 时默认 "nessie"
   * @param client 预配置的 {@link NessieIcebergClient}
   * @param fileIO {@link FileIO} 实例
   * @param catalogOptions Catalog 选项
   */
  @SuppressWarnings("checkstyle:HiddenField")
  public void initialize(
      String name, NessieIcebergClient client, FileIO fileIO, Map<String, String> catalogOptions) {
    this.name = name == null ? "nessie" : name;
    this.client = Preconditions.checkNotNull(client, "client must be non-null");
    this.fileIO = Preconditions.checkNotNull(fileIO, "fileIO must be non-null");
    this.catalogOptions =
        ImmutableMap.<String, String>builder()
            .putAll(DEFAULT_CATALOG_OPTIONS)
            .putAll(Preconditions.checkNotNull(catalogOptions, "catalogOptions must be non-null"))
            .buildKeepingLast();
    this.warehouseLocation = validateWarehouseLocation(name, catalogOptions);
    this.closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(client);
    closeableGroup.addCloseable(fileIO);
    closeableGroup.setSuppressCloseFailure(true);
  }

  /** 校验 warehouse location 必须配置，否则告警并抛出 IllegalStateException。 */
  @SuppressWarnings("checkstyle:HiddenField")
  private String validateWarehouseLocation(String name, Map<String, String> catalogOptions) {
    String warehouseLocation = catalogOptions.get(CatalogProperties.WAREHOUSE_LOCATION);
    if (warehouseLocation == null) {
      // Explicitly log a warning, otherwise the thrown exception can get list in the "silent-ish
      // catch"
      // in o.a.i.spark.Spark3Util.catalogAndIdentifier(o.a.s.sql.SparkSession, List<String>,
      //     o.a.s.sql.connector.catalog.CatalogPlugin)
      // in the code block
      //    Pair<CatalogPlugin, Identifier> catalogIdentifier =
      // SparkUtil.catalogAndIdentifier(nameParts,
      //        catalogName ->  {
      //          try {
      //            return catalogManager.catalog(catalogName);
      //          } catch (Exception e) {
      //            return null;
      //          }
      //        },
      //        Identifier::of,
      //        defaultCatalog,
      //        currentNamespace
      //    );
      LOG.warn(
          "Catalog creation for inputName={} and options {} failed, because parameter "
              + "'warehouse' is not set, Nessie can't store data.",
          name,
          catalogOptions);
      throw new IllegalStateException("Parameter 'warehouse' not set, Nessie can't store data.");
    }
    return warehouseLocation;
  }

  /** 创建 Nessie 客户端构建器：指定自定义构建器类名则反射调用其 builder 方法，否则用默认 HttpClientBuilder。 */
  private static NessieClientBuilder createNessieClientBuilder(String customBuilder) {
    NessieClientBuilder clientBuilder;
    if (customBuilder != null) {
      try {
        clientBuilder =
            DynMethods.builder("builder").impl(customBuilder).build().asStatic().invoke();
      } catch (Exception e) {
        throw new RuntimeException(
            String.format("Failed to use custom NessieClientBuilder '%s'.", customBuilder), e);
      }
    } else {
      clientBuilder = HttpClientBuilder.builder();
    }
    return clientBuilder;
  }

  /** 关闭 Catalog 及其持有的 client 与 fileIO 资源。 */
  @Override
  public void close() throws IOException {
    if (null != closeableGroup) {
      closeableGroup.close();
    }
  }

  /** 返回 Catalog 名称。 */
  @Override
  public String name() {
    return name;
  }

  /** 为指定表标识符构造 {@link NessieTableOperations}，解析表名中的引用信息并绑定对应 Nessie 引用。 */
  @Override
  protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
    TableReference tr = parseTableReference(tableIdentifier);
    return new NessieTableOperations(
        ContentKey.of(
            org.projectnessie.model.Namespace.of(tableIdentifier.namespace().levels()),
            tr.getName()),
        client.withReference(tr.getReference(), tr.getHash()),
        fileIO,
        catalogOptions);
  }

  /** 计算表默认仓库路径：基于 warehouse 与命名空间/表名拼接，并追加 UUID 避免同名表跨引用路径冲突。 */
  @Override
  protected String defaultWarehouseLocation(TableIdentifier table) {
    String location;
    if (table.hasNamespace()) {
      String baseLocation = SLASH.join(warehouseLocation, table.namespace().toString());
      try {
        baseLocation =
            loadNamespaceMetadata(table.namespace())
                .getOrDefault(NAMESPACE_LOCATION_PROPS, baseLocation);
      } catch (NoSuchNamespaceException e) {
        // do nothing we want the same behavior that if the location is not defined
      }
      location = SLASH.join(baseLocation, table.name());
    } else {
      location = SLASH.join(warehouseLocation, table.name());
    }
    // Different tables with same table name can exist across references in Nessie.
    // To avoid sharing same table path between two tables with same name, use uuid in the table
    // path.
    return location + "_" + UUID.randomUUID();
  }

  /** 列出指定命名空间下的所有表。 */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    return client.listTables(namespace);
  }

  /** 删除表：解析表名中的引用并委托 client 执行，purge 控制是否清理数据文件。 */
  @Override
  public boolean dropTable(TableIdentifier identifier, boolean purge) {
    TableReference tableReference = parseTableReference(identifier);
    return client
        .withReference(tableReference.getReference(), tableReference.getHash())
        .dropTable(identifierWithoutTableReference(identifier, tableReference), purge);
  }

  /** 重命名表：校验源与目标引用名一致后委托 client 执行。 */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    TableReference fromTableReference = parseTableReference(from);
    TableReference toTableReference = parseTableReference(to);
    String fromReference =
        fromTableReference.hasReference()
            ? fromTableReference.getReference()
            : client.getRef().getName();
    String toReference =
        toTableReference.hasReference()
            ? toTableReference.getReference()
            : client.getRef().getName();
    Preconditions.checkArgument(
        fromReference.equalsIgnoreCase(toReference),
        "from: %s and to: %s reference name must be same",
        fromReference,
        toReference);

    client
        .withReference(fromTableReference.getReference(), fromTableReference.getHash())
        .renameTable(
            identifierWithoutTableReference(from, fromTableReference),
            NessieUtil.removeCatalogName(
                identifierWithoutTableReference(to, toTableReference), name()));
  }

  /** 创建命名空间。 */
  @Override
  public void createNamespace(Namespace namespace, Map<String, String> metadata) {
    client.createNamespace(namespace, metadata);
  }

  /** 列出指定命名空间下的子命名空间。 */
  @Override
  public List<Namespace> listNamespaces(Namespace namespace) throws NoSuchNamespaceException {
    return client.listNamespaces(namespace);
  }

  /**
   * 加载命名空间并返回其属性。
   *
   * @param namespace 命名空间
   * @return 命名空间属性映射
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace namespace)
      throws NoSuchNamespaceException {
    return client.loadNamespaceMetadata(namespace);
  }

  /** 删除命名空间，非空时抛 {@link NamespaceNotEmptyException}。 */
  @Override
  public boolean dropNamespace(Namespace namespace) throws NamespaceNotEmptyException {
    return client.dropNamespace(namespace);
  }

  /** 为命名空间设置属性。 */
  @Override
  public boolean setProperties(Namespace namespace, Map<String, String> properties) {
    return client.setProperties(namespace, properties);
  }

  /** 移除命名空间的指定属性。 */
  @Override
  public boolean removeProperties(Namespace namespace, Set<String> properties) {
    return client.removeProperties(namespace, properties);
  }

  /** 注入 Hadoop 配置（实现 {@link Configurable}）。 */
  @Override
  public void setConf(Object conf) {
    this.config = conf;
  }

  /** 返回当前引用的 hash（仅测试可见）。 */
  @VisibleForTesting
  String currentHash() {
    return client.getRef().getHash();
  }

  /** 返回当前引用名（仅测试可见）。 */
  @VisibleForTesting
  String currentRefName() {
    return client.getRef().getName();
  }

  /** 返回 FileIO（仅测试可见）。 */
  @VisibleForTesting
  FileIO fileIO() {
    return fileIO;
  }

  /** 解析表标识符中的 Nessie 引用（ref#hash），不支持按时间戳引用。 */
  private TableReference parseTableReference(TableIdentifier tableIdentifier) {
    TableReference tr = TableReference.parse(tableIdentifier.name());
    Preconditions.checkArgument(
        !tr.hasTimestamp(),
        "Invalid table name: # is only allowed for hashes (reference by "
            + "timestamp is not supported)");
    return tr;
  }

  /** 去除表标识符中的引用信息，返回纯表名标识符。 */
  private TableIdentifier identifierWithoutTableReference(
      TableIdentifier identifier, TableReference tableReference) {
    if (tableReference.hasReference()) {
      return TableIdentifier.of(identifier.namespace(), tableReference.getName());
    }
    return identifier;
  }

  /** 返回 Catalog 选项映射。 */
  @Override
  protected Map<String, String> properties() {
    return catalogOptions;
  }
}
