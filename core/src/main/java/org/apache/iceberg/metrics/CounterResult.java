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

import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.immutables.value.Value;

/**
 * {@link Counter} 的可序列化结果视图，承载计数器最终的单位与计数值。
 *
 * <p>所属模块：iceberg-core，度量包中最基础的只读计数结果结构，用于上报与序列化。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载一个计数器的单位（{@link Unit}）与其最终累计值。
 *   <li>提供从运行时 {@link Counter} 提取结果、以及直接构造结果的工厂方法。
 * </ul>
 *
 * <p>设计意图：与运行时 {@link Counter} 解耦，仅保留可序列化的快照值， 便于跨进程传输与 JSON 持久化；Immutables 保证不可变。
 *
 * <p>上下游关系：被 {@link CommitMetricsResult}、{@link ScanMetricsResult} 等结果对象聚合； 由 {@link
 * CounterResultParser} 进行 JSON 转换。
 */
@Value.Immutable
public interface CounterResult {

  /** 返回 计数单位。 */
  Unit unit();

  /** 返回 累计计数值。 */
  long value();

  /**
   * 从运行时 {@link Counter} 提取可序列化结果。
   *
   * <p>设计要点：若计数器为 noop（空实现）则返回 null，表示该指标未实际采集， 避免上报无意义零值。
   *
   * @param counter 运行时计数器，不能为 null
   * @return 计数结果；若 counter 为 noop 则返回 null
   */
  static CounterResult fromCounter(Counter counter) {
    Preconditions.checkArgument(null != counter, "Invalid counter: null");
    if (counter.isNoop()) {
      return null;
    }

    return ImmutableCounterResult.builder().unit(counter.unit()).value(counter.value()).build();
  }

  /**
   * 直接以指定单位与值构造一个 {@link CounterResult}。
   *
   * @param unit 计数单位
   * @param value 计数值
   * @return 新构建的计数结果
   */
  static CounterResult of(Unit unit, long value) {
    return ImmutableCounterResult.builder().unit(unit).value(value).build();
  }
}
