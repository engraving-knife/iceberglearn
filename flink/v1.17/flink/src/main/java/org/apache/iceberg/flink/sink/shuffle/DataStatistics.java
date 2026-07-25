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

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.data.RowData;

/**
 * 文件级说明：数据统计信息收集接口，用于刻画数据分布。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink/shuffle 子包）。
 *
 * <p>职责：定义数据统计的标准接口，统计不同数据键的流量分布。 对于低基数键，可使用简单的 (key, count) Map；
 * 对于高基数键，可使用概率数据结构（sketching）来减少内存占用。
 *
 * <p>设计意图：抽象出统一接口，便于不同实现（Map、Sketch 等）以可插拔方式 接入 Flink shuffle 数据分布聚合流程。
 *
 * <p>上下游关系：上游为算子在处理数据时调用 {@link #add} 添加键， 下游为协调器调用 {@link #merge} 合并多个算子的统计，再通过 {@link
 * #statistics()} 取出底层数据。
 */
@Internal
interface DataStatistics<D extends DataStatistics, S> {

  /**
   * 判断是否包含任何统计信息。
   *
   * @return 若统计为空返回 true
   */
  boolean isEmpty();

  /**
   * 把一个数据键加入统计。
   *
   * @param key 通过键选择器从数据中抽取得到
   */
  void add(RowData key);

  /**
   * 把另一个统计合并到当前统计中。
   *
   * @param otherStatistics 待合并的统计
   */
  void merge(D otherStatistics);

  /**
   * 返回底层统计存储对象。
   *
   * @return 底层统计
   */
  S statistics();
}
