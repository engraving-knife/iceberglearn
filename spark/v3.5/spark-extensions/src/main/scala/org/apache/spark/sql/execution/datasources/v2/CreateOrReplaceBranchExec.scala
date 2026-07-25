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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions
import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.plans.logical.BranchOptions
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.catalog.TableCatalog

/**
 * 创建或替换分支的物理执行节点。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions。本类为 Spark V2 命令执行节点
 * （LeafV2CommandExec），负责实际执行 CREATE/REPLACE BRANCH 语义，调用 Iceberg 表的
 * manageSnapshots API 完成分支创建/替换及保留策略设置。
 *
 * <p>职责：
 * <ul>
 *   <li>加载目标 Iceberg 表，按 create/replace/ifNotExists 组合选择创建或替换分支。</li>
 *   <li>设置分支的最小保留快照数、最大快照年龄、引用最大年龄等保留选项并提交。</li>
 * </ul>
 *
 * <p>设计意图：把分支管理的具体操作收敛到 Iceberg 表 API，Spark 侧只做参数组装与流程控制；
 * 对非 Iceberg 表抛出 UnsupportedOperationException 以明确边界。
 *
 * <p>上下游关系：由 {@link org.apache.spark.sql.catalyst.plans.logical.CreateOrReplaceBranch}
 * 转换而来，调用 {@link org.apache.iceberg.spark.source.SparkTable} 暴露的 Iceberg 表操作。
 */
case class CreateOrReplaceBranchExec(
                              catalog: TableCatalog,
                              ident: Identifier,
                              branch: String,
                              branchOptions: BranchOptions,
                              create: Boolean,
                              replace: Boolean,
                              ifNotExists: Boolean) extends LeafV2CommandExec {

  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行分支创建/替换。
   *
   * <p>逻辑：
   * <ol>
   *   <li>加载表并匹配为 {@link SparkTable}，否则抛出 UnsupportedOperationException。</li>
   *   <li>确定快照 ID：优先使用分支选项指定值，否则取表当前快照。</li>
   *   <li>按 create/replace/refExists 组合决策：create&&replace 且分支不存在则直接创建；
   *       replace 则替换分支指向指定快照（要求快照非空）；其余情况在已存在且 ifNotExists 时直接返回，
   *       否则创建分支。</li>
   *   <li>按分支选项设置最小保留快照数、最大快照年龄、引用最大年龄，最后 commit。</li>
   * </ol>
   *
   * @return 空行列表（该命令无结果集）
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val snapshotId: java.lang.Long = branchOptions.snapshotId
          .orElse(Option(iceberg.table.currentSnapshot()).map(_.snapshotId()))
          .map(java.lang.Long.valueOf)
          .orNull

        val manageSnapshots = iceberg.table().manageSnapshots()
        val refExists = null != iceberg.table().refs().get(branch)
        /** 执行 safeCreateBranch 相关操作。 */

        def safeCreateBranch(): Unit = {
          if (snapshotId == null) {
            manageSnapshots.createBranch(branch)
          } else {
            manageSnapshots.createBranch(branch, snapshotId)
          }
        }

        if (create && replace && !refExists) {
          safeCreateBranch()
        } else if (replace) {
          Preconditions.checkArgument(snapshotId != null,
            "Cannot complete replace branch operation on %s, main has no snapshot", ident)
          manageSnapshots.replaceBranch(branch, snapshotId)
        } else {
          if (refExists && ifNotExists) {
            return Nil
          }

          safeCreateBranch()
        }

        if (branchOptions.numSnapshots.nonEmpty) {
          manageSnapshots.setMinSnapshotsToKeep(branch, branchOptions.numSnapshots.get.toInt)
        }

        if (branchOptions.snapshotRetain.nonEmpty) {
          manageSnapshots.setMaxSnapshotAgeMs(branch, branchOptions.snapshotRetain.get)
        }

        if (branchOptions.snapshotRefRetain.nonEmpty) {
          manageSnapshots.setMaxRefAgeMs(branch, branchOptions.snapshotRefRetain.get)
        }

        manageSnapshots.commit()

      case table =>
        throw new UnsupportedOperationException(s"Cannot create or replace branch on non-Iceberg table: $table")
    }

    Nil
  }

  /** 返回该命令的简要字符串描述，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"CreateOrReplace branch: $branch for table: ${ident.quoted}"
  }
}
