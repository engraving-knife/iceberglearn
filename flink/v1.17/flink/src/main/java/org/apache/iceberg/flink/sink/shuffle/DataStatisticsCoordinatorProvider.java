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
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;
import org.apache.flink.runtime.operators.coordination.RecreateOnResetOperatorCoordinator;

/**
 * {@link DataStatisticsCoordinator} 的提供器，用于在 Flink JobGraph 中创建协调器实例。
 *
 * <p>所属模块：iceberg-flink（sink shuffle 侧），继承 {@link RecreateOnResetOperatorCoordinator.Provider}。
 *
 * <p>职责：携带算子名与统计序列化器，按算子 id 创建 {@link DataStatisticsCoordinator}。
 *
 * <p>设计意图：Flink 通过 Provider 机制为算子关联 OperatorCoordinator；继承 RecreateOnReset 表示
 * 作业重启时重建协调器（协调器状态不持久化，由 tracker 重新累积）。
 *
 * <p>上下游关系：被 Flink 框架在作业图构建/恢复时调用；下游创建 DataStatisticsCoordinator。
 */
@Internal
public class DataStatisticsCoordinatorProvider<D extends DataStatistics<D, S>, S>
    extends RecreateOnResetOperatorCoordinator.Provider {

  private final String operatorName;
  private final TypeSerializer<DataStatistics<D, S>> statisticsSerializer;

  /**
   * 构造提供器。
   *
   * @param operatorName 算子名
   * @param operatorID 算子唯一 id
   * @param statisticsSerializer 统计序列化器
   */
  public DataStatisticsCoordinatorProvider(
      String operatorName,
      OperatorID operatorID,
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer) {
    super(operatorID);
    this.operatorName = operatorName;
    this.statisticsSerializer = statisticsSerializer;
  }

  /**
   * 创建 {@link DataStatisticsCoordinator} 实例。
   *
   * @param context 协调器上下文
   * @return 协调器实例
   */
  @Override
  public OperatorCoordinator getCoordinator(OperatorCoordinator.Context context) {
    return new DataStatisticsCoordinator<>(operatorName, context, statisticsSerializer);
  }
}
