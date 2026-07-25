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
 * Spark Catalyst 逻辑计划节点，实现 MERGE INTO 行级操作。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。
 * 类型：样例类 UnresolvedMergeIntoIcebergTable。
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 * <p>上下游：由解析器构造，被分析/优化规则处理。
 */
case class UnresolvedMergeIntoIcebergTable(
    targetTable: LogicalPlan,
    sourceTable: LogicalPlan,
    context: MergeIntoContext) extends BinaryCommand {

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  def duplicateResolved: Boolean = targetTable.outputSet.intersect(sourceTable.outputSet).isEmpty

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def left: LogicalPlan = targetTable
  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def right: LogicalPlan = sourceTable

  /**
   * 返回带新设置的副本。
   * @return 结果对象
   */
  override protected def withNewChildrenInternal(newLeft: LogicalPlan, newRight: LogicalPlan): LogicalPlan = {
    copy(targetTable = newLeft, sourceTable = newRight)
  }
}

/**
 * Spark Catalyst 逻辑计划节点，实现 MERGE INTO 行级操作。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。
 * 类型：样例类 MergeIntoContext。
 * <p>上下游：由解析器构造，被分析/优化规则处理。
 */
case class MergeIntoContext(
    mergeCondition: Expression,
    matchedActions: Seq[MergeAction],
    notMatchedActions: Seq[MergeAction])
