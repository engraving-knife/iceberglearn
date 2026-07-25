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
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 文件级说明：过滤迭代器抽象类，基于另一个迭代器按 {@link #shouldKeep(Object)} 谓词保留 元素，并实现 {@link CloseableIterator}
 * 以便正确关闭底层迭代器。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：遍历底层迭代器，仅向调用方暴露满足谓词的元素；迭代耗尽时关闭底层迭代器。
 *
 * <p>设计意图：采用“预取下一个匹配项”的状态机模式（nextReady/next 字段），让 hasNext
 * 可重入且无副作用——只在尚未预取到下一个匹配项时才推进底层迭代器；当底层耗尽或本迭代器 被关闭后，调用 close 释放底层资源。把 {@link IOException} 包装为 {@link
 * UncheckedIOException} 以符合 Iterator 不抛受检异常的契约。
 *
 * <p>上下游关系：由 {@link CloseableIterable#filter(CloseableIterable, java.util.function.Predicate)}
 * 等工厂使用；被 Iceberg 读路径中需要按谓词过滤元素的场景消费。
 *
 * @param <T> 本迭代器产出的元素类型
 */
public abstract class FilterIterator<T> implements CloseableIterator<T> {
  private final Iterator<T> items;
  private boolean closed;
  private boolean nextReady;
  private T next;

  /**
   * 构造过滤迭代器。
   *
   * @param items 被过滤的底层迭代器
   */
  protected FilterIterator(Iterator<T> items) {
    this.items = items;
    this.closed = false;
    this.next = null;
    this.nextReady = false;
  }

  /**
   * 谓词方法：判断给定元素是否应保留。由子类实现。
   *
   * @param item 待判断元素
   * @return 保留返回 true，丢弃返回 false
   */
  protected abstract boolean shouldKeep(T item);

  /**
   * 是否还有可输出元素。
   *
   * <p>逻辑：若已预取到下一个匹配项（nextReady）则直接返回 true；否则调用 {@link #advance()} 推进底层迭代器寻找下一个匹配项。
   *
   * @return 还有可输出元素返回 true，否则返回 false
   */
  @Override
  public boolean hasNext() {
    return nextReady || advance();
  }

  /**
   * 返回下一个匹配元素。
   *
   * <p>逻辑：若无下一元素则抛 {@link NoSuchElementException}；否则清除 nextReady 标记并 返回缓存的 next。
   *
   * @return 下一个匹配元素
   * @throws NoSuchElementException 若无下一元素
   */
  @Override
  public T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    this.nextReady = false;

    return next;
  }

  /**
   * 推进底层迭代器寻找下一个匹配元素。
   *
   * <p>逻辑：循环读取底层元素，对每个元素调用 {@link #shouldKeep(Object)}，命中则缓存到 next、置 nextReady 为 true 并返回
   * true；若底层耗尽则关闭本迭代器、置 nextReady 为 false 并返回 false。
   *
   * @return 找到匹配元素返回 true，否则返回 false
   */
  private boolean advance() {
    while (!closed && items.hasNext()) {
      this.next = items.next();
      if (shouldKeep(next)) {
        this.nextReady = true;
        return true;
      }
    }

    close();

    this.nextReady = false;
    return false;
  }

  /**
   * 关闭底层迭代器，保证只关闭一次。
   *
   * <p>逻辑：若未关闭，则强制把 items 当作 {@link Closeable} 关闭，并把可能抛出的 {@link IOException} 包装为 {@link
   * UncheckedIOException}；最后置 closed 为 true。
   */
  @Override
  public void close() {
    if (!closed) {
      try {
        ((Closeable) items).close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }

      this.closed = true;
    }
  }
}
