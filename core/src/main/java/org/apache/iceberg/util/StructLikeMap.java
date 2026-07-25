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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Types;

/**
 * 以 {@link StructLike} 为键的 {@link Map} 实现。
 *
 * <p>所属模块：iceberg-core（util 子包）。职责：允许把结构化值（分区键等）作为 map 的键， 内部通过 {@link StructLikeWrapper} 包装后委托给普通
 * {@link HashMap}。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>ThreadLocal wrapper：get/contains 时复用线程局部 wrapper，避免每次调用都分配对象。
 *   <li>类型感知：构造时传入 {@link Types.StructType}，wrapper 据此比较各字段。
 *   <li>继承 {@link AbstractMap}：复用 equals/hashCode 等标准 map 语义。
 * </ul>
 *
 * <p>上下游关系：被分区聚合、扫描汇总等场景用作按分区键分组的容器。
 */
public class StructLikeMap<T> extends AbstractMap<StructLike, T> implements Map<StructLike, T> {

  /**
   * 创建一个以指定 struct 类型为键的 StructLikeMap。
   *
   * @param type 键的 struct 类型
   * @param <T> 值类型
   * @return 新的 StructLikeMap 实例
   */
  public static <T> StructLikeMap<T> create(Types.StructType type) {
    return new StructLikeMap<>(type);
  }

  private final Types.StructType type;
  private final Map<StructLikeWrapper, T> wrapperMap;
  private final ThreadLocal<StructLikeWrapper> wrappers;

  /**
   * 私有构造：初始化类型、内部 HashMap 与 ThreadLocal wrapper。
   *
   * @param type 键的 struct 类型
   */
  private StructLikeMap(Types.StructType type) {
    this.type = type;
    this.wrapperMap = Maps.newHashMap();
    this.wrappers = ThreadLocal.withInitial(() -> StructLikeWrapper.forType(type));
  }

  /**
   * 返回 map 中键值对数量。
   *
   * @return 键值对数量
   */
  @Override
  public int size() {
    return wrapperMap.size();
  }

  /**
   * 判断 map 是否为空。
   *
   * @return true 表示为空
   */
  @Override
  public boolean isEmpty() {
    return wrapperMap.isEmpty();
  }

  /**
   * 判断是否包含指定键。
   *
   * <p>使用 ThreadLocal wrapper 包装 key 后查询内部 map，查询后清除引用避免内存泄漏。
   *
   * @param key 待检查的键（必须是 StructLike 或 null）
   * @return true 表示包含该键
   */
  @Override
  public boolean containsKey(Object key) {
    if (key instanceof StructLike || key == null) {
      StructLikeWrapper wrapper = wrappers.get();
      boolean result = wrapperMap.containsKey(wrapper.set((StructLike) key));
      wrapper.set(null); // don't hold a reference to the key.
      return result;
    }
    return false;
  }

  /**
   * 判断是否包含指定值。
   *
   * @param value 待检查的值
   * @return true 表示包含该值
   */
  @Override
  public boolean containsValue(Object value) {
    return wrapperMap.containsValue(value);
  }

  /**
   * 根据键获取值。
   *
   * <p>使用 ThreadLocal wrapper 包装 key 后查询内部 map，查询后清除引用避免内存泄漏。
   *
   * @param key 键（必须是 StructLike 或 null）
   * @return 对应的值，不存在则返回 null
   */
  @Override
  public T get(Object key) {
    if (key instanceof StructLike || key == null) {
      StructLikeWrapper wrapper = wrappers.get();
      T value = wrapperMap.get(wrapper.set((StructLike) key));
      wrapper.set(null); // don't hold a reference to the key.
      return value;
    }
    return null;
  }

  /**
   * put 键值对：复制 wrapper 后存入内部 map，避免外部修改影响键。
   *
   * @param key 键
   * @param value 值
   * @return 之前关联的值（若无则 null）
   */
  @Override
  public T put(StructLike key, T value) {
    return wrapperMap.put(wrappers.get().copyFor(key), value);
  }

  /**
   * 根据键移除键值对。
   *
   * <p>使用 ThreadLocal wrapper 包装 key 后从内部 map 移除，查询后清除引用。
   *
   * @param key 键（必须是 StructLike 或 null）
   * @return 被移除的值（若无则 null）
   */
  @Override
  public T remove(Object key) {
    if (key instanceof StructLike || key == null) {
      StructLikeWrapper wrapper = wrappers.get();
      T value = wrapperMap.remove(wrapper.set((StructLike) key));
      wrapper.set(null); // don't hold a reference to the key.
      return value;
    }
    return null;
  }

  /** 清空所有键值对。 */
  @Override
  public void clear() {
    wrapperMap.clear();
  }

  /**
   * 返回所有键的集合（以 {@link StructLikeSet} 实现）。
   *
   * @return 键集合
   */
  @Override
  public Set<StructLike> keySet() {
    StructLikeSet keySet = StructLikeSet.create(type);
    for (StructLikeWrapper wrapper : wrapperMap.keySet()) {
      keySet.add(wrapper.get());
    }
    return keySet;
  }

  /**
   * 返回所有值的集合。
   *
   * @return 值集合
   */
  @Override
  public Collection<T> values() {
    return wrapperMap.values();
  }

  @Override
  public Set<Entry<StructLike, T>> entrySet() {
    Set<Entry<StructLike, T>> entrySet = Sets.newHashSet();
    for (Entry<StructLikeWrapper, T> entry : wrapperMap.entrySet()) {
      entrySet.add(new StructLikeEntry<>(entry));
    }
    return entrySet;
  }

  private static class StructLikeEntry<R> implements Entry<StructLike, R> {

    private Map.Entry<StructLikeWrapper, R> inner;

    private StructLikeEntry(Map.Entry<StructLikeWrapper, R> inner) {
      this.inner = inner;
    }

    @Override
    public StructLike getKey() {
      return inner.getKey().get();
    }

    @Override
    public R getValue() {
      return inner.getValue();
    }

    @Override
    public int hashCode() {
      int hashCode = getKey().hashCode();
      if (getValue() != null) {
        hashCode ^= getValue().hashCode();
      }
      return hashCode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof StructLikeEntry)) {
        return false;
      } else {
        StructLikeEntry that = (StructLikeEntry<R>) o;
        return Objects.equals(getKey(), that.getKey())
            && Objects.equals(getValue(), that.getValue());
      }
    }

    @Override
    public R setValue(R value) {
      throw new UnsupportedOperationException("Does not support setValue.");
    }
  }

  /**
   * 对所有值应用转换函数，返回新的 StructLikeMap。
   *
   * @param func 值转换函数
   * @param <U> 转换后的值类型
   * @return 包含转换后值的新 map
   */
  public <U> StructLikeMap<U> transformValues(Function<T, U> func) {
    StructLikeMap<U> result = create(type);
    wrapperMap.forEach((key, value) -> result.put(key.get(), func.apply(value)));
    return result;
  }
}
