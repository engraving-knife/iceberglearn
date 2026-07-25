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

import org.apache.iceberg.DistributionMode
import org.apache.iceberg.NullOrder
import org.apache.iceberg.SortDirection
import org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE
import org.apache.iceberg.expressions.Term
import org.apache.iceberg.spark.SparkUtil
import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.catalog.CatalogV2Implicits
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.catalog.TableCatalog
/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：设置写入分布与排序的物理执行节点，为写入施加要求的分布与排序。
 * <p>设计意图：实现 SetWriteDistributionAndOrdering 的物理执行，注入 shuffle/sort。
 * <p>上下游关系：由 ExtendedDataSourceV2Strategy 从 SetWriteDistributionAndOrdering 创建。
 */

case class SetWriteDistributionAndOrderingExec(
    catalog: TableCatalog,
    ident: Identifier,
    distributionMode: DistributionMode,
    sortOrder: Seq[(Term, SortDirection, NullOrder)]) extends LeafV2CommandExec {

  import CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil
  /** 执行任务。 */

  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val txn = iceberg.table.newTransaction()

        val orderBuilder = txn.replaceSortOrder().caseSensitive(SparkUtil.caseSensitive(session))
        sortOrder.foreach {
          case (term, SortDirection.ASC, nullOrder) =>
            orderBuilder.asc(term, nullOrder)
          case (term, SortDirection.DESC, nullOrder) =>
            orderBuilder.desc(term, nullOrder)
        }
        orderBuilder.commit()

        txn.updateProperties()
          .set(WRITE_DISTRIBUTION_MODE, distributionMode.modeName())
          .commit()

        txn.commitTransaction()

      case table =>
        throw new UnsupportedOperationException(s"Cannot set write order of non-Iceberg table: $table")
    }

    Nil
  }
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    val tableIdent = s"${catalog.name}.${ident.quoted}"
    val order = sortOrder.map {
      case (term, direction, nullOrder) => s"$term $direction $nullOrder"
    }.mkString(", ")
    s"SetWriteDistributionAndOrdering $tableIdent $distributionMode $order"
  }
}
