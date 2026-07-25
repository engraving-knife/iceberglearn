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
 * 删除分区字段的物理执行节点。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions。本类为 Spark V2 命令执行节点，
 * 负责从 Iceberg 表分区规范中移除指定分区字段。
 *
 * <p>职责：加载目标 Iceberg 表，根据传入 Transform 调用 updateSpec().removeField(...) 提交移除。
 *
 * <p>设计意图：当传入的是恒等变换且对应名称在 schema 中不存在时，认为该名称是分区字段名
 * （而非列名），直接按名移除；否则按 Transform 转换后的 Iceberg 项移除，兼容两种指定方式。
 *
 * <p>上下游关系：由 {@link org.apache.spark.sql.catalyst.plans.logical.DropPartitionField}
 * 转换而来，操作 {@link org.apache.iceberg.spark.source.SparkTable} 暴露的表 API。
 */
case class DropPartitionFieldExec(
    catalog: TableCatalog,
    ident: Identifier,
    transform: Transform) extends LeafV2CommandExec {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行分区字段移除。
   *
   * <p>逻辑：加载表并匹配为 {@link SparkTable}；若 Transform 为单段恒等且该段名不在 schema 中，
   * 则按分区字段名移除；否则将 Transform 转为 Iceberg 项后移除。最后 commit。对非 Iceberg 表抛出异常。
   *
   * @return 空行列表（该命令无结果集）
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val schema = iceberg.table.schema
        transform match {
          case IdentityTransform(FieldReference(parts)) if parts.size == 1 && schema.findField(parts.head) == null =>
            // the name is not present in the Iceberg schema, so it must be a partition field name, not a column name
            iceberg.table.updateSpec()
                .removeField(parts.head)
                .commit()

          case _ =>
            iceberg.table.updateSpec()
                .removeField(Spark3Util.toIcebergTerm(transform))
                .commit()
        }

      case table =>
        throw new UnsupportedOperationException(s"Cannot drop partition field in non-Iceberg table: $table")
    }

    Nil
  }

  /** 返回该命令的简要字符串描述，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"DropPartitionField ${catalog.name}.${ident.quoted} ${transform.describe}"
  }
}
