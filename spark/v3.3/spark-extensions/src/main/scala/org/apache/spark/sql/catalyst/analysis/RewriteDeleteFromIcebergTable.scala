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
import org.apache.spark.sql.catalyst.expressions.Alias
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.catalyst.expressions.Not
import org.apache.spark.sql.catalyst.plans.logical.DeleteFromIcebergTable
import org.apache.spark.sql.catalyst.plans.logical.Filter
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.Project
import org.apache.spark.sql.catalyst.plans.logical.ReplaceIcebergData
import org.apache.spark.sql.catalyst.plans.logical.WriteDelta
import org.apache.spark.sql.catalyst.util.RowDeltaUtils._
import org.apache.spark.sql.connector.catalog.SupportsRowLevelOperations
import org.apache.spark.sql.connector.iceberg.write.SupportsDelta
import org.apache.spark.sql.connector.write.RowLevelOperation.Command.DELETE
import org.apache.spark.sql.connector.write.RowLevelOperationTable
import org.apache.spark.sql.execution.datasources.v2.DataSourceV2Relation
import org.apache.spark.sql.util.CaseInsensitiveStringMap

/**
 * Spark Catalyst 分析阶段的规则或检查的写入组件，负责数据写入与提交。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：对象 RewriteDeleteFromIcebergTable。
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 * <p>上下游：由 Spark SparkSessionExtensions 注册，作用于 Catalyst 计划。
 */
object RewriteDeleteFromIcebergTable extends RewriteRowLevelIcebergCommand {

  /**
   * 执行核心逻辑。
   * @return 结果对象
   */
  override def apply(plan: LogicalPlan): LogicalPlan = plan resolveOperators {
    case d @ DeleteFromIcebergTable(aliasedTable, Some(cond), None) if d.resolved =>
      EliminateSubqueryAliases(aliasedTable) match {
        case r @ DataSourceV2Relation(tbl: SupportsRowLevelOperations, _, _, _, _) =>
          val table = buildOperationTable(tbl, DELETE, CaseInsensitiveStringMap.empty())
          val rewritePlan = table.operation match {
            case _: SupportsDelta =>
              buildWriteDeltaPlan(r, table, cond)
            case _ =>
              buildReplaceDataPlan(r, table, cond)
          }
          // keep the original relation in DELETE to try deleting using filters
          DeleteFromIcebergTable(r, Some(cond), Some(rewritePlan))

        case p =>
          throw new AnalysisException(s"$p is not an Iceberg table")
      }
  }

  // build a rewrite plan for sources that support replacing groups of data (e.g. files, partitions)
  /**
   * 构造并返回目标对象。
   * @return 结果对象
   */
  private def buildReplaceDataPlan(
      relation: DataSourceV2Relation,
      operationTable: RowLevelOperationTable,
      cond: Expression): ReplaceIcebergData = {

    // resolve all needed attrs (e.g. metadata attrs for grouping data on write)
    val metadataAttrs = resolveRequiredMetadataAttrs(relation, operationTable.operation)

    // construct a read relation and include all required metadata columns
    val readRelation = buildRelationWithAttrs(relation, operationTable, metadataAttrs)

    // construct a plan that contains unmatched rows in matched groups that must be carried over
    // such rows do not match the condition but have to be copied over as the source can replace
    // only groups of rows
    val remainingRowsFilter = Not(EqualNullSafe(cond, Literal.TrueLiteral))
    val remainingRowsPlan = Filter(remainingRowsFilter, readRelation)

    // build a plan to replace read groups in the table
    val writeRelation = relation.copy(table = operationTable)
    ReplaceIcebergData(writeRelation, remainingRowsPlan, relation)
  }

  // build a rewrite plan for sources that support row deltas
  /**
   * 构造并返回目标对象。
   * @return 结果对象
   */
  private def buildWriteDeltaPlan(
      relation: DataSourceV2Relation,
      operationTable: RowLevelOperationTable,
      cond: Expression): WriteDelta = {

    // resolve all needed attrs (e.g. row ID and any required metadata attrs)
    val rowIdAttrs = resolveRowIdAttrs(relation, operationTable.operation)
    val metadataAttrs = resolveRequiredMetadataAttrs(relation, operationTable.operation)

    // construct a read relation and include all required metadata columns
    val readRelation = buildRelationWithAttrs(relation, operationTable, rowIdAttrs ++ metadataAttrs)

    // construct a plan that only contains records to delete
    val deletedRowsPlan = Filter(cond, readRelation)
    val operationType = Alias(Literal(DELETE_OPERATION), OPERATION_COLUMN)()
    val requiredWriteAttrs = dedupAttrs(rowIdAttrs ++ metadataAttrs)
    val project = Project(operationType +: requiredWriteAttrs, deletedRowsPlan)

    // build a plan to write deletes to the table
    val writeRelation = relation.copy(table = operationTable)
    val projections = buildWriteDeltaProjections(project, Nil, rowIdAttrs, metadataAttrs)
    WriteDelta(writeRelation, project, relation, projections)
  }
}
