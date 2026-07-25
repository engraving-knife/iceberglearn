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

import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestDefaultTimer 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestDefaultTimer 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestDefaultTimer {

  /**
   * 测试场景：null Check。
   *
   * <p>验证该方法在 null Check 条件下的行为是否符合预期。
   */
  @Test
  public void nullCheck() {
    Assertions.assertThatThrownBy(() -> new DefaultTimer(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid time unit: null");
  }

  /**
   * 测试场景：name And Unit。
   *
   * <p>验证该方法在 name And Unit 条件下的行为是否符合预期。
   */
  @Test
  public void nameAndUnit() {
    DefaultTimer timer = new DefaultTimer(TimeUnit.MINUTES);
    Assertions.assertThat(timer.unit()).isEqualTo(TimeUnit.MINUTES);
    Assertions.assertThat(timer.isNoop()).isFalse();
  }

  /**
   * 测试场景：noop。
   *
   * <p>验证该方法在 noop 条件下的行为是否符合预期。
   */
  @Test
  public void noop() {
    Assertions.assertThat(Timer.NOOP.isNoop()).isTrue();
    Assertions.assertThatThrownBy(Timer.NOOP::count)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("NOOP timer has no count");
    Assertions.assertThatThrownBy(Timer.NOOP::totalDuration)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("NOOP timer has no duration");
    Assertions.assertThatThrownBy(Timer.NOOP::unit)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("NOOP timer has no unit");
  }

  /**
   * 测试场景：record Negative Amount。
   *
   * <p>验证该方法在 record Negative Amount 条件下的行为是否符合预期。
   */
  @Test
  public void recordNegativeAmount() {
    Timer timer = new DefaultTimer(TimeUnit.NANOSECONDS);
    Assertions.assertThat(timer.count()).isEqualTo(0);
    Assertions.assertThatThrownBy(() -> timer.record(-1, TimeUnit.NANOSECONDS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot record -1 NANOSECONDS: must be >= 0");
    Assertions.assertThat(timer.count()).isEqualTo(0);
    Assertions.assertThat(timer.totalDuration()).isEqualTo(Duration.ZERO);
  }

  /**
   * 测试场景：multiple Stops。
   *
   * <p>验证该方法在 multiple Stops 条件下的行为是否符合预期。
   */
  @Test
  public void multipleStops() {
    Timer timer = new DefaultTimer(TimeUnit.NANOSECONDS);
    Timer.Timed timed = timer.start();
    timed.stop();
    // we didn't start the timer again
    Assertions.assertThatThrownBy(timed::stop)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("stop() called multiple times");
  }

  /**
   * 测试场景：closeable Timer。
   *
   * <p>验证该方法在 closeable Timer 条件下的行为是否符合预期。
   */
  @Test
  public void closeableTimer() throws InterruptedException {
    Timer timer = new DefaultTimer(TimeUnit.NANOSECONDS);
    Assertions.assertThat(timer.count()).isEqualTo(0);
    Assertions.assertThat(timer.totalDuration()).isEqualTo(Duration.ZERO);
    try (Timer.Timed sample = timer.start()) {
      Thread.sleep(500L);
    }
    Assertions.assertThat(timer.count()).isEqualTo(1);
    Assertions.assertThat(timer.totalDuration()).isGreaterThan(Duration.ZERO);
  }

  /**
   * 测试场景：measure Runnable。
   *
   * <p>验证该方法在 measure Runnable 条件下的行为是否符合预期。
   */
  @Test
  public void measureRunnable() {
    Timer timer = new DefaultTimer(TimeUnit.NANOSECONDS);
    Runnable runnable =
        () -> {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };
    Assertions.assertThat(timer.count()).isEqualTo(0);
    Assertions.assertThat(timer.totalDuration()).isEqualTo(Duration.ZERO);

    timer.time(runnable);
    Assertions.assertThat(timer.count()).isEqualTo(1);
    Duration duration = timer.totalDuration();
    Assertions.assertThat(duration).isGreaterThan(Duration.ZERO);

    timer.time(runnable);
    Assertions.assertThat(timer.count()).isEqualTo(2);
    Duration secondDuration = timer.totalDuration();
    Assertions.assertThat(secondDuration).isGreaterThan(duration);
  }

  /**
   * 测试场景：measure Callable。
   *
   * <p>验证该方法在 measure Callable 条件下的行为是否符合预期。
   */
  @Test
  public void measureCallable() throws Exception {
    Timer timer = new DefaultTimer(TimeUnit.NANOSECONDS);
    Callable<Boolean> callable =
        () -> {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          return true;
        };
    Assertions.assertThat(timer.count()).isEqualTo(0);
    Assertions.assertThat(timer.totalDuration()).isEqualTo(Duration.ZERO);

    Assertions.assertThat(timer.timeCallable(callable).booleanValue()).isTrue();
    Assertions.assertThat(timer.count()).isEqualTo(1);
    Duration duration = timer.totalDuration();
    Assertions.assertThat(duration).isGreaterThan(Duration.ZERO);

    Assertions.assertThat(timer.timeCallable(callable).booleanValue()).isTrue();
    Assertions.assertThat(timer.count()).isEqualTo(2);
    Duration secondDuration = timer.totalDuration();
    Assertions.assertThat(secondDuration).isGreaterThan(duration);
  }

  /**
   * 测试场景：measure Supplier。
   *
   * <p>验证该方法在 measure Supplier 条件下的行为是否符合预期。
   */
  @Test
  public void measureSupplier() {
    Timer timer = new DefaultTimer(TimeUnit.NANOSECONDS);
    Supplier<Boolean> supplier =
        () -> {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          return true;
        };
    Assertions.assertThat(timer.count()).isEqualTo(0);
    Assertions.assertThat(timer.totalDuration()).isEqualTo(Duration.ZERO);

    Assertions.assertThat(timer.time(supplier).booleanValue()).isTrue();
    Assertions.assertThat(timer.count()).isEqualTo(1);
    Duration duration = timer.totalDuration();
    Assertions.assertThat(duration).isGreaterThan(Duration.ZERO);

    Assertions.assertThat(timer.time(supplier).booleanValue()).isTrue();
    Assertions.assertThat(timer.count()).isEqualTo(2);
    Duration secondDuration = timer.totalDuration();
    Assertions.assertThat(secondDuration).isGreaterThan(duration);
  }

  /**
   * 测试场景：measure Nested Runnables。
   *
   * <p>验证该方法在 measure Nested Runnables 条件下的行为是否符合预期。
   */
  @Test
  public void measureNestedRunnables() {
    Timer timer = new DefaultTimer(TimeUnit.NANOSECONDS);
    Timer innerTimer = new DefaultTimer(TimeUnit.NANOSECONDS);
    Runnable inner =
        () -> {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };

    Runnable outer =
        () -> {
          try {
            Thread.sleep(100);
            innerTimer.time(inner);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };

    Assertions.assertThat(timer.count()).isEqualTo(0);
    Assertions.assertThat(timer.totalDuration()).isEqualTo(Duration.ZERO);
    Assertions.assertThat(innerTimer.count()).isEqualTo(0);
    Assertions.assertThat(innerTimer.totalDuration()).isEqualTo(Duration.ZERO);

    timer.time(outer);
    Assertions.assertThat(timer.count()).isEqualTo(1);
    Duration outerDuration = timer.totalDuration();
    Assertions.assertThat(outerDuration).isGreaterThan(Duration.ZERO);
    Assertions.assertThat(innerTimer.count()).isEqualTo(1);
    Duration innerDuration = innerTimer.totalDuration();
    Assertions.assertThat(innerDuration).isGreaterThan(Duration.ZERO);
    Assertions.assertThat(outerDuration).isGreaterThan(innerDuration);
  }

  /**
   * 测试场景：multi Threaded Starts。
   *
   * <p>验证该方法在 multi Threaded Starts 条件下的行为是否符合预期。
   */
  @Test
  public void multiThreadedStarts() throws InterruptedException {
    Timer timer = new DefaultTimer(TimeUnit.NANOSECONDS);

    int threads = 10;
    CyclicBarrier barrier = new CyclicBarrier(threads);
    ExecutorService executor = newFixedThreadPool(threads);

    List<Future<Duration>> futures =
        IntStream.range(0, threads)
            .mapToObj(
                threadNumber ->
                    executor.submit(
                        () -> {
                          try {
                            barrier.await(30, SECONDS);
                            timer.record(5, TimeUnit.NANOSECONDS);
                            return timer.totalDuration();
                          } catch (Exception e) {
                            throw new RuntimeException(e);
                          }
                        }))
            .collect(Collectors.toList());
    futures.stream()
        .map(
            f -> {
              try {
                return f.get(30, SECONDS);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .forEach(d -> System.out.println("d = " + d));
    executor.shutdownNow();
    executor.awaitTermination(5, SECONDS);

    Assertions.assertThat(timer.totalDuration()).isEqualTo(Duration.ofNanos(5 * threads));
    Assertions.assertThat(timer.count()).isEqualTo(threads);
  }
}
