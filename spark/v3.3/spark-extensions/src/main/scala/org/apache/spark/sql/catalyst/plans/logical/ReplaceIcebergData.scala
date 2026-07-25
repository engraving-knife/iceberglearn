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
 * Spark Catalyst 逻辑计划节点。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：样例类 ReplaceIcebergData。
 * <p>上下游：由解析器构造，被分析/优化规则处理。
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

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
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

  /**
   * 返回带新设置的副本。
   * @return 结果对象
   */
  override protected def withNewChildInternal(newChild: LogicalPlan): ReplaceIcebergData = {
    copy(query = newChild)
  }
}
