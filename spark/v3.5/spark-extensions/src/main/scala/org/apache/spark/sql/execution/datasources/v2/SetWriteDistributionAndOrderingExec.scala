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
 * 物理执行算子：设置 Iceberg 表的写入分布模式与排序顺序。
 *
 * <p>所属模块：iceberg-spark（spark-extensions 扩展包，Spark v2 数据源物理执行层）。
 *
 * <p>职责：在一个表事务内同时更新写分布模式（如 hash/range/none）与排序顺序，
 * 保证两者原子提交。
 *
 * <p>设计意图：写入分布与排序顺序共同决定数据文件如何分布与排序，二者需一致变更。
 * 通过 {@code newTransaction()} 开启事务，分别 replaceSortOrder 与 updateProperties，
 * 最后统一 commitTransaction，避免半完成状态。非 Iceberg 表抛出异常。
 *
 * <p>上下游关系：由对应的逻辑算子转换而来，调用 Iceberg 表事务 API 提交元数据变更。
 *
 * @param catalog 目标表所在 catalog
 * @param ident 目标表标识
 * @param distributionMode 写入分布模式
 * @param sortOrder 排序规则序列，每项为 (Term, 排序方向, 空值顺序)
 */
case class SetWriteDistributionAndOrderingExec(
    catalog: TableCatalog,
    ident: Identifier,
    distributionMode: DistributionMode,
    sortOrder: Seq[(Term, SortDirection, NullOrder)]) extends LeafV2CommandExec {

  import CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行写入分布与排序顺序的设置。
   *
   * <p>逻辑：
   * <ol>
   *   <li>加载目标表，非 Iceberg 表抛异常。</li>
   *   <li>开启表事务。</li>
   *   <li>构建新的排序顺序：按 sortOrder 逐项追加 asc/desc 规则并提交。</li>
   *   <li>更新表属性，写入分布模式属性。</li>
   *   <li>提交事务，使排序与分布模式变更原子生效。</li>
   * </ol>
   *
   * @return 空行集合（该命令无结果输出）
   */
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

  /** 返回该算子的可读字符串表示，展示表标识、分布模式与排序规则，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    val tableIdent = s"${catalog.name}.${ident.quoted}"
    val order = sortOrder.map {
      case (term, direction, nullOrder) => s"$term $direction $nullOrder"
    }.mkString(", ")
    s"SetWriteDistributionAndOrdering $tableIdent $distributionMode $order"
  }
}
