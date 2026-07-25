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
package org.apache.iceberg.spark.actions;

import java.util.Collections;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.spark.util.AccumulatorV2;

/**
 * 基于 Spark AccumulatorV2 的线程安全 Set 累加器。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层）。
 *
 * <p>职责：在 Spark 分布式任务执行期间，跨 executor 收集元素到一个去重集合中， 最终在 driver 端获取合并后的完整 Set。常用于收集被删除/处理的文件路径。
 *
 * <p>设计意图：Spark 原生 AccumulatorV2 不提供 Set 语义，本类通过 {@link Collections#synchronizedSet} 包装 HashSet
 * 实现线程安全，支持跨分区 合并（merge）与复制（copy）。泛型 T 允许收集任意类型的元素。
 *
 * <p>上下游关系：被 Iceberg Spark Action（如 DeleteReachableFilesSparkAction） 用于在分布式删除文件时收集已删除文件信息。
 */
public class SetAccumulator<T> extends AccumulatorV2<T, java.util.Set<T>> {

  private final Set<T> set = Collections.synchronizedSet(Sets.newHashSet());

  /** 判断累加器是否为零值（集合为空）。 */
  @Override
  public boolean isZero() {
    return set.isEmpty();
  }

  /** 创建当前累加器的副本，复制内部集合内容，用于 Spark 任务分发前的快照。 */
  @Override
  public AccumulatorV2<T, Set<T>> copy() {
    SetAccumulator<T> newAccumulator = new SetAccumulator<>();
    newAccumulator.set.addAll(set);
    return newAccumulator;
  }

  /** 重置累加器为零值状态（清空集合）。 */
  @Override
  public void reset() {
    set.clear();
  }

  /**
   * 添加一个元素到累加器集合中。
   *
   * @param v 待添加的元素
   */
  @Override
  public void add(T v) {
    set.add(v);
  }

  /**
   * 合并另一个累加器的值到当前累加器（将 other 的集合全部加入当前集合）。
   *
   * @param other 另一个同类型累加器
   */
  @Override
  public void merge(AccumulatorV2<T, Set<T>> other) {
    set.addAll(other.value());
  }

  /** 返回累加器当前收集到的集合视图。 */
  @Override
  public Set<T> value() {
    return set;
  }
}
