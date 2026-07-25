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
 * 添加 Iceberg 表分区字段的物理执行算子。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark 3.5 Catalyst 扩展，将 Iceberg 特有的
 * 逻辑命令转换为可执行的物理算子）。
 *
 * <p>职责：执行 ALTER TABLE ... ADD PARTITION FIELD 操作——加载目标表，若为
 * Iceberg 表则通过 {@link org.apache.iceberg.UpdatePartitionSpec#addField}
 * 向分区规格中添加新的分区变换字段。
 *
 * <p>设计意图：继承 Spark 的 {@code LeafV2CommandExec}，复用 V2 数据源的命令执行
 * 框架；output 为 Nil 表示该命令不产生结果行。对非 Iceberg 表抛出
 * UnsupportedOperationException，保证类型安全。
 *
 * <p>上下游关系：由 {@link ExtendedDataSourceV2Strategy} 从 AddPartitionField
 * 逻辑命令转换而来；调用 Iceberg 的 UpdatePartitionSpec 提交分区规格变更。
 */
case class AddPartitionFieldExec(
    catalog: TableCatalog,
    ident: Identifier,
    transform: Transform,
    name: Option[String]) extends LeafV2CommandExec {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行添加分区字段操作。
   *
   * <p>逻辑：通过 catalog 加载目标表，对 Iceberg 表调用 updateSpec().addField()
   * 添加分区变换并提交；非 Iceberg 表则抛出 UnsupportedOperationException。
   * 返回 Nil 表示无结果行输出。
   *
   * @return 空行序列（命令无返回数据）
   * @throws UnsupportedOperationException 目标表非 Iceberg 表时抛出
   */
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

  /** 返回该算子的可读字符串表示，用于 EXPLAIN 与日志输出。 */
  override def simpleString(maxFields: Int): String = {
    s"AddPartitionField ${catalog.name}.${ident.quoted} ${name.map(n => s"$n=").getOrElse("")}${transform.describe}"
  }
}
