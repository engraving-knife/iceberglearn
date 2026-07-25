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
 * 数据统计或记录的复合对象，支持在算子中交替传输统计与数据。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：封装统计快照或 RowData，实现 Serializable。
 *
 * <p>设计意图：标记联合类型；被 DataStatisticsOperator 使用。
 */
class DataStatisticsOrRecord<D extends DataStatistics<D, S>, S> implements Serializable {

  private static final long serialVersionUID = 1L;

  private DataStatistics<D, S> statistics;
  private RowData record;

  private DataStatisticsOrRecord(DataStatistics<D, S> statistics, RowData record) {
    Preconditions.checkArgument(
        record != null ^ statistics != null,
        "A DataStatisticsOrRecord contain either statistics or record, not neither or both");
    this.statistics = statistics;
    this.record = record;
  }

  static <D extends DataStatistics<D, S>, S> DataStatisticsOrRecord<D, S> fromRecord(
      RowData record) {
    return new DataStatisticsOrRecord<>(null, record);
  }

  static <D extends DataStatistics<D, S>, S> DataStatisticsOrRecord<D, S> fromDataStatistics(
      DataStatistics<D, S> statistics) {
    return new DataStatisticsOrRecord<>(statistics, null);
  }

  static <D extends DataStatistics<D, S>, S> DataStatisticsOrRecord<D, S> reuseRecord(
      DataStatisticsOrRecord<D, S> reuse, TypeSerializer<RowData> recordSerializer) {
    if (reuse.hasRecord()) {
      return reuse;
    } else {
      // not reusable
      return DataStatisticsOrRecord.fromRecord(recordSerializer.createInstance());
    }
  }

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

  boolean hasDataStatistics() {
    return statistics != null;
  }

  boolean hasRecord() {
    return record != null;
  }

  DataStatistics<D, S> dataStatistics() {
    return statistics;
  }

  void dataStatistics(DataStatistics<D, S> newStatistics) {
    this.statistics = newStatistics;
  }

  RowData record() {
    return record;
  }

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
