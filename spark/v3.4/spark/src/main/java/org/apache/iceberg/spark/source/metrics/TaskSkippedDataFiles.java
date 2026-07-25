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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：任务级已跳过数据文件数指标，在 Executor 端累计并上报。
 *
 * <p>设计意图：实现 Spark CustomTaskMetric，与 SkippedDataFiles 配对聚合。
 *
 * <p>上下游关系：由 BaseReader 在任务端上报。
 */
public class TaskSkippedDataFiles implements CustomTaskMetric {
  private final long value;

  private TaskSkippedDataFiles(long value) {
    this.value = value;
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return SkippedDataFiles.NAME;
  }
  /** 执行 value 相关操作。 */
  @Override
  public long value() {
    return value;
  }
  /** 工厂构造方法。 */
  public static TaskSkippedDataFiles from(ScanReport scanReport) {
    CounterResult counter = scanReport.scanMetrics().skippedDataFiles();
    long value = counter != null ? counter.value() : 0L;
    return new TaskSkippedDataFiles(value);
  }
}
