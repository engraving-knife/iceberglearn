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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.relocated.com.google.common.base.Suppliers;
import org.apache.iceberg.util.Tasks;
import org.projectnessie.client.NessieConfigConstants;
import org.projectnessie.client.api.CommitMultipleOperationsBuilder;
import org.projectnessie.client.api.NessieApiV1;
import org.projectnessie.client.api.OnReferenceBuilder;
import org.projectnessie.client.http.HttpClientException;
import org.projectnessie.error.BaseNessieClientServerException;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNamespaceAlreadyExistsException;
import org.projectnessie.error.NessieNamespaceNotEmptyException;
import org.projectnessie.error.NessieNamespaceNotFoundException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.error.NessieReferenceNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.Content;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.EntriesResponse;
import org.projectnessie.model.GetNamespacesResponse;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.ImmutableCommitMeta;
import org.projectnessie.model.ImmutableIcebergTable;
import org.projectnessie.model.Operation;
import org.projectnessie.model.Reference;
import org.projectnessie.model.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Nessie Catalog 的客户端实现，封装与 Nessie 服务端的全部交互。
 *
 * <p>所属模块：iceberg-nessie。职责：作为 {@link NessieCatalog} 的底层客户端，负责：
 *
 * <ul>
 *   <li>管理 Nessie 引用（Branch/Tag）的加载、刷新与切换；
 *   <li>实现表的 CRUD（list/load/create/drop/rename/commitTable）；
 *   <li>实现 Namespace 的 CRUD（create/list/drop/load/setProperties/removeProperties）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>引用懒加载：通过 {@link Suppliers#memoize} 把 reference 的加载推迟到首次使用， 避免在构造期就访问 Nessie 服务。
 *   <li>乐观锁重试：写操作（rename/drop/commitTable）使用 {@link Tasks} 工具做带重试的提交， 失败时 refresh
 *       引用后重试，应对并发提交冲突（NessieConflictException）。
 *   <li>引用切换：{@link #withReference(String, String)} 在读操作时按需创建指向不同 ref/hash 的临时客户端，使同一 catalog
 *       实例可访问任意 ref。
 *   <li>网络异常归一化：HttpClientException（含超时、连接重置等）统一转 {@link
 *       CommitStateUnknownException}，因为提交可能已成功，调用方不应误认为失败重试。
 * </ul>
 *
 * <p>上下游：上游由 {@link NessieCatalog} 持有并委托调用；下游通过 Nessie Java SDK （{@link NessieApiV1}）访问 Nessie
 * 服务，并把 Iceberg 的 {@link TableMetadata} 映射为 Nessie 的 {@link IcebergTable} 内容对象。
 */
public class NessieIcebergClient implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(NessieIcebergClient.class);

  private final NessieApiV1 api;
  private final Supplier<UpdateableReference> reference;
  private final Map<String, String> catalogOptions;

  /**
   * 构造 Nessie 客户端。
   *
   * <p>逻辑：保存 api 与 catalogOptions；reference 使用 {@link Suppliers#memoize} 懒加载， 首次调用 getRef() 时才执行
   * {@link #loadReference}。
   *
   * @param api Nessie API 客户端
   * @param requestedRef 请求的引用名（null 表示用默认分支）
   * @param requestedHash 请求的 hash（null 表示分支最新）
   * @param catalogOptions catalog 配置
   */
  public NessieIcebergClient(
      NessieApiV1 api,
      String requestedRef,
      String requestedHash,
      Map<String, String> catalogOptions) {
    this.api = api;
    this.catalogOptions = catalogOptions;
    this.reference = Suppliers.memoize(() -> loadReference(requestedRef, requestedHash));
  }

  /** 返回 Nessie API 客户端。 */
  public NessieApiV1 getApi() {
    return api;
  }

  /** 返回（懒加载并缓存的）可更新引用。 */
  UpdateableReference getRef() {
    return reference.get();
  }

  /** 返回当前 Nessie 引用对象。 */
  public Reference getReference() {
    return reference.get().getReference();
  }

  /**
   * 刷新当前引用，拉取分支最新状态。
   *
   * @throws NessieNotFoundException 若分支不存在
   */
  public void refresh() throws NessieNotFoundException {
    getRef().refresh(api);
  }

  /**
   * 返回指向指定 ref/hash 的客户端；若与当前一致则返回 this。
   *
   * <p>逻辑：requestedRef 为 null 或与当前 ref 名+hash 完全相同时返回 this； 否则新建一个 NessieIcebergClient 指向目标 ref。
   *
   * @param requestedRef 目标引用名
   * @param hash 目标 hash
   * @return 指向目标引用的客户端实例
   */
  public NessieIcebergClient withReference(String requestedRef, String hash) {
    if (null == requestedRef
        || (getRef().getReference().getName().equals(requestedRef)
            && getRef().getHash().equals(hash))) {
      return this;
    }
    return new NessieIcebergClient(getApi(), requestedRef, hash, catalogOptions);
  }

  /**
   * 加载 Nessie 引用（懒加载回调）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>requestedRef 为 null 时取默认分支，否则按名获取引用。
   *   <li>若指定了 hash，把 Branch/Tag 重新构造为带该 hash 的快照引用。
   *   <li>构造 {@link UpdateableReference}（hash!=null 时视为只读）。
   *   <li>引用不存在时抛 IllegalArgumentException，提示配置 ref 或创建默认分支。
   * </ol>
   *
   * @param requestedRef 引用名
   * @param hash 目标 hash
   * @return 加载好的可更新引用
   */
  private UpdateableReference loadReference(String requestedRef, String hash) {
    try {
      Reference ref =
          requestedRef == null
              ? api.getDefaultBranch()
              : api.getReference().refName(requestedRef).get();
      if (hash != null) {
        if (ref instanceof Branch) {
          ref = Branch.of(ref.getName(), hash);
        } else {
          ref = Tag.of(ref.getName(), hash);
        }
      }
      return new UpdateableReference(ref, hash != null);
    } catch (NessieNotFoundException ex) {
      if (requestedRef != null) {
        throw new IllegalArgumentException(
            String.format("Nessie ref '%s' does not exist", requestedRef), ex);
      }

      throw new IllegalArgumentException(
          String.format(
              "Nessie does not have an existing default branch. "
                  + "Either configure an alternative ref via '%s' or create the default branch on the server.",
              NessieConfigConstants.CONF_NESSIE_REF),
          ex);
    }
  }

  /**
   * 列出指定 namespace 下的所有 Iceberg 表。
   *
   * <p>逻辑：调用 Nessie getEntries 拉取所有条目，按 namespace 前缀过滤、按类型 （ICEBERG_TABLE）过滤，再映射为 Iceberg
   * TableIdentifier。
   *
   * @param namespace 命名空间（null 表示全部）
   * @return 表标识符列表
   * @throws NoSuchNamespaceException 若引用不存在
   */
  public List<TableIdentifier> listTables(Namespace namespace) {
    try {
      return withReference(api.getEntries()).get().getEntries().stream()
          .filter(namespacePredicate(namespace))
          .filter(e -> Content.Type.ICEBERG_TABLE == e.getType())
          .map(this::toIdentifier)
          .collect(Collectors.toList());
    } catch (NessieNotFoundException ex) {
      throw new NoSuchNamespaceException(
          ex, "Unable to list tables due to missing ref '%s'", getRef().getName());
    }
  }

  /**
   * 构造 namespace 前缀过滤谓词。
   *
   * <p>逻辑：ns 为 null 时恒真；否则要求条目名称层级数大于 ns 层级数， 且前 N 层与 ns 完全相等。
   */
  private Predicate<EntriesResponse.Entry> namespacePredicate(Namespace ns) {
    if (ns == null) {
      return e -> true;
    }

    final List<String> namespace = Arrays.asList(ns.levels());
    return e -> {
      List<String> names = e.getName().getElements();

      if (names.size() <= namespace.size()) {
        return false;
      }

      return namespace.equals(names.subList(0, namespace.size()));
    };
  }

  /** 把 Nessie Entry 的 ContentKey 元素转为 Iceberg TableIdentifier。 */
  private TableIdentifier toIdentifier(EntriesResponse.Entry entry) {
    List<String> elements = entry.getName().getElements();
    return TableIdentifier.of(elements.toArray(new String[elements.size()]));
  }

  /**
   * 加载指定表的 Nessie 内容对象（IcebergTable）。
   *
   * <p>逻辑：按 ContentKey 调用 getContent，unwrap 为 IcebergTable；不存在或引用缺失返回 null。
   *
   * @param tableIdentifier Iceberg 表标识符
   * @return Nessie 的 IcebergTable 对象；不存在返回 null
   */
  public IcebergTable table(TableIdentifier tableIdentifier) {
    try {
      ContentKey key = NessieUtil.toKey(tableIdentifier);
      Content table = withReference(api.getContent().key(key)).get().get(key);
      return table != null ? table.unwrap(IcebergTable.class).orElse(null) : null;
    } catch (NessieNotFoundException e) {
      return null;
    }
  }

  /**
   * 在 Nessie 创建 namespace 并附带元数据属性。
   *
   * <p>逻辑：校验引用可变 → 调用 createNamespace → refresh。
   *
   * @throws AlreadyExistsException namespace 已存在
   * @throws RuntimeException 引用失效
   */
  public void createNamespace(Namespace namespace, Map<String, String> metadata) {
    try {
      getRef().checkMutable();
      withReference(
              getApi()
                  .createNamespace()
                  .namespace(org.projectnessie.model.Namespace.of(namespace.levels()))
                  .properties(metadata))
          .create();
      refresh();
    } catch (NessieNamespaceAlreadyExistsException e) {
      throw new AlreadyExistsException(e, "Namespace already exists: %s", namespace);
    } catch (NessieNotFoundException e) {
      throw new RuntimeException(
          String.format(
              "Cannot create Namespace '%s': " + "ref '%s' is no longer valid.",
              namespace, getRef().getName()),
          e);
    }
  }

  /**
   * 列出指定 namespace 下一层的所有子 namespace。
   *
   * <p>逻辑：调用 getMultipleNamespaces，过滤出层级数恰为 namespace.length+1 的直接子级。
   *
   * @throws NoSuchNamespaceException 引用失效
   */
  public List<Namespace> listNamespaces(Namespace namespace) throws NoSuchNamespaceException {
    try {
      GetNamespacesResponse response =
          withReference(
                  getApi()
                      .getMultipleNamespaces()
                      .namespace(org.projectnessie.model.Namespace.of(namespace.levels())))
              .get();
      return response.getNamespaces().stream()
          .map(ns -> Namespace.of(ns.getElements().toArray(new String[0])))
          .filter(ns -> ns.length() == namespace.length() + 1)
          .collect(Collectors.toList());
    } catch (NessieReferenceNotFoundException e) {
      throw new RuntimeException(
          String.format(
              "Cannot list Namespaces starting from '%s': " + "ref '%s' is no longer valid.",
              namespace, getRef().getName()),
          e);
    }
  }

  /**
   * 删除指定 namespace。
   *
   * <p>逻辑：校验可变 → deleteNamespace → refresh。namespace 不存在返回 false； 非空抛 {@link
   * NamespaceNotEmptyException}。
   *
   * @return true 表示删除成功；false 表示 namespace 不存在
   * @throws NamespaceNotEmptyException namespace 非空
   */
  public boolean dropNamespace(Namespace namespace) throws NamespaceNotEmptyException {
    try {
      getRef().checkMutable();
      withReference(
              getApi()
                  .deleteNamespace()
                  .namespace(org.projectnessie.model.Namespace.of(namespace.levels())))
          .delete();
      refresh();
      return true;
    } catch (NessieNamespaceNotFoundException e) {
      return false;
    } catch (NessieNotFoundException e) {
      LOG.error(
          "Cannot drop Namespace '{}': ref '{}' is no longer valid.",
          namespace,
          getRef().getName(),
          e);
      return false;
    } catch (NessieNamespaceNotEmptyException e) {
      throw new NamespaceNotEmptyException(
          e, "Namespace '%s' is not empty. One or more tables exist.", namespace);
    }
  }

  /**
   * 加载 namespace 的元数据属性。
   *
   * @throws NoSuchNamespaceException namespace 不存在或引用失效
   */
  public Map<String, String> loadNamespaceMetadata(Namespace namespace)
      throws NoSuchNamespaceException {
    try {
      return withReference(
              getApi()
                  .getNamespace()
                  .namespace(org.projectnessie.model.Namespace.of(namespace.levels())))
          .get()
          .getProperties();
    } catch (NessieNamespaceNotFoundException e) {
      throw new NoSuchNamespaceException(e, "Namespace does not exist: %s", namespace);
    } catch (NessieReferenceNotFoundException e) {
      throw new RuntimeException(
          String.format(
              "Cannot load Namespace '%s': " + "ref '%s' is no longer valid.",
              namespace, getRef().getName()),
          e);
    }
  }

  /**
   * 给 namespace 设置属性（合并）。
   *
   * @return 始终 true（失败会抛异常）
   * @throws NoSuchNamespaceException namespace 不存在
   */
  public boolean setProperties(Namespace namespace, Map<String, String> properties) {
    try {
      withReference(
              getApi()
                  .updateProperties()
                  .namespace(org.projectnessie.model.Namespace.of(namespace.levels()))
                  .updateProperties(properties))
          .update();
      refresh();
      // always successful, otherwise an exception is thrown
      return true;
    } catch (NessieNamespaceNotFoundException e) {
      throw new NoSuchNamespaceException(e, "Namespace does not exist: %s", namespace);
    } catch (NessieNotFoundException e) {
      throw new RuntimeException(
          String.format(
              "Cannot update properties on Namespace '%s': ref '%s' is no longer valid.",
              namespace, getRef().getName()),
          e);
    }
  }

  /**
   * 从 namespace 移除指定属性。
   *
   * @return 始终 true（失败会抛异常）
   * @throws NoSuchNamespaceException namespace 不存在
   */
  public boolean removeProperties(Namespace namespace, Set<String> properties) {
    try {
      withReference(
              getApi()
                  .updateProperties()
                  .namespace(org.projectnessie.model.Namespace.of(namespace.levels()))
                  .removeProperties(properties))
          .update();
      refresh();
      // always successful, otherwise an exception is thrown
      return true;
    } catch (NessieNamespaceNotFoundException e) {
      throw new NoSuchNamespaceException(e, "Namespace does not exist: %s", namespace);
    } catch (NessieNotFoundException e) {
      throw new RuntimeException(
          String.format(
              "Cannot remove properties from Namespace '%s': ref '%s' is no longer valid.",
              namespace, getRef().getName()),
          e);
    }
  }

  /**
   * 重命名表：在 Nessie 上以一个 Delete + 一个 Put 组合提交实现原子重命名。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验引用可变；校验源表存在、目标表不存在。
   *   <li>构造 commit：Delete(from key) + Put(to key, 原表内容)。
   *   <li>用 {@link Tasks} 重试 5 次，失败时 refresh 引用后重试； NessieNotFoundException 停止重试。
   * </ol>
   *
   * <p>异常映射：
   *
   * <ul>
   *   <li>NessieNotFoundException → RuntimeException（引用不存在）
   *   <li>BaseNessieClientServerException → CommitFailedException（引用非最新，需重试）
   *   <li>HttpClientException → CommitStateUnknownException（提交状态未知，可能已成功）
   * </ul>
   *
   * @param from 源表标识符
   * @param to 目标表标识符
   * @throws NoSuchTableException 源表不存在
   * @throws AlreadyExistsException 目标表已存在
   * @throws CommitFailedException 引用非最新
   * @throws CommitStateUnknownException 网络异常导致提交状态未知
   */
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    getRef().checkMutable();

    IcebergTable existingFromTable = table(from);
    if (existingFromTable == null) {
      throw new NoSuchTableException("Table does not exist: %s", from.name());
    }
    IcebergTable existingToTable = table(to);
    if (existingToTable != null) {
      throw new AlreadyExistsException("Table already exists: %s", to.name());
    }

    CommitMultipleOperationsBuilder operations =
        getApi()
            .commitMultipleOperations()
            .commitMeta(
                NessieUtil.buildCommitMetadata(
                    String.format("Iceberg rename table from '%s' to '%s'", from, to),
                    catalogOptions))
            .operation(Operation.Delete.of(NessieUtil.toKey(from)))
            .operation(Operation.Put.of(NessieUtil.toKey(to), existingFromTable));

    try {
      Tasks.foreach(operations)
          .retry(5)
          .stopRetryOn(NessieNotFoundException.class)
          .throwFailureWhenFinished()
          .onFailure((o, exception) -> refresh())
          .run(
              ops -> {
                Branch branch = ops.branch((Branch) getRef().getReference()).commit();
                getRef().updateReference(branch);
              },
              BaseNessieClientServerException.class);
    } catch (NessieNotFoundException e) {
      // important note: the NotFoundException refers to the ref only. If a table was not found it
      // would imply that the
      // another commit has deleted the table from underneath us. This would arise as a Conflict
      // exception as opposed to
      // a not found exception. This is analogous to a merge conflict in git when a table has been
      // changed by one user
      // and removed by another.
      throw new RuntimeException(
          String.format(
              "Cannot rename table '%s' to '%s': " + "ref '%s' no longer exists.",
              from.name(), to.name(), getRef().getName()),
          e);
    } catch (BaseNessieClientServerException e) {
      throw new CommitFailedException(
          e,
          "Cannot rename table '%s' to '%s': " + "the current reference is not up to date.",
          from.name(),
          to.name());
    } catch (HttpClientException ex) {
      // Intentionally catch all nessie-client-exceptions here and not just the "timeout" variant
      // to catch all kinds of network errors (e.g. connection reset). Network code implementation
      // details and all kinds of network devices can induce unexpected behavior. So better be
      // safe than sorry.
      throw new CommitStateUnknownException(ex);
    }
    // Intentionally just "throw through" Nessie's HttpClientException here and do not "special
    // case"
    // just the "timeout" variant to propagate all kinds of network errors (e.g. connection reset).
    // Network code implementation details and all kinds of network devices can induce unexpected
    // behavior. So better be safe than sorry.
  }

  /**
   * 删除表（Nessie 上 Delete 操作）。purge 参数目前被忽略（仅记日志），因为 Nessie 自身 通过引用感知 GC 管理文件生命周期。
   *
   * <p>逻辑：校验可变 → 加载表（不存在返回 false）→ 构造 Delete commit → 重试 5 次提交。
   *
   * @param identifier 表标识符
   * @param purge 是否 purge（当前忽略）
   * @return true 表示删除成功；false 表示表不存在或重试后仍失败
   */
  public boolean dropTable(TableIdentifier identifier, boolean purge) {
    getRef().checkMutable();

    IcebergTable existingTable = table(identifier);
    if (existingTable == null) {
      return false;
    }

    if (purge) {
      LOG.info("Purging data for table {} was set to true but is ignored", identifier.toString());
    }

    CommitMultipleOperationsBuilder commitBuilderBase =
        getApi()
            .commitMultipleOperations()
            .commitMeta(
                NessieUtil.buildCommitMetadata(
                    String.format("Iceberg delete table %s", identifier), catalogOptions))
            .operation(Operation.Delete.of(NessieUtil.toKey(identifier)));

    // We try to drop the table. Simple retry after ref update.
    boolean threw = true;
    try {
      Tasks.foreach(commitBuilderBase)
          .retry(5)
          .stopRetryOn(NessieNotFoundException.class)
          .throwFailureWhenFinished()
          .onFailure((o, exception) -> refresh())
          .run(
              commitBuilder -> {
                Branch branch = commitBuilder.branch((Branch) getRef().getReference()).commit();
                getRef().updateReference(branch);
              },
              BaseNessieClientServerException.class);
      threw = false;
    } catch (NessieConflictException e) {
      LOG.error(
          "Cannot drop table: failed after retry (update ref '{}' and retry)",
          getRef().getName(),
          e);
    } catch (NessieNotFoundException e) {
      LOG.error("Cannot drop table: ref '{}' is no longer valid.", getRef().getName(), e);
    } catch (BaseNessieClientServerException e) {
      LOG.error("Cannot drop table: unknown error", e);
    }
    return !threw;
  }

  /**
   * 提交表元数据变更到 Nessie（核心写路径）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验引用可变；取当前 Branch 作为 expectedHead。
   *   <li>若 base 非 null，从 base 的 NESSIE_COMMIT_ID_PROPERTY 取上次 commit id 作为 乐观锁期望 hash（实现 CAS）。
   *   <li>用 metadata 的当前 schema/spec/sortOrder/snapshotId/metadataLocation 构造新的 {@link
   *       IcebergTable} 内容对象。
   *   <li>构造 commitMeta（消息 + 若为 snapshot 操作则附带 iceberg.operation 属性）。
   *   <li>调用 commitMultipleOperations 提交 Put(key, newTable, expectedContent)。
   *   <li>提交成功后更新本地引用为新 Branch。
   * </ol>
   *
   * @param base 基线元数据（用于乐观锁期望 hash；null 表示新建）
   * @param metadata 新元数据
   * @param newMetadataLocation 新元数据文件位置
   * @param expectedContent 期望的旧 Nessie 内容（用于条件 Put）
   * @param key Nessie ContentKey
   * @throws NessieConflictException 并发冲突
   * @throws NessieNotFoundException 引用不存在
   */
  public void commitTable(
      TableMetadata base,
      TableMetadata metadata,
      String newMetadataLocation,
      IcebergTable expectedContent,
      ContentKey key)
      throws NessieConflictException, NessieNotFoundException {
    UpdateableReference updateableReference = getRef();

    updateableReference.checkMutable();

    Branch current = (Branch) updateableReference.getReference();
    Branch expectedHead = current;
    if (base != null) {
      String metadataCommitId =
          base.property(NessieTableOperations.NESSIE_COMMIT_ID_PROPERTY, expectedHead.getHash());
      if (metadataCommitId != null) {
        expectedHead = Branch.of(expectedHead.getName(), metadataCommitId);
      }
    }

    ImmutableIcebergTable.Builder newTableBuilder = ImmutableIcebergTable.builder();
    if (expectedContent != null) {
      newTableBuilder.id(expectedContent.getId());
    }
    Snapshot snapshot = metadata.currentSnapshot();
    long snapshotId = snapshot != null ? snapshot.snapshotId() : -1L;

    IcebergTable newTable =
        newTableBuilder
            .snapshotId(snapshotId)
            .schemaId(metadata.currentSchemaId())
            .specId(metadata.defaultSpecId())
            .sortOrderId(metadata.defaultSortOrderId())
            .metadataLocation(newMetadataLocation)
            .build();

    LOG.debug(
        "Committing '{}' against '{}', current is '{}': {}",
        key,
        expectedHead,
        current.getHash(),
        newTable);
    ImmutableCommitMeta.Builder builder = ImmutableCommitMeta.builder();
    builder.message(buildCommitMsg(base, metadata, key.toString()));
    if (isSnapshotOperation(base, metadata)) {
      builder.putProperties("iceberg.operation", snapshot.operation());
    }
    Branch branch =
        getApi()
            .commitMultipleOperations()
            .operation(Operation.Put.of(key, newTable, expectedContent))
            .commitMeta(NessieUtil.catalogOptions(builder, catalogOptions).build())
            .branch(expectedHead)
            .commit();
    LOG.info(
        "Committed '{}' against '{}', expected commit-id was '{}'",
        key,
        branch,
        expectedHead.getHash());
    updateableReference.updateReference(branch);
  }

  /**
   * 判断本次提交是否为 snapshot 操作（即产生了新快照）。
   *
   * <p>逻辑：metadata 当前快照非 null，且 base 为 null 或 base 无快照或快照 id 不同。
   */
  private boolean isSnapshotOperation(TableMetadata base, TableMetadata metadata) {
    Snapshot snapshot = metadata.currentSnapshot();
    return snapshot != null
        && (base == null
            || base.currentSnapshot() == null
            || snapshot.snapshotId() != base.currentSnapshot().snapshotId());
  }

  /** 给 Nessie 读请求 builder 设置引用：不可变引用用 reference（指向具体 hash）， 可变引用用 refName（指向分支最新）。 */
  private <T extends OnReferenceBuilder<?>> T withReference(T builder) {
    UpdateableReference ref = getRef();
    if (!ref.isMutable()) {
      builder.reference(ref.getReference());
    } else {
      builder.refName(ref.getName());
    }
    return builder;
  }

  /**
   * 根据 base/metadata 差异构造 commit 消息。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>snapshot 操作 → "Iceberg {operation} against {table}"
   *   <li>schema 变更 → "Iceberg schema change against {table}"
   *   <li>新建表 → "Iceberg table created/registered with name {table}"
   *   <li>其他 → "Iceberg commit against {table}"
   * </ul>
   */
  private String buildCommitMsg(TableMetadata base, TableMetadata metadata, String tableName) {
    if (isSnapshotOperation(base, metadata)) {
      return String.format(
          "Iceberg %s against %s", metadata.currentSnapshot().operation(), tableName);
    } else if (base != null && metadata.currentSchemaId() != base.currentSchemaId()) {
      return String.format("Iceberg schema change against %s", tableName);
    } else if (base == null) {
      return String.format("Iceberg table created/registered with name %s", tableName);
    }
    return String.format("Iceberg commit against %s", tableName);
  }

  /** 返回当前引用名。 */
  public String refName() {
    return getRef().getName();
  }

  /** 关闭 Nessie API 客户端，释放资源。 */
  @Override
  public void close() {
    if (null != api) {
      api.close();
    }
  }
}
