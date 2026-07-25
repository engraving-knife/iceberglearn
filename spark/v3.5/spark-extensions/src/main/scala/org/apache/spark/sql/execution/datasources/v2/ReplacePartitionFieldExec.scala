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
import org.apache.spark.sql.connector.expressions.FieldReference
import org.apache.spark.sql.connector.expressions.IdentityTransform
import org.apache.spark.sql.connector.expressions.Transform

/**
 * ALTER TABLE ... REPLACE PARTITION FIELD 语句的物理执行算子。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark v3.5 扩展模块），位于 Spark SQL 物理执行层。
 *
 * <p>职责：在执行阶段加载目标 Iceberg 表，移除旧的分区字段并添加新的分区字段（可指定新名称），
 * 通过 {@code updateSpec} 提交分区规格变更。
 *
 * <p>设计意图：作为 {@code LeafV2CommandExec} 叶子物理命令，在 driver 端同步执行 DDL。
 * 对"按分区字段名替换"与"按 Transform 替换"两种语义做分支处理：当 transformFrom 是
 * 单段 Identity 且该名称不在 schema 中时，按分区字段名移除；否则按 Transform 移除。
 *
 * <p>上下游关系：由 {@code ExtendedDataSourceV2Strategy} 从逻辑节点
 * {@code ReplacePartitionField} 转换而来；调用 Iceberg {@code updateSpec} API 提交变更。
 *
 * @param catalog 目标表所在的 catalog
 * @param ident 目标表标识符
 * @param transformFrom 待替换的旧分区字段 Transform
 * @param transformTo 新的分区字段 Transform
 * @param name 新分区字段的可选名称
 */
case class ReplacePartitionFieldExec(
    catalog: TableCatalog,
    ident: Identifier,
    transformFrom: Transform,
    transformTo: Transform,
    name: Option[String]) extends LeafV2CommandExec {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  /** 该命令不产生输出行。 */
  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行分区字段替换操作。
   *
   * <p>逻辑：加载目标表，若为 {@link SparkTable} 则判断 transformFrom：
   * 若是单段 Identity 且该字段名不在 schema 中（说明是分区字段名而非列名），
   * 调用 {@code updateSpec().removeField(name).addField(...)}；否则按 Transform 移除后添加。
   * 非 Iceberg 表抛出 {@link UnsupportedOperationException}。
   *
   * @return 空行序列
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val schema = iceberg.table.schema
        transformFrom match {
          case IdentityTransform(FieldReference(parts)) if parts.size == 1 && schema.findField(parts.head) == null =>
            // the name is not present in the Iceberg schema, so it must be a partition field name, not a column name
            iceberg.table.updateSpec()
                .removeField(parts.head)
                .addField(name.orNull, Spark3Util.toIcebergTerm(transformTo))
                .commit()

          case _ =>
            iceberg.table.updateSpec()
                .removeField(Spark3Util.toIcebergTerm(transformFrom))
                .addField(name.orNull, Spark3Util.toIcebergTerm(transformTo))
                .commit()
        }

      case table =>
        throw new UnsupportedOperationException(s"Cannot replace partition field in non-Iceberg table: $table")
    }

    Nil
  }
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"ReplacePartitionField ${catalog.name}.${ident.quoted} ${transformFrom.describe} " +
        s"with ${name.map(n => s"$n=").getOrElse("")}${transformTo.describe}"
  }
}
