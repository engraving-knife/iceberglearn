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

import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.catalog.TableCatalog

/**
 * 物理执行算子：删除 Iceberg 表上的快照引用 Tag。
 *
 * <p>所属模块：iceberg-spark（spark-extensions 扩展包，Spark v2 数据源物理执行层）。
 *
 * <p>职责：加载目标 Iceberg 表，通过 {@code manageSnapshots().removeTag(tag).commit()}
 * 移除指定 Tag 引用，并支持 ifExists 容错语义。
 *
 * <p>设计意图：当 ifExists 为 true 且 Tag 不存在时静默返回，避免抛错，对应 SQL 的 IF EXISTS；
 * 非 Iceberg 表直接抛出 UnsupportedOperationException。
 *
 * <p>上下游关系：由 DropTag 逻辑算子转换而来，直接调用 Iceberg 表快照管理 API。
 *
 * @param catalog 目标表所在 catalog
 * @param ident 目标表标识
 * @param tag 要删除的 Tag 名称
 * @param ifExists 为 true 时 Tag 不存在也不报错
 */
case class DropTagExec(
    catalog: TableCatalog,
    ident: Identifier,
    tag: String,
    ifExists: Boolean) extends LeafV2CommandExec {

  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行删除 Tag 操作。
   *
   * <p>逻辑：加载目标表，非 Iceberg 表抛异常；查询 Tag 引用是否存在，
   * 当引用存在或 ifExists 为 false 时调用 removeTag 提交删除，否则跳过。
   *
   * @return 空行集合（该命令无结果输出）
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val ref = iceberg.table().refs().get(tag)
        if (ref != null || !ifExists) {
          iceberg.table().manageSnapshots().removeTag(tag).commit()
        }

      case table =>
        throw new UnsupportedOperationException(s"Cannot drop tag on non-Iceberg table: $table")
    }

    Nil
  }

  /** 返回该算子的可读字符串表示，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"DropTag tag: ${tag} for table: ${ident.quoted}"
  }
}
