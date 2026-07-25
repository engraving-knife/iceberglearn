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
 * 自定义指标：已应用的行删除数。
 *
 * <p>所属模块：iceberg-spark（source/metrics 子包）。实现 Spark {@link CustomMetric}，统计 读取时应用的删除行总数，并在 SQL UI
 * 中展示。
 *
 * <p>上下游关系：由 Spark 读取器在任务级上报，Spark 聚合后展示。
 */
public class NumDeletes implements CustomMetric {

  public static final String DISPLAY_STRING = "number of row deletes applied";

  /** 返回指标名 numDeletes。 */
  @Override
  public String name() {
    return "numDeletes";
  }

  /** 返回指标描述。 */
  @Override
  public String description() {
    return DISPLAY_STRING;
  }

  /** 聚合各任务的删除数并格式化为整数字符串。 */
  @Override
  public String aggregateTaskMetrics(long[] taskMetrics) {
    long sum = initialValue;
    for (long taskMetric : taskMetrics) {
      sum += taskMetric;
    }

    return NumberFormat.getIntegerInstance().format(sum);
  }
}
