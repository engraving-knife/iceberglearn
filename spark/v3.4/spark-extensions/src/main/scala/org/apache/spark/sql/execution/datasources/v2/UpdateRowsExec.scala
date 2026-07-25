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

package org.apache.spark.sql.execution.datasources.v2

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.expressions.AttributeSet
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.UnsafeProjection
import org.apache.spark.sql.catalyst.util.truncatedString
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.execution.UnaryExecNode
/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：行更新物理执行节点，按赋值对匹配行计算并输出更新后行。
 * <p>设计意图：实现 UpdateRows 的物理执行，流式输出更新后行供后续写入。
 * <p>上下游关系：由 ExtendedDataSourceV2Strategy 从 UpdateRows 创建。
 */

case class UpdateRowsExec(
    deleteOutput: Seq[Expression],
    insertOutput: Seq[Expression],
    output: Seq[Attribute],
    child: SparkPlan) extends UnaryExecNode {

  @transient override lazy val producedAttributes: AttributeSet = {
    AttributeSet(output.filterNot(attr => inputSet.contains(attr)))
  }
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"UpdateRowsExec${truncatedString(output, "[", ", ", "]", maxFields)}"
  }
  /** 执行 doExecute 相关操作。 */

  override protected def doExecute(): RDD[InternalRow] = {
    child.execute().mapPartitions(processPartition)
  }
  /** 返回带 NewChildInternal 设置的副本。 */

  override protected def withNewChildInternal(newChild: SparkPlan): SparkPlan = {
    copy(child = newChild)
  }
  /** 执行 processPartition 相关操作。 */

  private def processPartition(rowIterator: Iterator[InternalRow]): Iterator[InternalRow] = {
    val deleteProj = createProjection(deleteOutput)
    val insertProj = createProjection(insertOutput)
    new UpdateAsDeleteAndInsertRowIterator(rowIterator, deleteProj, insertProj)
  }
  /** 执行 createProjection 相关操作。 */

  private def createProjection(exprs: Seq[Expression]): UnsafeProjection = {
    UnsafeProjection.create(exprs, child.output)
  }

  class UpdateAsDeleteAndInsertRowIterator(
      private val inputRows: Iterator[InternalRow],
      private val deleteProj: UnsafeProjection,
      private val insertProj: UnsafeProjection)
    extends Iterator[InternalRow] {

    var cachedInsertRow: InternalRow = _
    /** 判断是否有下一个元素。 */

    override def hasNext: Boolean = cachedInsertRow != null || inputRows.hasNext
    /** 返回下一个元素。 */

    override def next(): InternalRow = {
      if (cachedInsertRow != null) {
        val insertRow = cachedInsertRow
        cachedInsertRow = null
        return insertRow
      }

      val row = inputRows.next()
      cachedInsertRow = insertProj.apply(row)
      deleteProj.apply(row)
    }
  }
}
