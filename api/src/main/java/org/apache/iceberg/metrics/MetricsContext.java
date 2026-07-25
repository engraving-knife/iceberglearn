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

import java.io.Serializable;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 度量上下文接口：用于创建追踪操作执行情况的各类遥测（telemetry）实例（计数器、计时器、直方图）。
 *
 * <p>所属模块：iceberg-api（核心抽象层，定义指标采集的统一入口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为指标工厂，按名称和单位创建 {@link Counter}/{@link Timer}/{@link Histogram}。
 *   <li>通过 {@link #initialize(Map)} 接收引擎传入的配置，便于实现按需初始化。
 *   <li>定义 {@link Unit} 度量单位枚举，统一指标的单位语义。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现需考虑线程安全与序列化：因为 {@link MetricsContext} 会随表对象序列化并在多线程 环境下使用，本接口继承 {@link Serializable}。
 *   <li>默认方法均抛出 {@link UnsupportedOperationException}：迫使具体实现显式声明支持哪些 指标类型，避免误用未实现的指标而静默丢失数据。
 *   <li>保留废弃的泛型 {@code Counter<T>} 与 {@code counter(String, Class, Unit)} 以兼容旧版， 新代码应使用非泛型的 {@link
 *       org.apache.iceberg.metrics.Counter}。
 * </ul>
 *
 * <p>上下游关系：被 core 模块及各引擎集成模块持有可能用；引擎可注入自定义实现对接其原生 指标系统，未注入时使用 {@link DefaultMetricsContext}。
 */
public interface MetricsContext extends Serializable {
  /**
   * 度量单位枚举：标识计数器/指标值的物理含义。
   *
   * <p>设计意图：通过 displayName 与字符串互转，便于指标序列化与跨系统传输时的单位识别。
   */
  enum Unit {
    UNDEFINED("undefined"),
    BYTES("bytes"),
    COUNT("count");

    private final String displayName;

    Unit(String displayName) {
      this.displayName = displayName;
    }

    /**
     * 返回单位的展示名称。
     *
     * @return 展示名称字符串
     */
    public String displayName() {
      return displayName;
    }

    /**
     * 根据展示名称解析出 {@link Unit}。
     *
     * <p>逻辑：将 displayName 转大写后调用 {@link Enum#valueOf}；若不存在抛出 {@link IllegalArgumentException}
     * 并附带原名称便于排查。
     *
     * @param displayName 展示名称，不可为 null
     * @return 对应的 {@link Unit}
     * @throws IllegalArgumentException 若 displayName 为 null 或无法识别
     */
    public static Unit fromDisplayName(String displayName) {
      Preconditions.checkArgument(null != displayName, "Invalid unit: null");
      try {
        return Unit.valueOf(displayName.toUpperCase(Locale.ENGLISH));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(String.format("Invalid unit: %s", displayName), e);
      }
    }
  }

  /**
   * 用给定属性初始化度量上下文。
   *
   * <p>默认空实现，具体实现可覆盖以读取引擎配置。
   *
   * @param properties 初始化属性
   */
  default void initialize(Map<String, String> properties) {}

  /**
   * 已废弃的泛型计数器接口：按数值类型 T 计数。
   *
   * @param <T> 数值类型
   * @deprecated 将在 2.0.0 移除，使用 {@link org.apache.iceberg.metrics.Counter} 替代。
   */
  @Deprecated
  interface Counter<T extends Number> {
    /** 自增 1。 */
    void increment();

    /**
     * 按指定量自增。
     *
     * @param amount 自增量
     */
    void increment(T amount);

    /**
     * 报告当前计数（可选）。
     *
     * <p>若计数器由外部系统上报，则可能不提供本地计数值。
     *
     * @return 当前计数的 Optional；默认返回 {@link Optional#empty()}
     * @deprecated 使用 {@link Counter#value()}
     */
    @Deprecated
    default Optional<T> count() {
      return Optional.empty();
    }

    /**
     * 报告当前计数值。
     *
     * @return 当前计数值
     * @throws UnsupportedOperationException 默认实现不支持
     */
    default T value() {
      throw new UnsupportedOperationException("Count is not supported.");
    }

    /**
     * 返回计数器单位。
     *
     * @return 单位，默认 {@link Unit#UNDEFINED}
     */
    default Unit unit() {
      return Unit.UNDEFINED;
    }
  }

  /**
   * 按类型获取具名计数器（已废弃）。
   *
   * <p>实现可对支持的数值类型施加限制。
   *
   * @param <T> 数值类型
   * @param name 指标名称
   * @param type 计数值的数值类型
   * @param unit 度量单位
   * @return 计数器实现
   * @throws UnsupportedOperationException 默认实现不支持
   * @deprecated 将在 2.0.0 移除，使用 {@link MetricsContext#counter(String, Unit)} 替代。
   */
  @Deprecated
  default <T extends Number> Counter<T> counter(String name, Class<T> type, Unit unit) {
    throw new UnsupportedOperationException("Counter is not supported.");
  }

  /**
   * 获取具名计数器。
   *
   * @param name 指标名称
   * @param unit 度量单位
   * @return {@link org.apache.iceberg.metrics.Counter} 实现
   * @throws UnsupportedOperationException 默认实现不支持
   */
  default org.apache.iceberg.metrics.Counter counter(String name, Unit unit) {
    throw new UnsupportedOperationException("Counter is not supported.");
  }

  /**
   * 获取以 {@link Unit#COUNT} 为单位的具名计数器。
   *
   * @param name 指标名称
   * @return {@link org.apache.iceberg.metrics.Counter} 实现
   */
  default org.apache.iceberg.metrics.Counter counter(String name) {
    return counter(name, Unit.COUNT);
  }

  /**
   * 获取具名计时器。
   *
   * @param name 指标名称
   * @param unit 计时单位
   * @return 计时器实现
   * @throws UnsupportedOperationException 默认实现不支持
   */
  default Timer timer(String name, TimeUnit unit) {
    throw new UnsupportedOperationException("Timer is not supported.");
  }

  /**
   * 获取具名直方图。
   *
   * @param name 指标名称
   * @return 直方图实现
   * @throws UnsupportedOperationException 默认实现不支持
   */
  default Histogram histogram(String name) {
    throw new UnsupportedOperationException("Histogram is not supported.");
  }

  /**
   * 返回一个不记录任何指标的 {@link MetricsContext}（空对象）。
   *
   * <p>逻辑：返回匿名实现，其 counter 返回 {@link DefaultCounter#NOOP}，timer 返回 {@link Timer#NOOP}；废弃的泛型 counter
   * 方法则按类型返回 NOOP 适配器。
   *
   * <p>设计意图：当调用方不关心指标时传入此实例，避免各处判空。
   *
   * @return 不记录指标的 MetricsContext
   */
  static MetricsContext nullMetrics() {
    return new MetricsContext() {

      @Override
      public Timer timer(String name, TimeUnit unit) {
        return Timer.NOOP;
      }

      @Override
      @SuppressWarnings("unchecked")
      public <T extends Number> Counter<T> counter(String name, Class<T> type, Unit unit) {
        if (Integer.class.equals(type)) {
          return (Counter<T>)
              ((DefaultCounter) org.apache.iceberg.metrics.DefaultCounter.NOOP).asIntCounter();
        }

        if (Long.class.equals(type)) {
          return (Counter<T>)
              ((DefaultCounter) org.apache.iceberg.metrics.DefaultCounter.NOOP).asLongCounter();
        }

        throw new IllegalArgumentException(
            String.format("Counter for type %s is not supported", type.getName()));
      }

      @Override
      public org.apache.iceberg.metrics.Counter counter(String name, Unit unit) {
        return org.apache.iceberg.metrics.DefaultCounter.NOOP;
      }
    };
  }
}
