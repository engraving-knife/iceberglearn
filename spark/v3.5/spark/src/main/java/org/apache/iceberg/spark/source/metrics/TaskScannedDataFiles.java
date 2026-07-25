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
 * 任务级自定义指标：扫描的数据文件数。
 *
 * <p>所属模块：iceberg-spark（source/metrics 子包）。实现 Spark {@link CustomTaskMetric}，
 * 由扫描报告构建，用于在任务级别上报扫描的数据文件计数。
 *
 * <p>上下游关系：由读取任务从 {@link ScanReport} 构造并上报，对应聚合指标为 ScannedDataFiles。
 */
public class TaskScannedDataFiles implements CustomTaskMetric {
  private final long value;

  /** 以计数值构造。 */
  private TaskScannedDataFiles(long value) {
    this.value = value;
  }

  /** 返回指标名（ScannedDataFiles.NAME）。 */
  @Override
  public String name() {
    return ScannedDataFiles.NAME;
  }

  /** 返回指标值。 */
  @Override
  public long value() {
    return value;
  }

  /** 由扫描报告构建：取 resultDataFiles 计数，缺失则为 0。 */
  public static TaskScannedDataFiles from(ScanReport scanReport) {
    CounterResult counter = scanReport.scanMetrics().resultDataFiles();
    long value = counter != null ? counter.value() : 0L;
    return new TaskScannedDataFiles(value);
  }
}
