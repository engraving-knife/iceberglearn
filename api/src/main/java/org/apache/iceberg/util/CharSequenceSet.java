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
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Iterators;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;

/**
 * 基于 {@link CharSequenceWrapper} 的 {@link Set} 实现：使不同类型但内容相同的 {@link CharSequence}（如 String 与
 * StringBuilder）在集合中按内容判等而非引用判等。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link Set} 接口，内部以 {@link CharSequenceWrapper} 为元素，按字符内容去重。
 *   <li>提供 {@code of}/{@code empty} 工厂方法构造实例。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>JDK Set 直接用 CharSequence 作为 key 时，String 与 StringBuilder 即使内容相同也因 equals/hashCode
 *       不一致而无法去重。通过包装为 {@link CharSequenceWrapper}（按内容 计算 hashCode 与 equals），统一判等语义。
 *   <li>使用 {@link ThreadLocal} 缓存临时 wrapper 用于 contains/remove 查询，避免每次查询都 新建 wrapper 对象，降低 GC
 *       压力；用完立即 {@code set(null)} 释放引用防止内存泄漏。
 *   <li>可序列化：继承 {@link Serializable}，便于随表对象序列化。
 * </ul>
 *
 * <p>上下游关系：被 core 模块及表达式求值逻辑用于维护字段值集合（如 IN 谓词的字面量集合）。
 */
public class CharSequenceSet implements Set<CharSequence>, Serializable {
  /** 线程局部缓存的可复用 wrapper，用于 contains/remove 等查询操作，避免重复分配。 */
  private static final ThreadLocal<CharSequenceWrapper> wrappers =
      ThreadLocal.withInitial(() -> CharSequenceWrapper.wrap(null));

  /**
   * 用给定字符序列集合构造 {@link CharSequenceSet}。
   *
   * @param charSequences 初始元素
   * @return 包含给定元素的集合
   */
  public static CharSequenceSet of(Iterable<CharSequence> charSequences) {
    return new CharSequenceSet(charSequences);
  }

  /**
   * 返回空的 {@link CharSequenceSet}。
   *
   * @return 空集合
   */
  public static CharSequenceSet empty() {
    return new CharSequenceSet(ImmutableList.of());
  }

  private final Set<CharSequenceWrapper> wrapperSet;

  private CharSequenceSet(Iterable<CharSequence> charSequences) {
    this.wrapperSet =
        Sets.newHashSet(Iterables.transform(charSequences, CharSequenceWrapper::wrap));
  }

  @Override
  public int size() {
    return wrapperSet.size();
  }

  @Override
  public boolean isEmpty() {
    return wrapperSet.isEmpty();
  }

  /**
   * 判断是否包含指定对象。
   *
   * <p>逻辑：若 obj 为 CharSequence，则借用线程局部 wrapper 包装后查询底层 wrapperSet， 查询完毕清空 wrapper 引用避免泄漏；非
   * CharSequence 返回 false。
   *
   * @param obj 待判断对象
   * @return 是否包含
   */
  @Override
  public boolean contains(Object obj) {
    if (obj instanceof CharSequence) {
      CharSequenceWrapper wrapper = wrappers.get();
      boolean result = wrapperSet.contains(wrapper.set((CharSequence) obj));
      wrapper.set(null); // don't hold a reference to the value
      return result;
    }
    return false;
  }

  @Override
  public Iterator<CharSequence> iterator() {
    return Iterators.transform(wrapperSet.iterator(), CharSequenceWrapper::get);
  }

  @Override
  public Object[] toArray() {
    return Iterators.toArray(iterator(), CharSequence.class);
  }

  /**
   * 将集合元素填入指定数组。
   *
   * <p>逻辑：若目标数组容量不足则新建 Object 数组返回；否则逐个填入，多余位置在末尾置 null 以符合 {@link Set#toArray(Object[])} 契约。
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

    Iterator<CharSequence> iter = iterator();
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

  @Override
  public boolean add(CharSequence charSequence) {
    return wrapperSet.add(CharSequenceWrapper.wrap(charSequence));
  }

  /**
   * 移除指定对象。
   *
   * <p>逻辑：与 {@link #contains} 类似，借用线程局部 wrapper 包装后从底层集合移除， 完毕清空引用；非 CharSequence 返回 false。
   *
   * @param obj 待移除对象
   * @return 是否实际移除
   */
  @Override
  public boolean remove(Object obj) {
    if (obj instanceof CharSequence) {
      CharSequenceWrapper wrapper = wrappers.get();
      boolean result = wrapperSet.remove(wrapper.set((CharSequence) obj));
      wrapper.set(null); // don't hold a reference to the value
      return result;
    }
    return false;
  }

  @Override
  @SuppressWarnings("CollectionUndefinedEquality")
  public boolean containsAll(Collection<?> objects) {
    if (objects != null) {
      return Iterables.all(objects, this::contains);
    }
    return false;
  }

  @Override
  public boolean addAll(Collection<? extends CharSequence> charSequences) {
    if (charSequences != null) {
      return Iterables.addAll(
          wrapperSet, Iterables.transform(charSequences, CharSequenceWrapper::wrap));
    }
    return false;
  }

  /**
   * 仅保留同时存在于给定集合中的元素（交集）。
   *
   * <p>逻辑：将给定集合中的 CharSequence 元素包装为 wrapper 集合，再对底层 wrapperSet 做 retainAll。
   *
   * @param objects 要保留的元素集合
   * @return 集合是否发生变化
   */
  @Override
  public boolean retainAll(Collection<?> objects) {
    if (objects != null) {
      Set<CharSequenceWrapper> toRetain =
          objects.stream()
              .filter(CharSequence.class::isInstance)
              .map(CharSequence.class::cast)
              .map(CharSequenceWrapper::wrap)
              .collect(Collectors.toSet());

      return Iterables.retainAll(wrapperSet, toRetain);
    }

    return false;
  }

  /**
   * 移除所有存在于给定集合中的元素。
   *
   * <p>逻辑：逐个调用 {@link #remove} 过滤，若有任意元素被移除则返回 true。
   *
   * @param objects 要移除的元素集合
   * @return 集合是否发生变化
   */
  @Override
  @SuppressWarnings("CollectionUndefinedEquality")
  public boolean removeAll(Collection<?> objects) {
    if (objects != null) {
      return objects.stream().filter(this::remove).count() != 0;
    }

    return false;
  }

  @Override
  public void clear() {
    wrapperSet.clear();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    CharSequenceSet that = (CharSequenceSet) o;
    return wrapperSet.equals(that.wrapperSet);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(wrapperSet);
  }

  @Override
  public String toString() {
    return Streams.stream(iterator()).collect(Collectors.joining("CharSequenceSet({", ", ", "})"));
  }
}
