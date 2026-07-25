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
package org.apache.iceberg.flink.sink.shuffle;

import java.util.Map;
import org.apache.flink.annotation.Internal;
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 基于 Map 的数据统计实现，用 {@link Map} 统计键的出现频次。
 *
 * <p>所属模块：iceberg-flink（sink shuffle 侧），实现 {@link DataStatistics}。
 *
 * <p>职责：以 RowData 为键累计计数，支持合并其它统计实例，供分布感知 shuffle 计算权重。
 *
 * <p>设计意图：使用普通 HashMap，add 通过 merge 累加；merge 逐键合并。简单直接，适用于键数量可控的场景。
 *
 * <p>上下游关系：被 {@link DataStatisticsOperator} 用于累积子任务级统计，聚合后供分区器决策。
 */
@Internal
class MapDataStatistics implements DataStatistics<MapDataStatistics, Map<RowData, Long>> {
  private final Map<RowData, Long> statistics;

  /** 构造空统计实例。 */
  MapDataStatistics() {
    this.statistics = Maps.newHashMap();
  }

  /** 使用给定 map 构造统计实例。 */
  MapDataStatistics(Map<RowData, Long> statistics) {
    this.statistics = statistics;
  }

  /** 统计是否为空。 */
  @Override
  public boolean isEmpty() {
    return statistics.size() == 0;
  }

  /** 将键的计数加一。 */
  @Override
  public void add(RowData key) {
    // increase count of occurrence by one in the dataStatistics map
    statistics.merge(key, 1L, Long::sum);
  }

  /** 合并另一统计实例，逐键累加计数。 */
  @Override
  public void merge(MapDataStatistics otherStatistics) {
    otherStatistics.statistics().forEach((key, count) -> statistics.merge(key, count, Long::sum));
  }

  /** 返回底层统计 map。 */
  @Override
  public Map<RowData, Long> statistics() {
    return statistics;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("statistics", statistics).toString();
  }
}
