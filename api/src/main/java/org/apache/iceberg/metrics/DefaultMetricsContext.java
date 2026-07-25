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

import java.util.concurrent.TimeUnit;

/**
 * {@link MetricsContext} 的默认实现：使用 JDK 原生计数器/计时器/直方图实现，不依赖任何外部 度量后端。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link Counter}/{@link Timer}/{@link Histogram} 的工厂方法，按名称和单位创建指标实例。
 *   <li>作为引擎未自定义 {@link MetricsContext} 时的兜底实现，保证指标采集功能开箱即用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>纯 JDK 实现：避免在 API 层引入对 Dropwizard Metrics 等第三方库的强依赖，保持 API 包 轻量与可移植。
 *   <li>直方图默认蓄水池大小 10000：在内存占用与采样精度间取平衡，足够支撑常见分位数计算。
 *   <li>保留废弃的泛型 {@code counter(String, Class, Unit)} 方法以兼容旧调用方，内部委托给 {@link DefaultCounter} 的适配器视图。
 * </ul>
 *
 * <p>上下游关系：可被 core 模块直接实例化使用；引擎集成模块通常会提供自定义 {@link MetricsContext} 以对接其原生指标系统，覆盖本默认实现。
 */
public class DefaultMetricsContext implements MetricsContext {
  /** 直方图默认蓄水池采样数量。 */
  private static final int DEFAULT_HISTOGRAM_RESERVOIR_SIZE = 10_000;

  /**
   * 按类型创建已废弃的泛型计数器。
   *
   * <p>逻辑：根据传入的 {@code type} 选择对应的适配器视图——{@link Integer} 使用 {@link
   * DefaultCounter#asIntCounter()}，{@link Long} 使用 {@link DefaultCounter#asLongCounter()}， 其他类型抛出
   * {@link IllegalArgumentException}。
   *
   * @param <T> 数值类型，必须为 Integer 或 Long
   * @param name 指标名称（本实现未使用，仅用于满足接口契约）
   * @param type 计数值的数值类型
   * @param unit 度量单位
   * @return 泛型计数器实现
   * @throws IllegalArgumentException 若 type 非 Integer/Long
   * @deprecated 将在 2.0.0 移除，使用 {@link org.apache.iceberg.metrics.Counter} 替代。
   */
  @Override
  @Deprecated
  @SuppressWarnings("unchecked")
  public <T extends Number> Counter<T> counter(String name, Class<T> type, Unit unit) {
    if (Integer.class.equals(type)) {
      return (Counter<T>) new DefaultCounter(unit).asIntCounter();
    }

    if (Long.class.equals(type)) {
      return (Counter<T>) new DefaultCounter(unit).asLongCounter();
    }
    throw new IllegalArgumentException(
        String.format("Counter for type %s is not supported", type.getName()));
  }

  /**
   * 创建一个 {@link DefaultTimer}。
   *
   * @param name 指标名称（本实现未使用）
   * @param unit 计时单位
   * @return 默认计时器实例
   */
  @Override
  public Timer timer(String name, TimeUnit unit) {
    return new DefaultTimer(unit);
  }

  /**
   * 创建一个 {@link DefaultCounter}。
   *
   * @param name 指标名称（本实现未使用）
   * @param unit 度量单位
   * @return 默认计数器实例
   */
  @Override
  public org.apache.iceberg.metrics.Counter counter(String name, Unit unit) {
    return new DefaultCounter(unit);
  }

  /**
   * 创建一个固定蓄水池大小的 {@link FixedReservoirHistogram}。
   *
   * @param name 指标名称（本实现未使用）
   * @return 默认直方图实例，蓄水池大小为 {@link #DEFAULT_HISTOGRAM_RESERVOIR_SIZE}
   */
  @Override
  public Histogram histogram(String name) {
    return new FixedReservoirHistogram(DEFAULT_HISTOGRAM_RESERVOIR_SIZE);
  }
}
