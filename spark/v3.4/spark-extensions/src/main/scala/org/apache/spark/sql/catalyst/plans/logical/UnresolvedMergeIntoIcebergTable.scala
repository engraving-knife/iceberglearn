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

import org.apache.spark.sql.catalyst.expressions.Expression

/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：未解析的 MERGE INTO Iceberg 表逻辑计划节点，仅包含原始语法信息。
 * <p>设计意图：在解析前承载 MERGE 语法，待 ResolveMergeIntoTableReferences 解析为 MergeIntoIcebergTable。
 * <p>上下游关系：由 IcebergSqlExtensionsAstBuilder 创建；由 ResolveMergeIntoTableReferences 消费。
 */
case class UnresolvedMergeIntoIcebergTable(
    targetTable: LogicalPlan,
    sourceTable: LogicalPlan,
    context: MergeIntoContext) extends BinaryCommand {
  /** 执行 duplicateResolved 相关操作。 */

  def duplicateResolved: Boolean = targetTable.outputSet.intersect(sourceTable.outputSet).isEmpty
  /** 执行 left 相关操作。 */

  override def left: LogicalPlan = targetTable
  /** 执行 right 相关操作。 */
  override def right: LogicalPlan = sourceTable
  /** 返回带 NewChildrenInternal 设置的副本。 */

  override protected def withNewChildrenInternal(newLeft: LogicalPlan, newRight: LogicalPlan): LogicalPlan = {
    copy(targetTable = newLeft, sourceTable = newRight)
  }
}

case class MergeIntoContext(
    mergeCondition: Expression,
    matchedActions: Seq[MergeAction],
    notMatchedActions: Seq[MergeAction])
