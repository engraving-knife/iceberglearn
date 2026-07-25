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

import org.apache.spark.sql.connector.metric.CustomSumMetric;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：已跳过数据文件数指标，定义指标名与聚合方式。
 *
 * <p>设计意图：实现 Spark CustomMetric，统计被谓词/指标跳过的文件数量。
 *
 * <p>上下游关系：由 BaseReader 上报；由 Spark UI 展示。
 */
public class SkippedDataFiles extends CustomSumMetric {

  static final String NAME = "skippedDataFiles";
  /** 返回名称。 */
  @Override
  public String name() {
    return NAME;
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "number of skipped data files";
  }
}
