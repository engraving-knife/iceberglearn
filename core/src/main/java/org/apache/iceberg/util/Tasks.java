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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 任务执行工具类，属于 iceberg-core 模块通用工具层。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供对一组任务项（item）的批量执行框架，支持单线程或并行（{@link ExecutorService}）执行
 *   <li>支持失败重试（指数退避）、失败回调、回滚（revert）与中止（abort）机制
 *   <li>通过 {@link Builder} 模式灵活配置重试策略、并发控制与异常处理策略
 * </ul>
 *
 * <p>设计意图：采用 Builder 模式将任务执行的各类配置（并发度、重试次数、退避参数、回滚/中止策略）
 * 与执行逻辑解耦；执行框架统一处理异常收集、重试退避、资源清理等横切关注点，使调用方只需关注 单个 item 的业务逻辑。该类为工具类不可实例化，所有功能通过静态工厂方法（{@link
 * #foreach}、 {@link #range}）入口使用。
 *
 * <p>上下游关系：被 iceberg 内部需要对多个文件/数据块执行相同操作的场景广泛使用，如 manifest 写入、 数据文件删除、扫描任务调度等；依赖 {@link
 * ExecutorService} 提供并行能力、{@link Counter} 进行指标统计。
 */
public class Tasks {
  private static final Logger LOG = LoggerFactory.getLogger(Tasks.class);

  /** 工具类私有构造器，禁止实例化。 */
  private Tasks() {}

  /** 表示不可恢复的异常，遇到此类异常时任务框架不会重试，而是立即终止。 */
  public static class UnrecoverableException extends RuntimeException {
    /** @param message 异常描述信息 */
    public UnrecoverableException(String message) {
      super(message);
    }

    /**
     * @param message 异常描述信息
     * @param cause 导致本异常的底层原因
     */
    public UnrecoverableException(String message, Throwable cause) {
      super(message, cause);
    }

    /** @param cause 导致本异常的底层原因 */
    public UnrecoverableException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * 失败回调任务接口，在单个 item 执行失败时被调用，用于执行清理或记录逻辑。
   *
   * @param <I> 任务项类型
   * @param <E> 可能抛出的异常类型
   */
  public interface FailureTask<I, E extends Exception> {
    /**
     * 在 item 执行失败时运行。
     *
     * @param item 执行失败的任务项
     * @param exception 导致失败的异常
     * @throws E 回调过程中可能抛出的异常
     */
    void run(I item, Exception exception) throws E;
  }

  /**
   * 单个任务项的执行接口。
   *
   * @param <I> 任务项类型
   * @param <E> 可能抛出的异常类型
   */
  public interface Task<I, E extends Exception> {
    /**
     * 对单个 item 执行业务逻辑。
     *
     * @param item 待处理的任务项
     * @throws E 执行过程中可能抛出的异常
     */
    void run(I item) throws E;
  }

  /**
   * 任务执行构建器，通过链式配置设定并发、重试、回滚、中止等策略后调用 {@link #run} 执行。
   *
   * @param <I> 任务项类型
   */
  public static class Builder<I> {
    /** 待执行的任务项集合。 */
    private final Iterable<I> items;
    /** 并行执行使用的线程池，为 null 时表示单线程执行。 */
    private ExecutorService service = null;
    /** 单个 item 失败时的回调任务。 */
    private FailureTask<I, ?> onFailure = null;
    /** 是否在首个失败后停止处理后续 item。 */
    private boolean stopOnFailure = false;
    /** 全部任务完成后是否抛出收集到的异常。 */
    private boolean throwFailureWhenFinished = true;
    /** 回滚任务，对已成功的 item 在整体失败时执行。 */
    private Task<I, ?> revertTask = null;
    /** 回滚任务在失败时是否停止后续回滚。 */
    private boolean stopRevertsOnFailure = false;
    /** 中止任务，对未执行的 item 在整体失败时执行。 */
    private Task<I, ?> abortTask = null;
    /** 中止任务在失败时是否停止后续中止。 */
    private boolean stopAbortsOnFailure = false;

    // retry settings
    /** 遇到这些异常类型时不重试，直接抛出。 */
    private List<Class<? extends Exception>> stopRetryExceptions =
        Lists.newArrayList(UnrecoverableException.class);
    /** 仅对这些异常类型重试，为 null 时表示除 stopRetryExceptions 外都重试。 */
    private List<Class<? extends Exception>> onlyRetryExceptions = null;
    /** 自定义重试判定谓词，优先于异常类型列表。 */
    private Predicate<Exception> shouldRetryPredicate = null;
    /** 最大重试次数（含首次执行），默认 1 表示不重试。 */
    private int maxAttempts = 1; // not all operations can be retried
    /** 退避最小睡眠时间（毫秒）。 */
    private long minSleepTimeMs = 1000; // 1 second
    /** 退避最大睡眠时间（毫秒）。 */
    private long maxSleepTimeMs = 600000; // 10 minutes
    /** 重试最大总时长（毫秒），超过则停止重试。 */
    private long maxDurationMs = 600000; // 10 minutes
    /** 指数退避的底数，每次重试睡眠时间乘以此因子。 */
    private double scaleFactor = 2.0; // exponential
    /** 尝试次数计数器，用于指标统计。 */
    private Counter attemptsCounter;

    /**
     * 构建器构造方法。
     *
     * @param items 待执行的任务项集合
     */
    public Builder(Iterable<I> items) {
      this.items = items;
    }

    /**
     * 设置并行执行使用的线程池。
     *
     * @param svc 线程池服务
     * @return 当前构建器
     */
    public Builder<I> executeWith(ExecutorService svc) {
      this.service = svc;
      return this;
    }

    /**
     * 设置单个 item 失败时的回调任务。
     *
     * @param task 失败回调
     * @return 当前构建器
     */
    public Builder<I> onFailure(FailureTask<I, ?> task) {
      this.onFailure = task;
      return this;
    }

    /** 设置在首个 item 失败后停止处理后续 item。 */
    public Builder<I> stopOnFailure() {
      this.stopOnFailure = true;
      return this;
    }

    /** 设置任务完成后抛出收集到的异常（默认行为）。 */
    public Builder<I> throwFailureWhenFinished() {
      this.throwFailureWhenFinished = true;
      return this;
    }

    /**
     * 设置任务完成后是否抛出异常。
     *
     * @param throwWhenFinished 是否抛出
     * @return 当前构建器
     */
    public Builder<I> throwFailureWhenFinished(boolean throwWhenFinished) {
      this.throwFailureWhenFinished = throwWhenFinished;
      return this;
    }

    /** 设置任务完成后不抛出异常，静默处理失败。 */
    public Builder<I> suppressFailureWhenFinished() {
      this.throwFailureWhenFinished = false;
      return this;
    }

    /**
     * 设置回滚任务，在整体失败时对已成功的 item 执行。
     *
     * @param task 回滚任务
     * @return 当前构建器
     */
    public Builder<I> revertWith(Task<I, ?> task) {
      this.revertTask = task;
      return this;
    }

    /** 设置回滚任务在自身失败时停止后续回滚。 */
    public Builder<I> stopRevertsOnFailure() {
      this.stopRevertsOnFailure = true;
      return this;
    }

    /**
     * 设置中止任务，在整体失败时对未执行的 item 执行。
     *
     * @param task 中止任务
     * @return 当前构建器
     */
    public Builder<I> abortWith(Task<I, ?> task) {
      this.abortTask = task;
      return this;
    }

    /** 设置中止任务在自身失败时停止后续中止。 */
    public Builder<I> stopAbortsOnFailure() {
      this.stopAbortsOnFailure = true;
      return this;
    }

    /**
     * 添加遇到时不重试的异常类型。
     *
     * @param exceptions 不重试的异常类型数组
     * @return 当前构建器
     */
    @SafeVarargs
    public final Builder<I> stopRetryOn(Class<? extends Exception>... exceptions) {
      stopRetryExceptions.addAll(Arrays.asList(exceptions));
      return this;
    }

    /**
     * 设置自定义重试判定谓词，优先于异常类型列表。
     *
     * @param shouldRetry 重试判定谓词，返回 true 表示应重试
     * @return 当前构建器
     */
    public Builder<I> shouldRetryTest(Predicate<Exception> shouldRetry) {
      this.shouldRetryPredicate = shouldRetry;
      return this;
    }

    /** 设置不重试，即最大尝试次数为 1。 */
    public Builder<I> noRetry() {
      this.maxAttempts = 1;
      return this;
    }

    /**
     * 设置重试次数（不含首次执行）。
     *
     * @param nTimes 重试次数
     * @return 当前构建器
     */
    public Builder<I> retry(int nTimes) {
      this.maxAttempts = nTimes + 1;
      return this;
    }

    /**
     * 设置仅对指定异常类型重试。
     *
     * @param exception 仅重试的异常类型
     * @return 当前构建器
     */
    public Builder<I> onlyRetryOn(Class<? extends Exception> exception) {
      this.onlyRetryExceptions = Collections.singletonList(exception);
      return this;
    }

    /**
     * 设置仅对指定异常类型数组重试。
     *
     * @param exceptions 仅重试的异常类型数组
     * @return 当前构建器
     */
    @SafeVarargs
    public final Builder<I> onlyRetryOn(Class<? extends Exception>... exceptions) {
      this.onlyRetryExceptions = Lists.newArrayList(exceptions);
      return this;
    }

    /**
     * 设置尝试次数计数器，用于指标统计。
     *
     * @param counter 计数器
     * @return 当前构建器
     */
    public Builder<I> countAttempts(Counter counter) {
      this.attemptsCounter = counter;
      return this;
    }

    /**
     * 配置指数退避参数。
     *
     * @param backoffMinSleepTimeMs 最小睡眠时间（毫秒）
     * @param backoffMaxSleepTimeMs 最大睡眠时间（毫秒）
     * @param backoffMaxRetryTimeMs 重试最大总时长（毫秒）
     * @param backoffScaleFactor 指数退避底数
     * @return 当前构建器
     */
    public Builder<I> exponentialBackoff(
        long backoffMinSleepTimeMs,
        long backoffMaxSleepTimeMs,
        long backoffMaxRetryTimeMs,
        double backoffScaleFactor) {
      this.minSleepTimeMs = backoffMinSleepTimeMs;
      this.maxSleepTimeMs = backoffMaxSleepTimeMs;
      this.maxDurationMs = backoffMaxRetryTimeMs;
      this.scaleFactor = backoffScaleFactor;
      return this;
    }

    public boolean run(Task<I, RuntimeException> task) {
      return run(task, RuntimeException.class);
    }

    public <E extends Exception> boolean run(Task<I, E> task, Class<E> exceptionClass) throws E {
      if (service != null) {
        return runParallel(task, exceptionClass);
      } else {
        return runSingleThreaded(task, exceptionClass);
      }
    }

    /**
     * 单线程串行执行所有任务项。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>逐个遍历 item，调用 {@link #runTaskWithRetry} 执行（含重试）
     *   <li>成功的 item 记录到 succeeded 列表；失败的 item 记录异常并可选调用 onFailure 回调
     *   <li>若 stopOnFailure 为 true，首个失败后停止处理后续 item
     *   <li>在 finally 块中，若有失败或异常：对 succeeded 的 item 执行 revertTask 回滚， 对未处理的 item 执行 abortTask 中止
     *   <li>根据 throwFailureWhenFinished 决定是否抛出收集到的异常
     * </ol>
     *
     * @param task 待执行的任务
     * @param exceptionClass 允许抛出的异常类型
     * @return 是否全部成功（无异常）
     * @throws E 任务执行中抛出的异常
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private <E extends Exception> boolean runSingleThreaded(
        Task<I, E> task, Class<E> exceptionClass) throws E {
      List<I> succeeded = Lists.newArrayList();
      List<Throwable> exceptions = Lists.newArrayList();

      Iterator<I> iterator = items.iterator();
      boolean threw = true;
      try {
        while (iterator.hasNext()) {
          I item = iterator.next();
          try {
            runTaskWithRetry(task, item);
            succeeded.add(item);
          } catch (Exception e) {
            exceptions.add(e);

            if (onFailure != null) {
              tryRunOnFailure(item, e);
            }

            if (stopOnFailure) {
              break;
            }
          }
        }

        threw = false;

      } finally {
        // threw handles exceptions that were *not* caught by the catch block,
        // and exceptions that were caught and possibly handled by onFailure
        // are kept in exceptions.
        if (threw || !exceptions.isEmpty()) {
          if (revertTask != null) {
            boolean failed = false;
            for (I item : succeeded) {
              try {
                revertTask.run(item);
              } catch (Exception e) {
                failed = true;
                LOG.error("Failed to revert task", e);
                // keep going
              }
              if (stopRevertsOnFailure && failed) {
                break;
              }
            }
          }

          if (abortTask != null) {
            boolean failed = false;
            while (iterator.hasNext()) {
              try {
                abortTask.run(iterator.next());
              } catch (Exception e) {
                failed = true;
                LOG.error("Failed to abort task", e);
                // keep going
              }
              if (stopAbortsOnFailure && failed) {
                break;
              }
            }
          }
        }
      }

      if (throwFailureWhenFinished && !exceptions.isEmpty()) {
        Tasks.throwOne(exceptions, exceptionClass);
      } else if (throwFailureWhenFinished && threw) {
        throw new RuntimeException("Task set failed with an uncaught throwable");
      }

      return !threw;
    }

    /**
     * 尝试执行失败回调，捕获回调自身的异常并作为 suppressed 异常附加到原失败异常上。
     *
     * @param item 执行失败的任务项
     * @param failure 导致失败的原始异常
     */
    private void tryRunOnFailure(I item, Exception failure) {
      try {
        onFailure.run(item, failure);
      } catch (Exception failException) {
        failure.addSuppressed(failException);
        LOG.error("Failed to clean up on failure", failException);
        // keep going
      }
    }

    /**
     * 使用线程池并行执行所有任务项。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>为每个 item 提交一个 Runnable 到线程池，内部调用 {@link #runTaskWithRetry} 执行
     *   <li>若 stopOnFailure 且已有任务失败，后续 item 改为执行 abortTask 中止而非运行任务
     *   <li>等待所有任务完成，收集未捕获异常
     *   <li>若有任务失败且配置了 revertTask，对已成功的 item 提交回滚任务并等待完成
     *   <li>根据 throwFailureWhenFinished 决定是否抛出收集到的异常
     * </ol>
     *
     * @param task 待执行的任务
     * @param exceptionClass 允许抛出的异常类型
     * @return 是否全部成功
     * @throws E 任务执行中抛出的异常
     */
    private <E extends Exception> boolean runParallel(
        final Task<I, E> task, Class<E> exceptionClass) throws E {
      final Queue<I> succeeded = new ConcurrentLinkedQueue<>();
      final Queue<Throwable> exceptions = new ConcurrentLinkedQueue<>();
      final AtomicBoolean taskFailed = new AtomicBoolean(false);
      final AtomicBoolean abortFailed = new AtomicBoolean(false);
      final AtomicBoolean revertFailed = new AtomicBoolean(false);

      List<Future<?>> futures = Lists.newArrayList();

      for (final I item : items) {
        // submit a task for each item that will either run or abort the task
        futures.add(
            service.submit(
                new Runnable() {
                  @Override
                  public void run() {
                    if (!(stopOnFailure && taskFailed.get())) {
                      // run the task with retries
                      boolean threw = true;
                      try {
                        runTaskWithRetry(task, item);

                        succeeded.add(item);

                        threw = false;

                      } catch (Exception e) {
                        taskFailed.set(true);
                        exceptions.add(e);

                        if (onFailure != null) {
                          tryRunOnFailure(item, e);
                        }
                      } finally {
                        if (threw) {
                          taskFailed.set(true);
                        }
                      }

                    } else if (abortTask != null) {
                      // abort the task instead of running it
                      if (stopAbortsOnFailure && abortFailed.get()) {
                        return;
                      }

                      boolean failed = true;
                      try {
                        abortTask.run(item);
                        failed = false;
                      } catch (Exception e) {
                        LOG.error("Failed to abort task", e);
                        // swallow the exception
                      } finally {
                        if (failed) {
                          abortFailed.set(true);
                        }
                      }
                    }
                  }
                }));
      }

      // let the above tasks complete (or abort)
      exceptions.addAll(waitFor(futures));
      futures.clear();

      if (taskFailed.get() && revertTask != null) {
        // at least one task failed, revert any that succeeded
        for (final I item : succeeded) {
          futures.add(
              service.submit(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (stopRevertsOnFailure && revertFailed.get()) {
                        return;
                      }

                      boolean failed = true;
                      try {
                        revertTask.run(item);
                        failed = false;
                      } catch (Exception e) {
                        LOG.error("Failed to revert task", e);
                        // swallow the exception
                      } finally {
                        if (failed) {
                          revertFailed.set(true);
                        }
                      }
                    }
                  }));
        }

        // let the revert tasks complete
        exceptions.addAll(waitFor(futures));
      }

      if (throwFailureWhenFinished && !exceptions.isEmpty()) {
        Tasks.throwOne(exceptions, exceptionClass);
      } else if (throwFailureWhenFinished && taskFailed.get()) {
        throw new RuntimeException("Task set failed with an uncaught throwable");
      }

      return !taskFailed.get();
    }

    /**
     * 对单个 item 执行任务，支持基于指数退避的重试。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>记录起始时间，循环执行任务
     *   <li>任务成功则跳出循环；失败时判断是否应重试
     *   <li>重试判定优先级：自定义谓词 {@code shouldRetryPredicate} > {@code onlyRetryExceptions} 白名单 > 排除
     *       {@code stopRetryExceptions} 黑名单
     *   <li>达到最大尝试次数或最大重试时长后不再重试，直接抛出异常
     *   <li>重试前按指数退避策略睡眠（含随机抖动），避免惊群效应
     * </ol>
     *
     * @param task 待执行的任务
     * @param item 当前任务项
     * @throws E 任务最终失败时抛出的异常
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private <E extends Exception> void runTaskWithRetry(Task<I, E> task, I item) throws E {
      long start = System.currentTimeMillis();
      int attempt = 0;
      while (true) {
        attempt += 1;
        if (null != attemptsCounter) {
          attemptsCounter.increment();
        }

        try {
          task.run(item);
          break;

        } catch (Exception e) {
          long durationMs = System.currentTimeMillis() - start;
          if (attempt >= maxAttempts || (durationMs > maxDurationMs && attempt > 1)) {
            if (durationMs > maxDurationMs) {
              LOG.info("Stopping retries after {} ms", durationMs);
            }
            throw e;
          }

          if (shouldRetryPredicate != null) {
            if (!shouldRetryPredicate.test(e)) {
              throw e;
            }

          } else if (onlyRetryExceptions != null) {
            // if onlyRetryExceptions are present, then this retries if one is found
            boolean matchedRetryException = false;
            for (Class<? extends Exception> exClass : onlyRetryExceptions) {
              if (exClass.isInstance(e)) {
                matchedRetryException = true;
                break;
              }
            }
            if (!matchedRetryException) {
              throw e;
            }

          } else {
            // otherwise, always retry unless one of the stop exceptions is found
            for (Class<? extends Exception> exClass : stopRetryExceptions) {
              if (exClass.isInstance(e)) {
                throw e;
              }
            }
          }

          int delayMs =
              (int) Math.min(minSleepTimeMs * Math.pow(scaleFactor, attempt - 1), maxSleepTimeMs);
          int jitter = ThreadLocalRandom.current().nextInt(Math.max(1, (int) (delayMs * 0.1)));

          LOG.warn("Retrying task after failure: {}", e.getMessage(), e);

          try {
            TimeUnit.MILLISECONDS.sleep(delayMs + jitter);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
          }
        }
      }
    }
  }

  /**
   * 等待所有 Future 完成，收集未捕获的异常。
   *
   * <p>逻辑：轮询检查所有 Future 是否完成，全部完成后逐一获取结果并收集 ExecutionException 中的 原始异常；若被中断则取消所有 Future 并抛出
   * RuntimeException。轮询间隔为 10 毫秒。
   *
   * @param futures 待等待的 Future 集合
   * @return 收集到的未捕获异常列表
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  private static Collection<Throwable> waitFor(Collection<Future<?>> futures) {
    while (true) {
      int numFinished = 0;
      for (Future<?> future : futures) {
        if (future.isDone()) {
          numFinished += 1;
        }
      }

      if (numFinished == futures.size()) {
        List<Throwable> uncaught = Lists.newArrayList();
        // all of the futures are done, get any uncaught exceptions
        for (Future<?> future : futures) {
          try {
            future.get();

          } catch (InterruptedException e) {
            LOG.warn("Interrupted while getting future results", e);
            for (Throwable t : uncaught) {
              e.addSuppressed(t);
            }
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);

          } catch (CancellationException e) {
            // ignore cancellations

          } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (Error.class.isInstance(cause)) {
              for (Throwable t : uncaught) {
                cause.addSuppressed(t);
              }
              throw (Error) cause;
            }

            if (cause != null) {
              uncaught.add(e);
            }

            LOG.warn("Task threw uncaught exception", cause);
          }
        }

        return uncaught;

      } else {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          LOG.warn("Interrupted while waiting for tasks to finish", e);

          for (Future<?> future : futures) {
            future.cancel(true);
          }
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
    }
  }

  /** 表示一个左闭右开区间 [0, size) 的整数可迭代对象。 */
  private static class Range implements Iterable<Integer> {
    /** 区间上界（不含）。 */
    private int size;

    /** @param size 区间上界 */
    Range(int size) {
      this.size = size;
    }

    @Override
    public Iterator<Integer> iterator() {
      return new Iterator<Integer>() {
        private int current = 0;

        @Override
        public boolean hasNext() {
          return current < size;
        }

        @Override
        public Integer next() {
          int ret = current;
          current += 1;
          return ret;
        }
      };
    }
  }

  /**
   * 创建一个对 [0, upTo) 范围内整数执行任务的构建器。
   *
   * @param upTo 区间上界（不含）
   * @return 任务构建器
   */
  public static Builder<Integer> range(int upTo) {
    return new Builder<>(new Range(upTo));
  }

  /**
   * 创建一个对迭代器中元素执行任务的构建器。
   *
   * @param items 任务项迭代器
   * @return 任务构建器
   */
  public static <I> Builder<I> foreach(Iterator<I> items) {
    return new Builder<>(() -> items);
  }

  /**
   * 创建一个对可迭代对象中元素执行任务的构建器。
   *
   * @param items 任务项可迭代对象
   * @return 任务构建器
   */
  public static <I> Builder<I> foreach(Iterable<I> items) {
    return new Builder<>(items);
  }

  /**
   * 创建一个对数组中元素执行任务的构建器。
   *
   * @param items 任务项数组
   * @return 任务构建器
   */
  @SafeVarargs
  public static <I> Builder<I> foreach(I... items) {
    return new Builder<>(Arrays.asList(items));
  }

  /**
   * 创建一个对流中元素执行任务的构建器。
   *
   * @param items 任务项流
   * @return 任务构建器
   */
  @SuppressWarnings("StreamToIterable")
  public static <I> Builder<I> foreach(Stream<I> items) {
    return new Builder<>(items::iterator);
  }

  /**
   * 从异常集合中选出一个主异常抛出，其余作为 suppressed 异常附加。
   *
   * <p>逻辑：取第一个异常作为主异常，遍历剩余异常，若类型与主异常不同则附加为 suppressed， 最后通过 {@link ExceptionUtil#castAndThrow}
   * 按指定类型抛出。
   *
   * @param exceptions 异常集合
   * @param allowedException 允许抛出的异常类型
   * @throws E 转换后的异常
   */
  private static <E extends Exception> void throwOne(
      Collection<Throwable> exceptions, Class<E> allowedException) throws E {
    Iterator<Throwable> iter = exceptions.iterator();
    Throwable exception = iter.next();
    Class<? extends Throwable> exceptionClass = exception.getClass();

    while (iter.hasNext()) {
      Throwable other = iter.next();
      if (!exceptionClass.isInstance(other)) {
        exception.addSuppressed(other);
      }
    }

    ExceptionUtil.castAndThrow(exception, allowedException);
  }
}
