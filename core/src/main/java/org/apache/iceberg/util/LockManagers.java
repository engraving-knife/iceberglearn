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
package org.apache.iceberg.util;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.LockManager;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.MoreExecutors;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 锁管理器工厂与基础实现集合，为 Iceberg 表提交提供乐观/悲观并发控制能力。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供默认的进程内 {@link InMemoryLockManager}，用于单 JVM 内的提交互斥。
 *   <li>通过 {@link #from(Map)} 按 catalog 属性加载自定义 {@link LockManager} 实现（反射构造）。
 *   <li>提供 {@link BaseLockManager} 抽象基类，封装获取锁超时、重试间隔、心跳间隔/超时等通用参数 及共享心跳调度器，供具体锁管理器继承。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>可插拔：锁实现通过 {@code lock.impl} 属性指定类名，用 {@link DynConstructors} 反射加载， 支持外部模块（如 Hive metastore
 *       锁、Redis 锁等）扩展。
 *   <li>心跳续约：长事务持锁期间通过定时心跳延长过期时间，避免因提交缓慢导致锁被误判失效。
 *   <li>共享调度器：{@link BaseLockManager#scheduler()} 使用双重检查锁的单例模式，所有锁实例 共用一个 daemon 线程池，减少线程开销。
 * </ul>
 *
 * <p>上下游关系：被各 Catalog 实现（如 HadoopCatalog、HiveCatalog 等）在提交时调用； 依赖 api 模块的 {@link LockManager} 接口与
 * common 模块的 {@link DynConstructors}。
 */
public class LockManagers {

  private static final LockManager LOCK_MANAGER_DEFAULT =
      new InMemoryLockManager(Maps.newHashMap());

  private LockManagers() {}

  /**
   * 返回默认的进程内锁管理器实例（{@link InMemoryLockManager}）。
   *
   * @return 默认锁管理器
   */
  public static LockManager defaultLockManager() {
    return LOCK_MANAGER_DEFAULT;
  }

  /**
   * 根据 catalog 属性创建锁管理器：若指定了 {@code lock.impl} 则反射加载对应实现并初始化， 否则返回默认的进程内锁管理器。
   *
   * @param properties catalog 属性集
   * @return 锁管理器实例
   */
  public static LockManager from(Map<String, String> properties) {
    if (properties.containsKey(CatalogProperties.LOCK_IMPL)) {
      return loadLockManager(properties.get(CatalogProperties.LOCK_IMPL), properties);
    } else {
      return defaultLockManager();
    }
  }

  /**
   * 反射加载并初始化指定的锁管理器实现。
   *
   * <p>逻辑：用 {@link DynConstructors} 查找目标类的无参构造器，newInstance 创建实例后调用 {@code initialize(properties)}
   * 完成配置；构造器缺失或类型不匹配时抛出 IllegalArgumentException。
   *
   * @param impl 锁管理器实现类全限定名
   * @param properties catalog 属性集
   * @return 已初始化的锁管理器
   * @throws IllegalArgumentException 若类缺少无参构造器或未实现 LockManager
   */
  private static LockManager loadLockManager(String impl, Map<String, String> properties) {
    DynConstructors.Ctor<LockManager> ctor;
    try {
      ctor = DynConstructors.builder(LockManager.class).hiddenImpl(impl).buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize LockManager, missing no-arg constructor: %s", impl), e);
    }

    LockManager lockManager;
    try {
      lockManager = ctor.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize LockManager, %s does not implement LockManager.", impl),
          e);
    }

    lockManager.initialize(properties);
    return lockManager;
  }

  /** 锁管理器抽象基类，封装通用的超时/重试/心跳参数及共享心跳调度器，供具体实现继承。 */
  public abstract static class BaseLockManager implements LockManager {

    private static volatile ScheduledExecutorService scheduler;

    private long acquireTimeoutMs;
    private long acquireIntervalMs;
    private long heartbeatIntervalMs;
    private long heartbeatTimeoutMs;
    private int heartbeatThreads;

    /** 返回 心跳超时时间（毫秒），超过后锁自动失效。 */
    public long heartbeatTimeoutMs() {
      return heartbeatTimeoutMs;
    }

    /** 返回 心跳发送间隔（毫秒）。 */
    public long heartbeatIntervalMs() {
      return heartbeatIntervalMs;
    }

    /** 返回 获取锁失败后的重试间隔（毫秒）。 */
    public long acquireIntervalMs() {
      return acquireIntervalMs;
    }

    /** 返回 获取锁的总超时时间（毫秒）。 */
    public long acquireTimeoutMs() {
      return acquireTimeoutMs;
    }

    /** 返回 心跳线程池大小。 */
    public int heartbeatThreads() {
      return heartbeatThreads;
    }

    /**
     * 获取共享的心跳调度线程池（双重检查锁单例）。
     *
     * <p>逻辑：首次调用时创建一个 daemon 类型的 {@link ScheduledThreadPoolExecutor}，线程名以 "iceberg-lock-manager-%d"
     * 标识；后续调用直接复用。使用 {@link MoreExecutors#getExitingScheduledExecutorService} 包装，使 JVM 退出时线程池自动关闭。
     *
     * @return 共享心跳调度器
     */
    public ScheduledExecutorService scheduler() {
      if (scheduler == null) {
        synchronized (BaseLockManager.class) {
          if (scheduler == null) {
            scheduler =
                MoreExecutors.getExitingScheduledExecutorService(
                    (ScheduledThreadPoolExecutor)
                        Executors.newScheduledThreadPool(
                            heartbeatThreads(),
                            new ThreadFactoryBuilder()
                                .setDaemon(true)
                                .setNameFormat("iceberg-lock-manager-%d")
                                .build()));
          }
        }
      }

      return scheduler;
    }

    /**
     * 从 catalog 属性中读取获取锁超时、重试间隔、心跳间隔/超时/线程数等参数。
     *
     * @param properties catalog 属性集
     */
    @Override
    public void initialize(Map<String, String> properties) {
      this.acquireTimeoutMs =
          PropertyUtil.propertyAsLong(
              properties,
              CatalogProperties.LOCK_ACQUIRE_TIMEOUT_MS,
              CatalogProperties.LOCK_ACQUIRE_TIMEOUT_MS_DEFAULT);
      this.acquireIntervalMs =
          PropertyUtil.propertyAsLong(
              properties,
              CatalogProperties.LOCK_ACQUIRE_INTERVAL_MS,
              CatalogProperties.LOCK_ACQUIRE_INTERVAL_MS_DEFAULT);
      this.heartbeatIntervalMs =
          PropertyUtil.propertyAsLong(
              properties,
              CatalogProperties.LOCK_HEARTBEAT_INTERVAL_MS,
              CatalogProperties.LOCK_HEARTBEAT_INTERVAL_MS_DEFAULT);
      this.heartbeatTimeoutMs =
          PropertyUtil.propertyAsLong(
              properties,
              CatalogProperties.LOCK_HEARTBEAT_TIMEOUT_MS,
              CatalogProperties.LOCK_HEARTBEAT_TIMEOUT_MS_DEFAULT);
      this.heartbeatThreads =
          PropertyUtil.propertyAsInt(
              properties,
              CatalogProperties.LOCK_HEARTBEAT_THREADS,
              CatalogProperties.LOCK_HEARTBEAT_THREADS_DEFAULT);
    }
  }

  /**
   * 基于进程内并发 Map 的 {@link LockManager} 实现，仅适用于测试或单 JVM 内的提交互斥。
   *
   * <p>设计意图：使用 {@link java.util.concurrent.ConcurrentMap} 存储锁实体与心跳任务，跨进程不可见。 锁通过
   * putIfAbsent/replace 的 CAS 语义保证并发安全；持锁期间以固定速率发送心跳延长过期时间。
   */
  static class InMemoryLockManager extends BaseLockManager {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryLockManager.class);

    private static final Map<String, InMemoryLockContent> LOCKS = Maps.newConcurrentMap();
    private static final Map<String, ScheduledFuture<?>> HEARTBEATS = Maps.newHashMap();

    InMemoryLockManager(Map<String, String> properties) {
      initialize(properties);
    }

    /**
     * 尝试获取一次锁（不重试）。
     *
     * <p>逻辑：读取当前锁内容，若未过期则抛 IllegalStateException；否则计算新过期时间并用 putIfAbsent（锁不存在）或
     * replace（锁已存在但已过期）CAS 写入。成功后取消旧心跳、注册新心跳 定时任务续约；失败则抛 IllegalStateException。
     *
     * @param entityId 被锁实体标识（如表 ID）
     * @param ownerId 锁持有者标识
     * @throws IllegalStateException 若锁被他人持有或 CAS 失败
     */
    @VisibleForTesting
    void acquireOnce(String entityId, String ownerId) {
      InMemoryLockContent content = LOCKS.get(entityId);
      if (content != null && content.expireMs() > System.currentTimeMillis()) {
        throw new IllegalStateException(
            String.format(
                "Lock for %s currently held by %s, expiration: %s",
                entityId, content.ownerId(), content.expireMs()));
      }

      long expiration = System.currentTimeMillis() + heartbeatTimeoutMs();
      boolean succeed;
      if (content == null) {
        InMemoryLockContent previous =
            LOCKS.putIfAbsent(entityId, new InMemoryLockContent(ownerId, expiration));
        succeed = previous == null;
      } else {
        succeed = LOCKS.replace(entityId, content, new InMemoryLockContent(ownerId, expiration));
      }

      if (succeed) {
        // cleanup old heartbeat
        if (HEARTBEATS.containsKey(entityId)) {
          HEARTBEATS.remove(entityId).cancel(false);
        }

        HEARTBEATS.put(
            entityId,
            scheduler()
                .scheduleAtFixedRate(
                    () -> {
                      InMemoryLockContent lastContent = LOCKS.get(entityId);
                      try {
                        long newExpiration = System.currentTimeMillis() + heartbeatTimeoutMs();
                        LOCKS.replace(
                            entityId, lastContent, new InMemoryLockContent(ownerId, newExpiration));
                      } catch (NullPointerException e) {
                        throw new RuntimeException(
                            "Cannot heartbeat to a deleted lock " + entityId, e);
                      }
                    },
                    0,
                    heartbeatIntervalMs(),
                    TimeUnit.MILLISECONDS));

      } else {
        throw new IllegalStateException("Unable to acquire lock " + entityId);
      }
    }

    /**
     * 带重试地获取锁：在超时时间内以指数退避策略反复调用 {@link #acquireOnce}。
     *
     * @param entityId 被锁实体标识
     * @param ownerId 锁持有者标识
     * @return 成功获取返回 true；超时未获取返回 false
     */
    @Override
    public boolean acquire(String entityId, String ownerId) {
      try {
        Tasks.foreach(entityId)
            .retry(Integer.MAX_VALUE - 1)
            .onlyRetryOn(IllegalStateException.class)
            .throwFailureWhenFinished()
            .exponentialBackoff(acquireIntervalMs(), acquireIntervalMs(), acquireTimeoutMs(), 1)
            .run(id -> acquireOnce(id, ownerId));
        return true;
      } catch (IllegalStateException e) {
        return false;
      }
    }

    /**
     * 释放锁：校验持有者后取消心跳并移除锁条目。
     *
     * @param entityId 被锁实体标识
     * @param ownerId 锁持有者标识
     * @return 释放成功返回 true；锁不存在或持有者不匹配返回 false
     */
    @Override
    public boolean release(String entityId, String ownerId) {
      InMemoryLockContent currentContent = LOCKS.get(entityId);
      if (currentContent == null) {
        LOG.error("Cannot find lock for entity {}", entityId);
        return false;
      }

      if (!currentContent.ownerId().equals(ownerId)) {
        LOG.error(
            "Cannot unlock {} by {}, current owner: {}",
            entityId,
            ownerId,
            currentContent.ownerId());
        return false;
      }

      HEARTBEATS.remove(entityId).cancel(false);
      LOCKS.remove(entityId);
      return true;
    }

    /** 关闭锁管理器：取消所有心跳任务并清空锁表。 */
    @Override
    public void close() {
      HEARTBEATS.values().forEach(future -> future.cancel(false));
      HEARTBEATS.clear();
      LOCKS.clear();
    }
  }

  /** 锁内容：记录持有者与过期时间，作为并发 Map 的值类型。 */
  private static class InMemoryLockContent {
    private final String ownerId;
    private final long expireMs;

    InMemoryLockContent(String ownerId, long expireMs) {
      this.ownerId = ownerId;
      this.expireMs = expireMs;
    }

    public long expireMs() {
      return expireMs;
    }

    public String ownerId() {
      return ownerId;
    }
  }
}
