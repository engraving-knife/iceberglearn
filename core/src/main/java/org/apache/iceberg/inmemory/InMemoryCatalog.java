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
package org.apache.iceberg.inmemory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 基于内存数据结构存储命名空间与表的 Catalog 实现。
 *
 * <p>所属模块：iceberg-core，继承 {@link BaseMetastoreCatalog} 并实现 {@link SupportsNamespaces}，
 * 定位于测试场景——不接触任何外部资源，可在无副作用的单元测试中使用。底层使用 {@link InMemoryFileIO}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>用 {@link ConcurrentMap} 维护命名空间属性与表元数据位置，提供 create/drop/rename/list 等操作。
 *   <li>实现命名空间层级管理（创建、列举、删除、属性增删）。
 *   <li>通过内部 {@link InMemoryTableOperations} 提供表元数据的刷新与提交（CAS 语义）。
 * </ul>
 *
 * <p>设计意图：使用 {@code ConcurrentMap} 实现线程安全的基础读写；renameTable 加 synchronized 保证原子性；doCommit 利用 {@code
 * tables.compute} 做乐观并发控制，基于 metadata location 比较 实现 CAS 提交。所有集合使用不可变副本存储属性，避免外部修改。
 *
 * <p>上下游关系：被测试代码作为 Catalog 使用；依赖 {@link InMemoryFileIO} 处理底层文件 IO， 继承 {@link
 * BaseMetastoreTableOperations} 的元数据读写能力。
 */
public class InMemoryCatalog extends BaseMetastoreCatalog implements SupportsNamespaces, Closeable {
  private static final Joiner SLASH = Joiner.on("/");
  private static final Joiner DOT = Joiner.on(".");

  private final ConcurrentMap<Namespace, Map<String, String>> namespaces;
  private final ConcurrentMap<TableIdentifier, String> tables;
  private FileIO io;
  private String catalogName;
  private String warehouseLocation;

  /** 构造一个空的内存 Catalog，需在 {@link #initialize} 后使用。 */
  public InMemoryCatalog() {
    this.namespaces = Maps.newConcurrentMap();
    this.tables = Maps.newConcurrentMap();
  }

  /** @return Catalog 名称 */
  @Override
  public String name() {
    return catalogName;
  }

  /**
   * 初始化 Catalog：设置名称、warehouse 路径并创建 {@link InMemoryFileIO}。
   *
   * @param name Catalog 名称，为 null 时使用类名
   * @param properties 配置属性，读取 {@link CatalogProperties#WAREHOUSE_LOCATION}
   */
  @Override
  public void initialize(String name, Map<String, String> properties) {
    this.catalogName = name != null ? name : InMemoryCatalog.class.getSimpleName();

    String warehouse = properties.getOrDefault(CatalogProperties.WAREHOUSE_LOCATION, "");
    this.warehouseLocation = warehouse.replaceAll("/*$", "");
    this.io = new InMemoryFileIO();
  }

  /**
   * 为指定表创建 {@link TableOperations}。
   *
   * @param tableIdentifier 表标识
   * @return 内存版 TableOperations
   */
  @Override
  protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
    return new InMemoryTableOperations(io, tableIdentifier);
  }

  /**
   * 计算默认 warehouse 位置：namespace 路径 + 表名。
   *
   * @param tableIdentifier 表标识
   * @return 默认表存储路径
   */
  @Override
  protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
    return SLASH.join(
        defaultNamespaceLocation(tableIdentifier.namespace()), tableIdentifier.name());
  }

  /**
   * 计算命名空间的存储根路径：空 namespace 返回 warehouse，否则拼接各级 level。
   *
   * @param namespace 命名空间
   * @return 命名空间路径
   */
  private String defaultNamespaceLocation(Namespace namespace) {
    if (namespace.isEmpty()) {
      return warehouseLocation;
    } else {
      return SLASH.join(warehouseLocation, SLASH.join(namespace.levels()));
    }
  }

  /**
   * 删除表，可选清理底层数据文件。
   *
   * <p>逻辑：先从 tables 移除表标识；若 purge 为真且表存在，调用 {@link CatalogUtil#dropTableData} 删除表数据文件。
   *
   * @param tableIdentifier 表标识
   * @param purge 是否物理删除数据文件
   * @return 表存在并删除返回 true，否则 false
   */
  @Override
  public boolean dropTable(TableIdentifier tableIdentifier, boolean purge) {
    TableOperations ops = newTableOps(tableIdentifier);
    TableMetadata lastMetadata;
    if (purge && ops.current() != null) {
      lastMetadata = ops.current();
    } else {
      lastMetadata = null;
    }

    if (null == tables.remove(tableIdentifier)) {
      return false;
    }

    if (purge && lastMetadata != null) {
      CatalogUtil.dropTableData(ops.io(), lastMetadata);
    }

    return true;
  }

  /**
   * 列出指定命名空间下的所有表。
   *
   * @param namespace 命名空间，为空时列出所有表
   * @return 按名称排序的表标识列表
   * @throws NoSuchNamespaceException 命名空间不存在且非空时抛出
   */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    if (!namespaceExists(namespace) && !namespace.isEmpty()) {
      throw new NoSuchNamespaceException(
          "Cannot list tables for namespace. Namespace does not exist: %s", namespace);
    }

    return tables.keySet().stream()
        .filter(t -> namespace.isEmpty() || t.namespace().equals(namespace))
        .sorted(Comparator.comparing(TableIdentifier::toString))
        .collect(Collectors.toList());
  }

  /**
   * 重命名表，整体操作加 synchronized 保证原子性。
   *
   * <p>逻辑：检查源表存在、目标命名空间存在且目标表不存在，随后以新标识写入旧 location 并删除原标识。
   *
   * @param from 源表标识
   * @param to 目标表标识
   * @throws NoSuchNamespaceException 目标命名空间不存在
   * @throws NoSuchTableException 源表不存在
   * @throws AlreadyExistsException 目标表已存在
   */
  @Override
  public synchronized void renameTable(TableIdentifier from, TableIdentifier to) {
    if (from.equals(to)) {
      return;
    }

    if (!namespaceExists(to.namespace())) {
      throw new NoSuchNamespaceException(
          "Cannot rename %s to %s. Namespace does not exist: %s", from, to, to.namespace());
    }

    String fromLocation = tables.get(from);
    if (null == fromLocation) {
      throw new NoSuchTableException("Cannot rename %s to %s. Table does not exist", from, to);
    }

    if (tables.containsKey(to)) {
      throw new AlreadyExistsException("Cannot rename %s to %s. Table already exists", from, to);
    }

    tables.put(to, fromLocation);
    tables.remove(from);
  }

  /** 创建命名空间，不带属性。 */
  @Override
  public void createNamespace(Namespace namespace) {
    createNamespace(namespace, Collections.emptyMap());
  }

  /**
   * 创建命名空间并写入属性（存为不可变副本）。
   *
   * @param namespace 命名空间
   * @param metadata 命名空间属性
   * @throws AlreadyExistsException 命名空间已存在
   */
  @Override
  public void createNamespace(Namespace namespace, Map<String, String> metadata) {
    if (namespaceExists(namespace)) {
      throw new AlreadyExistsException(
          "Cannot create namespace %s. Namespace already exists", namespace);
    }

    namespaces.put(namespace, ImmutableMap.copyOf(metadata));
  }

  /** @return 命名空间是否存在 */
  @Override
  public boolean namespaceExists(Namespace namespace) {
    return namespaces.containsKey(namespace);
  }

  /**
   * 删除命名空间；若命名空间下仍有表则抛出异常。
   *
   * @param namespace 命名空间
   * @return 命名空间存在并删除返回 true，否则 false
   * @throws NamespaceNotEmptyException 命名空间非空时抛出
   */
  @Override
  public boolean dropNamespace(Namespace namespace) throws NamespaceNotEmptyException {
    if (!namespaceExists(namespace)) {
      return false;
    }

    List<TableIdentifier> tableIdentifiers = listTables(namespace);
    if (!tableIdentifiers.isEmpty()) {
      throw new NamespaceNotEmptyException(
          "Namespace %s is not empty. Contains %d table(s).", namespace, tableIdentifiers.size());
    }

    return namespaces.remove(namespace) != null;
  }

  /**
   * 为命名空间追加属性，重复键以新值覆盖。
   *
   * @param namespace 命名空间
   * @param properties 待设置属性
   * @return 始终返回 true
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public boolean setProperties(Namespace namespace, Map<String, String> properties)
      throws NoSuchNamespaceException {
    if (!namespaceExists(namespace)) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }

    namespaces.computeIfPresent(
        namespace,
        (k, v) ->
            ImmutableMap.<String, String>builder().putAll(v).putAll(properties).buildKeepingLast());

    return true;
  }

  /**
   * 移除命名空间的指定属性。
   *
   * @param namespace 命名空间
   * @param properties 待移除属性键集合
   * @return 始终返回 true
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public boolean removeProperties(Namespace namespace, Set<String> properties)
      throws NoSuchNamespaceException {
    if (!namespaceExists(namespace)) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }

    namespaces.computeIfPresent(
        namespace,
        (k, v) -> {
          Map<String, String> newProperties = Maps.newHashMap(v);
          properties.forEach(newProperties::remove);
          return ImmutableMap.copyOf(newProperties);
        });

    return true;
  }

  /**
   * 加载命名空间属性（返回不可变副本）。
   *
   * @param namespace 命名空间
   * @return 命名空间属性
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace namespace)
      throws NoSuchNamespaceException {
    Map<String, String> properties = namespaces.get(namespace);
    if (properties == null) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }

    return ImmutableMap.copyOf(properties);
  }

  /** @return 所有顶层命名空间（取每个 namespace 的第一级，去重排序） */
  @Override
  public List<Namespace> listNamespaces() {
    return namespaces.keySet().stream()
        .filter(n -> !n.isEmpty())
        .map(n -> n.level(0))
        .distinct()
        .sorted()
        .map(Namespace::of)
        .collect(Collectors.toList());
  }

  /**
   * 列出指定命名空间下的直接子命名空间。
   *
   * <p>逻辑：以前缀匹配筛选所有命名空间，再截取到指定层级 +1 作为直接子节点，去重排序返回。
   *
   * @param namespace 命名空间，为空时列出所有顶层命名空间
   * @return 直接子命名空间列表
   * @throws NoSuchNamespaceException 命名空间既不存在也非任何命名空间前缀时抛出
   */
  @Override
  public List<Namespace> listNamespaces(Namespace namespace) throws NoSuchNamespaceException {
    final String searchNamespaceString =
        namespace.isEmpty() ? "" : DOT.join(namespace.levels()) + ".";
    final int searchNumberOfLevels = namespace.levels().length;

    List<Namespace> filteredNamespaces =
        namespaces.keySet().stream()
            .filter(n -> DOT.join(n.levels()).startsWith(searchNamespaceString))
            .collect(Collectors.toList());

    // If the namespace does not exist and the namespace is not a prefix of another namespace,
    // throw an exception.
    if (!namespaces.containsKey(namespace) && filteredNamespaces.isEmpty()) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }

    return filteredNamespaces.stream()
        // List only the child-namespaces roots.
        .map(n -> Namespace.of(Arrays.copyOf(n.levels(), searchNumberOfLevels + 1)))
        .distinct()
        .sorted(Comparator.comparing(n -> DOT.join(n.levels())))
        .collect(Collectors.toList());
  }

  /** 关闭 Catalog，清空所有命名空间与表映射。 */
  @Override
  public void close() throws IOException {
    namespaces.clear();
    tables.clear();
  }

  /** 内存版表操作实现，基于 tables 映射做刷新与提交。 */
  private class InMemoryTableOperations extends BaseMetastoreTableOperations {
    private final FileIO fileIO;
    private final TableIdentifier tableIdentifier;

    InMemoryTableOperations(FileIO fileIO, TableIdentifier tableIdentifier) {
      this.fileIO = fileIO;
      this.tableIdentifier = tableIdentifier;
    }

    /** 刷新元数据：从 tables 取最新 location，不存在则禁用刷新，存在则按 metadata location 刷新。 */
    @Override
    public void doRefresh() {
      String latestLocation = tables.get(tableIdentifier);
      if (latestLocation == null) {
        disableRefresh();
      } else {
        refreshFromMetadataLocation(latestLocation);
      }
    }

    /**
     * 提交表元数据：写入新 metadata 文件并通过 {@code tables.compute} 做 CAS 比较。
     *
     * <p>逻辑：若 base 为 null 校验命名空间存在；使用 tables.compute 比较 existingLocation 与 oldLocation，不一致则按并发修改抛
     * {@link CommitFailedException} 或 {@link AlreadyExistsException}。
     *
     * @param base 旧元数据，新建表时为 null
     * @param metadata 新元数据
     */
    @Override
    public void doCommit(TableMetadata base, TableMetadata metadata) {
      String newLocation = writeNewMetadata(metadata, currentVersion() + 1);
      String oldLocation = base == null ? null : base.metadataFileLocation();

      if (null == base && !namespaceExists(tableIdentifier.namespace())) {
        throw new NoSuchNamespaceException(
            "Cannot create table %s. Namespace does not exist: %s",
            tableIdentifier, tableIdentifier.namespace());
      }

      tables.compute(
          tableIdentifier,
          (k, existingLocation) -> {
            if (!Objects.equal(existingLocation, oldLocation)) {
              if (null == base) {
                throw new AlreadyExistsException("Table already exists: %s", tableName());
              }

              throw new CommitFailedException(
                  "Cannot commit to table %s metadata location from %s to %s "
                      + "because it has been concurrently modified to %s",
                  tableIdentifier, oldLocation, newLocation, existingLocation);
            }
            return newLocation;
          });
    }

    /** @return 该表操作使用的 FileIO */
    @Override
    public FileIO io() {
      return fileIO;
    }

    /** @return 表标识的字符串形式 */
    @Override
    protected String tableName() {
      return tableIdentifier.toString();
    }
  }
}
