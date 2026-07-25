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

import java.text.NumberFormat;
import org.apache.spark.sql.connector.metric.CustomMetric;

/**
 * 自定义 Spark 指标：统计读取的文件分片（split）总数。
 *
 * <p>所属模块：iceberg-spark（source/metrics 子包，向 Spark 暴露 Iceberg 读取指标）。
 *
 * <p>职责：聚合各任务读取的文件分片数，在 Spark UI 中展示总分片数。
 *
 * <p>设计意图：实现 {@link CustomMetric}，把各任务的分片计数求和并以本地化整数格式输出。
 *
 * <p>上下游关系：由 Iceberg 读取任务上报分片计数，Spark 聚合后展示。
 */
public class NumSplits implements CustomMetric {

  /** 返回指标名称。 */
  @Override
  public String name() {
    return "numSplits";
  }

  /** 返回指标描述。 */
  @Override
  public String description() {
    return "number of file splits read";
  }

  /**
   * 聚合各任务的分片计数为总和并以本地化整数格式返回。
   *
   * @param taskMetrics 各任务上报的分片数数组
   * @return 总分片数的字符串表示
   */
  @Override
  public String aggregateTaskMetrics(long[] taskMetrics) {
    long sum = initialValue;
    for (long taskMetric : taskMetrics) {
      sum += taskMetric;
    }

    return NumberFormat.getIntegerInstance().format(sum);
  }
}
