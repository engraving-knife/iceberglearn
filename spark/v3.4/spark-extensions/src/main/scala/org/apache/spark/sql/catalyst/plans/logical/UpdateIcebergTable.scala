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
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：UPDATE Iceberg 表逻辑计划节点，封装待更新表与赋值集合。
 * <p>设计意图：表示针对 Iceberg 表的 UPDATE 操作，待重写为可执行计划。
 * <p>上下游关系：由 IcebergSqlExtensionsAstBuilder 创建；由 RewriteUpdateTable 重写。
 */

case class UpdateIcebergTable(
    table: LogicalPlan,
    assignments: Seq[Assignment],
    condition: Option[Expression],
    rewritePlan: Option[LogicalPlan] = None) extends RowLevelCommand {

  lazy val aligned: Boolean = AssignmentUtils.aligned(table, assignments)
  /** 执行 children 相关操作。 */

  override def children: Seq[LogicalPlan] = if (rewritePlan.isDefined) {
    table :: rewritePlan.get :: Nil
  } else {
    table :: Nil
  }
  /** 返回带 NewRewritePlan 设置的副本。 */

  override def withNewRewritePlan(newRewritePlan: LogicalPlan): RowLevelCommand = {
    copy(rewritePlan = Some(newRewritePlan))
  }
  /** 返回带 NewChildrenInternal 设置的副本。 */

  override protected def withNewChildrenInternal(
      newChildren: IndexedSeq[LogicalPlan]): UpdateIcebergTable = {
    if (newChildren.size == 1) {
      copy(table = newChildren.head, rewritePlan = None)
    } else {
      require(newChildren.size == 2, "UpdateTable expects either one or two children")
      val Seq(newTable, newRewritePlan) = newChildren.take(2)
      copy(table = newTable, rewritePlan = Some(newRewritePlan))
    }
  }
}
