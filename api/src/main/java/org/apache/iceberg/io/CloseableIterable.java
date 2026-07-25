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
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 文件级说明：可关闭的可迭代接口，把 {@link Iterable} 与 {@link Closeable} 结合，使其在 迭代完成后能显式释放底层资源。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link Iterable} 语义的元素遍历，且 {@link #iterator()} 返回 {@link CloseableIterator}。
 *   <li>提供 {@link #close()} 释放底层资源。
 *   <li>提供丰富的静态工厂方法：空迭代（{@link #empty()}）、无操作关闭包装 （{@link #withNoopClose}）、组合 close（{@link
 *       #combine}）、完成回调 （{@link #whenComplete}）、过滤（{@link #filter}）、计数（{@link #count}）、 转换（{@link
 *       #transform}）、拼接（{@link #concat}）。
 * </ul>
 *
 * <p>设计意图：扫描任务结果、读取的数据集等都需要在迭代完毕后释放底层文件/网络资源， 标准 {@link Iterable} 缺少关闭语义。本接口补齐 close
 * 契约，并通过静态工厂提供常见装饰 模式的便捷构造，所有装饰都正确传播 close 调用，避免资源泄漏。
 *
 * <p>上下游关系：被 Iceberg 读路径、扫描任务结果集、各引擎集成广泛使用；下游装饰器 （FilterIterator、ConcatCloseableIterable 等）基于本接口构建。
 *
 * @param <T> 迭代元素类型
 */
public interface CloseableIterable<T> extends Iterable<T>, Closeable {

  /**
   * 返回元素类型为 {@code T} 的可关闭迭代器。
   *
   * @return {@link CloseableIterator}
   */
  @Override
  CloseableIterator<T> iterator();

  /**
   * 将单个元素包装为 close 为空操作的 {@link CloseableIterable}。
   *
   * @param entry 单个元素
   * @param <E> 元素类型
   * @return 只含一个元素且 close 空操作的 CloseableIterable
   */
  static <E> CloseableIterable<E> withNoopClose(E entry) {
    return withNoopClose(ImmutableList.of(entry));
  }

  /**
   * 将普通 {@link Iterable} 包装为 close 为空操作的 {@link CloseableIterable}。
   *
   * <p>设计要点：当底层 iterable 不持有需要释放的资源时使用；close 为空操作， iterator 通过 {@link
   * CloseableIterator#withClose(Iterator)} 包装。
   *
   * @param iterable 底层 iterable
   * @param <E> 元素类型
   * @return close 空操作的 CloseableIterable
   */
  static <E> CloseableIterable<E> withNoopClose(Iterable<E> iterable) {
    return new CloseableIterable<E>() {
      @Override
      public void close() {}

      @Override
      public CloseableIterator<E> iterator() {
        return CloseableIterator.withClose(iterable.iterator());
      }
    };
  }

  /**
   * 返回一个空的 {@link CloseableIterable}。
   *
   * @param <E> 元素类型
   * @return 空 CloseableIterable
   */
  static <E> CloseableIterable<E> empty() {
    return withNoopClose(Collections.emptyList());
  }

  /**
   * 将普通 {@link Iterable} 与一个 {@link Closeable} 组合为 {@link CloseableIterable}， close 时关闭传入的
   * closeable。
   *
   * @param iterable 元素来源
   * @param closeable 关闭时需释放的资源
   * @param <E> 元素类型
   * @return 组合后的 CloseableIterable
   */
  static <E> CloseableIterable<E> combine(Iterable<E> iterable, Closeable closeable) {
    return new CloseableIterable<E>() {
      @Override
      public void close() throws IOException {
        closeable.close();
      }

      @Override
      public CloseableIterator<E> iterator() {
        return CloseableIterator.withClose(iterable.iterator());
      }
    };
  }

  /**
   * 包装一个 {@link CloseableIterable}，在 {@link #close()} 被调用后执行给定的 Runnable。
   *
   * <p>逻辑：close 时先关闭底层 iterable，无论是否抛异常都在 finally 中执行回调，确保 完成动作（如释放外部状态、记录指标）一定被触发。
   *
   * @param iterable 底层 CloseableIterable
   * @param onCompletionRunnable 底层关闭后执行的回调
   * @param <E> 元素类型
   * @return 关闭后执行回调的新 CloseableIterable
   */
  static <E> CloseableIterable<E> whenComplete(
      CloseableIterable<E> iterable, Runnable onCompletionRunnable) {
    Preconditions.checkNotNull(onCompletionRunnable, "Invalid runnable: null");
    return new CloseableIterable<E>() {
      @Override
      public void close() throws IOException {
        try {
          iterable.close();
        } finally {
          onCompletionRunnable.run();
        }
      }

      @Override
      public CloseableIterator<E> iterator() {
        return iterable.iterator();
      }
    };
  }

  /**
   * 用给定谓词过滤 {@link CloseableIterable}，返回保留元素的新 CloseableIterable。
   *
   * <p>逻辑：基于 {@link FilterIterator} 构造过滤迭代器，并通过 {@link #combine} 把 底层 iterable 的 close 传播给结果。
   *
   * @param iterable 底层 iterable
   * @param pred 保留谓词
   * @param <E> 元素类型
   * @return 过滤后的 CloseableIterable
   */
  static <E> CloseableIterable<E> filter(CloseableIterable<E> iterable, Predicate<E> pred) {
    return combine(
        () ->
            new FilterIterator<E>(iterable.iterator()) {
              @Override
              protected boolean shouldKeep(E item) {
                return pred.test(item);
              }
            },
        iterable);
  }

  /**
   * 过滤 {@link CloseableIterable}，并在元素不匹配谓词时递增 {@link Counter}，用于统计被 跳过的元素数量。
   *
   * <p>逻辑：基于 {@link FilterIterator} 构造过滤迭代器；shouldKeep 中先求谓词，若不匹配 则递增 skipCounter，再返回匹配结果。
   *
   * @param skipCounter 不匹配时递增的计数器
   * @param iterable 底层 iterable
   * @param pred 保留谓词
   * @param <E> 元素类型
   * @return 过滤并统计跳过数的 CloseableIterable
   */
  static <E> CloseableIterable<E> filter(
      Counter skipCounter, CloseableIterable<E> iterable, Predicate<E> pred) {
    Preconditions.checkArgument(null != skipCounter, "Invalid counter: null");
    Preconditions.checkArgument(null != iterable, "Invalid iterable: null");
    Preconditions.checkArgument(null != pred, "Invalid predicate: null");
    return combine(
        () ->
            new FilterIterator<E>(iterable.iterator()) {
              @Override
              protected boolean shouldKeep(E item) {
                boolean matches = pred.test(item);
                if (!matches) {
                  skipCounter.increment();
                }
                return matches;
              }
            },
        iterable);
  }

  /**
   * 计数 {@link CloseableIterable} 的元素个数：每次 {@link Iterator#next()} 调用时递增 给定的 {@link Counter}。
   *
   * @param counter 每次 next 递增的计数器
   * @param iterable 底层 iterable
   * @param <T> 元素类型
   * @return 计数包装后的 CloseableIterable
   */
  static <T> CloseableIterable<T> count(Counter counter, CloseableIterable<T> iterable) {
    Preconditions.checkArgument(null != counter, "Invalid counter: null");
    Preconditions.checkArgument(null != iterable, "Invalid iterable: null");
    return new CloseableIterable<T>() {
      @Override
      public CloseableIterator<T> iterator() {
        return CloseableIterator.count(counter, iterable.iterator());
      }

      @Override
      public void close() throws IOException {
        iterable.close();
      }
    };
  }

  /**
   * 把 {@link CloseableIterable} 的元素按 {@code transform} 函数转换为另一类型，并正确 传播 close。
   *
   * <p>逻辑：返回匿名 CloseableIterable，close 委托给底层 iterable；iterator 返回一个 匿名
   * CloseableIterator，其内部持有底层迭代器，close/hasNext 转发，next 应用 transform。
   *
   * @param iterable 底层 iterable
   * @param transform 元素转换函数
   * @param <I> 输入元素类型
   * @param <O> 输出元素类型
   * @return 转换后的 CloseableIterable
   */
  static <I, O> CloseableIterable<O> transform(
      CloseableIterable<I> iterable, Function<I, O> transform) {
    Preconditions.checkNotNull(transform, "Invalid transform: null");

    return new CloseableIterable<O>() {
      @Override
      public void close() throws IOException {
        iterable.close();
      }

      @Override
      public CloseableIterator<O> iterator() {
        return new CloseableIterator<O>() {
          private final CloseableIterator<I> inner = iterable.iterator();

          @Override
          public void close() throws IOException {
            inner.close();
          }

          @Override
          public boolean hasNext() {
            return inner.hasNext();
          }

          @Override
          public O next() {
            return transform.apply(inner.next());
          }
        };
      }
    };
  }

  /**
   * 把多个 {@link CloseableIterable} 拼接为一个，按顺序遍历所有元素，并在关闭时关闭 所有子 iterable。
   *
   * @param iterable 由多个 CloseableIterable 组成的可迭代对象
   * @param <E> 元素类型
   * @return 拼接后的 CloseableIterable
   */
  static <E> CloseableIterable<E> concat(Iterable<CloseableIterable<E>> iterable) {
    return new ConcatCloseableIterable<>(iterable);
  }

  /**
   * 拼接 CloseableIterable 的实现类：继承 {@link CloseableGroup} 集中管理子迭代器的关闭。
   *
   * <p>设计意图：每次 {@link #iterator()} 调用都会构造一个新的 ConcatCloseableIterator 并 注册到 CloseableGroup，使得外层
   * close 能统一关闭所有由迭代器打开的子 iterable。
   */
  class ConcatCloseableIterable<E> extends CloseableGroup implements CloseableIterable<E> {
    private final Iterable<CloseableIterable<E>> inputs;

    ConcatCloseableIterable(Iterable<CloseableIterable<E>> inputs) {
      this.inputs = inputs;
    }

    /**
     * 返回拼接迭代器，并将其注册到 {@link CloseableGroup} 以便统一关闭。
     *
     * @return 拼接迭代器
     */
    @Override
    public CloseableIterator<E> iterator() {
      ConcatCloseableIterator<E> iter = new ConcatCloseableIterator<>(inputs);
      addCloseable(iter);
      return iter;
    }

    /**
     * 拼接迭代器内部实现：依次遍历每个子 iterable，及时关闭已用完的子 iterable，全部 耗尽后进入 closed 状态。
     *
     * <p>设计意图：维护“当前 iterable / 当前 iterator”两个游标，hasNext 时若当前 iterator 已无元素则切换到下一个子
     * iterable，切换前关闭上一个；所有子 iterable 耗尽时关闭 当前子 iterable 并标记 closed，避免重复关闭。
     */
    private static class ConcatCloseableIterator<E> implements CloseableIterator<E> {
      private final Iterator<CloseableIterable<E>> iterables;
      private CloseableIterable<E> currentIterable = null;
      private Iterator<E> currentIterator = null;
      private boolean closed = false;

      private ConcatCloseableIterator(Iterable<CloseableIterable<E>> inputs) {
        this.iterables = inputs.iterator();
      }

      /**
       * 判断是否还有可输出元素。
       *
       * <p>逻辑：若已 closed 直接返回 false；若当前 iterator 仍有元素返回 true；否则循环 切换到下一个子 iterable——切换前关闭上一个
       * currentIterable，取下一个并取其 iterator， 若新 iterator 有元素则返回 true；全部耗尽后关闭最后一个 currentIterable，置
       * closed 为 true，返回 false。
       *
       * @return 还有元素返回 true，否则返回 false
       */
      @Override
      public boolean hasNext() {
        if (closed) {
          return false;
        }

        if (null != currentIterator && currentIterator.hasNext()) {
          return true;
        }

        while (iterables.hasNext()) {
          try {
            if (null != currentIterable) {
              currentIterable.close();
            }
          } catch (IOException e) {
            throw new RuntimeIOException(e, "Failed to close iterable");
          }

          this.currentIterable = iterables.next();
          this.currentIterator = currentIterable.iterator();

          if (currentIterator.hasNext()) {
            return true;
          }
        }

        try {
          if (null != currentIterable) {
            currentIterable.close();
          }
        } catch (IOException e) {
          throw new RuntimeIOException(e, "Failed to close iterable");
        }

        this.closed = true;
        this.currentIterator = null;
        this.currentIterable = null;

        return false;
      }

      /**
       * 关闭本拼接迭代器：若未关闭，则关闭当前 currentIterable 并标记 closed，清空游标。
       *
       * @throws IOException 关闭过程中抛出的 IO 异常
       */
      @Override
      public void close() throws IOException {
        if (!closed) {
          if (null != currentIterable) {
            currentIterable.close();
          }
          this.closed = true;
          this.currentIterator = null;
          this.currentIterable = null;
        }
      }

      /**
       * 返回下一个元素。
       *
       * <p>逻辑：先调用 {@link #hasNext()} 推进到下一个有元素的子 iterator，再取其 next； 若无元素则抛 {@link
       * NoSuchElementException}。
       *
       * @return 下一个元素
       * @throws NoSuchElementException 若无下一元素
       */
      @Override
      public E next() {
        if (hasNext()) {
          return currentIterator.next();
        } else {
          throw new NoSuchElementException();
        }
      }
    }
  }
}
