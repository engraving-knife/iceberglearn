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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：支持集合值的 Spark 累加器，在 Executor 端收集元素集合并在 Driver 端合并。
 *
 * <p>设计意图：扩展 Spark AccumulatorV2 以支持 Set 类型的分布式聚合。
 *
 * <p>上下游关系：由各 SparkAction 在收集分布式结果（如文件路径集合）时使用。
 */
public class SetAccumulator<T> extends AccumulatorV2<T, java.util.Set<T>> {

  private final Set<T> set = Collections.synchronizedSet(Sets.newHashSet());
  /** 判断是否 Zero。 */
  @Override
  public boolean isZero() {
    return set.isEmpty();
  }
  /** 返回副本。 */
  @Override
  public AccumulatorV2<T, Set<T>> copy() {
    SetAccumulator<T> newAccumulator = new SetAccumulator<>();
    newAccumulator.set.addAll(set);
    return newAccumulator;
  }
  /** 重置状态。 */
  @Override
  public void reset() {
    set.clear();
  }
  /** 添加元素。 */
  @Override
  public void add(T v) {
    set.add(v);
  }
  /** 合并。 */
  @Override
  public void merge(AccumulatorV2<T, Set<T>> other) {
    set.addAll(other.value());
  }
  /** 执行 value 相关操作。 */
  @Override
  public Set<T> value() {
    return set;
  }
}
