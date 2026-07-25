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
 * Spark 自定义求和指标：总规划耗时（毫秒）。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source.metrics 子包。
 *
 * <p>职责：累加各扫描的规划阶段耗时，供 Spark UI 展示。
 */
public class TotalPlanningDuration extends CustomSumMetric {

  static final String NAME = "totalPlanningDuration";
  /** 返回名称。 */
  @Override
  public String name() {
    return NAME;
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "total planning duration (ms)";
  }
}
