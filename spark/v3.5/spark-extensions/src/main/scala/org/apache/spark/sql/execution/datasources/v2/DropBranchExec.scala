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
 * 删除 Iceberg 表分支的物理执行算子。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark 3.5 Catalyst 扩展，将 Iceberg 特有的
 * 逻辑命令转换为可执行的物理算子）。
 *
 * <p>职责：执行 ALTER TABLE ... DROP BRANCH 操作——加载目标表，若为 Iceberg 表
 * 则通过 {@link org.apache.iceberg.ManageSnapshots#removeBranch} 删除指定分支。
 *
 * <p>设计意图：继承 Spark 的 {@code LeafV2CommandExec}，复用 V2 命令执行框架。
 * 通过 ifExists 标志实现幂等语义：当 ifExists 为 true 且分支不存在时静默跳过，
 * 避免报错；否则正常执行删除。对非 Iceberg 表抛出异常以保证类型安全。
 *
 * <p>上下游关系：由 {@link ExtendedDataSourceV2Strategy} 从 DropBranch 逻辑命令
 * 转换而来；调用 Iceberg 的 ManageSnapshots 提交分支删除操作。
 */
case class DropBranchExec(
    catalog: TableCatalog,
    ident: Identifier,
    branch: String,
    ifExists: Boolean) extends LeafV2CommandExec {

  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行删除分支操作。
   *
   * <p>逻辑：通过 catalog 加载目标表，对 Iceberg 表先查询分支是否存在：
   * 若存在或 ifExists 为 false，则调用 manageSnapshots().removeBranch() 提交删除；
   * 若分支不存在且 ifExists 为 true，则静默跳过（幂等）。非 Iceberg 表抛异常。
   *
   * @return 空行序列（命令无返回数据）
   * @throws UnsupportedOperationException 目标表非 Iceberg 表时抛出
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val ref = iceberg.table().refs().get(branch)
        if (ref != null || !ifExists) {
          iceberg.table().manageSnapshots().removeBranch(branch).commit()
        }

      case table =>
        throw new UnsupportedOperationException(s"Cannot drop branch on non-Iceberg table: $table")
    }

    Nil
  }

  /** 返回该算子的可读字符串表示，用于 EXPLAIN 与日志输出。 */
  override def simpleString(maxFields: Int): String = {
    s"DropBranch branch: ${branch} for table: ${ident.quoted}"
  }
}
