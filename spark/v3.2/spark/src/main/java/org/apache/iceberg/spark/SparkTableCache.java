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
 * Iceberg Spark 集成相关组件。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkTableCache。
 */
public class SparkTableCache {

  private static final SparkTableCache INSTANCE = new SparkTableCache();

  private final Map<String, Table> cache = Maps.newConcurrentMap();

  /** 执行该方法的具体逻辑。 */
  public static SparkTableCache get() {
    return INSTANCE;
  }

  /**
   * 返回大小。
   *
   * @return 大小
   */
  public int size() {
    return cache.size();
  }

  /**
   * 添加元素或项。
   *
   * @param key 参数
   * @param table 参数
   */
  public void add(String key, Table table) {
    cache.put(key, table);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param key 参数
   * @return 结果对象
   */
  public boolean contains(String key) {
    return cache.containsKey(key);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param key 参数
   * @return 对应结果
   */
  public Table get(String key) {
    return cache.get(key);
  }

  /**
   * 移除元素或项。
   *
   * @param key 参数
   * @return 结果对象
   */
  public Table remove(String key) {
    return cache.remove(key);
  }
}
