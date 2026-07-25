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
package org.apache.iceberg.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 装箱（Bin Packing）工具类，用于按权重把一组元素划分到若干“箱”中，每箱总权重不超过目标权重。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link ListPacker}：把列表元素按权重装箱，常用于把数据文件按大小合并成读任务。
 *   <li>提供 {@link PackingIterable}：惰性装箱迭代器，支持回看（lookback）若干个未封顶的箱并择优填入。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>近似在线装箱：元素按输入顺序处理，仅在最近 lookback 个未封顶箱中寻找可容纳者，兼顾装箱质量与 内存占用（无需保存全部箱）。
 *   <li>largestBinFirst 策略：当箱数超过 lookback 需要封顶时，优先封存当前最重的箱，使剩余小箱 仍有合并空间，从而减小最终箱数。
 *   <li>惰性迭代：PackingIterable 每次返回一个已封顶的箱，避免一次性物化全部结果。
 * </ul>
 *
 * <p>上下游关系：被 {@link TableScanUtil} 等扫描计划类用于合并小文件成 CombinedScanTask， 以控制单个任务读取的数据量；也用于写入侧的文件大小估算与合并。
 */
public class BinPacking {
  /**
   * 列表装箱器：将元素按权重装箱，每个箱的总权重不超过 targetWeight。
   *
   * @param <T> 待装箱元素类型
   */
  public static class ListPacker<T> {
    private final long targetWeight;
    private final int lookback;
    private final boolean largestBinFirst;

    /**
     * 构造装箱器。
     *
     * @param targetWeight 单个箱的目标权重上限
     * @param lookback 回看窗口大小，即同时保留的未封顶箱数量上限
     * @param largestBinFirst 当箱数超过 lookback 时，是否优先封存最重的箱
     */
    public ListPacker(long targetWeight, int lookback, boolean largestBinFirst) {
      this.targetWeight = targetWeight;
      this.lookback = lookback;
      this.largestBinFirst = largestBinFirst;
    }

    /**
     * 从列表尾部开始装箱，并保持每个箱内元素相对原列表的顺序。
     *
     * <p>逻辑：先把输入列表反转，按反转序装箱；再对每个产出的箱反转回来，最后整体反转结果列表， 从而保证最终箱的顺序与箱内元素顺序都贴近原列表顺序。适用于需要“从末尾优先合并”的场景。
     *
     * @param items 待装箱元素列表
     * @param weightFunc 计算元素权重的函数
     * @return 装箱结果，每个子列表为一个箱
     */
    public List<List<T>> packEnd(List<T> items, Function<T, Long> weightFunc) {
      return Lists.reverse(
          ImmutableList.copyOf(
              Iterables.transform(
                  new PackingIterable<>(
                      Lists.reverse(items), targetWeight, lookback, weightFunc, largestBinFirst),
                  Lists::reverse)));
    }

    /**
     * 按迭代顺序装箱，返回不可变列表。
     *
     * @param items 待装箱元素
     * @param weightFunc 计算元素权重的函数
     * @return 装箱结果，每个子列表为一个箱
     */
    public List<List<T>> pack(Iterable<T> items, Function<T, Long> weightFunc) {
      return ImmutableList.copyOf(
          new PackingIterable<>(items, targetWeight, lookback, weightFunc, largestBinFirst));
    }
  }

  /**
   * 装箱迭代器包装：惰性产出已封顶的箱。
   *
   * @param <T> 元素类型
   */
  public static class PackingIterable<T> implements Iterable<List<T>> {
    private final Iterable<T> iterable;
    private final long targetWeight;
    private final int lookback;
    private final Function<T, Long> weightFunc;
    private final boolean largestBinFirst;

    /**
     * 构造装箱迭代器（默认 largestBinFirst=false）。
     *
     * @param iterable 元素来源
     * @param targetWeight 单箱目标权重上限
     * @param lookback 回看窗口大小，必须大于 0
     * @param weightFunc 权重函数
     */
    public PackingIterable(
        Iterable<T> iterable, long targetWeight, int lookback, Function<T, Long> weightFunc) {
      this(iterable, targetWeight, lookback, weightFunc, false);
    }

    /**
     * 构造装箱迭代器。
     *
     * @param iterable 元素来源
     * @param targetWeight 单箱目标权重上限
     * @param lookback 回看窗口大小，必须大于 0，否则抛 IllegalArgumentException
     * @param weightFunc 权重函数
     * @param largestBinFirst 是否优先封存最重的箱
     */
    public PackingIterable(
        Iterable<T> iterable,
        long targetWeight,
        int lookback,
        Function<T, Long> weightFunc,
        boolean largestBinFirst) {
      Preconditions.checkArgument(
          lookback > 0, "Bin look-back size must be greater than 0: %s", lookback);
      this.iterable = iterable;
      this.targetWeight = targetWeight;
      this.lookback = lookback;
      this.weightFunc = weightFunc;
      this.largestBinFirst = largestBinFirst;
    }

    @Override
    public Iterator<List<T>> iterator() {
      return new PackingIterator<>(
          iterable.iterator(), targetWeight, lookback, weightFunc, largestBinFirst);
    }
  }

  /**
   * 装箱迭代器核心实现：维护一个未封顶箱的双端队列，逐元素处理。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>取下一个元素并计算权重；
   *   <li>在未封顶箱队列中找第一个能容纳该权重的箱（{@link #findBin}），找到则加入；
   *   <li>找不到则新建箱加入队列尾部；若此时箱数超过 lookback，则按策略（largestBinFirst 时取最重箱， 否则取队首）封存并返回该箱；
   *   <li>元素耗尽后，依次封存剩余箱并返回。
   * </ol>
   *
   * @param <T> 元素类型
   */
  private static class PackingIterator<T> implements Iterator<List<T>> {
    private final Deque<Bin<T>> bins = Lists.newLinkedList();
    private final Iterator<T> items;
    private final long targetWeight;
    private final int lookback;
    private final Function<T, Long> weightFunc;
    private final boolean largestBinFirst;

    private PackingIterator(
        Iterator<T> items,
        long targetWeight,
        int lookback,
        Function<T, Long> weightFunc,
        boolean largestBinFirst) {
      this.items = items;
      this.targetWeight = targetWeight;
      this.lookback = lookback;
      this.weightFunc = weightFunc;
      this.largestBinFirst = largestBinFirst;
    }

    @Override
    public boolean hasNext() {
      return items.hasNext() || !bins.isEmpty();
    }

    @Override
    public List<T> next() {
      while (items.hasNext()) {
        T item = items.next();

        long weight = weightFunc.apply(item);
        Bin<T> bin = findBin(weight);

        if (bin != null) {
          bin.add(item, weight);

        } else {
          bin = newBin();
          bin.add(item, weight);
          bins.addLast(bin);

          if (bins.size() > lookback) {
            Bin<T> binToRemove;
            if (largestBinFirst) {
              binToRemove = removeLargestBin(bins);
            } else {
              binToRemove = bins.removeFirst();
            }
            return ImmutableList.copyOf(binToRemove.items());
          }
        }
      }

      if (bins.isEmpty()) {
        throw new NoSuchElementException();
      }

      return ImmutableList.copyOf(bins.removeFirst().items());
    }

    /**
     * 在未封顶箱队列中查找第一个能容纳指定权重的箱。
     *
     * @param weight 待加入元素的权重
     * @return 可容纳的箱；找不到返回 null
     */
    private Bin<T> findBin(long weight) {
      for (Bin<T> bin : bins) {
        if (bin.canAdd(weight)) {
          return bin;
        }
      }
      return null;
    }

    private Bin<T> newBin() {
      return new Bin<>(targetWeight);
    }

    /**
     * 从箱集合中移除并返回权重最大的箱，O(n) 时间。
     *
     * @param bins 待筛选的箱集合
     * @param <T> 元素类型
     * @return 权重最大的箱
     * @throws NoSuchElementException 若移除失败（理论上不应发生）
     */
    private static <T> Bin<T> removeLargestBin(Collection<Bin<T>> bins) {
      // Iterate through all bins looking for one with maximum weight, taking O(n) time.
      Bin<T> maxBin = Collections.max(bins, Comparator.comparingLong(Bin::weight));

      // Sanity check: we have removed maxBin from list of bins.
      if (bins.remove(maxBin)) {
        return maxBin;
      } else {
        throw new NoSuchElementException();
      }
    }
  }

  /**
   * 单个箱：记录目标权重上限、已装元素及当前累计权重。
   *
   * @param <T> 元素类型
   */
  private static class Bin<T> {
    private final long targetWeight;
    private final List<T> items = Lists.newArrayList();
    private long binWeight = 0L;

    Bin(long targetWeight) {
      this.targetWeight = targetWeight;
    }

    List<T> items() {
      return items;
    }

    /**
     * 判断当前箱是否还能容纳指定权重而不超限。
     *
     * @param weight 待加入权重
     * @return 不超目标权重返回 true
     */
    boolean canAdd(long weight) {
      return binWeight + weight <= targetWeight;
    }

    /**
     * 加入元素并累加权重。
     *
     * @param item 元素
     * @param weight 权重
     */
    void add(T item, long weight) {
      this.binWeight += weight;
      items.add(item);
    }

    /** @return 当前箱累计权重 */
    long weight() {
      return binWeight;
    }
  }
}
