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

import java.util.concurrent.TimeUnit;

/**
 * Catalog 配置属性常量类：集中定义所有 Catalog 实现通用的配置键名与默认值。
 *
 * <p>所属模块：iceberg-core，作为各 Catalog（HiveCatalog、HadoopCatalog、JdbcCatalog、 RESTCatalog 等）共享的配置契约。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 Catalog 实现类、FileIO 实现、warehouse 路径、表默认/覆盖属性前缀等核心配置键。
 *   <li>定义表元数据缓存、manifest 缓存、客户端连接池、锁、认证等运行期参数及其默认值。
 * </ul>
 *
 * <p>设计意图：以常量类集中管理配置键名，避免各 Catalog 实现中硬编码字符串导致拼写不一致； 默认值与键名成对声明，便于调用方 {@code
 * PropertyUtil.propertyAsLong(...)} 等工具读取。 缓存相关参数区分"表元数据缓存"与"manifest 文件 IO 缓存"两层，分别服务不同场景。
 *
 * <p>上下游关系：被各 Catalog 实现、{@code CatalogUtil}、{@code CachingCatalog}、 {@code
 * ClientPool}、锁与认证模块读取；最终由用户在 catalog 配置中传入。
 */
public class CatalogProperties {

  private CatalogProperties() {}

  /** Catalog 实现类全限定名配置键。 */
  public static final String CATALOG_IMPL = "catalog-impl";
  /** FileIO 实现类全限定名配置键。 */
  public static final String FILE_IO_IMPL = "io-impl";
  /** Warehouse 根路径配置键。 */
  public static final String WAREHOUSE_LOCATION = "warehouse";
  /** 表默认属性前缀：以此前缀开头的配置将作为新建表的默认属性。 */
  public static final String TABLE_DEFAULT_PREFIX = "table-default.";
  /** 表属性覆盖前缀：以此前缀开头的配置将覆盖新建表属性。 */
  public static final String TABLE_OVERRIDE_PREFIX = "table-override.";
  /** Metrics Reporter 实现类全限定名配置键。 */
  public static final String METRICS_REPORTER_IMPL = "metrics-reporter-impl";

  /**
   * 控制是否在加载表时缓存表条目。
   *
   * <p>若 {@link #CACHE_EXPIRATION_INTERVAL_MS} 设为 0，本值将被忽略且缓存禁用。
   */
  public static final String CACHE_ENABLED = "cache-enabled";

  /** {@link #CACHE_ENABLED} 默认值：启用缓存。 */
  public static final boolean CACHE_ENABLED_DEFAULT = true;

  /** 控制缓存表条目时是否使用大小写敏感的键。 */
  public static final String CACHE_CASE_SENSITIVE = "cache.case-sensitive";

  /** {@link #CACHE_CASE_SENSITIVE} 默认值：大小写敏感。 */
  public static final boolean CACHE_CASE_SENSITIVE_DEFAULT = true;

  /**
   * 控制 catalog 中表条目的缓存存活时长。
   *
   * <p>{@code cache.expiration-interval-ms} 不同取值的行为：
   *
   * <ul>
   *   <li>0 - 缓存与过期均禁用
   *   <li>负值 - 关闭过期，条目仅在刷新等场景失效
   *   <li>正值 - 条目在指定毫秒数未访问后过期
   * </ul>
   */
  public static final String CACHE_EXPIRATION_INTERVAL_MS = "cache.expiration-interval-ms";

  /** {@link #CACHE_EXPIRATION_INTERVAL_MS} 默认值：30 秒。 */
  public static final long CACHE_EXPIRATION_INTERVAL_MS_DEFAULT = TimeUnit.SECONDS.toMillis(30);
  /** {@link #CACHE_EXPIRATION_INTERVAL_MS} 关闭过期的特殊值：-1。 */
  public static final long CACHE_EXPIRATION_INTERVAL_MS_OFF = -1;

  /**
   * 控制读取 manifest 时是否启用 manifest 缓存。
   *
   * <p>启用 manifest 文件缓存需要以下约束同时成立：
   *
   * <ul>
   *   <li>{@link #IO_MANIFEST_CACHE_EXPIRATION_INTERVAL_MS} 必须为非负值。
   *   <li>{@link #IO_MANIFEST_CACHE_MAX_TOTAL_BYTES} 必须为正值。
   *   <li>{@link #IO_MANIFEST_CACHE_MAX_CONTENT_LENGTH} 必须为正值。
   * </ul>
   */
  public static final String IO_MANIFEST_CACHE_ENABLED = "io.manifest.cache-enabled";

  /** {@link #IO_MANIFEST_CACHE_ENABLED} 默认值：禁用。 */
  public static final boolean IO_MANIFEST_CACHE_ENABLED_DEFAULT = false;

  /**
   * 控制 manifest 缓存条目的最大存活时长。
   *
   * <p>必须为非负值。具体行为：
   *
   * <ul>
   *   <li>0 - 条目仅在因 {@link #IO_MANIFEST_CACHE_MAX_TOTAL_BYTES} 内存压力被驱逐时失效。
   *   <li>正值 - 条目在指定毫秒数未访问后过期
   * </ul>
   */
  public static final String IO_MANIFEST_CACHE_EXPIRATION_INTERVAL_MS =
      "io.manifest.cache.expiration-interval-ms";

  /** {@link #IO_MANIFEST_CACHE_EXPIRATION_INTERVAL_MS} 默认值：60 秒。 */
  public static final long IO_MANIFEST_CACHE_EXPIRATION_INTERVAL_MS_DEFAULT =
      TimeUnit.SECONDS.toMillis(60);

  /**
   * 控制 manifest 缓存的最大总字节数。
   *
   * <p>必须为正值。
   */
  public static final String IO_MANIFEST_CACHE_MAX_TOTAL_BYTES =
      "io.manifest.cache.max-total-bytes";

  /** {@link #IO_MANIFEST_CACHE_MAX_TOTAL_BYTES} 默认值：100MB。 */
  public static final long IO_MANIFEST_CACHE_MAX_TOTAL_BYTES_DEFAULT = 100 * 1024 * 1024;

  /**
   * 控制可纳入 manifest 缓存的单文件最大长度。
   *
   * <p>当 {@link org.apache.iceberg.io.InputFile} 长度超过此限制时不予缓存。必须为正值。
   */
  public static final String IO_MANIFEST_CACHE_MAX_CONTENT_LENGTH =
      "io.manifest.cache.max-content-length";

  /** {@link #IO_MANIFEST_CACHE_MAX_CONTENT_LENGTH} 默认值：8MB。 */
  public static final long IO_MANIFEST_CACHE_MAX_CONTENT_LENGTH_DEFAULT = 8 * 1024 * 1024;

  /** Catalog 服务 URI 配置键。 */
  public static final String URI = "uri";
  /** 客户端连接池大小配置键。 */
  public static final String CLIENT_POOL_SIZE = "clients";
  /** {@link #CLIENT_POOL_SIZE} 默认值：2。 */
  public static final int CLIENT_POOL_SIZE_DEFAULT = 2;
  /** 客户端连接池缓存驱逐间隔配置键。 */
  public static final String CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS =
      "client.pool.cache.eviction-interval-ms";
  /** {@link #CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS} 默认值：5 分钟。 */
  public static final long CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS_DEFAULT =
      TimeUnit.MINUTES.toMillis(5);
  /**
   * 除 {@link #URI} 外，用于组合客户端连接池缓存键的元素列表（逗号分隔）。
   *
   * <p>支持的键元素依 Catalog 实现而定。
   */
  public static final String CLIENT_POOL_CACHE_KEYS = "client-pool-cache-keys";

  /** 锁实现类全限定名配置键。 */
  public static final String LOCK_IMPL = "lock-impl";

  /** 锁心跳间隔配置键。 */
  public static final String LOCK_HEARTBEAT_INTERVAL_MS = "lock.heartbeat-interval-ms";
  /** {@link #LOCK_HEARTBEAT_INTERVAL_MS} 默认值：3 秒。 */
  public static final long LOCK_HEARTBEAT_INTERVAL_MS_DEFAULT = TimeUnit.SECONDS.toMillis(3);

  /** 锁心跳超时配置键。 */
  public static final String LOCK_HEARTBEAT_TIMEOUT_MS = "lock.heartbeat-timeout-ms";
  /** {@link #LOCK_HEARTBEAT_TIMEOUT_MS} 默认值：15 秒。 */
  public static final long LOCK_HEARTBEAT_TIMEOUT_MS_DEFAULT = TimeUnit.SECONDS.toMillis(15);

  /** 锁心跳线程数配置键。 */
  public static final String LOCK_HEARTBEAT_THREADS = "lock.heartbeat-threads";
  /** {@link #LOCK_HEARTBEAT_THREADS} 默认值：4。 */
  public static final int LOCK_HEARTBEAT_THREADS_DEFAULT = 4;

  /** 获取锁的重试间隔配置键。 */
  public static final String LOCK_ACQUIRE_INTERVAL_MS = "lock.acquire-interval-ms";
  /** {@link #LOCK_ACQUIRE_INTERVAL_MS} 默认值：5 秒。 */
  public static final long LOCK_ACQUIRE_INTERVAL_MS_DEFAULT = TimeUnit.SECONDS.toMillis(5);

  /** 获取锁的总超时配置键。 */
  public static final String LOCK_ACQUIRE_TIMEOUT_MS = "lock.acquire-timeout-ms";
  /** {@link #LOCK_ACQUIRE_TIMEOUT_MS} 默认值：3 分钟。 */
  public static final long LOCK_ACQUIRE_TIMEOUT_MS_DEFAULT = TimeUnit.MINUTES.toMillis(3);

  /** 锁表配置键（部分锁实现需要专用表）。 */
  public static final String LOCK_TABLE = "lock.table";

  /** 提交应用 ID 配置键。 */
  public static final String APP_ID = "app-id";
  /** 提交用户配置键。 */
  public static final String USER = "user";

  /** 认证会话超时配置键。 */
  public static final String AUTH_SESSION_TIMEOUT_MS = "auth.session-timeout-ms";
  /** {@link #AUTH_SESSION_TIMEOUT_MS} 默认值：1 小时。 */
  public static final long AUTH_SESSION_TIMEOUT_MS_DEFAULT = TimeUnit.HOURS.toMillis(1);
}
