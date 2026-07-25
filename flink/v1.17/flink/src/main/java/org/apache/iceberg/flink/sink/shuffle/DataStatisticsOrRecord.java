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
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 数据统计与记录的联合包装类，用于将全局聚合统计从统计算子下发到自定义分区器。
 *
 * <p>所属模块：iceberg-flink（sink shuffle 侧）。
 *
 * <p>职责：封装"全局统计"或"单条记录"二选一的数据，由 {@link DataStatisticsOperator} 发往分区器； 分区器收到统计后据此决定后续记录路由到哪个 writer
 * 子任务。
 *
 * <p>设计意图：统计与记录复用同一下行通道，下游需配合 filter/mapper 剥离统计权重并还原原始记录类型。 构造时通过异或校验保证两者只能有其一。
 *
 * <p>上下游关系：上游为 DataStatisticsOperator，下游为自定义分区器（及随后的 filter/mapper）。
 */
class DataStatisticsOrRecord<D extends DataStatistics<D, S>, S> implements Serializable {

  private static final long serialVersionUID = 1L;

  private DataStatistics<D, S> statistics;
  private RowData record;

  /** 私有构造，统计与记录只能有其一（异或校验）。 */
  private DataStatisticsOrRecord(DataStatistics<D, S> statistics, RowData record) {
    Preconditions.checkArgument(
        record != null ^ statistics != null, "DataStatistics or record, not neither or both");
    this.statistics = statistics;
    this.record = record;
  }

  /** 创建仅含一条记录的实例。 */
  static <D extends DataStatistics<D, S>, S> DataStatisticsOrRecord<D, S> fromRecord(
      RowData record) {
    return new DataStatisticsOrRecord<>(null, record);
  }

  /** 创建仅含全局统计的实例。 */
  static <D extends DataStatistics<D, S>, S> DataStatisticsOrRecord<D, S> fromDataStatistics(
      DataStatistics<D, S> statistics) {
    return new DataStatisticsOrRecord<>(statistics, null);
  }

  /** 尽量复用已有实例承载记录；不可复用时通过序列化器创建空记录实例。 */
  static <D extends DataStatistics<D, S>, S> DataStatisticsOrRecord<D, S> reuseRecord(
      DataStatisticsOrRecord<D, S> reuse, TypeSerializer<RowData> recordSerializer) {
    if (reuse.hasRecord()) {
      return reuse;
    } else {
      // not reusable
      return DataStatisticsOrRecord.fromRecord(recordSerializer.createInstance());
    }
  }

  /** 尽量复用已有实例承载统计；不可复用时通过序列化器创建空统计实例。 */
  static <D extends DataStatistics<D, S>, S> DataStatisticsOrRecord<D, S> reuseStatistics(
      DataStatisticsOrRecord<D, S> reuse,
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer) {
    if (reuse.hasDataStatistics()) {
      return reuse;
    } else {
      // not reusable
      return DataStatisticsOrRecord.fromDataStatistics(statisticsSerializer.createInstance());
    }
  }

  /** 是否包含全局统计。 */
  boolean hasDataStatistics() {
    return statistics != null;
  }

  /** 是否包含记录。 */
  boolean hasRecord() {
    return record != null;
  }

  /** 返回统计对象。 */
  DataStatistics<D, S> dataStatistics() {
    return statistics;
  }

  /** 设置统计对象。 */
  void dataStatistics(DataStatistics<D, S> newStatistics) {
    this.statistics = newStatistics;
  }

  /** 返回记录。 */
  RowData record() {
    return record;
  }

  /** 设置记录。 */
  void record(RowData newRecord) {
    this.record = newRecord;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("statistics", statistics)
        .add("record", record)
        .toString();
  }
}
