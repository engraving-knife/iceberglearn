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
import org.apache.flink.runtime.operators.coordination.OperatorEvent;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;

/**
 * DataStatisticsEvent is sent between data statistics coordinator and operator to transmit data
 * statistics
 */
@Internal
/**
 * 数据统计事件，承载统计快照用于算子间传输。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：封装序列化后的统计信息，作为 OperatorEvent 下发。
 *
 * <p>设计意图：事件对象；被 DataStatisticsOperator 发送，被下游接收。
 */
class DataStatisticsEvent<D extends DataStatistics<D, S>, S> implements OperatorEvent {

  private static final long serialVersionUID = 1L;

  private final long checkpointId;
  private final DataStatistics<D, S> dataStatistics;

  DataStatisticsEvent(long checkpointId, DataStatistics<D, S> dataStatistics) {
    this.checkpointId = checkpointId;
    this.dataStatistics = dataStatistics;
  }

  long checkpointId() {
    return checkpointId;
  }

  DataStatistics<D, S> dataStatistics() {
    return dataStatistics;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("checkpointId", checkpointId)
        .add("dataStatistics", dataStatistics)
        .toString();
  }
}
