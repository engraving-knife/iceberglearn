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

package org.apache.spark.sql.catalyst.analysis

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.UnresolvedMergeIntoIcebergTable

/**
 * Spark Catalyst 分析阶段的规则或检查，实现 MERGE INTO 行级操作。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：对象 MergeIntoIcebergTableResolutionCheck。
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 * <p>上下游：由 Spark SparkSessionExtensions 注册，作用于 Catalyst 计划。
 */
object MergeIntoIcebergTableResolutionCheck extends (LogicalPlan => Unit) {

  /** 执行核心逻辑。 */
  override def apply(plan: LogicalPlan): Unit = {
    plan foreach {
      case m: UnresolvedMergeIntoIcebergTable =>
        throw new AnalysisException(s"Could not resolve Iceberg MERGE INTO statement: $m")
      case _ => // OK
    }
  }
}
