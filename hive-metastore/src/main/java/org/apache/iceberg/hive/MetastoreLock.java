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
package org.apache.iceberg.hive;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.LockComponent;
import org.apache.hadoop.hive.metastore.api.LockLevel;
import org.apache.hadoop.hive.metastore.api.LockRequest;
import org.apache.hadoop.hive.metastore.api.LockResponse;
import org.apache.hadoop.hive.metastore.api.LockState;
import org.apache.hadoop.hive.metastore.api.LockType;
import org.apache.hadoop.hive.metastore.api.ShowLocksRequest;
import org.apache.hadoop.hive.metastore.api.ShowLocksResponse;
import org.apache.hadoop.hive.metastore.api.ShowLocksResponseElement;
import org.apache.iceberg.ClientPool;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.iceberg.util.Tasks;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Hive Metastore 的表级锁实现。
 *
 * <p>所属模块：iceberg-hive-metastore（表元数据提交的并发控制层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link HiveLock} 接口，通过 HMS Thrift 接口对表加排他锁。
 *   <li>在持锁期间启动后台心跳线程定期续约，防止锁因超时被 HMS 自动释放。
 *   <li>提供进程级 JVM 锁（ReentrantLock），避免同一 JVM 内多线程对同一表并发提交时 产生不必要的 HMS 锁竞争。
 *   <li>支持锁创建失败重试、锁等待超时、锁查找等容错机制。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>双层锁：JVM 锁（进程内互斥）+ HMS 锁（跨进程互斥），减少 HMS 锁请求量。
 *   <li>心跳续约：HMS 锁有超时机制，长时间提交需定期 heartbeat 保活，否则锁会被 HMS 回收 导致其他写入者并发提交。
 *   <li>agentInfo 标识：使用 UUID 标识锁请求，配合 showLocks API 实现锁查找与容错恢复 （Hive 2+）。
 *   <li>指数退避重试：锁创建与锁等待检查均采用指数退避策略，避免 HMS 压力过大。
 * </ul>
 *
 * <p>上下游关系：由 {@link HiveTableOperations#lockObject} 在启用 HMS 锁时创建； 内部通过 {@link ClientPool} 调用 HMS
 * Thrift 接口。
 */
class MetastoreLock implements HiveLock {
  private static final Logger LOG = LoggerFactory.getLogger(MetastoreLock.class);
  private static final String HIVE_ACQUIRE_LOCK_TIMEOUT_MS = "iceberg.hive.lock-timeout-ms";
  private static final String HIVE_LOCK_CHECK_MIN_WAIT_MS = "iceberg.hive.lock-check-min-wait-ms";
  private static final String HIVE_LOCK_CHECK_MAX_WAIT_MS = "iceberg.hive.lock-check-max-wait-ms";
  private static final String HIVE_LOCK_CREATION_TIMEOUT_MS =
      "iceberg.hive.lock-creation-timeout-ms";
  private static final String HIVE_LOCK_CREATION_MIN_WAIT_MS =
      "iceberg.hive.lock-creation-min-wait-ms";
  private static final String HIVE_LOCK_CREATION_MAX_WAIT_MS =
      "iceberg.hive.lock-creation-max-wait-ms";
  private static final String HIVE_LOCK_HEARTBEAT_INTERVAL_MS =
      "iceberg.hive.lock-heartbeat-interval-ms";
  private static final String HIVE_TABLE_LEVEL_LOCK_EVICT_MS =
      "iceberg.hive.table-level-lock-evict-ms";

  private static final long HIVE_ACQUIRE_LOCK_TIMEOUT_MS_DEFAULT = 3 * 60 * 1000; // 3 minutes
  private static final long HIVE_LOCK_CHECK_MIN_WAIT_MS_DEFAULT = 50; // 50 milliseconds
  private static final long HIVE_LOCK_CHECK_MAX_WAIT_MS_DEFAULT = 5 * 1000; // 5 seconds
  private static final long HIVE_LOCK_CREATION_TIMEOUT_MS_DEFAULT = 3 * 60 * 1000; // 3 minutes
  private static final long HIVE_LOCK_CREATION_MIN_WAIT_MS_DEFAULT = 50; // 50 milliseconds
  private static final long HIVE_LOCK_CREATION_MAX_WAIT_MS_DEFAULT = 5 * 1000; // 5 seconds
  private static final long HIVE_LOCK_HEARTBEAT_INTERVAL_MS_DEFAULT = 4 * 60 * 1000; // 4 minutes
  private static final long HIVE_TABLE_LEVEL_LOCK_EVICT_MS_DEFAULT = TimeUnit.MINUTES.toMillis(10);
  private static volatile Cache<String, ReentrantLock> commitLockCache;

  private final ClientPool<IMetaStoreClient, TException> metaClients;
  private final String databaseName;
  private final String tableName;
  private final String fullName;
  private final long lockAcquireTimeout;
  private final long lockCheckMinWaitTime;
  private final long lockCheckMaxWaitTime;
  private final long lockCreationTimeout;
  private final long lockCreationMinWaitTime;
  private final long lockCreationMaxWaitTime;
  private final long lockHeartbeatIntervalTime;
  private final ScheduledExecutorService exitingScheduledExecutorService;
  private final String agentInfo;

  private Optional<Long> hmsLockId = Optional.empty();
  private ReentrantLock jvmLock = null;
  private Heartbeat heartbeat = null;

  /**
   * 构造 MetastoreLock 实例。
   *
   * <p>逻辑：保存表标识与客户端池，从配置读取锁超时、检查间隔、心跳间隔等参数， 生成 UUID 作为 agentInfo，创建心跳调度线程池，并初始化表级 JVM 锁缓存。
   *
   * @param conf Hadoop 配置（含锁超时等参数）
   * @param metaClients HMS 客户端池
   * @param catalogName Catalog 名称
   * @param databaseName database 名
   * @param tableName 表名
   */
  MetastoreLock(
      Configuration conf,
      ClientPool<IMetaStoreClient, TException> metaClients,
      String catalogName,
      String databaseName,
      String tableName) {
    this.metaClients = metaClients;
    this.fullName = catalogName + "." + databaseName + "." + tableName;
    this.databaseName = databaseName;
    this.tableName = tableName;

    this.lockAcquireTimeout =
        conf.getLong(HIVE_ACQUIRE_LOCK_TIMEOUT_MS, HIVE_ACQUIRE_LOCK_TIMEOUT_MS_DEFAULT);
    this.lockCheckMinWaitTime =
        conf.getLong(HIVE_LOCK_CHECK_MIN_WAIT_MS, HIVE_LOCK_CHECK_MIN_WAIT_MS_DEFAULT);
    this.lockCheckMaxWaitTime =
        conf.getLong(HIVE_LOCK_CHECK_MAX_WAIT_MS, HIVE_LOCK_CHECK_MAX_WAIT_MS_DEFAULT);
    this.lockCreationTimeout =
        conf.getLong(HIVE_LOCK_CREATION_TIMEOUT_MS, HIVE_LOCK_CREATION_TIMEOUT_MS_DEFAULT);
    this.lockCreationMinWaitTime =
        conf.getLong(HIVE_LOCK_CREATION_MIN_WAIT_MS, HIVE_LOCK_CREATION_MIN_WAIT_MS_DEFAULT);
    this.lockCreationMaxWaitTime =
        conf.getLong(HIVE_LOCK_CREATION_MAX_WAIT_MS, HIVE_LOCK_CREATION_MAX_WAIT_MS_DEFAULT);
    this.lockHeartbeatIntervalTime =
        conf.getLong(HIVE_LOCK_HEARTBEAT_INTERVAL_MS, HIVE_LOCK_HEARTBEAT_INTERVAL_MS_DEFAULT);
    long tableLevelLockCacheEvictionTimeout =
        conf.getLong(HIVE_TABLE_LEVEL_LOCK_EVICT_MS, HIVE_TABLE_LEVEL_LOCK_EVICT_MS_DEFAULT);

    this.agentInfo = "Iceberg-" + UUID.randomUUID();

    this.exitingScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("iceberg-hive-lock-heartbeat-" + fullName + "-%d")
                .build());

    initTableLevelLockCache(tableLevelLockCacheEvictionTimeout);
  }

  /**
   * 加锁：先获取 JVM 进程级锁，再获取 HMS 锁，最后启动心跳续约线程。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>获取 JVM 锁（ReentrantLock），避免同进程内并发提交。
   *   <li>调用 {@link #acquireLock} 获取 HMS 排他锁。
   *   <li>创建 {@link Heartbeat} 并调度定期心跳。
   * </ol>
   *
   * @throws LockException 加锁失败
   */
  @Override
  public void lock() throws LockException {
    // getting a process-level lock per table to avoid concurrent commit attempts to the same table
    // from the same JVM process, which would result in unnecessary HMS lock acquisition requests
    acquireJvmLock();

    // Getting HMS lock
    hmsLockId = Optional.of(acquireLock());

    // Starting heartbeat for the HMS lock
    heartbeat = new Heartbeat(metaClients, hmsLockId.get(), lockHeartbeatIntervalTime);
    heartbeat.schedule(exitingScheduledExecutorService);
  }

  /**
   * 确保锁仍然活跃。
   *
   * <p>逻辑：检查心跳线程是否存在、是否遇到异常、是否仍在运行；任一条件不满足则抛出 {@link LockException}，使上层将提交标记为状态未知。
   *
   * @throws LockException 锁未激活或心跳异常
   */
  @Override
  public void ensureActive() throws LockException {
    if (heartbeat == null) {
      throw new LockException("Lock is not active");
    }

    if (heartbeat.encounteredException != null) {
      throw new LockException(
          heartbeat.encounteredException,
          "Failed to heartbeat for hive lock. %s",
          heartbeat.encounteredException.getMessage());
    }
    if (!heartbeat.active()) {
      throw new LockException("Hive lock heartbeat thread not active");
    }
  }

  /**
   * 释放锁：取消心跳、关闭调度线程池、释放 HMS 锁、释放 JVM 锁。
   *
   * <p>设计要点：先停心跳再释放 HMS 锁，确保释放过程中不会产生无用心跳； HMS 锁释放放在 finally 中保证 JVM 锁一定被释放。
   */
  @Override
  public void unlock() {
    if (heartbeat != null) {
      heartbeat.cancel();
      exitingScheduledExecutorService.shutdown();
    }

    try {
      unlock(hmsLockId);
    } finally {
      releaseJvmLock();
    }
  }

  /**
   * 获取 HMS 锁并等待其变为 ACQUIRED 状态。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>调用 {@link #createLock} 创建锁请求，获取 lockId 和初始状态。
   *   <li>若状态为 WAITING，使用指数退避策略反复调用 checkLock 轮询锁状态， 直至变为 ACQUIRED 或超时。
   *   <li>超时或异常时在 finally 中释放未获取成功的锁。
   *   <li>最终未获取成功则抛出带超时/错误信息的 LockException。
   * </ol>
   *
   * @return 已获取的锁 ID
   * @throws LockException 锁获取超时或失败
   */
  private long acquireLock() throws LockException {
    LockInfo lockInfo = createLock();

    final long start = System.currentTimeMillis();
    long duration = 0;
    boolean timeout = false;
    TException thriftError = null;

    try {
      if (lockInfo.lockState.equals(LockState.WAITING)) {
        // Retry count is the typical "upper bound of retries" for Tasks.run() function. In fact,
        // the maximum number of
        // attempts the Tasks.run() would try is `retries + 1`. Here, for checking locks, we use
        // timeout as the
        // upper bound of retries. So it is just reasonable to set a large retry count. However, if
        // we set
        // Integer.MAX_VALUE, the above logic of `retries + 1` would overflow into
        // Integer.MIN_VALUE. Hence,
        // the retry is set conservatively as `Integer.MAX_VALUE - 100` so it doesn't hit any
        // boundary issues.
        Tasks.foreach(lockInfo.lockId)
            .retry(Integer.MAX_VALUE - 100)
            .exponentialBackoff(lockCheckMinWaitTime, lockCheckMaxWaitTime, lockAcquireTimeout, 1.5)
            .throwFailureWhenFinished()
            .onlyRetryOn(WaitingForLockException.class)
            .run(
                id -> {
                  try {
                    LockResponse response = metaClients.run(client -> client.checkLock(id));
                    LockState newState = response.getState();
                    lockInfo.lockState = newState;
                    if (newState.equals(LockState.WAITING)) {
                      throw new WaitingForLockException(
                          String.format(
                              "Waiting for lock on table %s.%s", databaseName, tableName));
                    }
                  } catch (InterruptedException e) {
                    Thread.interrupted(); // Clear the interrupt status flag
                    LOG.warn(
                        "Interrupted while waiting for lock on table {}.{}",
                        databaseName,
                        tableName,
                        e);
                  }
                },
                TException.class);
      }
    } catch (WaitingForLockException e) {
      timeout = true;
      duration = System.currentTimeMillis() - start;
    } catch (TException e) {
      thriftError = e;
    } finally {
      if (!lockInfo.lockState.equals(LockState.ACQUIRED)) {
        unlock(Optional.of(lockInfo.lockId));
      }
    }

    if (!lockInfo.lockState.equals(LockState.ACQUIRED)) {
      if (timeout) {
        throw new LockException(
            "Timed out after %s ms waiting for lock on %s.%s", duration, databaseName, tableName);
      }

      if (thriftError != null) {
        throw new LockException(
            thriftError, "Metastore operation failed for %s.%s", databaseName, tableName);
      }

      // Just for safety. We should not get here.
      throw new LockException(
          "Could not acquire the lock on %s.%s, lock request ended in state %s",
          databaseName, tableName, lockInfo.lockState);
    } else {
      return lockInfo.lockId;
    }
  }

  /**
   * 创建 HMS 锁请求，失败时按指数退避重试。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>获取本机主机名，构造排他锁组件和锁请求（含用户名、主机名、agentInfo）。
   *   <li>使用 Tasks 框架以指数退避策略重试调用 HMS lock 接口。
   *   <li>若 lock 调用抛 TException（Hive 2+），尝试通过 showLocks + agentInfo 查找已创建的锁 （容错：lock 请求可能已到达 HMS
   *       但响应丢失）。
   *   <li>中断时设置中断标志并停止重试。
   * </ol>
   *
   * @return 成功创建的锁信息
   * @throws LockException 无法获取主机名或锁创建失败
   */
  @SuppressWarnings("ReverseDnsLookup")
  private LockInfo createLock() throws LockException {
    LockInfo lockInfo = new LockInfo();

    String hostName;
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException uhe) {
      throw new LockException(uhe, "Error generating host name");
    }

    LockComponent lockComponent =
        new LockComponent(LockType.EXCLUSIVE, LockLevel.TABLE, databaseName);
    lockComponent.setTablename(tableName);
    LockRequest lockRequest =
        new LockRequest(Lists.newArrayList(lockComponent), HiveHadoopUtil.currentUser(), hostName);

    // Only works in Hive 2 or later.
    if (HiveVersion.min(HiveVersion.HIVE_2)) {
      lockRequest.setAgentInfo(agentInfo);
    }

    AtomicBoolean interrupted = new AtomicBoolean(false);
    Tasks.foreach(lockRequest)
        .retry(Integer.MAX_VALUE - 100)
        .exponentialBackoff(
            lockCreationMinWaitTime, lockCreationMaxWaitTime, lockCreationTimeout, 2.0)
        .shouldRetryTest(
            e ->
                !interrupted.get()
                    && e instanceof LockException
                    && HiveVersion.min(HiveVersion.HIVE_2))
        .throwFailureWhenFinished()
        .run(
            request -> {
              try {
                LockResponse lockResponse = metaClients.run(client -> client.lock(request));
                lockInfo.lockId = lockResponse.getLockid();
                lockInfo.lockState = lockResponse.getState();
              } catch (TException te) {
                LOG.warn("Failed to create lock {}", request, te);
                try {
                  // If we can not check for lock, or we do not find it, then rethrow the exception
                  // Otherwise we are happy as the findLock sets the lockId and the state correctly
                  if (HiveVersion.min(HiveVersion.HIVE_2)) {
                    LockInfo lockFound = findLock();
                    if (lockFound != null) {
                      lockInfo.lockId = lockFound.lockId;
                      lockInfo.lockState = lockFound.lockState;
                      LOG.info("Found lock {} by agentInfo {}", lockInfo, agentInfo);
                      return;
                    }
                  }

                  throw new LockException(
                      "Failed to find lock for table %s.%s", databaseName, tableName);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  interrupted.set(true);
                  LOG.warn(
                      "Interrupted while trying to find lock for table {}.{}",
                      databaseName,
                      tableName,
                      e);
                  throw new LockException(
                      e,
                      "Interrupted while trying to find lock for table %s.%s",
                      databaseName,
                      tableName);
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted.set(true);
                LOG.warn(
                    "Interrupted while creating lock on table {}.{}", databaseName, tableName, e);
                throw new LockException(
                    e, "Interrupted while creating lock on table %s.%s", databaseName, tableName);
              }
            },
            LockException.class);

    // This should be initialized always, or exception should be thrown.
    LOG.debug("Lock {} created for table {}.{}", lockInfo, databaseName, tableName);
    return lockInfo;
  }

  /**
   * 通过 showLocks API 按 agentInfo 查找已创建的锁。
   *
   * <p>逻辑：向 HMS 发起 showLocks 请求获取该表的所有锁，遍历匹配 agentInfo 相等的锁。
   *
   * <p>设计要点：用于 lock 请求响应丢失时的容错恢复（Hive 2+）。
   *
   * @return 找到的锁信息，未找到返回 null
   * @throws LockException showLocks 调用失败
   * @throws InterruptedException 线程被中断
   */
  private LockInfo findLock() throws LockException, InterruptedException {
    Preconditions.checkArgument(
        HiveVersion.min(HiveVersion.HIVE_2),
        "Minimally Hive 2 HMS client is needed to find the Lock using the showLocks API call");
    ShowLocksRequest showLocksRequest = new ShowLocksRequest();
    showLocksRequest.setDbname(databaseName);
    showLocksRequest.setTablename(tableName);
    ShowLocksResponse response;
    try {
      response = metaClients.run(client -> client.showLocks(showLocksRequest));
    } catch (TException e) {
      throw new LockException(e, "Failed to find lock for table %s.%s", databaseName, tableName);
    }
    for (ShowLocksResponseElement lock : response.getLocks()) {
      if (lock.getAgentInfo().equals(agentInfo)) {
        // We found our lock
        return new LockInfo(lock.getLockid(), lock.getState());
      }
    }

    // Not found anything
    return null;
  }

  /**
   * 释放 HMS 锁，支持按 lockId 或按 agentInfo 查找后释放。
   *
   * <p>逻辑：若 lockId 存在则直接释放；否则（Hive 2+）通过 findLock 按 agentInfo 查找后释放。
   * 中断时清理中断状态并尝试再释放一次；其他异常仅告警不抛出（释放失败不应阻断流程）。
   *
   * @param lockId 锁 ID（可为空，空时按 agentInfo 查找）
   */
  private void unlock(Optional<Long> lockId) {
    Long id = null;
    try {
      if (!lockId.isPresent()) {
        // Try to find the lock based on agentInfo. Only works with Hive 2 or later.
        if (HiveVersion.min(HiveVersion.HIVE_2)) {
          LockInfo lockInfo = findLock();
          if (lockInfo == null) {
            // No lock found
            LOG.info("No lock found with {} agentInfo", agentInfo);
            return;
          }

          id = lockInfo.lockId;
        } else {
          LOG.warn("Could not find lock with HMSClient {}", HiveVersion.current());
          return;
        }
      } else {
        id = lockId.get();
      }

      doUnlock(id);
    } catch (InterruptedException ie) {
      if (id != null) {
        // Interrupted unlock. We try to unlock one more time if we have a lockId
        try {
          Thread.interrupted(); // Clear the interrupt status flag for now, so we can retry unlock
          LOG.warn("Interrupted unlock we try one more time {}.{}", databaseName, tableName, ie);
          doUnlock(id);
        } catch (Exception e) {
          LOG.warn("Failed to unlock even on 2nd attempt {}.{}", databaseName, tableName, e);
        } finally {
          Thread.currentThread().interrupt(); // Set back the interrupt status
        }
      } else {
        Thread.currentThread().interrupt(); // Set back the interrupt status
        LOG.warn("Interrupted finding locks to unlock {}.{}", databaseName, tableName, ie);
      }
    } catch (Exception e) {
      LOG.warn("Failed to unlock {}.{}", databaseName, tableName, e);
    }
  }

  /**
   * 调用 HMS unlock 接口释放指定锁。
   *
   * @param lockId 锁 ID
   */
  private void doUnlock(long lockId) throws TException, InterruptedException {
    metaClients.run(
        client -> {
          client.unlock(lockId);
          return null;
        });
  }

  /**
   * 获取表级 JVM 进程锁。
   *
   * <p>设计要点：使用 Caffeine 缓存的 ReentrantLock（按表全名缓存），避免同进程内多线程 对同一表并发提交产生不必要的 HMS 锁请求。
   */
  private void acquireJvmLock() {
    if (jvmLock != null) {
      throw new IllegalStateException(
          String.format("Cannot call acquireLock twice for %s", fullName));
    }

    jvmLock = commitLockCache.get(fullName, t -> new ReentrantLock(true));
    jvmLock.lock();
  }

  /** 释放表级 JVM 进程锁。 */
  private void releaseJvmLock() {
    if (jvmLock != null) {
      jvmLock.unlock();
      jvmLock = null;
    }
  }

  /**
   * 懒初始化表级 JVM 锁缓存（双重检查锁模式）。
   *
   * <p>设计要点：commitLockCache 为静态 volatile，使用 Caffeine 的 expireAfterAccess 策略 自动回收长时间未使用的表级锁对象。
   *
   * @param evictionTimeout 锁缓存淘汰时间（毫秒）
   */
  private static void initTableLevelLockCache(long evictionTimeout) {
    if (commitLockCache == null) {
      synchronized (MetastoreLock.class) {
        if (commitLockCache == null) {
          commitLockCache =
              Caffeine.newBuilder()
                  .expireAfterAccess(evictionTimeout, TimeUnit.MILLISECONDS)
                  .build();
        }
      }
    }
  }

  /**
   * 心跳续约任务，定期向 HMS 发送 heartbeat 防止锁超时。
   *
   * <p>设计要点：作为 Runnable 由调度线程池定期执行；遇到异常时记录到 encounteredException 字段供 {@link #ensureActive()} 检查，并抛出
   * {@link CommitFailedException} 终止心跳。
   */
  private static class Heartbeat implements Runnable {
    private final ClientPool<IMetaStoreClient, TException> hmsClients;
    private final long lockId;
    private final long intervalMs;
    private ScheduledFuture<?> future;
    private volatile Exception encounteredException = null;

    Heartbeat(ClientPool<IMetaStoreClient, TException> hmsClients, long lockId, long intervalMs) {
      this.hmsClients = hmsClients;
      this.lockId = lockId;
      this.intervalMs = intervalMs;
      this.future = null;
    }

    /**
     * 执行一次心跳：调用 HMS heartbeat 续约锁。
     *
     * <p>逻辑：通过客户端池调用 {@code heartbeat(txnId=0, lockId)} 续约；异常时记录到 encounteredException 并抛出
     * CommitFailedException 终止后续心跳。
     */
    @Override
    public void run() {
      try {
        hmsClients.run(
            client -> {
              client.heartbeat(0, lockId);
              return null;
            });
      } catch (TException | InterruptedException e) {
        this.encounteredException = e;
        throw new CommitFailedException(e, "Failed to heartbeat for lock: %d", lockId);
      }
    }

    /**
     * 以固定速率调度心跳任务。
     *
     * @param scheduler 调度线程池
     */
    public void schedule(ScheduledExecutorService scheduler) {
      future =
          scheduler.scheduleAtFixedRate(this, intervalMs / 2, intervalMs, TimeUnit.MILLISECONDS);
    }

    boolean active() {
      return future != null && !future.isCancelled();
    }

    public void cancel() {
      if (future != null) {
        future.cancel(false);
      }
    }
  }

  /** 锁信息 holder：持有 lockId 与锁状态。 */
  private static class LockInfo {
    private long lockId;
    private LockState lockState;

    private LockInfo() {
      this.lockId = -1;
      this.lockState = null;
    }

    private LockInfo(long lockId, LockState lockState) {
      this.lockId = lockId;
      this.lockState = lockState;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("lockId", lockId)
          .add("lockState", lockState)
          .toString();
    }
  }

  /** 等待锁超时信号异常，用于在 Tasks 框架中标识锁等待状态。 */
  private static class WaitingForLockException extends RuntimeException {
    WaitingForLockException(String message) {
      super(message);
    }
  }
}
