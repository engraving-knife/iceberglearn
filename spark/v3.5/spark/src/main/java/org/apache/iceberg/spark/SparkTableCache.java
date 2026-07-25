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
package org.apache.iceberg.spark;

import java.util.Map;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * Spark 端 Iceberg {@link Table} 缓存（单例）。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：按字符串 key 缓存已加载的 Iceberg Table 实例，避免重复加载元数据。
 *
 * <p>设计意图：用 {@link java.util.concurrent.ConcurrentMap} 保证并发安全；单例模式提供全局访问点。 用于在 Spark 会话内复用 Table
 * 对象，减少元数据 IO。
 *
 * <p>上下游关系：被 Spark catalog/扫描等需要复用 Table 的路径调用。
 */
public class SparkTableCache {

  private static final SparkTableCache INSTANCE = new SparkTableCache();

  private final Map<String, Table> cache = Maps.newConcurrentMap();

  /** 返回单例实例。 */
  public static SparkTableCache get() {
    return INSTANCE;
  }

  /** 返回缓存条目数。 */
  public int size() {
    return cache.size();
  }

  /** 添加/覆盖缓存条目。 */
  public void add(String key, Table table) {
    cache.put(key, table);
  }

  /** 是否包含指定 key。 */
  public boolean contains(String key) {
    return cache.containsKey(key);
  }

  /** 返回 key 对应的 Table，不存在返回 null。 */
  public Table get(String key) {
    return cache.get(key);
  }

  /** 移除并返回 key 对应的 Table。 */
  public Table remove(String key) {
    return cache.remove(key);
  }
}
