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

import org.apache.spark.sql.connector.metric.CustomTaskMetric;

/**
 * Spark 任务级自定义指标：单个 task 的分片（split）数。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source.metrics 子包。
 *
 * <p>职责：实现 {@link CustomTaskMetric} 把每个 task 处理的 split 数上报给 Spark。
 */
public class TaskNumSplits implements CustomTaskMetric {
  private final long value;

  /** 构造任务级 split 数指标。 */
  public TaskNumSplits(long value) {
    this.value = value;
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return "numSplits";
  }
  /** 执行 value 相关操作。 */
  @Override
  public long value() {
    return value;
  }
}
