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

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 可序列化的 Map 包装类，同时实现 {@link Map} 与 {@link Serializable}。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：把任意 Map 拷贝为可序列化的 HashMap，并提供惰性创建的不可变视图；支持在分布式引擎中 序列化传输。
 *
 * <p>设计意图：{@code immutableMap} 字段标记为 {@code transient}，序列化时不传输，反序列化后按需重建，
 * 减少序列化体积。双重检查锁保证不可变视图的线程安全单例创建。
 *
 * <p>上下游关系：被需要跨节点传递属性映射的场景使用（如引擎任务序列化）；依赖 relocated guava。
 */
public class SerializableMap<K, V> implements Map<K, V>, Serializable {

  private final Map<K, V> copiedMap;
  private transient volatile Map<K, V> immutableMap;

  SerializableMap() {
    this.copiedMap = Maps.newHashMap();
  }

  private SerializableMap(Map<K, V> map) {
    this.copiedMap = Maps.newHashMap();
    this.copiedMap.putAll(map);
  }

  /**
   * 创建给定 Map 的可序列化拷贝；输入为 null 时返回 null。
   *
   * @param map 源映射
   * @return 可序列化拷贝，或 null
   */
  public static <K, V> SerializableMap<K, V> copyOf(Map<K, V> map) {
    return map == null ? null : new SerializableMap<>(map);
  }

  /**
   * 返回内部 Map 的不可变视图（双重检查锁惰性创建）。
   *
   * @return 不可变 Map 视图
   */
  public Map<K, V> immutableMap() {
    if (immutableMap == null) {
      synchronized (this) {
        if (immutableMap == null) {
          immutableMap = Collections.unmodifiableMap(copiedMap);
        }
      }
    }

    return immutableMap;
  }

  @Override
  public int size() {
    return copiedMap.size();
  }

  @Override
  public boolean isEmpty() {
    return copiedMap.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return copiedMap.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return copiedMap.containsValue(value);
  }

  @Override
  public V get(Object key) {
    return copiedMap.get(key);
  }

  @Override
  public V put(K key, V value) {
    return copiedMap.put(key, value);
  }

  @Override
  public V remove(Object key) {
    return copiedMap.remove(key);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    copiedMap.putAll(m);
  }

  @Override
  public void clear() {
    copiedMap.clear();
  }

  @Override
  public Set<K> keySet() {
    return copiedMap.keySet();
  }

  @Override
  public Collection<V> values() {
    return copiedMap.values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return copiedMap.entrySet();
  }

  @Override
  public boolean equals(Object o) {
    return copiedMap.equals(o);
  }

  @Override
  public int hashCode() {
    return copiedMap.hashCode();
  }
}
