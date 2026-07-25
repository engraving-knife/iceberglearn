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

import org.apache.spark.sql.catalyst.analysis.NamedRelation
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.expressions.AttributeSet
import org.apache.spark.sql.catalyst.util.CharVarcharUtils
import org.apache.spark.sql.connector.write.Write
import org.apache.spark.sql.types.DataType

/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：替换 Iceberg 数据的逻辑计划节点，表示用新数据文件替换目标表数据。
 * <p>设计意图：作为行级命令重写的目标节点，封装数据替换写命令语义。
 * <p>上下游关系：由 RewriteRowLevelIcebergCommand 创建；由 ReplaceDataExec 执行。
 */
case class ReplaceIcebergData(
    table: NamedRelation,
    query: LogicalPlan,
    originalTable: NamedRelation,
    write: Option[Write] = None) extends V2WriteCommandLike {

  override lazy val references: AttributeSet = query.outputSet
  override lazy val stringArgs: Iterator[Any] = Iterator(table, query, write)

  // the incoming query may include metadata columns
  lazy val dataInput: Seq[Attribute] = {
    val tableAttrNames = table.output.map(_.name)
    query.output.filter(attr => tableAttrNames.exists(conf.resolver(_, attr.name)))
  }
  /** 执行 outputResolved 相关操作。 */

  override def outputResolved: Boolean = {
    assert(table.resolved && query.resolved,
      "`outputResolved` can only be called when `table` and `query` are both resolved.")

    // take into account only incoming data columns and ignore metadata columns in the query
    // they will be discarded after the logical write is built in the optimizer
    // metadata columns may be needed to request a correct distribution or ordering
    // but are not passed back to the data source during writes

    table.skipSchemaResolution || (dataInput.size == table.output.size &&
      dataInput.zip(table.output).forall { case (inAttr, outAttr) =>
        val outType = CharVarcharUtils.getRawType(outAttr.metadata).getOrElse(outAttr.dataType)
        // names and types must match, nullability must be compatible
        inAttr.name == outAttr.name &&
          DataType.equalsIgnoreCompatibleNullability(inAttr.dataType, outType) &&
          (outAttr.nullable || !inAttr.nullable)
      })
  }
  /** 返回带 NewChildInternal 设置的副本。 */

  override protected def withNewChildInternal(newChild: LogicalPlan): ReplaceIcebergData = {
    copy(query = newChild)
  }
}
