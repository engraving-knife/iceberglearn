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
 * 自定义指标：跳过的数据文件数。
 *
 * <p>所属模块：iceberg-spark（source/metrics 子包）。继承 {@link CustomSumMetric}，统计读取时 被过滤掉的数据文件数，求和聚合。
 *
 * <p>上下游关系：由 Spark 读取器上报，Spark 自动按求和聚合展示。
 */
public class SkippedDataFiles extends CustomSumMetric {

  static final String NAME = "skippedDataFiles";

  /** 返回指标名 skippedDataFiles。 */
  @Override
  public String name() {
    return NAME;
  }

  /** 返回指标描述。 */
  @Override
  public String description() {
    return "number of skipped data files";
  }
}
