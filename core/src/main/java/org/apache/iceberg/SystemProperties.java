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

/**
 * 通过 Java 系统属性控制的配置项。
 *
 * <p>所属模块：iceberg-core（全局配置层）。
 *
 * <p>职责：定义跨表的全局配置项键名，如 worker 线程池大小、扫描线程池开关、manifest 缓存上限。
 *
 * <p>设计意图：这些配置需要在 JVM 级别生效（影响所有表），故通过 System.getProperty 读取。
 *
 * @deprecated 请改用 {@link SystemConfigs}，本类将在 2.0.0 移除
 */
@Deprecated
public class SystemProperties {

  private SystemProperties() {}

  /** worker 线程池大小：限制所有并发计划/提交操作中处理 manifest 的最大并发任务数。 */
  public static final String WORKER_THREAD_POOL_SIZE_PROP = "iceberg.worker.num-threads";

  /** 是否在计划表扫描时使用共享 worker 线程池。 */
  public static final String SCAN_THREAD_POOL_ENABLED = "iceberg.scan.plan-in-worker-pool";

  /**
   * 允许同时关联 {@link org.apache.iceberg.io.ContentCache} 的不同 {@link org.apache.iceberg.io.FileIO}
   * 实例的最大数量。
   */
  public static final String IO_MANIFEST_CACHE_MAX_FILEIO = "iceberg.io.manifest.cache.fileio-max";

  /** IO_MANIFEST_CACHE_MAX_FILEIO 的默认值。 */
  public static final int IO_MANIFEST_CACHE_MAX_FILEIO_DEFAULT = 8;

  /**
   * 从系统属性读取布尔值，缺失时返回默认值。
   *
   * @param systemProperty 系统属性名
   * @param defaultValue 默认值
   * @return 解析后的布尔值
   */
  static boolean getBoolean(String systemProperty, boolean defaultValue) {
    String value = System.getProperty(systemProperty);
    if (value != null) {
      return Boolean.parseBoolean(value);
    }

    return defaultValue;
  }
}
