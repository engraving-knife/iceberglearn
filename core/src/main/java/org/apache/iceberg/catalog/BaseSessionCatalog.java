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
package org.apache.iceberg.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;

/**
 * 文件级说明：会话级 Catalog 抽象基类，为面向多会话/多租户的 Catalog 提供通用骨架。
 *
 * <p>所属模块：iceberg-core（catalog 包），位于 Catalog 抽象层，向上实现 {@link SessionCatalog}， 向下被子类（如 REST
 * Catalog）实现以提供具体的会话化表/命名空间操作。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 Catalog 名称与初始化属性，统一管理生命周期。
 *   <li>按 {@link SessionContext#sessionId()} 缓存每个会话对应的 {@link Catalog} 视图， 使得同一会话的多次调用复用同一个
 *       AsCatalog 适配器。
 *   <li>通过内部类 {@link AsCatalog} 将“会话化”的调用适配到底层会话无关 Catalog 方法上， 把 {@link Catalog} / {@link
 *       SupportsNamespaces} 接口的方法转发到带 context 的方法。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>会话隔离：引擎（Spark/Flink）中一条 SQL 可能跨多个会话/用户，BaseSessionCatalog 以 sessionId 为键缓存
 *       AsCatalog，实现会话级别的视图隔离，同时避免重复构造适配器。
 *   <li>缓存过期策略：使用 Caffeine 的 expireAfterAccess(10 分钟)，使长期不活跃的会话视图 能被回收，防止内存泄漏。
 *   <li>适配器模式：AsCatalog 把 {@link Catalog} 接口“去会话化”，让调用方可以用标准 Catalog API 操作某个会话上下文，减少接口改造面。
 * </ul>
 *
 * <p>上下游关系：被 {@link SessionCatalog} 的具体实现继承；AsCatalog 被 {@link #asCatalog} 产出， 供引擎集成层以标准 Catalog
 * 方式访问特定会话。
 */
public abstract class BaseSessionCatalog implements SessionCatalog {
  private final Cache<String, Catalog> catalogs =
      Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

  private String name = null;
  private Map<String, String> properties = null;

  /** 初始化 Catalog 名称与属性，由子类在加载时调用。 */
  @Override
  public void initialize(String catalogName, Map<String, String> props) {
    this.name = catalogName;
    this.properties = props;
  }

  /** 返回 Catalog 名称。 */
  @Override
  public String name() {
    return name;
  }

  /** 返回初始化时传入的属性快照。 */
  @Override
  public Map<String, String> properties() {
    return properties;
  }

  /**
   * 获取指定会话上下文对应的 {@link Catalog} 视图。
   *
   * <p>逻辑：以 sessionId 为键查 Caffeine 缓存；未命中则惰性构造一个绑定该 context 的 {@link AsCatalog}
   * 并缓存，命中则直接复用，从而实现会话级隔离与适配器复用。
   *
   * @param context 当前会话上下文
   * @return 与该会话绑定的 Catalog 适配器
   */
  public Catalog asCatalog(SessionContext context) {
    return catalogs.get(context.sessionId(), id -> new AsCatalog(context));
  }

  /**
   * 在指定会话上下文中以标准 {@link Catalog} 视图执行一个任务并返回结果。
   *
   * @param context 当前会话上下文
   * @param task 接收 Catalog 的回调
   * @param <T> 任务返回类型
   * @return 任务执行结果
   */
  public <T> T withContext(SessionContext context, Function<Catalog, T> task) {
    return task.apply(asCatalog(context));
  }

  /**
   * 会话化 Catalog 适配器：把标准 {@link Catalog} 与 {@link SupportsNamespaces} 接口的方法 全部转发到外层 {@link
   * BaseSessionCatalog} 的带 context 方法上。
   *
   * <p>设计意图：让上层调用方无需感知会话存在，直接以 Catalog API 操作某会话的表与命名空间。
   */
  public class AsCatalog implements Catalog, SupportsNamespaces {
    private final SessionContext context;

    private AsCatalog(SessionContext context) {
      this.context = context;
    }

    /** 返回外层 Catalog 名称。 */
    @Override
    public String name() {
      return BaseSessionCatalog.this.name();
    }

    /** 转发：在当前会话下列出指定命名空间下的表。 */
    @Override
    public List<TableIdentifier> listTables(Namespace namespace) {
      return BaseSessionCatalog.this.listTables(context, namespace);
    }

    /** 转发：在当前会话下构造建表构建器。 */
    @Override
    public TableBuilder buildTable(TableIdentifier ident, Schema schema) {
      return BaseSessionCatalog.this.buildTable(context, ident, schema);
    }

    /** 转发：在当前会话下按已有元数据文件注册表。 */
    @Override
    public Table registerTable(TableIdentifier ident, String metadataFileLocation) {
      return BaseSessionCatalog.this.registerTable(context, ident, metadataFileLocation);
    }

    /** 转发：在当前会话下判断表是否存在。 */
    @Override
    public boolean tableExists(TableIdentifier ident) {
      return BaseSessionCatalog.this.tableExists(context, ident);
    }

    /** 转发：在当前会话下加载表。 */
    @Override
    public Table loadTable(TableIdentifier ident) {
      return BaseSessionCatalog.this.loadTable(context, ident);
    }

    /** 转发：在当前会话下删除表（仅删元数据，不清理数据文件）。 */
    @Override
    public boolean dropTable(TableIdentifier ident) {
      return BaseSessionCatalog.this.dropTable(context, ident);
    }

    /**
     * 转发：在当前会话下删除表。
     *
     * <p>逻辑：purge 为 true 时调用 purgeTable 清理数据文件，否则仅删元数据。
     *
     * @param ident 表标识
     * @param purge 是否清理数据文件
     * @return 删除是否成功
     */
    @Override
    public boolean dropTable(TableIdentifier ident, boolean purge) {
      if (purge) {
        return BaseSessionCatalog.this.purgeTable(context, ident);
      } else {
        return BaseSessionCatalog.this.dropTable(context, ident);
      }
    }

    /** 转发：在当前会话下重命名表。 */
    @Override
    public void renameTable(TableIdentifier from, TableIdentifier to) {
      BaseSessionCatalog.this.renameTable(context, from, to);
    }

    /** 转发：在当前会话下使表的缓存失效。 */
    @Override
    public void invalidateTable(TableIdentifier ident) {
      BaseSessionCatalog.this.invalidateTable(context, ident);
    }

    /** 转发：在当前会话下创建命名空间。 */
    @Override
    public void createNamespace(Namespace namespace, Map<String, String> metadata) {
      BaseSessionCatalog.this.createNamespace(context, namespace, metadata);
    }

    /** 转发：在当前会话下列出命名空间。 */
    @Override
    public List<Namespace> listNamespaces(Namespace namespace) {
      return BaseSessionCatalog.this.listNamespaces(context, namespace);
    }

    /** 转发：在当前会话下加载命名空间元数据。 */
    @Override
    public Map<String, String> loadNamespaceMetadata(Namespace namespace) {
      return BaseSessionCatalog.this.loadNamespaceMetadata(context, namespace);
    }

    /** 转发：在当前会话下删除命名空间，命名空间非空时抛 {@link NamespaceNotEmptyException}。 */
    @Override
    public boolean dropNamespace(Namespace namespace) throws NamespaceNotEmptyException {
      return BaseSessionCatalog.this.dropNamespace(context, namespace);
    }

    /** 转发：在当前会话下更新命名空间属性（updates 非空、removals 为空）。 */
    @Override
    public boolean setProperties(Namespace namespace, Map<String, String> updates) {
      return BaseSessionCatalog.this.updateNamespaceMetadata(
          context, namespace, updates, ImmutableSet.of());
    }

    /** 转发：在当前会话下移除命名空间属性（removals 非空、updates 为空）。 */
    @Override
    public boolean removeProperties(Namespace namespace, Set<String> removals) {
      return BaseSessionCatalog.this.updateNamespaceMetadata(
          context, namespace, ImmutableMap.of(), removals);
    }

    /** 转发：在当前会话下判断命名空间是否存在。 */
    @Override
    public boolean namespaceExists(Namespace namespace) {
      return BaseSessionCatalog.this.namespaceExists(context, namespace);
    }
  }
}
