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

import org.apache.iceberg.DistributionMode
import org.apache.iceberg.NullOrder
import org.apache.iceberg.SortDirection
import org.apache.iceberg.expressions.Term
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.catalog.CatalogV2Implicits
/**
 * 所属模块：iceberg-spark v3.5
 * <p>职责：设置写入分布与排序的逻辑计划节点，为 Iceberg 写入施加要求的分布/排序。
 * <p>设计意图：在逻辑计划阶段注入写入分布与排序要求，保证写出文件满足排序约束。
 * <p>上下游关系：由 IcebergSparkSessionExtensions 注册；由 SetWriteDistributionAndOrderingExec 执行。
 */

case class SetWriteDistributionAndOrdering(
    table: Seq[String],
    distributionMode: DistributionMode,
    sortOrder: Seq[(Term, SortDirection, NullOrder)]) extends LeafCommand {

  import CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    val order = sortOrder.map {
      case (term, direction, nullOrder) => s"$term $direction $nullOrder"
    }.mkString(", ")
    s"SetWriteDistributionAndOrdering ${table.quoted} $distributionMode $order"
  }
}
