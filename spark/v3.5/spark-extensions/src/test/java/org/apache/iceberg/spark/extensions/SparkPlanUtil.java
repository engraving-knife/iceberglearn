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
package org.apache.iceberg.spark.extensions;

import static scala.collection.JavaConverters.seqAsJavaListConverter;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.spark.sql.execution.CommandResultExec;
import org.apache.spark.sql.execution.SparkPlan;
import org.apache.spark.sql.execution.adaptive.AdaptiveSparkPlanHelper;
import org.apache.spark.sql.execution.datasources.v2.BatchScanExec;
import scala.collection.Seq;

/**
 * 文件级说明：测试 SparkPlanUtil 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 Spark计划工具 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class SparkPlanUtil {

  private static final AdaptiveSparkPlanHelper SPARK_HELPER = new AdaptiveSparkPlanHelper() {};

  /** Spark计划工具。 */
  private SparkPlanUtil() {}

  /** 辅助方法：collectLeaves。 */
  public static List<SparkPlan> collectLeaves(SparkPlan plan) {
    return toJavaList(SPARK_HELPER.collectLeaves(actualPlan(plan)));
  }

  /** collect批扫描。 */
  public static List<SparkPlan> collectBatchScans(SparkPlan plan) {
    List<SparkPlan> leaves = collectLeaves(plan);
    return leaves.stream()
        .filter(scan -> scan instanceof BatchScanExec)
        .collect(Collectors.toList());
  }

  /** 实际计划。 */
  private static SparkPlan actualPlan(SparkPlan plan) {
    if (plan instanceof CommandResultExec) {
      return ((CommandResultExec) plan).commandPhysicalPlan();
    } else {
      return plan;
    }
  }

  /** 到Java列表。 */
  private static <T> List<T> toJavaList(Seq<T> seq) {
    return seqAsJavaListConverter(seq).asJava();
  }
}
