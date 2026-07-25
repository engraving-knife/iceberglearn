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

import java.io.Serializable;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：聚合统计数据，用于在特定 checkpoint 周期内合并各子任务上报的 {@link DataStatistics}。
 *
 * <p>所属模块：iceberg-flink（sink/shuffle 子包），服务于 RANGE 分发模式的数据聚类优化。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 checkpointId 和对应的合并后 {@link DataStatistics}。
 *   <li>提供 mergeDataStatistic 方法将各子任务上报的统计数据合并到当前聚合结果。
 * </ul>
 *
 * <p>设计意图：{@link DataStatisticsCoordinator} 为每个 checkpoint 创建一个 AggregatedStatistics，
 * 收集所有子任务的统计数据后合并，再将结果下发给各子任务用于自定义分区器优化数据分布。
 *
 * <p>上下游关系：由 {@link DataStatisticsCoordinator} 创建和管理；合并来自 {@link DataStatisticsOperator}
 * 各子任务上报的数据统计。
 *
 * @param <D> 数据统计类型
 * @param <S> 统计结果类型
 */
class AggregatedStatistics<D extends DataStatistics<D, S>, S> implements Serializable {

  private final long checkpointId;
  private final DataStatistics<D, S> dataStatistics;

  /** 构造方法：创建空的统计数据实例（用于开始新的 checkpoint 聚合）。 */
  AggregatedStatistics(long checkpoint, TypeSerializer<DataStatistics<D, S>> statisticsSerializer) {
    this.checkpointId = checkpoint;
    this.dataStatistics = statisticsSerializer.createInstance();
  }

  /** 构造方法：使用已有的统计数据构造。 */
  AggregatedStatistics(long checkpoint, DataStatistics<D, S> dataStatistics) {
    this.checkpointId = checkpoint;
    this.dataStatistics = dataStatistics;
  }

  long checkpointId() {
    return checkpointId;
  }

  DataStatistics<D, S> dataStatistics() {
    return dataStatistics;
  }

  /**
   * 合并子任务上报的统计数据。
   *
   * <p>逻辑：校验 checkpointId 一致后，将子任务的统计数据合并到当前聚合结果。
   *
   * @param operatorName 算子名称
   * @param eventCheckpointId 事件携带的 checkpoint id
   * @param eventDataStatistics 子任务的统计数据
   */
  void mergeDataStatistic(String operatorName, long eventCheckpointId, D eventDataStatistics) {
    Preconditions.checkArgument(
        checkpointId == eventCheckpointId,
        "Received unexpected event from operator %s checkpoint %s. Expected checkpoint %s",
        operatorName,
        eventCheckpointId,
        checkpointId);
    dataStatistics.merge(eventDataStatistics);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("checkpointId", checkpointId)
        .add("dataStatistics", dataStatistics)
        .toString();
  }
}
