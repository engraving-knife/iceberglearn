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

import static org.apache.iceberg.TableProperties.COMMIT_MAX_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_MIN_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.iceberg.BaseMetadataTable;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.BaseTransaction;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.Transaction;
import org.apache.iceberg.UpdateRequirement;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.rest.requests.CreateNamespaceRequest;
import org.apache.iceberg.rest.requests.CreateTableRequest;
import org.apache.iceberg.rest.requests.RegisterTableRequest;
import org.apache.iceberg.rest.requests.RenameTableRequest;
import org.apache.iceberg.rest.requests.UpdateNamespacePropertiesRequest;
import org.apache.iceberg.rest.requests.UpdateTableRequest;
import org.apache.iceberg.rest.responses.CreateNamespaceResponse;
import org.apache.iceberg.rest.responses.GetNamespaceResponse;
import org.apache.iceberg.rest.responses.ListNamespacesResponse;
import org.apache.iceberg.rest.responses.ListTablesResponse;
import org.apache.iceberg.rest.responses.LoadTableResponse;
import org.apache.iceberg.rest.responses.UpdateNamespacePropertiesResponse;
import org.apache.iceberg.util.Tasks;

/**
 * 文件级说明：REST Catalog 服务端请求处理器，将 REST 请求委托到底层 {@link Catalog} 实现。
 *
 * <p>所属模块：iceberg-core（同时供 REST Catalog 服务端实现与单元测试使用，是连接 REST API 层与 Catalog 实现层的桥梁）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 namespace/table 相关的 REST 请求（创建、加载、删除、列表、重命名等）转换为对底层 {@link Catalog} / {@link
 *       SupportsNamespaces} 的调用。
 *   <li>将 {@link UpdateTableRequest} 中的 requirements/updates 应用到 {@link TableOperations}， 实现基于 REST
 *       的表元数据提交（含重试）。
 *   <li>将操作结果包装为 REST 响应对象（如 {@link LoadTableResponse}、{@link CreateNamespaceResponse}）。
 * </ul>
 *
 * <p>设计意图：以静态方法形式提供无状态处理器，便于服务端框架（如 Jersey/Spring）直接调用。
 * 提交逻辑使用指数退避重试以处理乐观锁冲突；ValidationFailureException 用于将断言失败从 重试循环中提取出来，避免对断言失败进行无意义重试。
 *
 * <p>上下游关系：上游为 REST Catalog 服务端路由/控制器层；下游为具体的 Catalog 实现 （如 HiveCatalog、JdbcCatalog 等）。
 */
public class CatalogHandlers {
  private static final Schema EMPTY_SCHEMA = new Schema();

  private CatalogHandlers() {}

  /**
   * 用于避免对断言失败进行重试的内部异常包装类。
   *
   * <p>设计意图：当 REST 断言（requirement）校验失败时会抛出 {@link CommitFailedException} 返回给客户端。 但断言检查发生在 {@link
   * TableOperations#commit(TableMetadata, TableMetadata)} 的重试块内，
   * 若不特殊处理则断言失败也会被当作提交冲突而重试。本类将断言失败包装后在重试循环外解包重抛， 从而避免对断言失败的无意义重试。
   */
  private static class ValidationFailureException extends RuntimeException {
    private final CommitFailedException wrapped;

    private ValidationFailureException(CommitFailedException cause) {
      super(cause);
      this.wrapped = cause;
    }

    public CommitFailedException wrapped() {
      return wrapped;
    }
  }

  /**
   * 列出指定父命名空间下的子命名空间。
   *
   * @param catalog 支持命名空间的 Catalog
   * @param parent 父命名空间；为空时列出顶层命名空间
   * @return 命名空间列表响应
   */
  public static ListNamespacesResponse listNamespaces(
      SupportsNamespaces catalog, Namespace parent) {
    List<Namespace> results;
    if (parent.isEmpty()) {
      results = catalog.listNamespaces();
    } else {
      results = catalog.listNamespaces(parent);
    }

    return ListNamespacesResponse.builder().addAll(results).build();
  }

  /**
   * 创建命名空间并返回创建结果（含服务端回填的属性）。
   *
   * @param catalog 支持命名空间的 Catalog
   * @param request 创建命名空间请求
   * @return 创建响应（含命名空间与最终属性）
   */
  public static CreateNamespaceResponse createNamespace(
      SupportsNamespaces catalog, CreateNamespaceRequest request) {
    Namespace namespace = request.namespace();
    catalog.createNamespace(namespace, request.properties());
    return CreateNamespaceResponse.builder()
        .withNamespace(namespace)
        .setProperties(catalog.loadNamespaceMetadata(namespace))
        .build();
  }

  /**
   * 加载命名空间的元数据属性。
   *
   * @param catalog 支持命名空间的 Catalog
   * @param namespace 命名空间
   * @return 命名空间详情响应
   */
  public static GetNamespaceResponse loadNamespace(
      SupportsNamespaces catalog, Namespace namespace) {
    Map<String, String> properties = catalog.loadNamespaceMetadata(namespace);
    return GetNamespaceResponse.builder()
        .withNamespace(namespace)
        .setProperties(properties)
        .build();
  }

  /**
   * 删除命名空间；不存在时抛出 {@link NoSuchNamespaceException}。
   *
   * @param catalog 支持命名空间的 Catalog
   * @param namespace 待删除的命名空间
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  public static void dropNamespace(SupportsNamespaces catalog, Namespace namespace) {
    boolean dropped = catalog.dropNamespace(namespace);
    if (!dropped) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }
  }

  /**
   * 更新命名空间属性（设置/移除），并返回更新结果摘要。
   *
   * <p>逻辑：先校验请求；加载当前属性以计算待移除中不存在的键（missing）；先执行 setProperties， 再执行 removeProperties（使用原始 removals
   * 集合以防 set 与 remove 之间有重叠）； 最终返回 missing/updated/removed 三组摘要。
   *
   * @param catalog 支持命名空间的 Catalog
   * @param namespace 命名空间
   * @param request 属性更新请求
   * @return 更新结果响应
   */
  public static UpdateNamespacePropertiesResponse updateNamespaceProperties(
      SupportsNamespaces catalog, Namespace namespace, UpdateNamespacePropertiesRequest request) {
    request.validate();

    Set<String> removals = Sets.newHashSet(request.removals());
    Map<String, String> updates = request.updates();

    Map<String, String> startProperties = catalog.loadNamespaceMetadata(namespace);
    Set<String> missing = Sets.difference(removals, startProperties.keySet());

    if (!updates.isEmpty()) {
      catalog.setProperties(namespace, updates);
    }

    if (!removals.isEmpty()) {
      // remove the original set just in case there was an update just after loading properties
      catalog.removeProperties(namespace, removals);
    }

    return UpdateNamespacePropertiesResponse.builder()
        .addMissing(missing)
        .addUpdated(updates.keySet())
        .addRemoved(Sets.difference(removals, missing))
        .build();
  }

  /**
   * 列出命名空间下的所有表标识符。
   *
   * @param catalog Catalog 实例
   * @param namespace 命名空间
   * @return 表列表响应
   */
  public static ListTablesResponse listTables(Catalog catalog, Namespace namespace) {
    List<TableIdentifier> idents = catalog.listTables(namespace);
    return ListTablesResponse.builder().addAll(idents).build();
  }

  /**
   * 暂存（stage）表创建：生成表元数据但不提交，用于 create-or-replace 事务的第一阶段。
   *
   * <p>逻辑：校验请求与表不存在；添加 created-at 时间戳属性；若未指定 location 则通过 createTransaction 获取默认 location；构造
   * TableMetadata 并返回。
   *
   * @param catalog Catalog 实例
   * @param namespace 命名空间
   * @param request 创建表请求
   * @return 含暂存元数据的加载表响应
   * @throws AlreadyExistsException 表已存在
   */
  public static LoadTableResponse stageTableCreate(
      Catalog catalog, Namespace namespace, CreateTableRequest request) {
    request.validate();

    TableIdentifier ident = TableIdentifier.of(namespace, request.name());
    if (catalog.tableExists(ident)) {
      throw new AlreadyExistsException("Table already exists: %s", ident);
    }

    Map<String, String> properties = Maps.newHashMap();
    properties.put("created-at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    properties.putAll(request.properties());

    String location;
    if (request.location() != null) {
      location = request.location();
    } else {
      location =
          catalog
              .buildTable(ident, request.schema())
              .withPartitionSpec(request.spec())
              .withSortOrder(request.writeOrder())
              .withProperties(properties)
              .createTransaction()
              .table()
              .location();
    }

    TableMetadata metadata =
        TableMetadata.newTableMetadata(
            request.schema(),
            request.spec() != null ? request.spec() : PartitionSpec.unpartitioned(),
            request.writeOrder() != null ? request.writeOrder() : SortOrder.unsorted(),
            location,
            properties);

    return LoadTableResponse.builder().withTableMetadata(metadata).build();
  }

  /**
   * 创建表并返回其元数据。
   *
   * @param catalog Catalog 实例
   * @param namespace 命名空间
   * @param request 创建表请求
   * @return 含表元数据的加载表响应
   * @throws IllegalStateException Catalog 未返回 BaseTable
   */
  public static LoadTableResponse createTable(
      Catalog catalog, Namespace namespace, CreateTableRequest request) {
    request.validate();

    TableIdentifier ident = TableIdentifier.of(namespace, request.name());
    Table table =
        catalog
            .buildTable(ident, request.schema())
            .withLocation(request.location())
            .withPartitionSpec(request.spec())
            .withSortOrder(request.writeOrder())
            .withProperties(request.properties())
            .create();

    if (table instanceof BaseTable) {
      return LoadTableResponse.builder()
          .withTableMetadata(((BaseTable) table).operations().current())
          .build();
    }

    throw new IllegalStateException("Cannot wrap catalog that does not produce BaseTable");
  }

  /**
   * 注册已有表（通过 metadata location）到 Catalog。
   *
   * @param catalog Catalog 实例
   * @param namespace 命名空间
   * @param request 注册表请求
   * @return 含表元数据的加载表响应
   * @throws IllegalStateException Catalog 未返回 BaseTable
   */
  public static LoadTableResponse registerTable(
      Catalog catalog, Namespace namespace, RegisterTableRequest request) {
    request.validate();

    TableIdentifier identifier = TableIdentifier.of(namespace, request.name());
    Table table = catalog.registerTable(identifier, request.metadataLocation());
    if (table instanceof BaseTable) {
      return LoadTableResponse.builder()
          .withTableMetadata(((BaseTable) table).operations().current())
          .build();
    }

    throw new IllegalStateException("Cannot wrap catalog that does not produce BaseTable");
  }

  /**
   * 删除表（不清除数据文件）；不存在时抛出 {@link NoSuchTableException}。
   *
   * @param catalog Catalog 实例
   * @param ident 表标识符
   * @throws NoSuchTableException 表不存在
   */
  public static void dropTable(Catalog catalog, TableIdentifier ident) {
    boolean dropped = catalog.dropTable(ident, false);
    if (!dropped) {
      throw new NoSuchTableException("Table does not exist: %s", ident);
    }
  }

  /**
   * 删除表并清除数据文件；不存在时抛出 {@link NoSuchTableException}。
   *
   * @param catalog Catalog 实例
   * @param ident 表标识符
   * @throws NoSuchTableException 表不存在
   */
  public static void purgeTable(Catalog catalog, TableIdentifier ident) {
    boolean dropped = catalog.dropTable(ident, true);
    if (!dropped) {
      throw new NoSuchTableException("Table does not exist: %s", ident);
    }
  }

  /**
   * 加载表并返回其当前元数据。
   *
   * <p>逻辑：加载表；若为 {@link BaseTable} 则返回其 current 元数据；若为 {@link BaseMetadataTable}（元数据表）则抛出
   * NoSuchTableException（元数据表由客户端构造）。
   *
   * @param catalog Catalog 实例
   * @param ident 表标识符
   * @return 加载表响应
   * @throws NoSuchTableException 表不存在
   * @throws IllegalStateException Catalog 未返回 BaseTable
   */
  public static LoadTableResponse loadTable(Catalog catalog, TableIdentifier ident) {
    Table table = catalog.loadTable(ident);

    if (table instanceof BaseTable) {
      return LoadTableResponse.builder()
          .withTableMetadata(((BaseTable) table).operations().current())
          .build();
    } else if (table instanceof BaseMetadataTable) {
      // metadata tables are loaded on the client side, return NoSuchTableException for now
      throw new NoSuchTableException("Table does not exist: %s", ident.toString());
    }

    throw new IllegalStateException("Cannot wrap catalog that does not produce BaseTable");
  }

  /**
   * 处理表更新请求（创建或更新表元数据）。
   *
   * <p>逻辑：若请求含 AssertTableDoesNotExist 则走 create 路径（通过 createOrReplaceTransaction 获取
   * TableOperations）；否则走 update 路径（加载已有表并 commit）。最终返回更新后的表元数据。
   *
   * @param catalog Catalog 实例
   * @param ident 表标识符
   * @param request 表更新请求（含 requirements 与 updates）
   * @return 含更新后元数据的加载表响应
   */
  public static LoadTableResponse updateTable(
      Catalog catalog, TableIdentifier ident, UpdateTableRequest request) {
    TableMetadata finalMetadata;
    if (isCreate(request)) {
      // this is a hacky way to get TableOperations for an uncommitted table
      Transaction transaction =
          catalog.buildTable(ident, EMPTY_SCHEMA).createOrReplaceTransaction();
      if (transaction instanceof BaseTransaction) {
        BaseTransaction baseTransaction = (BaseTransaction) transaction;
        finalMetadata = create(baseTransaction.underlyingOps(), request);
      } else {
        throw new IllegalStateException(
            "Cannot wrap catalog that does not produce BaseTransaction");
      }

    } else {
      Table table = catalog.loadTable(ident);
      if (table instanceof BaseTable) {
        TableOperations ops = ((BaseTable) table).operations();
        finalMetadata = commit(ops, request);
      } else {
        throw new IllegalStateException("Cannot wrap catalog that does not produce BaseTable");
      }
    }

    return LoadTableResponse.builder().withTableMetadata(finalMetadata).build();
  }

  /**
   * 重命名表。
   *
   * @param catalog Catalog 实例
   * @param request 重命名请求（含源与目标标识符）
   */
  public static void renameTable(Catalog catalog, RenameTableRequest request) {
    catalog.renameTable(request.source(), request.destination());
  }

  /**
   * 判断更新请求是否为“创建表”请求（含 AssertTableDoesNotExist 前置条件）。
   *
   * <p>逻辑：检查 requirements 中是否存在 AssertTableDoesNotExist；若是，则校验不存在其他 非法 requirement。
   *
   * @param request 表更新请求
   * @return 是创建请求返回 true
   * @throws IllegalArgumentException 创建请求中包含非法 requirement
   */
  private static boolean isCreate(UpdateTableRequest request) {
    boolean isCreate =
        request.requirements().stream()
            .anyMatch(UpdateRequirement.AssertTableDoesNotExist.class::isInstance);

    if (isCreate) {
      List<UpdateRequirement> invalidRequirements =
          request.requirements().stream()
              .filter(req -> !(req instanceof UpdateRequirement.AssertTableDoesNotExist))
              .collect(Collectors.toList());
      Preconditions.checkArgument(
          invalidRequirements.isEmpty(), "Invalid create requirements: %s", invalidRequirements);
    }

    return isCreate;
  }

  /**
   * 执行表创建提交（不重试）。
   *
   * <p>逻辑：校验所有 requirements；从空元数据开始应用所有 updates；提交（base 为 null）。 创建事务不重试，若表已存在则重试无意义。
   *
   * @param ops 表操作接口
   * @param request 表更新请求
   * @return 创建后的表元数据
   */
  private static TableMetadata create(TableOperations ops, UpdateTableRequest request) {
    // the only valid requirement is that the table will be created
    request.requirements().forEach(requirement -> requirement.validate(ops.current()));

    TableMetadata.Builder builder = TableMetadata.buildFromEmpty();
    request.updates().forEach(update -> update.applyTo(builder));

    // create transactions do not retry. if the table exists, retrying is not a solution
    ops.commit(null, builder.build());

    return ops.current();
  }

  /**
   * 执行表元数据提交（含指数退避重试）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>使用 {@link Tasks} 进行指数退避重试（仅重试 CommitFailedException）。
   *   <li>首次使用 current()，重试时使用 refresh() 获取最新元数据作为 base。
   *   <li>校验所有 requirements；断言失败抛出 ValidationFailureException 以跳过重试。
   *   <li>应用所有 updates 到 builder；若元数据无变化则跳过提交。
   *   <li>提交 base→updated；最终返回 ops.current()。
   * </ol>
   *
   * @param ops 表操作接口
   * @param request 表更新请求
   * @return 更新后的表元数据
   * @throws CommitFailedException 断言失败或提交冲突且重试耗尽
   */
  static TableMetadata commit(TableOperations ops, UpdateTableRequest request) {
    AtomicBoolean isRetry = new AtomicBoolean(false);
    try {
      Tasks.foreach(ops)
          .retry(COMMIT_NUM_RETRIES_DEFAULT)
          .exponentialBackoff(
              COMMIT_MIN_RETRY_WAIT_MS_DEFAULT,
              COMMIT_MAX_RETRY_WAIT_MS_DEFAULT,
              COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT,
              2.0 /* exponential */)
          .onlyRetryOn(CommitFailedException.class)
          .run(
              taskOps -> {
                TableMetadata base = isRetry.get() ? taskOps.refresh() : taskOps.current();
                isRetry.set(true);

                // validate requirements
                try {
                  request.requirements().forEach(requirement -> requirement.validate(base));
                } catch (CommitFailedException e) {
                  // wrap and rethrow outside of tasks to avoid unnecessary retry
                  throw new ValidationFailureException(e);
                }

                // apply changes
                TableMetadata.Builder metadataBuilder = TableMetadata.buildFrom(base);
                request.updates().forEach(update -> update.applyTo(metadataBuilder));

                TableMetadata updated = metadataBuilder.build();
                if (updated.changes().isEmpty()) {
                  // do not commit if the metadata has not changed
                  return;
                }

                // commit
                taskOps.commit(base, updated);
              });

    } catch (ValidationFailureException e) {
      throw e.wrapped();
    }

    return ops.current();
  }
}
