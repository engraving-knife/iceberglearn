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
package org.apache.iceberg.flink.sink;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.MetricGroup;

/**
 * 文件级说明：Iceberg 文件提交算子的 Flink 指标收集器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>记录最近一次 checkpoint 与提交的耗时。
 *   <li>记录自上次成功提交以来经过的秒数。
 *   <li>累加已提交的数据文件/删除文件的数量、记录数、字节数。
 * </ul>
 *
 * <p>设计意图：把提交相关的指标统一注册到 Flink MetricGroup 下， 便于通过 Flink Metrics 系统对外暴露，方便运维监控。
 *
 * <p>上下游关系：上游为 {@code IcebergFilesCommitter}， 下游为 Flink 的 {@link MetricGroup} 与 {@link
 * Counter}/{@link Gauge}。
 */
class IcebergFilesCommitterMetrics {
  private final AtomicLong lastCheckpointDurationMs = new AtomicLong();
  private final AtomicLong lastCommitDurationMs = new AtomicLong();
  private final ElapsedTimeGauge elapsedSecondsSinceLastSuccessfulCommit;
  private final Counter committedDataFilesCount;
  private final Counter committedDataFilesRecordCount;
  private final Counter committedDataFilesByteCount;
  private final Counter committedDeleteFilesCount;
  private final Counter committedDeleteFilesRecordCount;
  private final Counter committedDeleteFilesByteCount;

  /** 构造指标收集器，把所有指标注册到指定 MetricGroup 下，按表名分组。 */
  IcebergFilesCommitterMetrics(MetricGroup metrics, String fullTableName) {
    MetricGroup committerMetrics =
        metrics.addGroup("IcebergFilesCommitter").addGroup("table", fullTableName);
    committerMetrics.gauge("lastCheckpointDurationMs", lastCheckpointDurationMs::get);
    committerMetrics.gauge("lastCommitDurationMs", lastCommitDurationMs::get);
    this.elapsedSecondsSinceLastSuccessfulCommit = new ElapsedTimeGauge(TimeUnit.SECONDS);
    committerMetrics.gauge(
        "elapsedSecondsSinceLastSuccessfulCommit", elapsedSecondsSinceLastSuccessfulCommit);
    this.committedDataFilesCount = committerMetrics.counter("committedDataFilesCount");
    this.committedDataFilesRecordCount = committerMetrics.counter("committedDataFilesRecordCount");
    this.committedDataFilesByteCount = committerMetrics.counter("committedDataFilesByteCount");
    this.committedDeleteFilesCount = committerMetrics.counter("committedDeleteFilesCount");
    this.committedDeleteFilesRecordCount =
        committerMetrics.counter("committedDeleteFilesRecordCount");
    this.committedDeleteFilesByteCount = committerMetrics.counter("committedDeleteFilesByteCount");
  }

  /** 更新最近一次 checkpoint 耗时（毫秒）。 */
  void checkpointDuration(long checkpointDurationMs) {
    lastCheckpointDurationMs.set(checkpointDurationMs);
  }

  /** 更新最近一次提交耗时（毫秒）。 */
  void commitDuration(long commitDurationMs) {
    lastCommitDurationMs.set(commitDurationMs);
  }

  /** 提交成功后调用，按 {@link CommitSummary} 累加各计数器并刷新提交时间。 */
  void updateCommitSummary(CommitSummary stats) {
    elapsedSecondsSinceLastSuccessfulCommit.refreshLastRecordedTime();
    committedDataFilesCount.inc(stats.dataFilesCount());
    committedDataFilesRecordCount.inc(stats.dataFilesRecordCount());
    committedDataFilesByteCount.inc(stats.dataFilesByteCount());
    committedDeleteFilesCount.inc(stats.deleteFilesCount());
    committedDeleteFilesRecordCount.inc(stats.deleteFilesRecordCount());
    committedDeleteFilesByteCount.inc(stats.deleteFilesByteCount());
  }

  /**
   * 测量自上次记录时间起经过时长的 Gauge。
   *
   * <p>逻辑：getValue 时根据 {@link #refreshLastRecordedTime()} 记录的时间点 计算当前与之的差值，并按指定单位返回。
   */
  private static class ElapsedTimeGauge implements Gauge<Long> {
    private final TimeUnit reportUnit;
    private volatile long lastRecordedTimeNano;

    ElapsedTimeGauge(TimeUnit timeUnit) {
      this.reportUnit = timeUnit;
      this.lastRecordedTimeNano = System.nanoTime();
    }

    /** 刷新最近一次记录时间，通常在成功提交时调用。 */
    void refreshLastRecordedTime() {
      this.lastRecordedTimeNano = System.nanoTime();
    }

    @Override
    public Long getValue() {
      return reportUnit.convert(System.nanoTime() - lastRecordedTimeNano, TimeUnit.NANOSECONDS);
    }
  }
}
