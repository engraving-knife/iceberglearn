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

import org.apache.iceberg.spark.Spark3Util
import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.catalog.TableCatalog
import org.apache.spark.sql.connector.expressions.Transform
/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：添加分区字段的物理执行节点，调用 Iceberg 表更新分区规范以添加分区字段。
 * <p>设计意图：实现 AddPartitionField 的物理执行。
 * <p>上下游关系：由 ExtendedDataSourceV2Strategy 从 AddPartitionField 创建。
 */

case class AddPartitionFieldExec(
    catalog: TableCatalog,
    ident: Identifier,
    transform: Transform,
    name: Option[String]) extends LeafV2CommandExec {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil
  /** 执行任务。 */

  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        iceberg.table.updateSpec()
            .addField(name.orNull, Spark3Util.toIcebergTerm(transform))
            .commit()

      case table =>
        throw new UnsupportedOperationException(s"Cannot add partition field to non-Iceberg table: $table")
    }

    Nil
  }
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"AddPartitionField ${catalog.name}.${ident.quoted} ${name.map(n => s"$n=").getOrElse("")}${transform.describe}"
  }
}
