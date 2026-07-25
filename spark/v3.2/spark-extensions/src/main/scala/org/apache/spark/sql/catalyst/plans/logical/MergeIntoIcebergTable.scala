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

package org.apache.spark.sql.catalyst.plans.logical

import org.apache.spark.sql.catalyst.expressions.AssignmentUtils
import org.apache.spark.sql.catalyst.expressions.Expression

/**
 * Spark Catalyst 逻辑计划节点，实现 MERGE INTO 行级操作。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。
 * 类型：样例类 MergeIntoIcebergTable。
 * <p>上下游：由解析器构造，被分析/优化规则处理。
 */
case class MergeIntoIcebergTable(
    targetTable: LogicalPlan,
    sourceTable: LogicalPlan,
    mergeCondition: Expression,
    matchedActions: Seq[MergeAction],
    notMatchedActions: Seq[MergeAction],
    rewritePlan: Option[LogicalPlan] = None) extends RowLevelCommand {

  lazy val aligned: Boolean = {
    val matchedActionsAligned = matchedActions.forall {
      case UpdateAction(_, assignments) =>
        AssignmentUtils.aligned(targetTable, assignments)
      case _: DeleteAction =>
        true
      case _ =>
        false
    }

    val notMatchedActionsAligned = notMatchedActions.forall {
      case InsertAction(_, assignments) =>
        AssignmentUtils.aligned(targetTable, assignments)
      case _ =>
        false
    }

    matchedActionsAligned && notMatchedActionsAligned
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  def condition: Option[Expression] = Some(mergeCondition)

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def children: Seq[LogicalPlan] = if (rewritePlan.isDefined) {
    targetTable :: sourceTable :: rewritePlan.get :: Nil
  } else {
    targetTable :: sourceTable :: Nil
  }

  /**
   * 返回带新设置的副本。
   * @return 结果对象
   */
  override def withNewRewritePlan(newRewritePlan: LogicalPlan): RowLevelCommand = {
    copy(rewritePlan = Some(newRewritePlan))
  }

  /**
   * 返回带新设置的副本。
   * @return 结果对象
   */
  override protected def withNewChildrenInternal(
      newChildren: IndexedSeq[LogicalPlan]): MergeIntoIcebergTable = {

    newChildren match {
      case Seq(newTarget, newSource) =>
        copy(targetTable = newTarget, sourceTable = newSource, rewritePlan = None)
      case Seq(newTarget, newSource, newRewritePlan) =>
        copy(targetTable = newTarget, sourceTable = newSource, rewritePlan = Some(newRewritePlan))
      case _ =>
        throw new IllegalArgumentException("MergeIntoIcebergTable expects either two or three children")
    }
  }
}
