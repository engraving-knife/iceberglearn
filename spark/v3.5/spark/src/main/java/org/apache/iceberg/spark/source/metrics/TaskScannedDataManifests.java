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
package org.apache.iceberg.spark.source.metrics;

import org.apache.iceberg.metrics.CounterResult;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.spark.sql.connector.metric.CustomTaskMetric;

/**
 * 任务级自定义指标：单个任务扫描的数据清单（data manifest）数量。
 *
 * <p>所属模块：iceberg-spark（source/metrics 子包，向 Spark 暴露 Iceberg 读取指标）。
 *
 * <p>职责：实现 {@link CustomTaskMetric}，从 {@link ScanReport} 提取已扫描数据清单计数， 供 Spark 聚合为作业级指标。
 *
 * <p>设计意图：作为任务级指标与 {@link ScannedDataManifests} 聚合指标配合使用， 通过静态工厂 {@link #from} 从扫描报告构造。
 *
 * <p>上下游关系：由读取任务从 ScanReport 构造并上报，Spark 聚合后展示。
 */
public class TaskScannedDataManifests implements CustomTaskMetric {
  private final long value;

  private TaskScannedDataManifests(long value) {
    this.value = value;
  }

  /** 返回指标名称。 */
  @Override
  public String name() {
    return ScannedDataManifests.NAME;
  }

  /** 返回指标值。 */
  @Override
  public long value() {
    return value;
  }

  /**
   * 从扫描报告构造任务级已扫描数据清单指标。
   *
   * @param scanReport 扫描报告
   * @return 含计数的任务级指标
   */
  public static TaskScannedDataManifests from(ScanReport scanReport) {
    CounterResult counter = scanReport.scanMetrics().scannedDataManifests();
    long value = counter != null ? counter.value() : 0L;
    return new TaskScannedDataManifests(value);
  }
}
