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

import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FilterIterator;

/**
 * 泛型过滤器基类：对可迭代元素按自定义规则进行保留/丢弃。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：子类实现 {@link #shouldKeep(Object)} 决定保留策略，本类提供惰性过滤的 {@link Iterable} 与 {@link
 * CloseableIterable} 视图，避免提前物化全部结果。
 *
 * <p>设计意图：基于 {@link FilterIterator} 实现流式过滤，使调用方可以在遍历时才逐个判定， 降低内存占用，并支持与 CloseableIterable
 * 组合以保证资源释放。
 *
 * <p>上下游关系：被扫描计划、manifest 读取等需要按条件筛选元素的场景使用； 依赖 api 的 {@link CloseableIterable} 与 {@link
 * FilterIterator}。
 *
 * @param <T> 被过滤元素的类型
 */
public abstract class Filter<T> {

  /**
   * 判定单个元素是否保留。
   *
   * @param item 待判定元素
   * @return 保留返回 true，丢弃返回 false
   */
  protected abstract boolean shouldKeep(T item);

  /**
   * 返回过滤后的惰性 Iterable 视图。
   *
   * <p>设计要点：返回的是一个每次 iterator() 调用都会新建过滤迭代器的 Iterable， 元素在被遍历时才判定，不预先物化。
   *
   * @param items 原始可迭代元素
   * @return 过滤后的 Iterable
   */
  public Iterable<T> filter(Iterable<T> items) {
    return () -> new Iterator(items.iterator());
  }

  /**
   * 返回过滤后的 CloseableIterable 视图，关闭时委托给原 items 关闭。
   *
   * <p>设计要点：通过 {@link CloseableIterable#combine} 把过滤后的 Iterable 与原 CloseableIterable
   * 组合，保证遍历结束后底层资源（如文件句柄）能被正确释放。
   *
   * @param items 原始可关闭迭代元素
   * @return 过滤后的 CloseableIterable
   */
  public CloseableIterable<T> filter(CloseableIterable<T> items) {
    return CloseableIterable.combine(filter((Iterable<T>) items), items);
  }

  private class Iterator extends FilterIterator<T> {
    protected Iterator(java.util.Iterator<T> items) {
      super(items);
    }

    @Override
    protected boolean shouldKeep(T item) {
      return Filter.this.shouldKeep(item);
    }
  }
}
