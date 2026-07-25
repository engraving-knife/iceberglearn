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

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通过 Java 系统属性或环境变量控制的 Iceberg 全局配置项集合。
 *
 * <p>所属模块：iceberg-core，定位为运行期可调的全局调优入口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中声明 Iceberg 内部使用的系统级配置项（如 worker 线程池大小、扫描线程池开关、manifest 缓存上限）；
 *   <li>通过 {@link ConfigEntry} 提供统一的"系统属性优先、环境变量兜底、再回退默认值"的取值策略；
 *   <li>对配置值做懒加载缓存，避免重复解析带来的开销。
 * </ul>
 *
 * <p>设计意图：将分散的 JVM 层参数（-D 系统属性与 OS 环境变量）封装为强类型、可复用的 {@link ConfigEntry}， 避免散落在各业务类中的 {@code
 * System.getProperty} 调用。{@link ConfigEntry} 采用懒加载 + 解析失败回退默认值 的策略，保证配置异常不会导致运行时崩溃；同时通过 final
 * 字段保证常量声明的不可变性。
 *
 * <p>上下游关系：被扫描计划、提交、{@link org.apache.iceberg.io.FileIO} 缓存等模块读取； 由部署方在启动 Iceberg 客户端时通过 -D
 * 参数或环境变量配置。
 */
public class SystemConfigs {
  private static final Logger LOG = LoggerFactory.getLogger(SystemConfigs.class);

  private SystemConfigs() {}

  /**
   * 设置 Iceberg 共享 worker 线程池的大小。该线程池用于限制基表实现中跨所有并发规划或提交操作 同时处理 manifest 的最大任务并发数。
   *
   * <p>默认值为 {@code max(2, CPU 核心数)}，取值方式为系统属性 {@code iceberg.worker.num-threads} 或环境变量 {@code
   * ICEBERG_WORKER_NUM_THREADS}。
   */
  public static final ConfigEntry<Integer> WORKER_THREAD_POOL_SIZE =
      new ConfigEntry<>(
          "iceberg.worker.num-threads",
          "ICEBERG_WORKER_NUM_THREADS",
          Math.max(2, Runtime.getRuntime().availableProcessors()),
          Integer::parseUnsignedInt);

  /**
   * 在规划表扫描时是否使用共享 worker 线程池。
   *
   * <p>默认值为 {@code true}，可通过系统属性 {@code iceberg.scan.plan-in-worker-pool} 或环境变量 {@code
   * ICEBERG_SCAN_PLAN_IN_WORKER_POOL} 覆盖。
   */
  public static final ConfigEntry<Boolean> SCAN_THREAD_POOL_ENABLED =
      new ConfigEntry<>(
          "iceberg.scan.plan-in-worker-pool",
          "ICEBERG_SCAN_PLAN_IN_WORKER_POOL",
          true,
          Boolean::parseBoolean);

  /**
   * 允许同时持有 {@link org.apache.iceberg.io.ContentCache} 的不同 {@link org.apache.iceberg.io.FileIO}
   * 实例的最大数量。
   *
   * <p>默认值为 {@code 8}，可通过系统属性 {@code iceberg.io.manifest.cache.fileio-max} 或环境变量 {@code
   * ICEBERG_IO_MANIFEST_CACHE_FILEIO_MAX} 覆盖。
   */
  public static final ConfigEntry<Integer> IO_MANIFEST_CACHE_MAX_FILEIO =
      new ConfigEntry<>(
          "iceberg.io.manifest.cache.fileio-max",
          "ICEBERG_IO_MANIFEST_CACHE_FILEIO_MAX",
          8,
          Integer::parseUnsignedInt);

  /**
   * 单个配置项的强类型封装，统一管理系统属性、环境变量与默认值之间的优先级，并对取值做懒加载与解析失败回退。
   *
   * @param <T> 配置值的 Java 类型
   */
  public static class ConfigEntry<T> {
    private final String propertyKey;
    private final String envKey;
    private final T defaultValue;
    private final Function<String, T> parseFunc;
    private T lazyValue = null;

    /**
     * 构造一个配置项。
     *
     * @param propertyKey 对应的 Java 系统属性名
     * @param envKey 对应的环境变量名
     * @param defaultValue 当属性与环境变量均未设置或解析失败时使用的默认值
     * @param parseFunc 将字符串值解析为强类型 T 的函数
     */
    private ConfigEntry(
        String propertyKey, String envKey, T defaultValue, Function<String, T> parseFunc) {
      this.propertyKey = propertyKey;
      this.envKey = envKey;
      this.defaultValue = defaultValue;
      this.parseFunc = parseFunc;
    }

    /** 返回该配置项对应的 Java 系统属性名。 */
    public final String propertyKey() {
      return propertyKey;
    }

    /** 返回该配置项对应的环境变量名。 */
    public final String envKey() {
      return envKey;
    }

    /** 返回该配置项的默认值。 */
    public final T defaultValue() {
      return defaultValue;
    }

    /**
     * 返回解析后的配置值，首次调用时按系统属性 -> 环境变量 -> 默认值的优先级解析并缓存。
     *
     * <p>逻辑：若 {@code lazyValue} 仍为 null，调用 {@link #getValue()} 进行实际解析并缓存；
     * 后续调用直接返回缓存值。这种懒加载策略避免在静态初始化阶段就触发解析， 让运行期通过 -D 修改的属性能被正确读取。
     *
     * @return 解析后的配置值
     */
    public final T value() {
      if (lazyValue == null) {
        lazyValue = getValue();
      }

      return lazyValue;
    }

    /**
     * 按优先级实际读取并解析配置值。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>优先读取 Java 系统属性 {@link #propertyKey}；
     *   <li>若系统属性为 null，再读取环境变量 {@link #envKey}；
     *   <li>若取到非空字符串，调用 {@link #parseFunc} 解析；解析异常时记录错误日志并回退默认值；
     *   <li>若两者均为 null，直接返回 {@link #defaultValue}。
     * </ol>
     *
     * @return 解析后的配置值
     */
    private T getValue() {
      String value = System.getProperty(propertyKey);
      if (value == null) {
        value = System.getenv(envKey);
      }

      if (value != null) {
        try {
          return parseFunc.apply(value);
        } catch (Exception e) {
          // will return the default value
          LOG.error(
              "Failed to parse the config value set by system property: {} or env variable: {}, "
                  + "using the default value: {}",
              propertyKey,
              envKey,
              defaultValue,
              e);
        }
      }

      return defaultValue;
    }
  }
}
