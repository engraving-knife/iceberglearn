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

import com.codahale.metrics.SlidingWindowReservoir;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.MetricGroup;
import org.apache.iceberg.io.WriteResult;

/**
 * Iceberg 流式写入器的指标收集器。
 *
 * <p>所属模块：iceberg-flink（sink 侧），将写入相关指标注册到 Flink MetricGroup。
 *
 * <p>职责：统计已刷写的数据/删除/引用数据文件数量、最近一次刷写耗时， 以及数据/删除文件大小的直方图分布。
 *
 * <p>设计意图：直方图使用 1024 容量的滑动窗口 reservoir（约 8KB），在内存占用与百分位精度间取得平衡； 文件大小直方图在刷写结果产生时即更新，避免在
 * CommitSummary 中额外维护大小列表。
 *
 * <p>上下游关系：被 {@link IcebergStreamWriter} 调用以上报指标。
 */
class IcebergStreamWriterMetrics {
  // 1,024 reservoir size should cost about 8KB, which is quite small.
  // It should also produce good accuracy for histogram distribution (like percentiles).
  private static final int HISTOGRAM_RESERVOIR_SIZE = 1024;

  private final Counter flushedDataFiles;
  private final Counter flushedDeleteFiles;
  private final Counter flushedReferencedDataFiles;
  private final AtomicLong lastFlushDurationMs;
  private final Histogram dataFilesSizeHistogram;
  private final Histogram deleteFilesSizeHistogram;

  /**
   * 构造指标收集器，在 IcebergStreamWriter/table 分组下注册各 Counter/Gauge/Histogram。
   *
   * @param metrics Flink 指标组
   * @param fullTableName 完整表名
   */
  IcebergStreamWriterMetrics(MetricGroup metrics, String fullTableName) {
    MetricGroup writerMetrics =
        metrics.addGroup("IcebergStreamWriter").addGroup("table", fullTableName);
    this.flushedDataFiles = writerMetrics.counter("flushedDataFiles");
    this.flushedDeleteFiles = writerMetrics.counter("flushedDeleteFiles");
    this.flushedReferencedDataFiles = writerMetrics.counter("flushedReferencedDataFiles");
    this.lastFlushDurationMs = new AtomicLong();
    writerMetrics.gauge("lastFlushDurationMs", lastFlushDurationMs::get);

    com.codahale.metrics.Histogram dropwizardDataFilesSizeHistogram =
        new com.codahale.metrics.Histogram(new SlidingWindowReservoir(HISTOGRAM_RESERVOIR_SIZE));
    this.dataFilesSizeHistogram =
        writerMetrics.histogram(
            "dataFilesSizeHistogram",
            new DropwizardHistogramWrapper(dropwizardDataFilesSizeHistogram));
    com.codahale.metrics.Histogram dropwizardDeleteFilesSizeHistogram =
        new com.codahale.metrics.Histogram(new SlidingWindowReservoir(HISTOGRAM_RESERVOIR_SIZE));
    this.deleteFilesSizeHistogram =
        writerMetrics.histogram(
            "deleteFilesSizeHistogram",
            new DropwizardHistogramWrapper(dropwizardDeleteFilesSizeHistogram));
  }

  /**
   * 根据刷写结果更新指标。
   *
   * <p>逻辑：累加数据/删除/引用数据文件计数，并将各文件大小更新到对应直方图。
   *
   * @param result 刷写结果
   */
  void updateFlushResult(WriteResult result) {
    flushedDataFiles.inc(result.dataFiles().length);
    flushedDeleteFiles.inc(result.deleteFiles().length);
    flushedReferencedDataFiles.inc(result.referencedDataFiles().length);

    // For file size distribution histogram, we don't have to update them after successful commits.
    // This should works equally well and we avoided the overhead of tracking the list of file sizes
    // in the {@link CommitSummary}, which currently stores simple stats for counters and gauges
    // metrics.
    Arrays.stream(result.dataFiles())
        .forEach(
            dataFile -> {
              dataFilesSizeHistogram.update(dataFile.fileSizeInBytes());
            });
    Arrays.stream(result.deleteFiles())
        .forEach(
            deleteFile -> {
              deleteFilesSizeHistogram.update(deleteFile.fileSizeInBytes());
            });
  }

  /** 设置最近一次刷写耗时（毫秒）。 */
  void flushDuration(long flushDurationMs) {
    lastFlushDurationMs.set(flushDurationMs);
  }
}
