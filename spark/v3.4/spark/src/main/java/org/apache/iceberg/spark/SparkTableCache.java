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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 表对象缓存，按标识符缓存已加载的 SparkTable 以减少元数据重复读取。
 *
 * <p>设计意图：采用带过期时间的 ConcurrentMap 缓存，支持大小与时间淘汰。
 *
 * <p>上下游关系：由 SparkCachedTableCatalog 使用。
 */
public class SparkTableCache {

  private static final SparkTableCache INSTANCE = new SparkTableCache();

  private final Map<String, Table> cache = Maps.newConcurrentMap();
  /** 返回值。 */
  public static SparkTableCache get() {
    return INSTANCE;
  }
  /** 返回大小。 */
  public int size() {
    return cache.size();
  }
  /** 添加元素。 */
  public void add(String key, Table table) {
    cache.put(key, table);
  }
  /** 判断是否包含。 */
  public boolean contains(String key) {
    return cache.containsKey(key);
  }
  /** 返回值。 */
  public Table get(String key) {
    return cache.get(key);
  }
  /** 移除元素。 */
  public Table remove(String key) {
    return cache.remove(key);
  }
}
