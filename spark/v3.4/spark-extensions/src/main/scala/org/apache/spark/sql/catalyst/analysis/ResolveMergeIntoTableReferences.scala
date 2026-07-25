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
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.plans.logical.Assignment
import org.apache.spark.sql.catalyst.plans.logical.DeleteAction
import org.apache.spark.sql.catalyst.plans.logical.InsertAction
import org.apache.spark.sql.catalyst.plans.logical.InsertStarAction
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.MergeIntoIcebergTable
import org.apache.spark.sql.catalyst.plans.logical.Project
import org.apache.spark.sql.catalyst.plans.logical.UnresolvedMergeIntoIcebergTable
import org.apache.spark.sql.catalyst.plans.logical.UpdateAction
import org.apache.spark.sql.catalyst.plans.logical.UpdateStarAction
import org.apache.spark.sql.catalyst.rules.Rule

/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：MERGE INTO 引用解析规则，解析 MERGE 语句中的表与列引用。
 * <p>设计意图：在分析阶段完成 MERGE 源/目标/条件引用的解析与绑定。
 * <p>上下游关系：由 IcebergSparkSessionExtensions 注册。
 */
case class ResolveMergeIntoTableReferences(spark: SparkSession) extends Rule[LogicalPlan] {

  private lazy val analyzer: Analyzer = spark.sessionState.analyzer
  /** 应用转换。 */

  override def apply(plan: LogicalPlan): LogicalPlan = plan resolveOperatorsUp {
    case m @ UnresolvedMergeIntoIcebergTable(targetTable, sourceTable, context)
        if targetTable.resolved && sourceTable.resolved && m.duplicateResolved =>

      val resolvedMatchedActions = context.matchedActions.map {
        case DeleteAction(cond) =>
          val resolvedCond = cond.map(resolveCond("DELETE", _, m))
          DeleteAction(resolvedCond)

        case UpdateAction(cond, assignments) =>
          val resolvedCond = cond.map(resolveCond("UPDATE", _, m))
          // the update action can access columns from both target and source tables
          val resolvedAssignments = resolveAssignments(assignments, m, resolveValuesWithSourceOnly = false)
          UpdateAction(resolvedCond, resolvedAssignments)

        case UpdateStarAction(updateCondition) =>
          val resolvedUpdateCondition = updateCondition.map(resolveCond("UPDATE", _, m))
          val assignments = targetTable.output.map { attr =>
            Assignment(attr, UnresolvedAttribute(Seq(attr.name)))
          }
          // for UPDATE *, the value must be from the source table
          val resolvedAssignments = resolveAssignments(assignments, m, resolveValuesWithSourceOnly = true)
          UpdateAction(resolvedUpdateCondition, resolvedAssignments)

        case _ =>
          throw new AnalysisException("Matched actions can only contain UPDATE or DELETE")
      }

      val resolvedNotMatchedActions = context.notMatchedActions.map {
        case InsertAction(cond, assignments) =>
          // the insert action is used when not matched, so its condition and value can only
          // access columns from the source table
          val resolvedCond = cond.map(resolveCond("INSERT", _, Project(Nil, m.sourceTable)))
          val resolvedAssignments = resolveAssignments(assignments, m, resolveValuesWithSourceOnly = true)
          InsertAction(resolvedCond, resolvedAssignments)

        case InsertStarAction(cond) =>
          // the insert action is used when not matched, so its condition and value can only
          // access columns from the source table
          val resolvedCond = cond.map(resolveCond("INSERT", _, Project(Nil, m.sourceTable)))
          val assignments = targetTable.output.map { attr =>
            Assignment(attr, UnresolvedAttribute(Seq(attr.name)))
          }
          val resolvedAssignments = resolveAssignments(assignments, m, resolveValuesWithSourceOnly = true)
          InsertAction(resolvedCond, resolvedAssignments)

        case _ =>
          throw new AnalysisException("Not matched actions can only contain INSERT")
      }

      val resolvedMergeCondition = resolveCond("SEARCH", context.mergeCondition, m)

      MergeIntoIcebergTable(
        targetTable,
        sourceTable,
        mergeCondition = resolvedMergeCondition,
        matchedActions = resolvedMatchedActions,
        notMatchedActions = resolvedNotMatchedActions)
  }
  /** 执行 resolveCond 相关操作。 */

  private def resolveCond(condName: String, cond: Expression, plan: LogicalPlan): Expression = {
    val resolvedCond = analyzer.resolveExpressionByPlanChildren(cond, plan)

    val unresolvedAttrs = resolvedCond.references.filter(!_.resolved)
    if (unresolvedAttrs.nonEmpty) {
      throw new AnalysisException(
        s"Cannot resolve ${unresolvedAttrs.map(_.sql).mkString("[", ",", "]")} in $condName condition " +
        s"of MERGE operation given input columns: ${plan.inputSet.toSeq.map(_.sql).mkString("[", ",", "]")}")
    }

    resolvedCond
  }

  // copied from ResolveReferences in Spark
  private def resolveAssignments(
      assignments: Seq[Assignment],
      mergeInto: UnresolvedMergeIntoIcebergTable,
      resolveValuesWithSourceOnly: Boolean): Seq[Assignment] = {
    assignments.map { assign =>
      val resolvedKey = assign.key match {
        case c if !c.resolved =>
          resolveMergeExprOrFail(c, Project(Nil, mergeInto.targetTable))
        case o => o
      }
      val resolvedValue = assign.value match {
        // The update values may contain target and/or source references.
        case c if !c.resolved =>
          if (resolveValuesWithSourceOnly) {
            resolveMergeExprOrFail(c, Project(Nil, mergeInto.sourceTable))
          } else {
            resolveMergeExprOrFail(c, mergeInto)
          }
        case o => o
      }
      Assignment(resolvedKey, resolvedValue)
    }
  }

  // copied from ResolveReferences in Spark
  private def resolveMergeExprOrFail(e: Expression, p: LogicalPlan): Expression = {
    val resolved = analyzer.resolveExpressionByPlanChildren(e, p)
    resolved.references.filter(!_.resolved).foreach { a =>
      // Note: This will throw error only on unresolved attribute issues,
      // not other resolution errors like mismatched data types.
      val cols = p.inputSet.toSeq.map(_.sql).mkString(", ")
      throw new AnalysisException(s"cannot resolve ${a.sql} in MERGE command given columns [$cols]")
    }
    resolved
  }
}
