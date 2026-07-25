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
import java.util.function.Supplier;

/**
 * 计时器接口：用于在遥测场景中度量操作的执行时长。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>记录单次耗时（{@link #record(long, TimeUnit)}）或包装任务自动计时 （{@link #time(Runnable)}/{@link
 *       #time(Supplier)}/{@link #timeCallable(Callable)}）。
 *   <li>汇总已记录的次数（{@link #count()}）与累计耗时（{@link #totalDuration()}）。
 *   <li>提供 {@link #start()} 返回 {@link Timed} 样本，支持手动 start/stop 计时模式。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>抽象优先：接口只定义计时语义，具体实现（{@link DefaultTimer}、引擎自定义实现）通过 {@link MetricsContext#timer(String,
 *       TimeUnit)} 注入。
 *   <li>{@link Timed} 继承 {@link AutoCloseable}，支持 try-with-resources 自动 stop， 避免忘记停止计时导致数据丢失。
 *   <li>{@link #NOOP} 空对象：所有写方法为空，读取抛异常，配合 {@link #isNoop()} 让调用方 在不关心计时时传入 NOOP 而无需判空。
 * </ul>
 *
 * <p>上下游关系：由 {@link MetricsContext#timer(String, TimeUnit)} 创建；被 core 模块的扫描、 提交等关键路径用于度量耗时，最终汇入
 * {@link MetricsReport}。
 */
public interface Timer {

  /**
   * 返回已记录的计时次数（即 {@link #time(Duration)} / {@link #record(long, TimeUnit)} 被调用的次数）。
   *
   * @return 计时次数
   */
  long count();

  /**
   * 返回已记录的总耗时。
   *
   * @return 累计耗时
   */
  Duration totalDuration();

  /**
   * 启动计时并返回一个 {@link Timed} 实例，调用 {@link Timed#stop()} 完成计时。
   *
   * @return 已记录起始时间的 {@link Timed} 实例
   */
  Timed start();

  /**
   * 返回本计时器使用的 {@link TimeUnit}。
   *
   * @return 计时单位，默认 {@link TimeUnit#NANOSECONDS}
   */
  default TimeUnit unit() {
    return TimeUnit.NANOSECONDS;
  }

  /**
   * 以指定单位记录一次耗时。
   *
   * @param amount 耗时数值
   * @param unit 耗时数值的单位
   */
  void record(long amount, TimeUnit unit);

  /**
   * 记录一段 {@link Duration} 表示的耗时。
   *
   * <p>逻辑：将 duration 转为纳秒后委托给 {@link #record(long, TimeUnit)}。
   *
   * @param duration 待记录的时长
   */
  default void time(Duration duration) {
    record(duration.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * 执行并计时给定的 {@link Runnable}。
   *
   * @param runnable 待执行并计时的任务
   */
  void time(Runnable runnable);

  /**
   * 执行并计时给定的 {@link Callable}，返回其结果。
   *
   * @param callable 待执行并计时的可调用对象
   * @param <T> 返回值类型
   * @return callable 的执行结果
   * @throws Exception callable 执行失败时抛出
   */
  <T> T timeCallable(Callable<T> callable) throws Exception;

  /**
   * 执行并计时给定的 {@link Supplier}，返回其结果。
   *
   * @param supplier 待执行并计时的供应者
   * @param <T> 返回值类型
   * @return supplier 的执行结果
   */
  <T> T time(Supplier<T> supplier);

  /**
   * 判断本计时器是否为 NOOP（空操作）计时器。
   *
   * @return 若本对象等于 {@link #NOOP} 则返回 true
   */
  default boolean isNoop() {
    return NOOP.equals(this);
  }

  /**
   * 计时样本：携带计时器起始时刻的内部状态，通过 {@link #stop()} 完成计时。
   *
   * <p>设计意图：继承 {@link AutoCloseable}，使 {@link #close()} 委托给 {@link #stop()}， 从而支持
   * try-with-resources 自动停止计时，避免遗漏。
   */
  interface Timed extends AutoCloseable {
    /** 停止计时并记录自 {@link Timer#start()} 起的总时长。 */
    void stop();

    @Override
    default void close() {
      stop();
    }

    /** NOOP 计时样本，stop 为空操作。 */
    Timed NOOP = () -> {};
  }

  /**
   * NOOP 计时器单例：所有计时写方法为空，读取类方法抛出 {@link UnsupportedOperationException}，{@code timeCallable}/{@code
   * time(Supplier)} 仍会 执行原任务但不计时。
   *
   * <p>设计意图：作为空对象，供 {@link MetricsContext#nullMetrics()} 等场景使用。
   */
  Timer NOOP =
      new Timer() {
        @Override
        public Timed start() {
          return Timed.NOOP;
        }

        @Override
        public long count() {
          throw new UnsupportedOperationException("NOOP timer has no count");
        }

        @Override
        public Duration totalDuration() {
          throw new UnsupportedOperationException("NOOP timer has no duration");
        }

        @Override
        public TimeUnit unit() {
          throw new UnsupportedOperationException("NOOP timer has no unit");
        }

        @Override
        public void record(long amount, TimeUnit unit) {}

        @Override
        public void time(Runnable runnable) {}

        @Override
        public <T> T timeCallable(Callable<T> callable) throws Exception {
          return callable.call();
        }

        @Override
        public <T> T time(Supplier<T> supplier) {
          return supplier.get();
        }

        @Override
        public String toString() {
          return "NOOP timer";
        }
      };
}
