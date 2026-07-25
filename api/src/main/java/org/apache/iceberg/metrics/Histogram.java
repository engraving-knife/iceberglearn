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

/**
 * 直方图接口：用于记录一系列观测值并计算其分布统计量。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>接收观测值（{@link #update(long)}）并维护内部样本集合。
 *   <li>提供观测次数与基于样本的统计快照（均值、标准差、最值、分位数等）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>接口只定义采集与查询语义，不约束采样策略；实现可采用蓄水池采样 （如 {@link FixedReservoirHistogram}）或精确存储，由 {@link
 *       MetricsContext} 决定。
 *   <li>{@link Statistics} 作为不可变快照返回，使调用方在拿快照后无需持锁即可多次查询。
 * </ul>
 *
 * <p>上下游关系：由 {@link MetricsContext#histogram(String)} 创建；被 core 模块用于统计 数据量、耗时等分布型指标。
 */
public interface Histogram {
  /**
   * 更新直方图，记录一个新观测值。
   *
   * @param value 观测值
   */
  void update(long value);

  /**
   * 返回已观测的次数。
   *
   * @return 观测次数
   */
  int count();

  /**
   * 基于已观测样本计算统计快照。
   *
   * @return 当前样本的 {@link Statistics}
   */
  Statistics statistics();

  /**
   * 统计快照接口：基于直方图当前样本计算得到的不可变统计结果。
   *
   * <p>设计意图：作为一次性的快照返回，调用方可多次查询而无需持锁，避免统计期间阻塞 观测写入。
   */
  interface Statistics {
    /**
     * 返回统计计算所基于的样本数量。
     *
     * <p>若采样次数小于蓄水池容量，返回实际采样次数；否则返回蓄水池容量。
     *
     * @return 样本数量
     */
    int size();

    /**
     * 返回观测值的均值。
     *
     * @return 均值
     */
    double mean();

    /**
     * 返回观测值分布的标准差。
     *
     * @return 标准差
     */
    double stdDev();

    /**
     * 返回观测值中的最大值。
     *
     * @return 最大值
     */
    long max();

    /**
     * 返回观测值中的最小值。
     *
     * @return 最小值
     */
    long min();

    /**
     * 返回指定分位点对应的观测值。
     *
     * @param percentile 分位点，例如 0.75 表示 75 分位；具体支持的分位点由实现决定
     * @return 该分位点对应的值
     */
    long percentile(double percentile);
  }
}
