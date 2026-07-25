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

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.analysis.EliminateSubqueryAliases
import org.apache.spark.sql.catalyst.analysis.NamedRelation
import org.apache.spark.sql.catalyst.expressions.AttributeReference
import org.apache.spark.sql.catalyst.expressions.ExtendedV2ExpressionUtils
import org.apache.spark.sql.catalyst.expressions.NamedExpression
import org.apache.spark.sql.catalyst.util.CharVarcharUtils
import org.apache.spark.sql.catalyst.util.RowDeltaUtils.OPERATION_COLUMN
import org.apache.spark.sql.catalyst.util.WriteDeltaProjections
import org.apache.spark.sql.connector.iceberg.write.DeltaWrite
import org.apache.spark.sql.connector.iceberg.write.SupportsDelta
import org.apache.spark.sql.connector.write.RowLevelOperationTable
import org.apache.spark.sql.execution.datasources.v2.DataSourceV2Relation
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.types.StructField

/**
 * Spark Catalyst 逻辑计划节点的写入组件，负责数据写入与提交。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：样例类 WriteDelta。
 * <p>上下游：由解析器构造，被分析/优化规则处理。
 */
case class WriteDelta(
    table: NamedRelation,
    query: LogicalPlan,
    originalTable: NamedRelation,
    projections: WriteDeltaProjections,
    write: Option[DeltaWrite] = None) extends V2WriteCommandLike {

  override protected lazy val stringArgs: Iterator[Any] = Iterator(table, query, write)

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  private def operationResolved: Boolean = {
    val attr = query.output.head
    attr.name == OPERATION_COLUMN && attr.dataType == IntegerType && !attr.nullable
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  private def operation: SupportsDelta = {
    EliminateSubqueryAliases(table) match {
      case DataSourceV2Relation(RowLevelOperationTable(_, operation), _, _, _, _) =>
        operation match {
          case supportsDelta: SupportsDelta =>
            supportsDelta
          case _ =>
            throw new AnalysisException(s"Operation $operation is not a delta operation")
        }
      case _ =>
        throw new AnalysisException(s"Cannot retrieve row-level operation from $table")
    }
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  private def rowAttrsResolved: Boolean = {
    table.skipSchemaResolution || (projections.rowProjection match {
      case Some(projection) =>
        table.output.size == projection.schema.size &&
          projection.schema.zip(table.output).forall { case (field, outAttr) =>
            isCompatible(field, outAttr)
          }
      case None =>
        true
    })
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  private def rowIdAttrsResolved: Boolean = {
    val rowIdAttrs = ExtendedV2ExpressionUtils.resolveRefs[AttributeReference](
      operation.rowId.toSeq,
      originalTable)

    projections.rowIdProjection.schema.forall { field =>
      rowIdAttrs.exists(rowIdAttr => isCompatible(field, rowIdAttr))
    }
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  private def metadataAttrsResolved: Boolean = {
    projections.metadataProjection match {
      case Some(projection) =>
        val metadataAttrs = ExtendedV2ExpressionUtils.resolveRefs[AttributeReference](
          operation.requiredMetadataAttributes.toSeq,
          originalTable)

        projection.schema.forall { field =>
          metadataAttrs.exists(metadataAttr => isCompatible(field, metadataAttr))
        }
      case None =>
        true
    }
  }

  /** 判断是否compatible。 */
  private def isCompatible(projectionField: StructField, outAttr: NamedExpression): Boolean = {
    val inType = CharVarcharUtils.getRawType(projectionField.metadata).getOrElse(outAttr.dataType)
    val outType = CharVarcharUtils.getRawType(outAttr.metadata).getOrElse(outAttr.dataType)
    // names and types must match, nullability must be compatible
    projectionField.name == outAttr.name &&
      DataType.equalsIgnoreCompatibleNullability(inType, outType) &&
      (outAttr.nullable || !projectionField.nullable)
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def outputResolved: Boolean = {
    assert(table.resolved && query.resolved,
      "`outputResolved` can only be called when `table` and `query` are both resolved.")

    operationResolved && rowAttrsResolved && rowIdAttrsResolved && metadataAttrsResolved
  }

  /**
   * 返回带新设置的副本。
   * @return 结果对象
   */
  override protected def withNewChildInternal(newChild: LogicalPlan): WriteDelta = {
    copy(query = newChild)
  }
}
