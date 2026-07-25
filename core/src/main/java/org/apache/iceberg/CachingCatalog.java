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
package org.apache.iceberg;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 包装 Iceberg {@link Catalog} 并缓存已加载表，避免重复调用底层 Catalog 的开销。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 Catalog 装饰器模式的实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>缓存 {@link Table} 实例（基于 Caffeine softValues + 可选过期）。
 *   <li>当底层表被 drop/rename 时自动失效缓存条目，并联动失效关联的元数据表缓存。
 *   <li>支持大小写敏感/不敏感的表名规范化。
 *   <li>元数据表与原表共享 {@link TableOperations}，原表刷新时元数据表也同步刷新。
 * </ul>
 *
 * <p>设计意图：装饰器模式让缓存逻辑可叠加在任意 Catalog 之上；使用 softValues 避免内存溢出， 通过 expirationIntervalMillis
 * 控制缓存新鲜度。{@code expirationIntervalMillis < 0} 表示永不过期， {@code = 0} 表示禁用缓存（不应进入本类，构造方法会校验拒绝）。
 *
 * <p>上下游关系：被 HiveCatalog、JdbcCatalog 等具体 Catalog 通过 {@link #wrap(Catalog)} 静态方法包装； 上层被引擎
 * SessionCatalog 调用。
 *
 * <p>关于 {@code expirationIntervalMillis} 特殊值见 {@link
 * CatalogProperties#CACHE_EXPIRATION_INTERVAL_MS}。
 */
public class CachingCatalog implements Catalog {
  private static final Logger LOG = LoggerFactory.getLogger(CachingCatalog.class);

  /**
   * 包装 Catalog，缓存永不过期。
   *
   * @param catalog 被包装的底层 Catalog
   * @return 带缓存的 Catalog
   */
  public static Catalog wrap(Catalog catalog) {
    return wrap(catalog, CatalogProperties.CACHE_EXPIRATION_INTERVAL_MS_OFF);
  }

  /**
   * 包装 Catalog 并指定缓存过期间隔（默认大小写敏感）。
   *
   * @param catalog 被包装的底层 Catalog
   * @param expirationIntervalMillis 缓存过期毫秒数
   * @return 带缓存的 Catalog
   */
  public static Catalog wrap(Catalog catalog, long expirationIntervalMillis) {
    return wrap(catalog, true, expirationIntervalMillis);
  }

  /**
   * 包装 Catalog 并指定大小写敏感性与缓存过期间隔。
   *
   * @param catalog 被包装的底层 Catalog
   * @param caseSensitive 表名是否大小写敏感
   * @param expirationIntervalMillis 缓存过期毫秒数
   * @return 带缓存的 Catalog
   */
  public static Catalog wrap(
      Catalog catalog, boolean caseSensitive, long expirationIntervalMillis) {
    return new CachingCatalog(catalog, caseSensitive, expirationIntervalMillis);
  }

  private final Catalog catalog;
  private final boolean caseSensitive;

  @SuppressWarnings("checkstyle:VisibilityModifier")
  protected final long expirationIntervalMillis;

  @SuppressWarnings("checkstyle:VisibilityModifier")
  protected final Cache<TableIdentifier, Table> tableCache;

  /** 私有构造方法：使用系统 Ticker。 */
  private CachingCatalog(Catalog catalog, boolean caseSensitive, long expirationIntervalMillis) {
    this(catalog, caseSensitive, expirationIntervalMillis, Ticker.systemTicker());
  }

  @SuppressWarnings("checkstyle:VisibilityModifier")
  /**
   * 受保护构造方法，允许子类传入自定义 {@link Ticker}（用于测试控制时间）。
   *
   * <p>逻辑：校验 expirationIntervalMillis 非 0（0 表示禁用缓存，不应进入本类）； 保存底层 Catalog、大小写敏感性、过期间隔，并基于 ticker
   * 构建缓存。
   *
   * @param catalog 被包装的底层 Catalog
   * @param caseSensitive 表名是否大小写敏感
   * @param expirationIntervalMillis 缓存过期毫秒数；负值表示永不过期
   * @param ticker 时间源（测试可注入）
   */
  protected CachingCatalog(
      Catalog catalog, boolean caseSensitive, long expirationIntervalMillis, Ticker ticker) {
    Preconditions.checkArgument(
        expirationIntervalMillis != 0,
        "When %s is set to 0, the catalog cache should be disabled. This indicates a bug.",
        CatalogProperties.CACHE_EXPIRATION_INTERVAL_MS);
    this.catalog = catalog;
    this.caseSensitive = caseSensitive;
    this.expirationIntervalMillis = expirationIntervalMillis;
    this.tableCache = createTableCache(ticker);
  }

  /** 缓存移除监听器：当数据表因过期被移除时，联动失效其关联的所有元数据表缓存。 */
  class MetadataTableInvalidatingRemovalListener
      implements RemovalListener<TableIdentifier, Table> {
    @Override
    public void onRemoval(TableIdentifier tableIdentifier, Table table, RemovalCause cause) {
      LOG.debug("Evicted {} from the table cache ({})", tableIdentifier, cause);
      if (RemovalCause.EXPIRED.equals(cause)) {
        if (!MetadataTableUtils.hasMetadataTableName(tableIdentifier)) {
          tableCache.invalidateAll(metadataTableIdentifiers(tableIdentifier));
        }
      }
    }
  }

  /**
   * 构建 Caffeine 表缓存。
   *
   * <p>逻辑：始终使用 softValues；若 expirationIntervalMillis > 0，则配置访问后过期、 同步 removal 监听器与
   * ticker；否则构建不过期的缓存。
   */
  private Cache<TableIdentifier, Table> createTableCache(Ticker ticker) {
    Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder().softValues();

    if (expirationIntervalMillis > 0) {
      return cacheBuilder
          .removalListener(new MetadataTableInvalidatingRemovalListener())
          .executor(Runnable::run) // Makes the callbacks to removal listener synchronous
          .expireAfterAccess(Duration.ofMillis(expirationIntervalMillis))
          .ticker(ticker)
          .build();
    }

    return cacheBuilder.build();
  }

  /** 大小写规范化表标识：大小写不敏感时转为小写。 */
  private TableIdentifier canonicalizeIdentifier(TableIdentifier tableIdentifier) {
    if (caseSensitive) {
      return tableIdentifier;
    } else {
      return tableIdentifier.toLowerCase();
    }
  }

  /** 返回底层 Catalog 名称。 */
  @Override
  public String name() {
    return catalog.name();
  }

  /** 列出命名空间下的表（直接委托底层 Catalog，不缓存）。 */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    return catalog.listTables(namespace);
  }

  /**
   * 加载表：优先返回缓存，未命中则委托底层 Catalog 加载并缓存。
   *
   * <p>逻辑：先规范化标识并查询缓存；若命中直接返回。若标识是元数据表名（含 .entries/.snapshots 等后缀）， 则加载对应原表，复用其 {@link
   * TableOperations} 创建元数据表实例并缓存——这样原表刷新时元数据表也同步刷新。 否则用 Caffeine 的 get 接口原子地加载并缓存普通表。
   *
   * @param ident 表标识
   * @return 表实例（可能为元数据表）
   */
  @Override
  public Table loadTable(TableIdentifier ident) {
    TableIdentifier canonicalized = canonicalizeIdentifier(ident);
    Table cached = tableCache.getIfPresent(canonicalized);
    if (cached != null) {
      return cached;
    }

    if (MetadataTableUtils.hasMetadataTableName(canonicalized)) {
      TableIdentifier originTableIdentifier =
          TableIdentifier.of(canonicalized.namespace().levels());
      Table originTable = tableCache.get(originTableIdentifier, catalog::loadTable);

      // share TableOperations instance of origin table for all metadata tables, so that metadata
      // table instances are
      // also refreshed as well when origin table instance is refreshed.
      if (originTable instanceof HasTableOperations) {
        TableOperations ops = ((HasTableOperations) originTable).operations();
        MetadataTableType type = MetadataTableType.from(canonicalized.name());

        Table metadataTable =
            MetadataTableUtils.createMetadataTableInstance(
                ops, catalog.name(), originTableIdentifier, canonicalized, type);
        tableCache.put(canonicalized, metadataTable);
        return metadataTable;
      }
    }

    return tableCache.get(canonicalized, catalog::loadTable);
  }

  /** 删除表并失效缓存。 */
  @Override
  public boolean dropTable(TableIdentifier ident, boolean purge) {
    boolean dropped = catalog.dropTable(ident, purge);
    invalidateTable(ident);
    return dropped;
  }

  /** 重命名表并失效旧标识缓存（新标识下次加载时重新加载）。 */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    catalog.renameTable(from, to);
    invalidateTable(from);
  }

  /** 失效表缓存：同时失效底层 Catalog 缓存、本缓存中的表与所有关联元数据表。 */
  @Override
  public void invalidateTable(TableIdentifier ident) {
    catalog.invalidateTable(ident);
    TableIdentifier canonicalized = canonicalizeIdentifier(ident);
    tableCache.invalidate(canonicalized);
    tableCache.invalidateAll(metadataTableIdentifiers(canonicalized));
  }

  /** 注册表并失效缓存（下次加载时重新加载）。 */
  @Override
  public Table registerTable(TableIdentifier identifier, String metadataFileLocation) {
    Table table = catalog.registerTable(identifier, metadataFileLocation);
    invalidateTable(identifier);
    return table;
  }

  /** 枚举与给定表标识关联的所有元数据表标识（大写与小写两种形式）。 */
  private Iterable<TableIdentifier> metadataTableIdentifiers(TableIdentifier ident) {
    ImmutableList.Builder<TableIdentifier> builder = ImmutableList.builder();

    for (MetadataTableType type : MetadataTableType.values()) {
      // metadata table resolution is case insensitive right now
      builder.add(TableIdentifier.parse(ident + "." + type.name()));
      builder.add(TableIdentifier.parse(ident + "." + type.name().toLowerCase(Locale.ROOT)));
    }

    return builder.build();
  }

  /** 创建表构建器，返回带缓存语义的 {@link CachingTableBuilder}。 */
  @Override
  public TableBuilder buildTable(TableIdentifier identifier, Schema schema) {
    return new CachingTableBuilder(identifier, schema);
  }

  /**
   * 表构建器包装类：在底层构建器之上增加缓存语义。
   *
   * <p>设计要点：create 时通过 Caffeine 原子 get 接口避免并发重复创建； 事务类方法不直接修改缓存，而是在提交回调时失效缓存，保证一致性。
   */
  private class CachingTableBuilder implements TableBuilder {
    private final TableIdentifier ident;
    private final TableBuilder innerBuilder;

    private CachingTableBuilder(TableIdentifier identifier, Schema schema) {
      this.innerBuilder = catalog.buildTable(identifier, schema);
      this.ident = identifier;
    }

    @Override
    public TableBuilder withPartitionSpec(PartitionSpec spec) {
      innerBuilder.withPartitionSpec(spec);
      return this;
    }

    @Override
    public TableBuilder withSortOrder(SortOrder sortOrder) {
      innerBuilder.withSortOrder(sortOrder);
      return this;
    }

    @Override
    public TableBuilder withLocation(String location) {
      innerBuilder.withLocation(location);
      return this;
    }

    @Override
    public TableBuilder withProperties(Map<String, String> properties) {
      innerBuilder.withProperties(properties);
      return this;
    }

    @Override
    public TableBuilder withProperty(String key, String value) {
      innerBuilder.withProperty(key, value);
      return this;
    }

    /**
     * 创建表。
     *
     * <p>逻辑：通过 Caffeine 原子 get 接口确保并发下只创建一次；若缓存中已存在（created 未置 true）， 则抛出 AlreadyExistsException。
     */
    @Override
    public Table create() {
      AtomicBoolean created = new AtomicBoolean(false);
      Table table =
          tableCache.get(
              canonicalizeIdentifier(ident),
              identifier -> {
                created.set(true);
                return innerBuilder.create();
              });

      if (!created.get()) {
        throw new AlreadyExistsException("Table already exists: %s", ident);
      }

      return table;
    }

    @Override
    public Transaction createTransaction() {
      // create a new transaction without altering the cache. the table doesn't exist until the
      // transaction is
      // committed. if the table is created before the transaction commits, any cached version is
      // correct and the
      // transaction create will fail. if the transaction commits before another create, then the
      // cache will be empty.
      return innerBuilder.createTransaction();
    }

    @Override
    public Transaction replaceTransaction() {
      // create a new transaction without altering the cache. the table doesn't change until the
      // transaction is
      // committed. when the transaction commits, invalidate the table in the cache if it is
      // present.
      return CommitCallbackTransaction.addCallback(
          innerBuilder.replaceTransaction(), () -> invalidateTable(ident));
    }

    @Override
    public Transaction createOrReplaceTransaction() {
      // create a new transaction without altering the cache. the table doesn't change until the
      // transaction is
      // committed. when the transaction commits, invalidate the table in the cache if it is
      // present.
      return CommitCallbackTransaction.addCallback(
          innerBuilder.createOrReplaceTransaction(), () -> invalidateTable(ident));
    }
  }
}
