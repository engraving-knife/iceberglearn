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
import org.apache.flink.runtime.operators.coordination.OperatorEvent;

/**
 * 文件级说明：数据统计协调器与算子之间传输数据统计信息的算子事件。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink/shuffle 子包）。
 *
 * <p>职责：把数据统计对象序列化为字节数组，并以 {@link OperatorEvent} 形式 在协调器与算子之间传递，附带对应的 checkpoint ID 用于关联。
 *
 * <p>设计意图：Flink 算子事件要求可序列化，通过字节流传输可承载任意统计实现， checkpoint ID 用于保证事件与对应 checkpoint 的一致性。
 *
 * <p>上下游关系：上游为协调器/算子调用 {@link #create} 构造事件， 下游为接收方反序列化 {@link #statisticsBytes()} 还原统计信息。
 */
@Internal
class DataStatisticsEvent<D extends DataStatistics<D, S>, S> implements OperatorEvent {

  private static final long serialVersionUID = 1L;
  private final long checkpointId;
  private final byte[] statisticsBytes;

  private DataStatisticsEvent(long checkpointId, byte[] statisticsBytes) {
    this.checkpointId = checkpointId;
    this.statisticsBytes = statisticsBytes;
  }

  /**
   * 构造数据统计事件，将统计对象序列化为字节。
   *
   * @param checkpointId 对应的 checkpoint ID
   * @param dataStatistics 待传输的数据统计对象
   * @param statisticsSerializer 统计对象的序列化器
   * @param <D> 数据统计的具体类型
   * @param <S> 统计底层存储的类型
   * @return 序列化后的事件
   */
  static <D extends DataStatistics<D, S>, S> DataStatisticsEvent<D, S> create(
      long checkpointId,
      DataStatistics<D, S> dataStatistics,
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer) {
    return new DataStatisticsEvent<>(
        checkpointId,
        DataStatisticsUtil.serializeDataStatistics(dataStatistics, statisticsSerializer));
  }

  /** 返回本事件对应的 checkpoint ID。 */
  long checkpointId() {
    return checkpointId;
  }

  /** 返回序列化后的统计信息字节。 */
  byte[] statisticsBytes() {
    return statisticsBytes;
  }
}
