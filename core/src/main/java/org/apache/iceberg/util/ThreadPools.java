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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.iceberg.SystemConfigs;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.MoreExecutors;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Iceberg 内部线程池工具。
 *
 * <p>所属模块：iceberg-core（util 子包）。职责：提供全局 worker 线程池与按需创建命名线程池的能力， 用于并行读取 manifest、并行规划扫描等。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>单例 worker 池：进程级共享，大小由 {@link SystemConfigs#WORKER_THREAD_POOL_SIZE} 决定。
 *   <li>命名线程工厂：通过 {@link ThreadFactoryBuilder} 给线程命名，便于排查。
 *   <li>调度池支持：提供 {@link ScheduledExecutorService} 用于定时/延迟任务。
 * </ul>
 *
 * <p>上下游关系：被 core 扫描/写入路径在需要并行时调用；上游可由系统属性配置池大小。
 */
public class ThreadPools {

  /** 私有构造：工具类禁止实例化。 */
  private ThreadPools() {}

  /**
   * @deprecated Use {@link SystemConfigs#WORKER_THREAD_POOL_SIZE WORKER_THREAD_POOL_SIZE} instead;
   *     will be removed in 2.0.0
   */
  @Deprecated
  public static final String WORKER_THREAD_POOL_SIZE_PROP =
      SystemConfigs.WORKER_THREAD_POOL_SIZE.propertyKey();

  public static final int WORKER_THREAD_POOL_SIZE = SystemConfigs.WORKER_THREAD_POOL_SIZE.value();

  private static final ExecutorService WORKER_POOL = newWorkerPool("iceberg-worker-pool");

  /**
   * 返回全局共享的 worker 线程池。
   *
   * <p>池大小由系统属性 {@code iceberg.worker.num-threads} 控制，限制并发读取 manifest 的任务数。
   *
   * @return 全局 worker 线程池
   */
  public static ExecutorService getWorkerPool() {
    return WORKER_POOL;
  }

  /**
   * 创建一个新的 worker 线程池，使用默认池大小。
   *
   * @param namePrefix 线程名前缀
   * @return 新的线程池（退出时自动关闭）
   */
  public static ExecutorService newWorkerPool(String namePrefix) {
    return newWorkerPool(namePrefix, WORKER_THREAD_POOL_SIZE);
  }

  /**
   * 创建一个新的固定大小 worker 线程池，线程为守护线程。
   *
   * @param namePrefix 线程名前缀
   * @param poolSize 池大小
   * @return 新的线程池（JVM 退出时自动关闭）
   */
  public static ExecutorService newWorkerPool(String namePrefix, int poolSize) {
    return MoreExecutors.getExitingExecutorService(
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(poolSize, newDaemonThreadFactory(namePrefix)));
  }

  /**
   * 创建一个新的调度线程池，线程为守护线程。
   *
   * @param namePrefix 线程名前缀
   * @param poolSize 池大小
   * @return 新的调度线程池
   */
  public static ScheduledExecutorService newScheduledPool(String namePrefix, int poolSize) {
    return new ScheduledThreadPoolExecutor(poolSize, newDaemonThreadFactory(namePrefix));
  }

  /**
   * 创建守护线程工厂，线程名格式为 {@code namePrefix-%d}。
   *
   * @param namePrefix 线程名前缀
   * @return 守护线程工厂
   */
  private static ThreadFactory newDaemonThreadFactory(String namePrefix) {
    return new ThreadFactoryBuilder().setDaemon(true).setNameFormat(namePrefix + "-%d").build();
  }
}
