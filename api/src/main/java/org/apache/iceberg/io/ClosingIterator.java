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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * 文件级说明：自动关闭迭代器，是 {@link CloseableIterator} 的便捷包装，在元素耗尽时自动 关闭底层迭代器。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：把 {@link CloseableIterator} 适配为普通 {@link Iterator}，并在迭代结束 （{@link #hasNext()} 返回
 * false）时自动调用底层 close，避免使用方忘记显式关闭导致 资源泄漏。
 *
 * <p>设计意图：许多既有 API（如 for-each 循环、guava 工具）只接受 {@link Iterator}， 不会主动调用
 * close。本类通过“耗尽即关闭”的约定，在不改变调用方写法的前提下保证资源 释放；同时把 {@link IOException} 转为 {@link UncheckedIOException}
 * 以符合 Iterator 不抛受检异常的契约。
 *
 * <p>上下游关系：由需要把 {@link CloseableIterator} 喂给不接受 Closeable 的上游消费方时 使用。
 *
 * @param <T> 迭代元素类型
 */
public class ClosingIterator<T> implements Iterator<T> {
  private final CloseableIterator<T> iterator;
  private boolean isClosed;

  /**
   * 构造包装器。
   *
   * @param iterator 被包装的可关闭迭代器
   */
  public ClosingIterator(CloseableIterator<T> iterator) {
    this.iterator = iterator;
  }

  /**
   * 判断是否还有元素；当底层已无元素时自动关闭底层迭代器。
   *
   * <p>逻辑：先查询底层 hasNext；若返回 false 且尚未关闭，则触发 close 释放资源。
   *
   * @return 还有元素返回 true，否则返回 false
   */
  @Override
  public boolean hasNext() {
    boolean hasNext = iterator.hasNext();
    if (!hasNext && !isClosed) {
      close();
    }
    return hasNext;
  }

  /**
   * 返回下一个元素。
   *
   * @return 下一个元素
   */
  @Override
  public T next() {
    return iterator.next();
  }

  /** 关闭底层迭代器，并把可能抛出的 {@link IOException} 包装为 {@link UncheckedIOException}。 */
  private void close() {
    try {
      iterator.close();
      isClosed = true;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
