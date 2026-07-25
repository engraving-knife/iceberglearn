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
package org.apache.iceberg.io;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Function;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：可关闭的迭代器接口，把 {@link Iterator} 与 {@link Closeable} 结合，使其在 迭代完成后能显式释放底层资源。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供标准 {@link Iterator} 语义的元素遍历。
 *   <li>提供 {@link #close()} 方法释放迭代过程中持有的底层资源（流、文件句柄等）。
 *   <li>提供若干静态工厂方法（{@link #withClose}、{@link #transform}、{@link #count}、 {@link
 *       #empty()}）以组合和装饰既有迭代器。
 * </ul>
 *
 * <p>设计意图：扫描/读取数据时迭代器背后常持有打开的文件或网络流，标准 {@link Iterator} 没有关闭语义，会导致资源泄漏。本接口补齐关闭契约；并通过默认方法/静态工厂让常见装饰
 * （包装、转换、计数）都能正确传播 close 调用。
 *
 * <p>上下游关系：由 {@link CloseableIterable#iterator()} 等返回；被 Iceberg 读路径及各引擎 集成消费；上层装饰器（FilterIterator
 * 等）基于本接口构建。
 *
 * @param <T> 迭代元素类型
 */
public interface CloseableIterator<T> extends Iterator<T>, Closeable {

  /**
   * 返回一个空的 {@link CloseableIterator}。
   *
   * @param <E> 元素类型
   * @return 空 closeable 迭代器
   */
  static <E> CloseableIterator<E> empty() {
    return withClose(Collections.emptyIterator());
  }

  /**
   * 将普通 {@link Iterator} 包装为 {@link CloseableIterator}。
   *
   * <p>逻辑：若 iterator 已经是 CloseableIterator 则直接返回（避免双层包装）；否则返回一个 匿名实现，其 close 在 iterator 实现 {@link
   * Closeable} 时委托关闭，hasNext/next 直接 转发。
   *
   * @param iterator 原始迭代器
   * @param <E> 元素类型
   * @return 包装后的 CloseableIterator
   */
  static <E> CloseableIterator<E> withClose(Iterator<E> iterator) {
    if (iterator instanceof CloseableIterator) {
      return (CloseableIterator<E>) iterator;
    }

    return new CloseableIterator<E>() {
      @Override
      public void close() throws IOException {
        if (iterator instanceof Closeable) {
          ((Closeable) iterator).close();
        }
      }

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public E next() {
        return iterator.next();
      }
    };
  }

  /**
   * 将输入迭代器元素按 {@code transform} 函数转换后输出，并正确传播 close。
   *
   * <p>逻辑：返回匿名 CloseableIterator，close 委托给底层迭代器；hasNext 转发； next 调用 transform 应用转换。
   *
   * @param iterator 输入迭代器
   * @param transform 元素转换函数
   * @param <I> 输入元素类型
   * @param <O> 输出元素类型
   * @return 转换后的 CloseableIterator
   */
  static <I, O> CloseableIterator<O> transform(
      CloseableIterator<I> iterator, Function<I, O> transform) {
    Preconditions.checkNotNull(transform, "Invalid transform: null");

    return new CloseableIterator<O>() {
      @Override
      public void close() throws IOException {
        iterator.close();
      }

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public O next() {
        return transform.apply(iterator.next());
      }
    };
  }

  /**
   * 包装迭代器，在每次 {@code next} 时递增给定的 {@link Counter}，用于元素计数。
   *
   * <p>逻辑：返回匿名 CloseableIterator，close 委托给底层迭代器；hasNext 转发； next 取出元素后调用 counter.increment() 再返回元素。
   *
   * @param counter 计数器
   * @param iterator 输入迭代器
   * @param <T> 元素类型
   * @return 计数包装后的 CloseableIterator
   */
  static <T> CloseableIterator<T> count(Counter counter, CloseableIterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() throws IOException {
        iterator.close();
      }

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        T next = iterator.next();
        counter.increment();
        return next;
      }
    };
  }
}
