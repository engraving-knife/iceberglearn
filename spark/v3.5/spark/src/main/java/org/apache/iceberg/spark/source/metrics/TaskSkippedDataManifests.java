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
 * Spark 任务级自定义指标：跳过的数据 manifest 数。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source.metrics 子包。
 *
 * <p>职责：从 {@link ScanReport} 提取 skippedDataManifests 计数，包装为 {@link CustomTaskMetric} 上报。
 */
public class TaskSkippedDataManifests implements CustomTaskMetric {
  private final long value;

  private TaskSkippedDataManifests(long value) {
    this.value = value;
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return SkippedDataManifests.NAME;
  }
  /** 执行 value 相关操作。 */
  @Override
  public long value() {
    return value;
  }

  /**
   * 从 ScanReport 构造指标实例。
   *
   * <p>逻辑：取 scanMetrics().skippedDataManifests()，counter 为 null 时记 0。
   *
   * @param scanReport 扫描报告
   * @return TaskSkippedDataManifests 实例
   */
  public static TaskSkippedDataManifests from(ScanReport scanReport) {
    CounterResult counter = scanReport.scanMetrics().skippedDataManifests();
    long value = counter != null ? counter.value() : 0L;
    return new TaskSkippedDataManifests(value);
  }
}
