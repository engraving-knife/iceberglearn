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
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.runtime.operators.coordination.OperatorEvent;
import org.apache.flink.runtime.operators.coordination.OperatorEventGateway;
import org.apache.flink.runtime.operators.coordination.OperatorEventHandler;
import org.apache.flink.runtime.state.StateInitializationContext;
import org.apache.flink.runtime.state.StateSnapshotContext;
import org.apache.flink.streaming.api.operators.AbstractStreamOperator;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：数据统计算子，收集流量分布统计并传递给下游自定义分区器。
 *
 * <p>所属模块：iceberg-flink（sink/shuffle 子包），继承 Flink 的 {@link AbstractStreamOperator}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>收集本地（当前子任务）数据统计：通过 keySelector 从 RowData 提取 key 并更新 localStatistics。
 *   <li>在 checkpoint 时将本地统计上报给 {@link DataStatisticsCoordinator}。
 *   <li>接收协调器下发的全局统计（globalStatistics），供下游自定义分区器使用。
 *   <li>将数据和统计封装为 {@link DataStatisticsOrRecord} 输出给下游。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>localStatistics 在每个 checkpoint 周期内累积，checkpoint 后上报并重置。
 *   <li>globalStatistics 通过 OperatorEvent 从协调器接收，存储在 union list state 中以支持故障恢复。
 *   <li>数据流向：RowData → DataStatisticsOrRecord（含统计或记录），下游分区器据此分发。
 * </ul>
 *
 * <p>上下游关系：上游为 RowData 数据流；下游为自定义分区器 + IcebergStreamWriter； 与 {@link DataStatisticsCoordinator} 通过
 * OperatorEvent 通信。
 *
 * @param <D> 数据统计类型
 * @param <S> 统计结果类型
 */
@Internal
class DataStatisticsOperator<D extends DataStatistics<D, S>, S>
    extends AbstractStreamOperator<DataStatisticsOrRecord<D, S>>
    implements OneInputStreamOperator<RowData, DataStatisticsOrRecord<D, S>>, OperatorEventHandler {
  private static final long serialVersionUID = 1L;

  private final String operatorName;
  // keySelector will be used to generate key from data for collecting data statistics
  private final KeySelector<RowData, RowData> keySelector;
  private final OperatorEventGateway operatorEventGateway;
  private final TypeSerializer<DataStatistics<D, S>> statisticsSerializer;
  private transient volatile DataStatistics<D, S> localStatistics;
  private transient volatile DataStatistics<D, S> globalStatistics;
  private transient ListState<DataStatistics<D, S>> globalStatisticsState;

  /**
   * 构造方法。
   *
   * @param operatorName 算子名称
   * @param keySelector 从 RowData 提取统计 key 的选择器
   * @param operatorEventGateway 算子事件网关（与协调器通信）
   * @param statisticsSerializer 数据统计序列化器
   */
  DataStatisticsOperator(
      String operatorName,
      KeySelector<RowData, RowData> keySelector,
      OperatorEventGateway operatorEventGateway,
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer) {
    this.operatorName = operatorName;
    this.keySelector = keySelector;
    this.operatorEventGateway = operatorEventGateway;
    this.statisticsSerializer = statisticsSerializer;
  }

  /**
   * 初始化算子状态。
   *
   * <p>逻辑：创建 localStatistics → 从 union list state 恢复 globalStatisticsState → 若从 checkpoint
   * 恢复则读取已保存的全局统计。
   */
  @Override
  public void initializeState(StateInitializationContext context) throws Exception {
    localStatistics = statisticsSerializer.createInstance();
    globalStatisticsState =
        context
            .getOperatorStateStore()
            .getUnionListState(
                new ListStateDescriptor<>("globalStatisticsState", statisticsSerializer));

    if (context.isRestored()) {
      int subtaskIndex = getRuntimeContext().getIndexOfThisSubtask();
      if (globalStatisticsState.get() == null
          || !globalStatisticsState.get().iterator().hasNext()) {
        LOG.warn(
            "Operator {} subtask {} doesn't have global statistics state to restore",
            operatorName,
            subtaskIndex);
        globalStatistics = statisticsSerializer.createInstance();
      } else {
        LOG.info(
            "Restoring operator {} global statistics state for subtask {}",
            operatorName,
            subtaskIndex);
        globalStatistics = globalStatisticsState.get().iterator().next();
      }
    } else {
      globalStatistics = statisticsSerializer.createInstance();
    }
  }

  @Override
  public void open() throws Exception {
    if (!globalStatistics.isEmpty()) {
      output.collect(
          new StreamRecord<>(DataStatisticsOrRecord.fromDataStatistics(globalStatistics)));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void handleOperatorEvent(OperatorEvent event) {
    int subtaskIndex = getRuntimeContext().getIndexOfThisSubtask();
    Preconditions.checkArgument(
        event instanceof DataStatisticsEvent,
        String.format(
            "Operator %s subtask %s received unexpected operator event %s",
            operatorName, subtaskIndex, event.getClass()));
    DataStatisticsEvent<D, S> statisticsEvent = (DataStatisticsEvent<D, S>) event;
    LOG.info(
        "Operator {} received global data event from coordinator checkpoint {}",
        operatorName,
        statisticsEvent.checkpointId());
    globalStatistics =
        DataStatisticsUtil.deserializeDataStatistics(
            statisticsEvent.statisticsBytes(), statisticsSerializer);
    output.collect(new StreamRecord<>(DataStatisticsOrRecord.fromDataStatistics(globalStatistics)));
  }

  @Override
  public void processElement(StreamRecord<RowData> streamRecord) throws Exception {
    RowData record = streamRecord.getValue();
    RowData key = keySelector.getKey(record);
    localStatistics.add(key);
    output.collect(new StreamRecord<>(DataStatisticsOrRecord.fromRecord(record)));
  }

  @Override
  public void snapshotState(StateSnapshotContext context) throws Exception {
    long checkpointId = context.getCheckpointId();
    int subTaskId = getRuntimeContext().getIndexOfThisSubtask();
    LOG.info(
        "Snapshotting data statistics operator {} for checkpoint {} in subtask {}",
        operatorName,
        checkpointId,
        subTaskId);

    // Pass global statistics to partitioners so that all the operators refresh statistics
    // at same checkpoint barrier
    if (!globalStatistics.isEmpty()) {
      output.collect(
          new StreamRecord<>(DataStatisticsOrRecord.fromDataStatistics(globalStatistics)));
    }

    // Only subtask 0 saves the state so that globalStatisticsState(UnionListState) stores
    // an exact copy of globalStatistics
    if (!globalStatistics.isEmpty() && getRuntimeContext().getIndexOfThisSubtask() == 0) {
      globalStatisticsState.clear();
      LOG.info(
          "Saving operator {} global statistics {} to state in subtask {}",
          operatorName,
          globalStatistics,
          subTaskId);
      globalStatisticsState.add(globalStatistics);
    }

    // For now, local statistics are sent to coordinator at checkpoint
    operatorEventGateway.sendEventToCoordinator(
        DataStatisticsEvent.create(checkpointId, localStatistics, statisticsSerializer));
    LOG.debug(
        "Subtask {} of operator {} sent local statistics to coordinator at checkpoint{}: {}",
        subTaskId,
        operatorName,
        checkpointId,
        localStatistics);

    // Recreate the local statistics
    localStatistics = statisticsSerializer.createInstance();
  }

  @VisibleForTesting
  DataStatistics<D, S> localDataStatistics() {
    return localStatistics;
  }

  @VisibleForTesting
  DataStatistics<D, S> globalDataStatistics() {
    return globalStatistics;
  }
}
