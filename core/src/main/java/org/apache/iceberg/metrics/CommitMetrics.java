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
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.immutables.value.Value;

/**
 * 提交（commit）操作的度量指标集合。
 *
 * <p>所属模块：iceberg-core，位于度量（metrics）包内，负责表元数据提交过程中的指标收集与上报。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载一次表提交相关的全部运行时度量（提交总耗时、提交尝试次数）。
 *   <li>基于 {@link MetricsContext} 派生具体的 {@link Timer} 与 {@link Counter}，
 *       使底层指标实现可替换（如可对接到引擎自带的指标系统）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 Immutables 生成不可变实现，保证线程安全且可在并发提交场景下安全共享。
 *   <li>通过 {@code @Value.Derived} 懒派生指标句柄，避免在构造期立即创建计数器， 使指标实例与具体 {@link MetricsContext} 解耦。
 *   <li>{@link #noop()} 返回基于空 MetricsContext 的实例，用于不关心指标的场景， 避免调用方处理 null。
 * </ul>
 *
 * <p>上下游关系：依赖 {@link MetricsContext}（定义于 iceberg-api）；被提交相关流程 （如 {@code
 * SnapshotUpdater}、表提交实现）使用，并由 {@link CommitMetricsResult} 收集结果后上报。
 */
@Value.Immutable
public abstract class CommitMetrics {
  /** 提交总耗时指标的名称键，单位为纳秒。 */
  public static final String TOTAL_DURATION = "total-duration";
  /** 提交尝试次数指标的名称键，单位为 COUNT。 */
  public static final String ATTEMPTS = "attempts";

  /**
   * 返回一个空实现（noop）的提交度量实例，所有计数器/计时器均不产生实际记录。
   *
   * <p>设计意图：在不关心指标的场景下提供非 null 的占位对象，简化调用方判空逻辑。
   *
   * @return 基于 {@link MetricsContext#nullMetrics()} 的提交度量实例
   */
  public static CommitMetrics noop() {
    return CommitMetrics.of(MetricsContext.nullMetrics());
  }

  /**
   * 返回该实例所依赖的 {@link MetricsContext}，所有指标句柄均通过它派生。
   *
   * @return 度量上下文
   */
  public abstract MetricsContext metricsContext();

  /**
   * 派生提交总耗时的 {@link Timer}（单位：纳秒）。
   *
   * <p>由 Immutables 在首次访问时派生，多次调用返回同一实例。
   *
   * @return 提交总耗时计时器
   */
  @Value.Derived
  public Timer totalDuration() {
    return metricsContext().timer(TOTAL_DURATION, TimeUnit.NANOSECONDS);
  }

  /**
   * 派生提交尝试次数的 {@link Counter}（单位：COUNT）。
   *
   * <p>由 Immutables 在首次访问时派生，多次调用返回同一实例。
   *
   * @return 提交尝试次数计数器
   */
  @Value.Derived
  public Counter attempts() {
    return metricsContext().counter(ATTEMPTS, Unit.COUNT);
  }

  /**
   * 基于给定 {@link MetricsContext} 构造一个不可变的 {@link CommitMetrics} 实例。
   *
   * @param metricsContext 度量上下文，决定指标的具体实现后端
   * @return 新构建的提交度量实例
   */
  public static CommitMetrics of(MetricsContext metricsContext) {
    return ImmutableCommitMetrics.builder().metricsContext(metricsContext).build();
  }
}
