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

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 将多个已排序的 {@link CloseableIterable} 合并为一个有序 {@link CloseableIterable} 的工具类。
 *
 * <p>所属模块：iceberg-core；层次定位：通用 IO/迭代器工具层，服务于读取合并场景。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>合并多个输入可迭代对象，输出按指定 {@link Comparator} 全局有序的元素流
 *   <li>正确管理底层迭代器的生命周期，统一关闭已耗尽或剩余的迭代器
 *   <li>在仅有一个有效输入时直接透传，避免无谓的小顶堆开销
 * </ul>
 *
 * <p>设计意图：基于小顶堆（{@link PriorityQueue}）实现 k 路归并，假设每个输入自身已有序； 继承 {@link CloseableGroup}
 * 以集中管理所有底层迭代器的关闭，防止资源泄漏。该类非线程安全， 单次迭代的 {@link #iterator()} 仅供单线程使用。
 *
 * <p>上下游关系：依赖 {@link CloseableIterable}、{@link CloseableIterator} 与 {@link Pair}；
 * 被需要合并多路已排序数据流的上层组件（如 manifest 读取、合并扫描等）使用。
 *
 * @param <T> 此迭代器产出的元素类型
 */
public class SortedMerge<T> extends CloseableGroup implements CloseableIterable<T> {
  /** 用于全局排序的比较器。 */
  private final Comparator<T> comparator;
  /** 待合并的多个已排序输入可迭代对象。 */
  private final List<CloseableIterable<T>> iterables;

  /**
   * 构造归并迭代器。
   *
   * @param comparator 元素比较器，用于在堆中维护全局顺序
   * @param iterables 多个已排序的输入可迭代对象
   */
  public SortedMerge(Comparator<T> comparator, List<CloseableIterable<T>> iterables) {
    this.comparator = comparator;
    this.iterables = iterables;
  }

  /**
   * 创建合并迭代器。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>获取每个输入的迭代器，过滤掉已没有元素的迭代器
   *   <li>若仅剩一个有效迭代器，直接注册到 {@link CloseableGroup} 后返回，避免堆开销
   *   <li>否则用 {@link MergeIterator} 包装多路迭代器并注册关闭
   * </ol>
   *
   * @return 合并后的 {@link CloseableIterator}
   */
  @Override
  public CloseableIterator<T> iterator() {
    List<CloseableIterator<T>> iterators =
        iterables.stream()
            .map(CloseableIterable::iterator)
            .filter(Iterator::hasNext)
            .collect(Collectors.toList());

    if (iterators.size() == 1) {
      addCloseable(iterators.get(0));
      return iterators.get(0);
    } else {
      CloseableIterator<T> merge = new MergeIterator(iterators);
      addCloseable(merge);
      return merge;
    }
  }

  /**
   * 基于小顶堆将多个已排序迭代器合并为一个有序迭代器的内部实现。
   *
   * <p>设计意图：堆中存放 {@code (当前元素, 来源迭代器)} 的 {@link Pair}，按元素比较器排序； 每次 {@link #next()}
   * 弹出堆顶后，从同一来源迭代器取下一个元素回填堆，保证全局有序。
   */
  private class MergeIterator implements CloseableIterator<T> {
    /** 小顶堆，按 {@link Pair#first()}（即当前元素）排序。 */
    private final PriorityQueue<Pair<T, Iterator<T>>> heap;

    /**
     * 构造归并迭代器，将所有输入迭代器的首个元素入堆。
     *
     * @param iterators 多个已排序的输入迭代器
     */
    private MergeIterator(Iterable<CloseableIterator<T>> iterators) {
      this.heap = new PriorityQueue<>(Comparator.comparing(Pair::first, comparator));
      iterators.forEach(this::addNext);
    }

    /** @return 堆是否非空，即是否还有元素可输出 */
    @Override
    public boolean hasNext() {
      return !heap.isEmpty();
    }

    /**
     * 弹出堆顶元素并将其来源迭代器的下一个元素回填入堆。
     *
     * <p>逻辑：从堆顶取出 {@link Pair}，向堆中回填该来源迭代器的下一个元素（若已耗尽则关闭之）， 返回弹出的元素。
     *
     * @return 当前全局最小元素
     * @throws NoSuchElementException 当堆为空时抛出
     */
    @Override
    public T next() {
      if (heap.isEmpty()) {
        throw new NoSuchElementException();
      }

      Pair<T, Iterator<T>> pair = heap.poll();

      addNext(pair.second());

      return pair.first();
    }

    /**
     * 从给定迭代器取下一个元素并入堆；若迭代器已耗尽则尝试关闭。
     *
     * @param iter 来源迭代器
     */
    private void addNext(Iterator<T> iter) {
      if (iter.hasNext()) {
        heap.add(Pair.of(iter.next(), iter));
      } else {
        close(iter);
      }
    }

    /**
     * 关闭归并迭代器，清空堆并关闭所有剩余的来源迭代器。
     *
     * @throws IOException 当底层迭代器关闭失败时抛出
     */
    @Override
    public void close() throws IOException {
      while (!heap.isEmpty()) {
        Pair<T, Iterator<T>> pair = heap.poll();
        close(pair.second());
      }
    }

    /**
     * 关闭实现了 {@link Closeable} 的迭代器，将 {@link IOException} 包装为 {@link UncheckedIOException} 抛出。
     *
     * @param iter 待关闭的迭代器
     */
    private void close(Iterator<?> iter) {
      if (iter instanceof Closeable) {
        try {
          ((Closeable) iter).close();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }
}
