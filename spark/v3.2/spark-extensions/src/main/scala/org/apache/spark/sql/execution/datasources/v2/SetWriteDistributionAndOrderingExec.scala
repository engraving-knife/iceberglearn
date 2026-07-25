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
import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.catalog.CatalogV2Implicits
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.catalog.TableCatalog

/**
 * Spark 物理执行相关组件的写入组件，负责数据写入与提交。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。
 * 类型：样例类 SetWriteDistributionAndOrderingExec。
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
case class SetWriteDistributionAndOrderingExec(
    catalog: TableCatalog,
    ident: Identifier,
    distributionMode: DistributionMode,
    sortOrder: Seq[(Term, SortDirection, NullOrder)]) extends LeafV2CommandExec {

  import CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val txn = iceberg.table.newTransaction()

        val orderBuilder = txn.replaceSortOrder()
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

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def simpleString(maxFields: Int): String = {
    val tableIdent = s"${catalog.name}.${ident.quoted}"
    val order = sortOrder.map {
      case (term, direction, nullOrder) => s"$term $direction $nullOrder"
    }.mkString(", ")
    s"SetWriteDistributionAndOrdering $tableIdent $distributionMode $order"
  }
}
