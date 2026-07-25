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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * {@link Counter} 的默认实现：基于 {@link LongAdder} 进行高并发自增计数。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供线程安全的计数器实现，作为 {@link DefaultMetricsContext} 的默认计数后端。
 *   <li>提供 NOOP 空对象 {@link #NOOP}，用于"无需采集指标"的场景，避免调用方处理 null。
 *   <li>通过 {@link #asIntCounter()}/{@link #asLongCounter()} 适配已废弃的泛型 {@link MetricsContext.Counter}
 *       接口，保持向后兼容。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>选用 {@link LongAdder} 而非 {@link AtomicLong}：在写多读少的高竞争场景下， LongAdder 通过分散热点 cell 显著提升自增吞吐，仅在
 *       {@code value()} 时求和，符合 指标采集"频繁写、偶尔读"的访问模式。
 *   <li>NOOP 采用匿名子类覆盖所有方法，对 {@code value()} 直接抛异常，符合"空对象不应被读取" 的语义，同时让 {@link Counter#isNoop()}
 *       能基于引用相等性识别。
 * </ul>
 *
 * <p>上下游关系：由 {@link DefaultMetricsContext#counter(String, Unit)} 创建；适配器被 废弃的 {@link
 * MetricsContext#counter(String, Class, Unit)} 调用以兼容旧 API。
 */
public class DefaultCounter implements Counter {
  /**
   * 空操作计数器单例：所有写方法为空实现，读取 {@code value()} 抛出 {@link UnsupportedOperationException}。
   *
   * <p>设计意图：作为空对象模式实现，供 {@link MetricsContext#nullMetrics()} 等场景使用， 使调用链无需判空。
   */
  public static final Counter NOOP =
      new DefaultCounter(Unit.UNDEFINED) {
        @Override
        public void increment() {}

        @Override
        public void increment(long amount) {}

        @Override
        public long value() {
          throw new UnsupportedOperationException("NOOP counter has no value");
        }

        @Override
        public String toString() {
          return "NOOP counter";
        }
      };

  private final LongAdder counter;
  private final MetricsContext.Unit unit;
  private AsIntCounter asIntCounter = null;
  private AsLongCounter asLongCounter = null;

  /**
   * 构造指定单位的默认计数器。
   *
   * @param unit 度量单位，不可为 null
   */
  DefaultCounter(MetricsContext.Unit unit) {
    Preconditions.checkArgument(null != unit, "Invalid count unit: null");
    this.unit = unit;
    this.counter = new LongAdder();
  }

  @Override
  public void increment() {
    increment(1L);
  }

  @Override
  public void increment(long amount) {
    counter.add(amount);
  }

  @Override
  public long value() {
    return counter.longValue();
  }

  @Override
  public String toString() {
    return String.format("{%s=%s}", unit().displayName(), value());
  }

  @Override
  public MetricsContext.Unit unit() {
    return unit;
  }

  /**
   * 将本计数器包装为已废弃的泛型 {@link MetricsContext.Counter} 视图，懒初始化。
   *
   * @return Integer 类型的计数器适配器
   */
  MetricsContext.Counter<Integer> asIntCounter() {
    if (null == asIntCounter) {
      this.asIntCounter = new AsIntCounter();
    }

    return asIntCounter;
  }

  /**
   * 将本计数器包装为已废弃的泛型 {@link MetricsContext.Counter} 视图，懒初始化。
   *
   * @return Long 类型的计数器适配器
   */
  MetricsContext.Counter<Long> asLongCounter() {
    if (null == asLongCounter) {
      this.asLongCounter = new AsLongCounter();
    }

    return asLongCounter;
  }

  /**
   * Integer 类型计数器适配器：将底层 long 计数转换为 Integer 视图，读取时做溢出检查。
   *
   * <p>设计意图：兼容已废弃的泛型 {@link MetricsContext.Counter} 接口；内部仍使用外层 DefaultCounter 的 LongAdder，避免双重计数。
   */
  private class AsIntCounter implements MetricsContext.Counter<Integer> {

    @Override
    public void increment() {
      increment(1);
    }

    @Override
    public void increment(Integer amount) {
      DefaultCounter.this.increment(amount);
    }

    @Override
    public Optional<Integer> count() {
      return Optional.of(value());
    }

    /**
     * 返回当前计数值（int 视图）。
     *
     * <p>逻辑：读取底层 LongAdder 的 long 值，若超过 {@link Integer#MAX_VALUE} 则抛出 {@link
     * ArithmeticException}，否则窄化为 int 返回。
     *
     * @return 当前计数值
     * @throws ArithmeticException 若累计值超过 int 最大值
     */
    @Override
    public Integer value() {
      long value = counter.longValue();
      if (value > Integer.MAX_VALUE) {
        throw new ArithmeticException("integer overflow");
      }
      return (int) value;
    }

    @Override
    public MetricsContext.Unit unit() {
      return unit;
    }
  }

  /**
   * Long 类型计数器适配器：将底层 long 计数直接以 Long 视图暴露，无溢出风险。
   *
   * <p>设计意图：兼容已废弃的泛型 {@link MetricsContext.Counter} 接口。
   */
  private class AsLongCounter implements MetricsContext.Counter<Long> {

    @Override
    public void increment() {
      DefaultCounter.this.increment();
    }

    @Override
    public void increment(Long amount) {
      DefaultCounter.this.increment(amount);
    }

    @Override
    public Optional<Long> count() {
      return Optional.of(value());
    }

    @Override
    public Long value() {
      return counter.longValue();
    }

    @Override
    public MetricsContext.Unit unit() {
      return unit;
    }
  }
}
