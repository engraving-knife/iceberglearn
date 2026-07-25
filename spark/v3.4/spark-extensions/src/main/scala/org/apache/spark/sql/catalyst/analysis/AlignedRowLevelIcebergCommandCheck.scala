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
import org.apache.spark.sql.catalyst.plans.logical.MergeIntoIcebergTable
import org.apache.spark.sql.catalyst.plans.logical.UpdateIcebergTable
/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：行级 Iceberg 命令对齐校验规则，检查行级命令的赋值与目标列是否对齐一致。
 * <p>设计意图：作为分析后置检查（check），确保行级操作语义正确。
 * <p>上下游关系：由 IcebergSparkSessionExtensions 注册到 Spark 分析器。
 */

object AlignedRowLevelIcebergCommandCheck extends (LogicalPlan => Unit) {
  /** 应用转换。 */

  override def apply(plan: LogicalPlan): Unit = {
    plan foreach {
      case m: MergeIntoIcebergTable if !m.aligned =>
        throw new AnalysisException(s"Could not align Iceberg MERGE INTO: $m")
      case u: UpdateIcebergTable if !u.aligned =>
        throw new AnalysisException(s"Could not align Iceberg UPDATE: $u")
      case _ => // OK
    }
  }
}
