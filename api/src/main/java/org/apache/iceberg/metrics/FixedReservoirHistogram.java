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

import java.util.Arrays;
import java.util.Random;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 基于蓄水池采样（reservoir sampling）的 {@link Histogram} 实现：维护一个固定大小的样本数组，
 * 当样本数超过容量时按概率随机替换已有样本，从而以等概率保留近期观测的代表性子集。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>接收大量观测值并仅保留固定大小的样本集合，控制内存占用。
 *   <li>基于保留样本计算均值、标准差、最值、分位数等统计量。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>蓄水池采样算法（Algorithm R）：第 n 个观测到来时，以 n/count 的概率随机替换池中某个 位置，保证每个观测被保留的概率均为
 *       reservoirSize/总观测数，从而支持无界数据流的 在线统计。
 *   <li>update 与 count 加 synchronized 保证采样过程的线程安全；{@link #statistics()} 则先在
 *       锁内拷贝样本数组，再在锁外计算，减少临界区持有时间。
 *   <li>方差采用未修正估计量（除以 n 而非 n-1）：在蓄水池采样近似场景下 Bessel 修正反而不合适， 参见 Wikipedia 关于 Bessel 校正的 caveat。
 * </ul>
 *
 * <p>上下游关系：由 {@link DefaultMetricsContext#histogram(String)} 创建；被 core 模块用于 统计扫描返回的数据量、提交耗时分布等。
 */
public class FixedReservoirHistogram implements Histogram {
  private final Random rand;
  private final long[] measurements;
  private int count;

  /**
   * 构造指定蓄水池容量的直方图。
   *
   * @param reservoirSize 蓄水池样本容量
   */
  public FixedReservoirHistogram(int reservoirSize) {
    this.rand = new Random();
    this.measurements = new long[reservoirSize];
    this.count = 0;
  }

  @Override
  public synchronized int count() {
    return count;
  }

  /**
   * 更新一个观测值，按蓄水池采样策略决定是否纳入样本。
   *
   * <p>逻辑：先 count 自增；若未填满蓄水池则直接放入 count-1 位置；若已填满，则在 {@code [0, count)} 范围内生成随机 index，仅当 index
   * 落在蓄水池范围内时替换对应位置样本， 以保证等概率采样。
   *
   * @param value 观测值
   */
  @Override
  public synchronized void update(long value) {
    count += 1;
    int index = count <= measurements.length ? count - 1 : rand.nextInt(count);
    if (index < measurements.length) {
      measurements[index] = value;
    }
  }

  /**
   * 基于当前蓄水池样本计算统计量。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>取实际样本数 size = min(count, 池容量)，若为 0 返回空统计 {@link UniformWeightStatistics#EMPTY}。
   *   <li>在 synchronized 块内拷贝样本数组到局部变量，避免计算期间被并发 update 干扰。
   *   <li>遍历累加 sum 与 sumSquares，计算 mean 与未修正方差得到 stdDev。
   *   <li>返回包含排序后样本的 {@link UniformWeightStatistics}，便于后续分位数查询。
   * </ul>
   *
   * <p>参考：<a href="https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance">方差计算算法</a>。
   *
   * @return 当前样本的统计快照
   */
  @Override
  public Statistics statistics() {
    int size = (int) Math.min(count, (long) measurements.length);
    if (size == 0) {
      return UniformWeightStatistics.EMPTY;
    }

    long[] values = new long[size];
    synchronized (this) {
      System.arraycopy(measurements, 0, values, 0, size);
    }

    double sum = 0.0d;
    double sumSquares = 0.0d;
    for (long x : values) {
      sum += x;
      // Convert to double value to avoid potential overflow of square
      double value = (double) x;
      sumSquares += value * value;
    }

    double mean = sum / size;
    // Use the uncorrected estimator. Bessel's correction is not a good fit in this context.
    // https://en.wikipedia.org/wiki/Bessel%27s_correction#Caveats
    double variance = (sumSquares - (sum * sum) / size) / size;
    double stdDev = Math.sqrt(variance);
    return new UniformWeightStatistics(values, mean, stdDev);
  }

  /**
   * 等权重统计实现：对样本数组排序后提供均值、标准差、最值与分位数查询。
   *
   * <p>设计意图：构造时即对样本排序，使 min/max/percentile 都能 O(1) 或 O(log n) 完成； 由于入参已是拷贝数组，直接原地排序避免再次复制。
   */
  private static class UniformWeightStatistics implements Statistics {
    private static final UniformWeightStatistics EMPTY =
        new UniformWeightStatistics(new long[0], 0.0, 0.0);

    private final long[] values;
    private final double mean;
    private final double stdDev;

    private UniformWeightStatistics(long[] values, double mean, double stdDev) {
      this.values = values;
      this.mean = mean;
      this.stdDev = stdDev;

      // since the input values is already a copied array,
      // there is no need to copy again.
      Arrays.sort(this.values);
    }

    @Override
    public int size() {
      return values.length;
    }

    @Override
    public double mean() {
      return mean;
    }

    @Override
    public double stdDev() {
      return stdDev;
    }

    @Override
    public long max() {
      return values.length == 0 ? 0L : values[values.length - 1];
    }

    @Override
    public long min() {
      return values.length == 0 ? 0L : values[0];
    }

    /**
     * 返回指定分位数对应的值。
     *
     * <p>逻辑：基于已排序数组，计算 position = percentile * size，取相邻索引做线性插值。 边界处理：position
     * 落在首个或末尾区间时直接返回端点值，避免越界。
     *
     * @param percentile 分位点，范围 [0.0, 1.0]，例如 0.75 表示 75 分位
     * @return 该分位点对应的值
     * @throws IllegalArgumentException 若 percentile 为 NaN 或超出 [0.0, 1.0]
     */
    @Override
    public long percentile(double percentile) {
      Preconditions.checkArgument(
          !Double.isNaN(percentile) && percentile >= 0.0 && percentile <= 1.0,
          "Percentile point cannot be outside the range of [0.0 - 1.0]: %s",
          percentile);
      if (values.length == 0) {
        return 0L;
      } else {
        double position = percentile * values.length;
        int index = (int) position;
        if (index < 1) {
          return values[0];
        } else if (index >= values.length) {
          return values[values.length - 1];
        } else {
          double lower = (double) values[index - 1];
          double upper = (double) values[index];
          double interpolated = lower + (position - Math.floor(position)) * (upper - lower);
          return (long) interpolated;
        }
      }
    }
  }
}
