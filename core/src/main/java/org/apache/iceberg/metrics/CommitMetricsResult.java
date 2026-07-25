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
package org.apache.iceberg.metrics;

import java.util.Map;
import javax.annotation.Nullable;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.immutables.value.Value;

/**
 * {@link CommitMetrics} 的可序列化结果视图，承载提交产生的各项最终度量结果。
 *
 * <p>所属模块：iceberg-core，度量包内用于"提交后汇总"的只读数据结构。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>汇总一次提交所产生的数据文件、删除文件、记录数、文件大小、删除条目等计数结果， 以及提交耗时与尝试次数。
 *   <li>提供从运行时 {@link CommitMetrics} 与快照摘要（{@link org.apache.iceberg.SnapshotSummary}）
 *       装配结果的工厂方法，统一指标来源。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用 Immutables 生成不可变接口实现，便于序列化与跨进程传输。
 *   <li>各字段允许为 null（{@code @Nullable}），以兼容不同提交场景下未必全部存在的指标， 避免强制填充零值造成歧义。
 *   <li>度量名称常量集中声明，保证序列化键名与 {@link CommitMetricsResultParser} 一致。
 * </ul>
 *
 * <p>上下游关系：由 {@link #from(CommitMetrics, Map)} 从提交度量与快照摘要构建； 被 {@link CommitReport} 持有并经 {@link
 * CommitMetricsResultParser} 序列化为 JSON 上报。
 */
@Value.Immutable
public interface CommitMetricsResult {
  /** 新增数据文件数量的指标键名。 */
  String ADDED_DATA_FILES = "added-data-files";
  /** 删除数据文件数量的指标键名。 */
  String REMOVED_DATA_FILES = "removed-data-files";
  /** 数据文件总数的指标键名。 */
  String TOTAL_DATA_FILES = "total-data-files";
  /** 新增删除文件数量的指标键名。 */
  String ADDED_DELETE_FILES = "added-delete-files";
  /** 新增等值删除文件数量的指标键名。 */
  String ADDED_EQ_DELETE_FILES = "added-equality-delete-files";
  /** 新增位置删除文件数量的指标键名。 */
  String ADDED_POS_DELETE_FILES = "added-positional-delete-files";
  /** 移除位置删除文件数量的指标键名。 */
  String REMOVED_POS_DELETE_FILES = "removed-positional-delete-files";
  /** 移除等值删除文件数量的指标键名。 */
  String REMOVED_EQ_DELETE_FILES = "removed-equality-delete-files";
  /** 移除删除文件数量的指标键名。 */
  String REMOVED_DELETE_FILES = "removed-delete-files";
  /** 删除文件总数的指标键名。 */
  String TOTAL_DELETE_FILES = "total-delete-files";
  /** 新增记录数的指标键名。 */
  String ADDED_RECORDS = "added-records";
  /** 删除记录数的指标键名。 */
  String REMOVED_RECORDS = "removed-records";
  /** 记录总数的指标键名。 */
  String TOTAL_RECORDS = "total-records";
  /** 新增文件大小（字节）的指标键名。 */
  String ADDED_FILE_SIZE_BYTES = "added-files-size-bytes";
  /** 移除文件大小（字节）的指标键名。 */
  String REMOVED_FILE_SIZE_BYTES = "removed-files-size-bytes";
  /** 文件总大小（字节）的指标键名。 */
  String TOTAL_FILE_SIZE_BYTES = "total-files-size-bytes";
  /** 新增位置删除条目数的指标键名。 */
  String ADDED_POS_DELETES = "added-positional-deletes";
  /** 移除位置删除条目数的指标键名。 */
  String REMOVED_POS_DELETES = "removed-positional-deletes";
  /** 位置删除条目总数的指标键名。 */
  String TOTAL_POS_DELETES = "total-positional-deletes";
  /** 新增等值删除条目数的指标键名。 */
  String ADDED_EQ_DELETES = "added-equality-deletes";
  /** 移除等值删除条目数的指标键名。 */
  String REMOVED_EQ_DELETES = "removed-equality-deletes";
  /** 等值删除条目总数的指标键名。 */
  String TOTAL_EQ_DELETES = "total-equality-deletes";

  /** 提交总耗时结果，可能为 null（无记录时）。 */
  @Nullable
  TimerResult totalDuration();

  /** 提交尝试次数结果，可能为 null（无记录时）。 */
  @Nullable
  CounterResult attempts();

  /** 新增数据文件计数结果，可能为 null。 */
  @Nullable
  CounterResult addedDataFiles();

  /** 删除数据文件计数结果，可能为 null。 */
  @Nullable
  CounterResult removedDataFiles();

  /** 数据文件总数计数结果，可能为 null。 */
  @Nullable
  CounterResult totalDataFiles();

  /** 新增删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult addedDeleteFiles();

  /** 新增等值删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult addedEqualityDeleteFiles();

  /** 新增位置删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult addedPositionalDeleteFiles();

  /** 移除删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult removedDeleteFiles();

  /** 移除等值删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult removedEqualityDeleteFiles();

  /** 移除位置删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult removedPositionalDeleteFiles();

  /** 删除文件总数计数结果，可能为 null。 */
  @Nullable
  CounterResult totalDeleteFiles();

  /** 新增记录数计数结果，可能为 null。 */
  @Nullable
  CounterResult addedRecords();

  /** 删除记录数计数结果，可能为 null。 */
  @Nullable
  CounterResult removedRecords();

  /** 记录总数计数结果，可能为 null。 */
  @Nullable
  CounterResult totalRecords();

  /** 新增文件大小（字节）计数结果，可能为 null。 */
  @Nullable
  CounterResult addedFilesSizeInBytes();

  /** 移除文件大小（字节）计数结果，可能为 null。 */
  @Nullable
  CounterResult removedFilesSizeInBytes();

  /** 文件总大小（字节）计数结果，可能为 null。 */
  @Nullable
  CounterResult totalFilesSizeInBytes();

  /** 新增位置删除条目计数结果，可能为 null。 */
  @Nullable
  CounterResult addedPositionalDeletes();

  /** 移除位置删除条目计数结果，可能为 null。 */
  @Nullable
  CounterResult removedPositionalDeletes();

  /** 位置删除条目总数计数结果，可能为 null。 */
  @Nullable
  CounterResult totalPositionalDeletes();

  /** 新增等值删除条目计数结果，可能为 null。 */
  @Nullable
  CounterResult addedEqualityDeletes();

  /** 移除等值删除条目计数结果，可能为 null。 */
  @Nullable
  CounterResult removedEqualityDeletes();

  /** 等值删除条目总数计数结果，可能为 null。 */
  @Nullable
  CounterResult totalEqualityDeletes();

  /**
   * 从运行时提交度量与快照摘要装配出可序列化的 {@link CommitMetricsResult}。
   *
   * <p>逻辑：耗时与尝试次数取自 {@link CommitMetrics}，其余文件/记录/删除条目相关计数 则从快照摘要（{@link
   * SnapshotSummary}）按对应属性键解析；最终由 ImmutableBuilder 一次性构建。
   *
   * @param commitMetrics 提交运行时度量，不能为 null
   * @param snapshotSummary 快照摘要键值对，不能为 null
   * @return 装配完成的提交度量结果
   */
  static CommitMetricsResult from(
      CommitMetrics commitMetrics, Map<String, String> snapshotSummary) {
    Preconditions.checkArgument(null != commitMetrics, "Invalid commit metrics: null");
    Preconditions.checkArgument(null != snapshotSummary, "Invalid snapshot summary: null");
    return ImmutableCommitMetricsResult.builder()
        .attempts(CounterResult.fromCounter(commitMetrics.attempts()))
        .totalDuration(TimerResult.fromTimer(commitMetrics.totalDuration()))
        .addedDataFiles(counterFrom(snapshotSummary, SnapshotSummary.ADDED_FILES_PROP))
        .removedDataFiles(counterFrom(snapshotSummary, SnapshotSummary.DELETED_FILES_PROP))
        .totalDataFiles(counterFrom(snapshotSummary, SnapshotSummary.TOTAL_DATA_FILES_PROP))
        .addedDeleteFiles(counterFrom(snapshotSummary, SnapshotSummary.ADDED_DELETE_FILES_PROP))
        .addedPositionalDeleteFiles(
            counterFrom(snapshotSummary, SnapshotSummary.ADD_POS_DELETE_FILES_PROP))
        .addedEqualityDeleteFiles(
            counterFrom(snapshotSummary, SnapshotSummary.ADD_EQ_DELETE_FILES_PROP))
        .removedDeleteFiles(counterFrom(snapshotSummary, SnapshotSummary.REMOVED_DELETE_FILES_PROP))
        .removedEqualityDeleteFiles(
            counterFrom(snapshotSummary, SnapshotSummary.REMOVED_EQ_DELETE_FILES_PROP))
        .removedPositionalDeleteFiles(
            counterFrom(snapshotSummary, SnapshotSummary.REMOVED_POS_DELETE_FILES_PROP))
        .totalDeleteFiles(counterFrom(snapshotSummary, SnapshotSummary.TOTAL_DELETE_FILES_PROP))
        .addedRecords(counterFrom(snapshotSummary, SnapshotSummary.ADDED_RECORDS_PROP))
        .removedRecords(counterFrom(snapshotSummary, SnapshotSummary.DELETED_RECORDS_PROP))
        .totalRecords(counterFrom(snapshotSummary, SnapshotSummary.TOTAL_RECORDS_PROP))
        .addedFilesSizeInBytes(
            counterFrom(snapshotSummary, SnapshotSummary.ADDED_FILE_SIZE_PROP, Unit.BYTES))
        .removedFilesSizeInBytes(
            counterFrom(snapshotSummary, SnapshotSummary.REMOVED_FILE_SIZE_PROP, Unit.BYTES))
        .totalFilesSizeInBytes(
            counterFrom(snapshotSummary, SnapshotSummary.TOTAL_FILE_SIZE_PROP, Unit.BYTES))
        .addedPositionalDeletes(
            counterFrom(snapshotSummary, SnapshotSummary.ADDED_POS_DELETES_PROP))
        .removedPositionalDeletes(
            counterFrom(snapshotSummary, SnapshotSummary.REMOVED_POS_DELETES_PROP))
        .totalPositionalDeletes(
            counterFrom(snapshotSummary, SnapshotSummary.TOTAL_POS_DELETES_PROP))
        .addedEqualityDeletes(counterFrom(snapshotSummary, SnapshotSummary.ADDED_EQ_DELETES_PROP))
        .removedEqualityDeletes(
            counterFrom(snapshotSummary, SnapshotSummary.REMOVED_EQ_DELETES_PROP))
        .totalEqualityDeletes(counterFrom(snapshotSummary, SnapshotSummary.TOTAL_EQ_DELETES_PROP))
        .build();
  }

  /**
   * 以默认单位 COUNT 从快照摘要中解析指定指标为 {@link CounterResult}。
   *
   * @param snapshotSummary 快照摘要键值对
   * @param metricName 指标对应的属性键
   * @return 计数结果；若摘要中无该键或解析失败则返回 null
   */
  static CounterResult counterFrom(Map<String, String> snapshotSummary, String metricName) {
    return counterFrom(snapshotSummary, metricName, Unit.COUNT);
  }

  /**
   * 以指定单位从快照摘要中解析指定指标为 {@link CounterResult}。
   *
   * <p>逻辑：先判断摘要是否包含该键，不包含直接返回 null；否则将值解析为 long 并构造结果， 解析失败（NumberFormatException）同样返回
   * null，避免因个别非法值导致整体提交度量失败。
   *
   * @param snapshotSummary 快照摘要键值对
   * @param metricName 指标对应的属性键
   * @param unit 计数单位
   * @return 计数结果；若不存在或解析失败则返回 null
   */
  static CounterResult counterFrom(
      Map<String, String> snapshotSummary, String metricName, Unit unit) {
    if (!snapshotSummary.containsKey(metricName)) {
      return null;
    }

    try {
      return CounterResult.of(unit, Long.parseLong(snapshotSummary.get(metricName)));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
