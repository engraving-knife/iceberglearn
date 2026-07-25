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
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.immutables.value.Value;

/**
 * {@link Timer} 的可序列化结果视图，承载计时器最终的时间单位、总时长与计数。
 *
 * <p>所属模块：iceberg-core，度量包中基础的只读计时结果结构，用于上报与序列化。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载一个计时器的时间单位（{@link TimeUnit}）、总累计时长（{@link Duration}）与触发次数。
 *   <li>提供从运行时 {@link Timer} 提取结果、以及直接构造结果的工厂方法。
 * </ul>
 *
 * <p>设计意图：与运行时 {@link Timer} 解耦，仅保留可序列化的快照值， 便于跨进程传输与 JSON 持久化；Immutables 保证不可变。
 *
 * <p>上下游关系：被 {@link CommitMetricsResult}、{@link ScanMetricsResult} 等结果对象聚合； 由 {@link
 * TimerResultParser} 进行 JSON 转换。
 */
@Value.Immutable
public interface TimerResult {

  /** 返回 计时器的时间单位。 */
  TimeUnit timeUnit();

  /** 返回 总累计时长。 */
  Duration totalDuration();

  /** 返回 计时器触发次数。 */
  long count();

  /**
   * 从运行时 {@link Timer} 提取可序列化结果。
   *
   * <p>设计要点：若计时器为 noop（空实现）则返回 null，表示该指标未实际采集， 避免上报无意义零值。
   *
   * @param timer 运行时计时器，不能为 null
   * @return 计时结果；若 timer 为 noop 则返回 null
   */
  static TimerResult fromTimer(Timer timer) {
    Preconditions.checkArgument(null != timer, "Invalid timer: null");
    if (timer.isNoop()) {
      return null;
    }

    return ImmutableTimerResult.builder()
        .timeUnit(timer.unit())
        .totalDuration(timer.totalDuration())
        .count(timer.count())
        .build();
  }

  /**
   * 直接以指定时间单位、总时长与计数构造一个 {@link TimerResult}。
   *
   * @param timeUnit 时间单位
   * @param duration 总时长
   * @param count 触发次数
   * @return 新构建的计时结果
   */
  static TimerResult of(TimeUnit timeUnit, Duration duration, long count) {
    return ImmutableTimerResult.builder()
        .timeUnit(timeUnit)
        .totalDuration(duration)
        .count(count)
        .build();
  }
}
