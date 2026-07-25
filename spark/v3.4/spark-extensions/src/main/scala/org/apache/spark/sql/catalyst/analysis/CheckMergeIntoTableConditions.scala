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
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.SubqueryExpression
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression
import org.apache.spark.sql.catalyst.plans.logical.DeleteAction
import org.apache.spark.sql.catalyst.plans.logical.InsertAction
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.MergeIntoIcebergTable
import org.apache.spark.sql.catalyst.plans.logical.UpdateAction
import org.apache.spark.sql.catalyst.rules.Rule

/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：MERGE INTO 条件校验规则，检查 MERGE 语句的 WHEN 子句条件是否合法。
 * <p>设计意图：作为分析后置检查，确保 MERGE 各分支条件无冲突且语义正确。
 * <p>上下游关系：由 IcebergSparkSessionExtensions 注册。
 */
object CheckMergeIntoTableConditions extends Rule[LogicalPlan] {
  /** 应用转换。 */

  override def apply(plan: LogicalPlan): LogicalPlan = plan resolveOperators {
    case m: MergeIntoIcebergTable if m.resolved =>
      checkMergeIntoCondition("SEARCH", m.mergeCondition)

      val actions = m.matchedActions ++ m.notMatchedActions
      actions.foreach {
        case DeleteAction(Some(cond)) => checkMergeIntoCondition("DELETE", cond)
        case UpdateAction(Some(cond), _) => checkMergeIntoCondition("UPDATE", cond)
        case InsertAction(Some(cond), _) => checkMergeIntoCondition("INSERT", cond)
        case _ => // OK
      }

      m
  }
  /** 执行 checkMergeIntoCondition 相关操作。 */

  private def checkMergeIntoCondition(condName: String, cond: Expression): Unit = {
    if (!cond.deterministic) {
      throw new AnalysisException(
        s"Non-deterministic functions are not supported in $condName conditions of " +
        s"MERGE operations: ${cond.sql}")
    }

    if (SubqueryExpression.hasSubquery(cond)) {
      throw new AnalysisException(
        s"Subqueries are not supported in conditions of MERGE operations. " +
        s"Found a subquery in the $condName condition: ${cond.sql}")
    }

    if (cond.find(_.isInstanceOf[AggregateExpression]).isDefined) {
      throw new AnalysisException(
        s"Agg functions are not supported in $condName conditions of MERGE operations: " + {cond.sql})
    }
  }
}
