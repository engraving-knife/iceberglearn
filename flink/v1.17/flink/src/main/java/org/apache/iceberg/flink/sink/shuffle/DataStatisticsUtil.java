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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.core.memory.DataInputDeserializer;
import org.apache.flink.core.memory.DataOutputSerializer;

/**
 * 文件级说明：数据统计的序列化/反序列化工具类。
 *
 * <p>所属模块：iceberg-flink（sink/shuffle 子包），提供 {@link DataStatistics} 和 {@link AggregatedStatistics}
 * 的序列化与反序列化方法。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>序列化/反序列化 {@link DataStatistics}（使用 Flink TypeSerializer）。
 *   <li>序列化/反序列化 {@link AggregatedStatistics}（含 checkpointId + 统计数据）。
 *   <li>序列化/反序列化 {@link DataStatisticsEvent}（OperatorEvent 载荷）。
 * </ul>
 *
 * <p>设计意图：统一封装序列化逻辑，供 {@link DataStatisticsOperator} 和 {@link DataStatisticsCoordinator}
 * 在事件传输时使用。使用 Flink 的 DataOutputSerializer/DataInputDeserializer 做高效序列化。
 *
 * <p>上下游关系：被 {@link DataStatisticsOperator}、{@link DataStatisticsCoordinator} 和 {@link
 * DataStatisticsEvent} 调用。
 */
class DataStatisticsUtil {

  private DataStatisticsUtil() {}

  /**
   * 序列化 {@link DataStatistics} 为字节数组。
   *
   * @param dataStatistics 数据统计
   * @param statisticsSerializer 序列化器
   * @return 序列化后的字节数组
   */
  static <D extends DataStatistics<D, S>, S> byte[] serializeDataStatistics(
      DataStatistics<D, S> dataStatistics,
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer) {
    DataOutputSerializer out = new DataOutputSerializer(64);
    try {
      statisticsSerializer.serialize(dataStatistics, out);
      return out.getCopyOfBuffer();
    } catch (IOException e) {
      throw new IllegalStateException("Fail to serialize data statistics", e);
    }
  }

  @SuppressWarnings("unchecked")
  /**
   * 反序列化字节数组为 {@link DataStatistics}。
   *
   * @param bytes 字节数组
   * @param statisticsSerializer 序列化器
   * @return 反序列化后的数据统计
   */
  static <D extends DataStatistics<D, S>, S> D deserializeDataStatistics(
      byte[] bytes, TypeSerializer<DataStatistics<D, S>> statisticsSerializer) {
    DataInputDeserializer input = new DataInputDeserializer(bytes, 0, bytes.length);
    try {
      return (D) statisticsSerializer.deserialize(input);
    } catch (IOException e) {
      throw new IllegalStateException("Fail to deserialize data statistics", e);
    }
  }

  /**
   * 序列化 {@link AggregatedStatistics} 为字节数组（含 checkpointId + 统计数据）。
   *
   * @param aggregatedStatistics 聚合统计
   * @param statisticsSerializer 序列化器
   * @return 序列化后的字节数组
   * @throws IOException 序列化失败时抛出
   */
  static <D extends DataStatistics<D, S>, S> byte[] serializeAggregatedStatistics(
      AggregatedStatistics<D, S> aggregatedStatistics,
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(bytes);

    DataOutputSerializer outSerializer = new DataOutputSerializer(64);
    out.writeLong(aggregatedStatistics.checkpointId());
    statisticsSerializer.serialize(aggregatedStatistics.dataStatistics(), outSerializer);
    byte[] statisticsBytes = outSerializer.getCopyOfBuffer();
    out.writeInt(statisticsBytes.length);
    out.write(statisticsBytes);
    out.flush();

    return bytes.toByteArray();
  }

  @SuppressWarnings("unchecked")
  static <D extends DataStatistics<D, S>, S>
      AggregatedStatistics<D, S> deserializeAggregatedStatistics(
          byte[] bytes, TypeSerializer<DataStatistics<D, S>> statisticsSerializer)
          throws IOException {
    ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
    ObjectInputStream in = new ObjectInputStream(bytesIn);

    long completedCheckpointId = in.readLong();
    int statisticsBytesLength = in.readInt();
    byte[] statisticsBytes = new byte[statisticsBytesLength];
    in.readFully(statisticsBytes);
    DataInputDeserializer input =
        new DataInputDeserializer(statisticsBytes, 0, statisticsBytesLength);
    DataStatistics<D, S> dataStatistics = statisticsSerializer.deserialize(input);

    return new AggregatedStatistics<>(completedCheckpointId, dataStatistics);
  }
}
