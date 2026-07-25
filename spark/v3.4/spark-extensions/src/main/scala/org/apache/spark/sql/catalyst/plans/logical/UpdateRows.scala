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

import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.expressions.AttributeSet
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.util.truncatedString
/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：行更新逻辑计划节点，按赋值对匹配行计算更新后的行。
 * <p>设计意图：作为 UPDATE 重写的中间节点，流式输出更新后行。
 * <p>上下游关系：由 RewriteUpdateTable 创建；由 UpdateRowsExec 执行。
 */

case class UpdateRows(
    deleteOutput: Seq[Expression],
    insertOutput: Seq[Expression],
    output: Seq[Attribute],
    child: LogicalPlan) extends UnaryNode {

  override lazy val producedAttributes: AttributeSet = {
    AttributeSet(output.filterNot(attr => inputSet.contains(attr)))
  }
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"UpdateRows${truncatedString(output, "[", ", ", "]", maxFields)}"
  }
  /** 返回带 NewChildInternal 设置的副本。 */

  override protected def withNewChildInternal(newChild: LogicalPlan): LogicalPlan = {
    copy(child = newChild)
  }
}
