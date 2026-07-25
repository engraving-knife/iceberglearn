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
 * Spark 自定义求和指标：扫描的数据 manifest 数量。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source.metrics 子包。
 *
 * <p>职责：作为 {@link CustomSumMetric} 累加各 task 扫描的数据 manifest 数，供 Spark UI 展示。
 *
 * <p>设计意图：继承 Spark 的 CustomSumMetric 自动获得跨 task 聚合能力，只需声明 name 与 description。
 */
public class ScannedDataManifests extends CustomSumMetric {

  static final String NAME = "scannedDataManifests";
  /** 返回名称。 */
  @Override
  public String name() {
    return NAME;
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "number of scanned data manifests";
  }
}
