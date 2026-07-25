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
 * 自定义 Spark 求和指标：统计扫描时跳过的数据清单（data manifest）数量。
 *
 * <p>所属模块：iceberg-spark（source/metrics 子包，向 Spark 暴露 Iceberg 读取指标）。
 *
 * <p>职责：基于 Spark {@link CustomSumMetric} 求和各任务跳过的数据清单数， 用于评估清单裁剪（manifest pruning）的效果。
 *
 * <p>上下游关系：由 Iceberg 读取任务上报，Spark 聚合后展示。
 */
public class SkippedDataManifests extends CustomSumMetric {

  static final String NAME = "skippedDataManifests";

  /** 返回指标名称。 */
  @Override
  public String name() {
    return NAME;
  }

  /** 返回指标描述。 */
  @Override
  public String description() {
    return "number of skipped data manifests";
  }
}
