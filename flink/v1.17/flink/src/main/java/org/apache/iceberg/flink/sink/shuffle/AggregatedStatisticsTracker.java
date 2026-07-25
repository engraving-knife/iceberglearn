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

import java.util.Set;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 聚合统计追踪器，由 {@link DataStatisticsCoordinator} 使用，追踪某 checkpoint 下各子任务上报的统计并聚合。
 *
 * <p>所属模块：iceberg-flink（sink shuffle 侧）。
 *
 * <p>职责：收集 {@link DataStatisticsOperator} 各子任务上报的 {@link DataStatisticsEvent}，合并为 {@link
 * AggregatedStatistics}，在满足完成条件时返回给协调器下发。
 *
 * <p>设计意图：当多数子任务（≥90% 阈值）已上报时即可视为该 checkpoint 聚合完成，容忍部分子任务滞后； 收到旧 checkpoint 事件时忽略，收到新 checkpoint
 * 事件时切换并按阈值决定是否保留旧聚合结果。
 *
 * <p>上下游关系：被 {@link DataStatisticsCoordinator} 调用；上游为各子任务的统计事件。
 */
class AggregatedStatisticsTracker<D extends DataStatistics<D, S>, S> {
  private static final Logger LOG = LoggerFactory.getLogger(AggregatedStatisticsTracker.class);
  private static final double ACCEPT_PARTIAL_AGGR_THRESHOLD = 90;
  private final String operatorName;
  private final TypeSerializer<DataStatistics<D, S>> statisticsSerializer;
  private final int parallelism;
  private final Set<Integer> inProgressSubtaskSet;
  private volatile AggregatedStatistics<D, S> inProgressStatistics;

  /**
   * 构造追踪器。
   *
   * @param operatorName 算子名（用于日志）
   * @param statisticsSerializer 统计序列化器
   * @param parallelism 子任务并行度
   */
  AggregatedStatisticsTracker(
      String operatorName,
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer,
      int parallelism) {
    this.operatorName = operatorName;
    this.statisticsSerializer = statisticsSerializer;
    this.parallelism = parallelism;
    this.inProgressSubtaskSet = Sets.newHashSet();
  }

  /**
   * 更新某子任务的统计并在满足完成条件时返回聚合结果。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>收到比当前更旧的 checkpoint 事件：忽略。
   *   <li>收到更新 checkpoint 事件：对旧 checkpoint 的聚合按 90% 阈值决定是否作为完成结果，随后清空并开启新 checkpoint 聚合。
   *   <li>将子任务统计合并进 inProgressStatistics；重复子任务忽略。
   *   <li>全部子任务到齐时立即返回完成结果，并为下一 checkpoint 预建空聚合。
   * </ul>
   *
   * @param subtask 子任务索引
   * @param event 统计事件
   * @return 完成的聚合统计，未完成返回 null
   */
  AggregatedStatistics<D, S> updateAndCheckCompletion(
      int subtask, DataStatisticsEvent<D, S> event) {
    long checkpointId = event.checkpointId();

    if (inProgressStatistics != null && inProgressStatistics.checkpointId() > checkpointId) {
      LOG.info(
          "Expect data statistics for operator {} checkpoint {}, but receive event from older checkpoint {}. Ignore it.",
          operatorName,
          inProgressStatistics.checkpointId(),
          checkpointId);
      return null;
    }

    AggregatedStatistics<D, S> completedStatistics = null;
    if (inProgressStatistics != null && inProgressStatistics.checkpointId() < checkpointId) {
      if ((double) inProgressSubtaskSet.size() / parallelism * 100
          >= ACCEPT_PARTIAL_AGGR_THRESHOLD) {
        completedStatistics = inProgressStatistics;
        LOG.info(
            "Received data statistics from {} subtasks out of total {} for operator {} at checkpoint {}. "
                + "Complete data statistics aggregation at checkpoint {} as it is more than the threshold of {} percentage",
            inProgressSubtaskSet.size(),
            parallelism,
            operatorName,
            checkpointId,
            inProgressStatistics.checkpointId(),
            ACCEPT_PARTIAL_AGGR_THRESHOLD);
      } else {
        LOG.info(
            "Received data statistics from {} subtasks out of total {} for operator {} at checkpoint {}. "
                + "Aborting the incomplete aggregation for checkpoint {}",
            inProgressSubtaskSet.size(),
            parallelism,
            operatorName,
            checkpointId,
            inProgressStatistics.checkpointId());
      }

      inProgressStatistics = null;
      inProgressSubtaskSet.clear();
    }

    if (inProgressStatistics == null) {
      LOG.info("Starting a new data statistics for checkpoint {}", checkpointId);
      inProgressStatistics = new AggregatedStatistics<>(checkpointId, statisticsSerializer);
      inProgressSubtaskSet.clear();
    }

    if (!inProgressSubtaskSet.add(subtask)) {
      LOG.debug(
          "Ignore duplicated data statistics from operator {} subtask {} for checkpoint {}.",
          operatorName,
          subtask,
          checkpointId);
    } else {
      inProgressStatistics.mergeDataStatistic(
          operatorName,
          event.checkpointId(),
          DataStatisticsUtil.deserializeDataStatistics(
              event.statisticsBytes(), statisticsSerializer));
    }

    if (inProgressSubtaskSet.size() == parallelism) {
      completedStatistics = inProgressStatistics;
      LOG.info(
          "Received data statistics from all {} operators {} for checkpoint {}. Return last completed aggregator {}.",
          parallelism,
          operatorName,
          inProgressStatistics.checkpointId(),
          completedStatistics.dataStatistics());
      inProgressStatistics = new AggregatedStatistics<>(checkpointId + 1, statisticsSerializer);
      inProgressSubtaskSet.clear();
    }

    return completedStatistics;
  }

  /** 返回当前进行中的聚合统计（仅供测试）。 */
  @VisibleForTesting
  AggregatedStatistics<D, S> inProgressStatistics() {
    return inProgressStatistics;
  }
}
