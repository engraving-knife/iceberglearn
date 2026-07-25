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

/**
 * 计数器接口：用于在遥测（telemetry）场景中统计事件发生次数。
 *
 * <p>所属模块：iceberg-api（最核心的抽象 API 层，被 iceberg-core 及各引擎集成模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义计数器的基本语义：自增、按量自增、读取当前值、查询度量单位。
 *   <li>为 Iceberg 各类操作（扫描、提交、写入等）提供统一的计数抽象，便于上层采集指标。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>抽象先于实现：API 层只暴露接口，具体计数实现（{@link DefaultCounter}、引擎自定义实现等） 通过 {@link MetricsContext} 注入，使
 *       API 不绑定特定度量后端。
 *   <li>区分 int/long 重载以兼顾调用便利性与精度；{@code increment(int)} 默认委托给 {@code increment(long)}，避免重复实现。
 *   <li>{@link #isNoop()} 配合 {@link DefaultCounter#NOOP} 空对象，使调用方在不需采集指标时 可直接传入 NOOP，省去 null 判断。
 * </ul>
 *
 * <p>上下游关系：由 {@link MetricsContext#counter(String, Unit)} 创建；被 core 模块的扫描/提交等 流程调用以记录关键事件计数，最终经
 * {@link MetricsReport} 汇总上报。
 */
public interface Counter {

  /** 计数器自增 1。 */
  void increment();

  /**
   * 按指定 int 量自增计数器。
   *
   * <p>实现可省略溢出检查以提升写入吞吐。默认实现将 int 提升为 long 后委托给 {@link #increment(long)}。
   *
   * @param amount 自增量
   */
  default void increment(int amount) {
    increment((long) amount);
  }

  /**
   * 按指定 long 量自增计数器。
   *
   * <p>实现可省略溢出检查以提升写入吞吐。
   *
   * @param amount 自增量
   */
  void increment(long amount);

  /**
   * 报告当前累计计数值。
   *
   * @return 当前计数值
   */
  long value();

  /**
   * 返回该计数器对应的度量单位。
   *
   * @return 计数器单位，默认为 {@link Unit#COUNT}
   */
  default Unit unit() {
    return Unit.COUNT;
  }

  /**
   * 判断本计数器是否为 NOOP（空操作）计数器。
   *
   * <p>设计意图：调用方无需关心是否真的采集指标，通过本方法可识别空对象实现。
   *
   * @return 若本对象等于 {@link DefaultCounter#NOOP} 则返回 true
   */
  default boolean isNoop() {
    return DefaultCounter.NOOP.equals(this);
  }
}
