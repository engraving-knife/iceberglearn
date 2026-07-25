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
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;

/**
 * 并行迭代器：使用线程池并行消费多个子 Iterable，将所有元素汇聚到一个线程安全队列供调用方顺序读取。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：把多个 {@link Iterable} 的元素通过 {@link ExecutorService} 并行拉取到 {@link
 * ConcurrentLinkedQueue}，对外提供统一的 {@link CloseableIterator}，实现"多生产者-单消费者"模型。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>预取与背压：每个 worker 同时提交 2 个任务（{@code 2 * WORKER_THREAD_POOL_SIZE}），当队列有数据时
 *       消费者优先消费而不提交新任务，避免记录堆积占用内存；当队列空时才检查任务状态并补充提交。
 *   <li>异常传播：后台任务抛出的异常通过 {@link Future#get()} 捕获并在 {@code checkTasks} 中重新抛出， 确保并行迭代中的错误不会丢失。
 *   <li>资源安全：继承 {@link CloseableGroup}，迭代器关闭时取消所有未完成任务并清空队列。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.ManifestGroup} 等扫描计划类使用，并行读取多个 manifest 的数据文件列表；依赖 {@link
 * ThreadPools} 提供的 worker 线程池。
 */
public class ParallelIterable<T> extends CloseableGroup implements CloseableIterable<T> {
  private final Iterable<? extends Iterable<T>> iterables;
  private final ExecutorService workerPool;

  /**
   * 构造并行迭代器。
   *
   * @param iterables 多个子 Iterable 的集合
   * @param workerPool 用于并行拉取的线程池
   */
  public ParallelIterable(Iterable<? extends Iterable<T>> iterables, ExecutorService workerPool) {
    this.iterables = iterables;
    this.workerPool = workerPool;
  }

  /**
   * 创建并行迭代器；迭代器被注册到 {@link CloseableGroup} 以便统一关闭。
   *
   * @return 并行迭代器
   */
  @Override
  public CloseableIterator<T> iterator() {
    ParallelIterator<T> iter = new ParallelIterator<>(iterables, workerPool);
    addCloseable(iter);
    return iter;
  }

  /** 并行迭代器实现：维护一组 Future 任务槽位和一个共享队列，按需提交任务并从队列取元素。 */
  private static class ParallelIterator<T> implements CloseableIterator<T> {
    private final Iterator<Runnable> tasks;
    private final ExecutorService workerPool;
    private final Future<?>[] taskFutures;
    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean closed = false;

    private ParallelIterator(
        Iterable<? extends Iterable<T>> iterables, ExecutorService workerPool) {
      this.tasks =
          Iterables.transform(
                  iterables,
                  iterable ->
                      (Runnable)
                          () -> {
                            try (Closeable ignored =
                                (iterable instanceof Closeable) ? (Closeable) iterable : () -> {}) {
                              for (T item : iterable) {
                                queue.add(item);
                              }
                            } catch (IOException e) {
                              throw new RuntimeIOException(e, "Failed to close iterable");
                            }
                          })
              .iterator();
      this.workerPool = workerPool;
      // 每个 worker 同时提交 2 个任务以提高并行度
      this.taskFutures = new Future[2 * ThreadPools.WORKER_THREAD_POOL_SIZE];
    }

    @Override
    public void close() {
      // close first, avoid new task submit
      this.closed = true;

      // cancel background tasks
      for (Future<?> taskFuture : taskFutures) {
        if (taskFuture != null && !taskFuture.isDone()) {
          taskFuture.cancel(true);
        }
      }
      // clean queue
      this.queue.clear();
    }

    /**
     * 检查任务运行状态并按需提交新任务。
     *
     * <p>逻辑：遍历所有 Future 槽位，若槽位为空或任务已完成则尝试提交新任务；对已完成任务调用 {@code get()}
     * 检查异常并重新抛出。返回是否仍有未完成的任务或还有待提交的任务。
     *
     * @return 仍有待处理任务返回 true；否则返回 false
     */
    private boolean checkTasks() {
      boolean hasRunningTask = false;

      for (int i = 0; i < taskFutures.length; i += 1) {
        if (taskFutures[i] == null || taskFutures[i].isDone()) {
          if (taskFutures[i] != null) {
            // check for task failure and re-throw any exception
            try {
              taskFutures[i].get();
            } catch (ExecutionException e) {
              if (e.getCause() instanceof RuntimeException) {
                // rethrow a runtime exception
                throw (RuntimeException) e.getCause();
              } else {
                throw new RuntimeException("Failed while running parallel task", e.getCause());
              }
            } catch (InterruptedException e) {
              throw new RuntimeException("Interrupted while running parallel task", e);
            }
          }

          taskFutures[i] = submitNextTask();
        }

        if (taskFutures[i] != null) {
          hasRunningTask = true;
        }
      }

      return !closed && (tasks.hasNext() || hasRunningTask);
    }

    /**
     * 提交下一个任务到线程池；若已关闭或无更多任务则返回 null。
     *
     * @return 新提交任务的 Future，或 null
     */
    private Future<?> submitNextTask() {
      if (!closed && tasks.hasNext()) {
        return workerPool.submit(tasks.next());
      }
      return null;
    }

    @Override
    public synchronized boolean hasNext() {
      Preconditions.checkState(!closed, "Already closed");

      // if the consumer is processing records more slowly than the producers, then this check will
      // prevent tasks from being submitted. while the producers are running, this will always
      // return here before running checkTasks. when enough of the tasks are finished that the
      // consumer catches up, then lots of new tasks will be submitted at once. this behavior is
      // okay because it ensures that records are not stacking up waiting to be consumed and taking
      // up memory.
      //
      // consumers that process results quickly will periodically exhaust the queue and submit new
      // tasks when checkTasks runs. fast consumers should not be delayed.
      if (!queue.isEmpty()) {
        return true;
      }

      // this cannot conclude that there are no more records until tasks have finished. while some
      // are running, return true when there is at least one item to return.
      while (checkTasks()) {
        if (!queue.isEmpty()) {
          return true;
        }

        try {
          Thread.sleep(10);

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }

      // when tasks are no longer running, return whether the queue has items
      return !queue.isEmpty();
    }

    @Override
    public synchronized T next() {
      // use hasNext to block until there is an available record
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return queue.poll();
    }
  }
}
