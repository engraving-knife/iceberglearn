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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Iterators;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Types;

/**
 * 以 {@link StructLike} 为元素的 {@link Set} 实现。
 *
 * <p>所属模块：iceberg-core（util 子包）。职责：把结构化值放入集合，去重与成员判定， 内部委托给 {@link StructLikeWrapper} 集合。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>ThreadLocal wrapper：复用线程局部 wrapper 避免 add/contains 时的对象分配。
 *   <li>类型感知：构造时传入 {@link Types.StructType}。
 *   <li>迭代适配：通过 {@link Iterators} 把 wrapper 迭代器转回原始 StructLike。
 * </ul>
 *
 * <p>上下游关系：被扫描路径在收集分区键集合、去重数据文件分区等场景使用。
 */
public class StructLikeSet extends AbstractSet<StructLike> implements Set<StructLike> {

  /**
   * 创建一个以指定 struct 类型为元素的 StructLikeSet。
   *
   * @param type 元素的 struct 类型
   * @return 新的 StructLikeSet 实例
   */
  public static StructLikeSet create(Types.StructType type) {
    return new StructLikeSet(type);
  }

  private final Types.StructType type;
  private final Set<StructLikeWrapper> wrapperSet;
  private final ThreadLocal<StructLikeWrapper> wrappers;

  /**
   * 私有构造：初始化类型、内部 HashSet 与 ThreadLocal wrapper。
   *
   * @param type 元素的 struct 类型
   */
  private StructLikeSet(Types.StructType type) {
    this.type = type;
    this.wrapperSet = Sets.newHashSet();
    this.wrappers = ThreadLocal.withInitial(() -> StructLikeWrapper.forType(type));
  }

  /**
   * 返回集合中元素数量。
   *
   * @return 元素数量
   */
  @Override
  public int size() {
    return wrapperSet.size();
  }

  /**
   * 判断集合是否为空。
   *
   * @return true 表示为空
   */
  @Override
  public boolean isEmpty() {
    return wrapperSet.isEmpty();
  }

  /**
   * 判断是否包含指定元素。
   *
   * <p>使用 ThreadLocal wrapper 包装元素后查询内部 set，查询后清除引用。
   *
   * @param obj 待检查的元素（必须是 StructLike 或 null）
   * @return true 表示包含该元素
   */
  @Override
  public boolean contains(Object obj) {
    if (obj instanceof StructLike || obj == null) {
      StructLikeWrapper wrapper = wrappers.get();
      boolean result = wrapperSet.contains(wrapper.set((StructLike) obj));
      wrapper.set(null); // don't hold a reference to the value
      return result;
    }
    return false;
  }

  /**
   * 返回集合的迭代器，把 wrapper 迭代器转换为 StructLike 迭代器。
   *
   * @return StructLike 迭代器
   */
  @Override
  public Iterator<StructLike> iterator() {
    return Iterators.transform(wrapperSet.iterator(), StructLikeWrapper::get);
  }

  /**
   * 将集合转为 Object 数组。
   *
   * @return 包含所有元素的数组
   */
  @Override
  public Object[] toArray() {
    return Iterators.toArray(iterator(), StructLike.class);
  }

  /**
   * 将集合元素填入目标数组，不够大则新建数组。
   *
   * @param destArray 目标数组
   * @param <T> 数组元素类型
   * @return 填充后的数组
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T[] toArray(T[] destArray) {
    int size = wrapperSet.size();
    if (destArray.length < size) {
      return (T[]) toArray();
    }

    Iterator<StructLike> iter = iterator();
    int ind = 0;
    while (iter.hasNext()) {
      destArray[ind] = (T) iter.next();
      ind += 1;
    }

    if (destArray.length > size) {
      destArray[size] = null;
    }

    return destArray;
  }

  /**
   * 添加元素：复制 wrapper 后存入内部 set。
   *
   * @param struct 待添加的结构化值
   * @return true 表示集合发生了变化（之前不包含该元素）
   */
  @Override
  public boolean add(StructLike struct) {
    return wrapperSet.add(wrappers.get().copyFor(struct));
  }

  /**
   * 移除元素：使用 ThreadLocal wrapper 包装后从内部 set 移除。
   *
   * @param obj 待移除的元素（必须是 StructLike 或 null）
   * @return true 表示集合发生了变化
   */
  @Override
  public boolean remove(Object obj) {
    if (obj instanceof StructLike || obj == null) {
      StructLikeWrapper wrapper = wrappers.get();
      boolean result = wrapperSet.remove(wrapper.set((StructLike) obj));
      wrapper.set(null); // don't hold a reference to the value
      return result;
    }
    return false;
  }

  /**
   * 判断是否包含集合中的所有元素。
   *
   * @param objects 待检查的集合
   * @return true 表示全部包含
   */
  @Override
  public boolean containsAll(Collection<?> objects) {
    if (objects != null) {
      return Iterables.all(objects, this::contains);
    }
    return false;
  }

  /**
   * 批量添加元素。
   *
   * @param structs 待添加的结构化值集合
   * @return true 表示集合发生了变化
   */
  @Override
  public boolean addAll(Collection<? extends StructLike> structs) {
    if (structs != null) {
      return Iterables.addAll(
          wrapperSet, Iterables.transform(structs, struct -> wrappers.get().copyFor(struct)));
    }
    return false;
  }

  /**
   * 仅保留指定集合中的元素（求交集）。不支持。
   *
   * @param objects 要保留的元素集合
   * @return 不支持，始终抛出异常
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public boolean retainAll(Collection<?> objects) {
    throw new UnsupportedOperationException("retailAll is not supported");
  }

  /**
   * 批量移除元素。
   *
   * @param objects 待移除的元素集合
   * @return true 表示集合发生了变化
   */
  @Override
  public boolean removeAll(Collection<?> objects) {
    boolean changed = false;
    if (objects != null) {
      for (Object object : objects) {
        changed |= remove(object);
      }
    }
    return changed;
  }

  /** 清空所有元素。 */
  @Override
  public void clear() {
    wrapperSet.clear();
  }

  /**
   * 比较两个 StructLikeSet 是否相等。
   *
   * <p>相等条件：类型相同 + 大小相同 + 包含对方所有元素。
   *
   * @param o 待比较的对象
   * @return true 表示相等
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StructLikeSet that = (StructLikeSet) o;
    if (!type.equals(that.type)) {
      return false;
    }

    if (wrapperSet.size() != that.wrapperSet.size()) {
      return false;
    }

    return containsAll(that);
  }

  /**
   * 返回哈希码：类型哈希 + 所有 wrapper 哈希之和。
   *
   * @return 哈希码
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(type) + wrapperSet.stream().mapToInt(StructLikeWrapper::hashCode).sum();
  }
}
