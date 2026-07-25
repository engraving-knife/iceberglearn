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

import static org.assertj.core.api.Assertions.withinPercentage;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestDefaultMetricsContext 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestDefaultMetricsContext 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestDefaultMetricsContext {

  /**
   * 测试场景：unsupported Counter。
   *
   * <p>验证该方法在 unsupported Counter 条件下的行为是否符合预期。
   */
  @Test
  public void unsupportedCounter() {
    MetricsContext metricsContext = new DefaultMetricsContext();
    Assertions.assertThatThrownBy(
            () -> metricsContext.counter("test", Double.class, MetricsContext.Unit.COUNT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Counter for type java.lang.Double is not supported");
  }

  /**
   * 测试场景：int Counter Null Check。
   *
   * <p>验证该方法在 int Counter Null Check 条件下的行为是否符合预期。
   */
  @Test
  public void intCounterNullCheck() {
    Assertions.assertThatThrownBy(
            () -> new DefaultMetricsContext().counter("name", Integer.class, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid count unit: null");
  }

  /**
   * 测试场景：int Counter。
   *
   * <p>验证该方法在 int Counter 条件下的行为是否符合预期。
   */
  @Test
  public void intCounter() {
    MetricsContext metricsContext = new DefaultMetricsContext();
    MetricsContext.Counter<Integer> counter =
        metricsContext.counter("intCounter", Integer.class, MetricsContext.Unit.BYTES);
    counter.increment(5);
    Assertions.assertThat(counter.value()).isEqualTo(5);
    Assertions.assertThat(counter.unit()).isEqualTo(MetricsContext.Unit.BYTES);
  }

  /**
   * 测试场景：int Counter Overflow。
   *
   * <p>验证该方法在 int Counter Overflow 条件下的行为是否符合预期。
   */
  @Test
  public void intCounterOverflow() {
    MetricsContext metricsContext = new DefaultMetricsContext();
    MetricsContext.Counter<Integer> counter =
        metricsContext.counter("test", Integer.class, MetricsContext.Unit.COUNT);
    counter.increment(Integer.MAX_VALUE);
    counter.increment();
    Assertions.assertThatThrownBy(counter::value)
        .isInstanceOf(ArithmeticException.class)
        .hasMessage("integer overflow");
  }

  /**
   * 测试场景：long Counter Null Check。
   *
   * <p>验证该方法在 long Counter Null Check 条件下的行为是否符合预期。
   */
  @Test
  public void longCounterNullCheck() {
    Assertions.assertThatThrownBy(
            () -> new DefaultMetricsContext().counter("name", Long.class, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid count unit: null");
  }

  /**
   * 测试场景：long Counter。
   *
   * <p>验证该方法在 long Counter 条件下的行为是否符合预期。
   */
  @Test
  public void longCounter() {
    MetricsContext metricsContext = new DefaultMetricsContext();
    MetricsContext.Counter<Long> counter =
        metricsContext.counter("longCounter", Long.class, MetricsContext.Unit.COUNT);
    counter.increment(5L);
    Assertions.assertThat(counter.value()).isEqualTo(5L);
    Assertions.assertThat(counter.unit()).isEqualTo(MetricsContext.Unit.COUNT);
  }

  /**
   * 测试场景：timer。
   *
   * <p>验证该方法在 timer 条件下的行为是否符合预期。
   */
  @Test
  public void timer() {
    MetricsContext metricsContext = new DefaultMetricsContext();
    Timer timer = metricsContext.timer("test", TimeUnit.MICROSECONDS);
    timer.record(10, TimeUnit.MINUTES);
    Assertions.assertThat(timer.totalDuration()).isEqualTo(Duration.ofMinutes(10L));
  }

  /**
   * 测试场景：histogram。
   *
   * <p>验证该方法在 histogram 条件下的行为是否符合预期。
   */
  @Test
  public void histogram() {
    MetricsContext metricsContext = new DefaultMetricsContext();
    int reservoirSize = 1000;
    Histogram histogram = metricsContext.histogram("test");
    for (int i = 1; i <= reservoirSize; ++i) {
      histogram.update(i);
    }

    Assertions.assertThat(histogram.count()).isEqualTo(reservoirSize);
    Histogram.Statistics statistics = histogram.statistics();
    Assertions.assertThat(statistics.size()).isEqualTo(reservoirSize);
    Assertions.assertThat(statistics.mean()).isEqualTo(500.5);
    Assertions.assertThat(statistics.stdDev()).isCloseTo(288.67499, withinPercentage(0.001));
    Assertions.assertThat(statistics.max()).isEqualTo(1000L);
    Assertions.assertThat(statistics.min()).isEqualTo(1L);
    Assertions.assertThat(statistics.percentile(0.50)).isEqualTo(500);
    Assertions.assertThat(statistics.percentile(0.75)).isEqualTo(750);
    Assertions.assertThat(statistics.percentile(0.90)).isEqualTo(900);
    Assertions.assertThat(statistics.percentile(0.95)).isEqualTo(950);
    Assertions.assertThat(statistics.percentile(0.99)).isEqualTo(990);
    Assertions.assertThat(statistics.percentile(0.999)).isEqualTo(999);
  }
}
