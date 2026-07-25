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
package org.apache.iceberg.metrics;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Stopwatch;

/**
 * {@link Timer} 的默认实现：内部使用 {@link Stopwatch} 计时，并用 {@link LongAdder} 累加次数 与总耗时。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供对操作耗时的记录能力，支持手动 {@code record}、{@code start/stop} 以及 包装 {@code Supplier}/{@code
 *       Callable}/{@code Runnable} 自动计时。
 *   <li>汇总单次计时的次数与累计耗时，供上层指标上报。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 {@link LongAdder} 而非 {@link java.util.concurrent.atomic.AtomicLong}：与 {@link
 *       DefaultCounter} 同理，在多线程高并发计时场景下减少竞争、提升写入吞吐。
 *   <li>总耗时统一以纳秒存储（{@code totalTime} 单位为 ns），通过 {@link #totalDuration()} 转换为 {@link Duration}
 *       暴露，避免单位歧义。
 *   <li>{@link DefaultTimed} 使用 {@link AtomicReference} 持有 {@link Stopwatch}，确保 {@code stop()}
 *       只能被调用一次（重复 stop 会得到 null 并抛出状态异常），防止误用导致 重复计时。
 * </ul>
 *
 * <p>上下游关系：由 {@link DefaultMetricsContext#timer(String, TimeUnit)} 创建；被 core 模块
 * 的扫描、提交等流程用于度量关键路径耗时。
 */
public class DefaultTimer implements Timer {
  private final TimeUnit timeUnit;
  private final LongAdder count = new LongAdder();
  private final LongAdder totalTime = new LongAdder();

  /**
   * 构造指定计时单位的默认计时器。
   *
   * @param timeUnit 计时单位，不可为 null
   */
  public DefaultTimer(TimeUnit timeUnit) {
    Preconditions.checkArgument(null != timeUnit, "Invalid time unit: null");
    this.timeUnit = timeUnit;
  }

  @Override
  public long count() {
    return count.longValue();
  }

  @Override
  public Duration totalDuration() {
    return Duration.ofNanos(totalTime.longValue());
  }

  @Override
  public Timed start() {
    return new DefaultTimed(this, timeUnit);
  }

  /**
   * 记录一次耗时。
   *
   * <p>逻辑：校验 amount 非负；将 amount 按目标单位转换为纳秒累加到 {@code totalTime}， 并将计数 +1。多次调用累加，{@link #count()} 与
   * {@link #totalDuration()} 反映汇总值。
   *
   * @param amount 耗时数值，必须 &gt;= 0
   * @param unit 耗时数值的单位
   */
  @Override
  public void record(long amount, TimeUnit unit) {
    Preconditions.checkArgument(amount >= 0, "Cannot record %s %s: must be >= 0", amount, unit);
    this.totalTime.add(TimeUnit.NANOSECONDS.convert(amount, unit));
    this.count.increment();
  }

  /**
   * 计时执行 {@link Supplier} 并返回其结果。
   *
   * <p>逻辑：先 {@link #start()} 开启计时，在 finally 中 {@link Timed#stop()} 确保即使抛异常 也记录耗时，最终返回 supplier
   * 的执行结果。
   *
   * @param supplier 待执行并计时的供应者
   * @param <T> 返回值类型
   * @return supplier 的返回值
   */
  @Override
  public <T> T time(Supplier<T> supplier) {
    Timed timed = start();
    try {
      return supplier.get();
    } finally {
      timed.stop();
    }
  }

  /**
   * 计时执行 {@link Callable} 并返回其结果。
   *
   * <p>逻辑：与 {@link #time(Supplier)} 类似，区别在于 Callable 可抛受检异常，本方法原样抛出。
   *
   * @param callable 待执行并计时的可调用对象
   * @param <T> 返回值类型
   * @return callable 的返回值
   * @throws Exception callable 执行过程中抛出的异常
   */
  @Override
  public <T> T timeCallable(Callable<T> callable) throws Exception {
    Timed timed = start();
    try {
      return callable.call();
    } finally {
      timed.stop();
    }
  }

  /**
   * 计时执行 {@link Runnable}。
   *
   * <p>逻辑：与 {@link #time(Supplier)} 类似，但无返回值，finally 中保证 stop。
   *
   * @param runnable 待执行并计时的任务
   */
  @Override
  public void time(Runnable runnable) {
    Timed timed = start();
    try {
      runnable.run();
    } finally {
      timed.stop();
    }
  }

  @Override
  public TimeUnit unit() {
    return timeUnit;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(DefaultTimer.class)
        .add("duration", totalDuration())
        .add("count", count)
        .add("timeUnit", timeUnit)
        .toString();
  }

  /**
   * 计时样本：持有一个 {@link Stopwatch} 引用，{@code stop()} 时将经过的时间回写到外层 {@link DefaultTimer}。
   *
   * <p>设计意图：用 {@link AtomicReference} 持有 Stopwatch 并在 {@code stop()} 时通过 {@code getAndSet(null)}
   * 取出，保证 stop 只生效一次；重复 stop 会因取到 null 而抛出 {@link IllegalStateException}，避免重复计时。
   */
  private static class DefaultTimed implements Timed {
    private final Timer timer;
    private final TimeUnit defaultTimeUnit;
    private final AtomicReference<Stopwatch> stopwatchRef = new AtomicReference<>();

    private DefaultTimed(Timer timer, TimeUnit defaultTimeUnit) {
      this.timer = timer;
      this.defaultTimeUnit = defaultTimeUnit;
      stopwatchRef.compareAndSet(null, Stopwatch.createStarted());
    }

    /**
     * 停止计时并将耗时回写到外层 Timer。
     *
     * <p>逻辑：通过 {@code getAndSet(null)} 原子取出 Stopwatch；若为 null 说明已被停止过， 抛出 {@link
     * IllegalStateException}；否则停止 Stopwatch，将经过的时间按 {@code defaultTimeUnit} 回调 {@link
     * Timer#record(long, TimeUnit)}。
     *
     * @throws IllegalStateException 若 stop 被多次调用
     */
    @Override
    public void stop() {
      Stopwatch stopwatch = stopwatchRef.getAndSet(null);
      Preconditions.checkState(null != stopwatch, "stop() called multiple times");
      timer.record(stopwatch.stop().elapsed(defaultTimeUnit), defaultTimeUnit);
    }
  }
}
