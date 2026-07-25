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
import org.apache.spark.sql.catalyst.plans.logical.TagOptions
import org.apache.spark.sql.connector.catalog._

/**
 * 物理执行算子：在 Iceberg 表上创建或替换快照引用 Tag。
 *
 * <p>所属模块：iceberg-spark（spark-extensions 扩展包，Spark v2 数据源物理执行层）。
 *
 * <p>职责：接收 {@link org.apache.spark.sql.catalyst.plans.logical.CreateOrReplaceTag}
 * 逻辑算子的参数，加载目标 Iceberg 表并通过 {@code manageSnapshots} API 执行
 * 创建/替换 Tag 的实际操作。
 *
 * <p>设计意图：将 create、replace、ifNotExists 三种语义合并到单一执行算子，
 * 在 {@code run()} 内根据标志组合决定调用 createTag 还是 replaceTag，
 * 避免拆分多个物理算子。非 Iceberg 表直接抛出 UnsupportedOperationException。
 *
 * <p>上下游关系：由 CreateOrReplaceTag 逻辑算子在物理计划阶段转换而来，
 * 直接调用 Iceberg 表的快照管理 API 提交变更。
 *
 * @param catalog 目标表所在 catalog
 * @param ident 目标表标识
 * @param tag 要创建/替换的 Tag 名称
 * @param tagOptions Tag 配置（目标快照、保留时长）
 * @param create 是否为创建模式
 * @param replace 是否为替换模式
 * @param ifNotExists 仅在 Tag 不存在时创建
 */
case class CreateOrReplaceTagExec(
    catalog: TableCatalog,
    ident: Identifier,
    tag: String,
    tagOptions: TagOptions,
    create: Boolean,
    replace: Boolean,
    ifNotExists: Boolean) extends LeafV2CommandExec {

  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行创建/替换 Tag 操作。
   *
   * <p>逻辑：
   * <ol>
   *   <li>加载目标表，非 Iceberg 表则抛出异常。</li>
   *   <li>解析目标快照 ID：优先使用 tagOptions 指定值，否则取表当前快照；为空则报错。</li>
   *   <li>根据 create/replace/ifNotExists 与引用是否已存在，选择 createTag 或 replaceTag；
   *       若 ifNotExists 且引用已存在则直接返回。</li>
   *   <li>若指定了保留时长，设置该 Tag 的最大引用存活时间。</li>
   *   <li>提交快照管理事务。</li>
   * </ol>
   *
   * @return 空行集合（该命令无结果输出）
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val snapshotId: java.lang.Long = tagOptions.snapshotId
          .orElse(Option(iceberg.table.currentSnapshot()).map(_.snapshotId()))
          .map(java.lang.Long.valueOf)
          .orNull

        Preconditions.checkArgument(snapshotId != null,
          "Cannot complete create or replace tag operation on %s, main has no snapshot", ident)

        val manageSnapshot = iceberg.table.manageSnapshots()
        val refExists = null != iceberg.table().refs().get(tag)

        if (create && replace && !refExists) {
          manageSnapshot.createTag(tag, snapshotId)
        } else if (replace) {
          manageSnapshot.replaceTag(tag, snapshotId)
        } else {
          if (refExists && ifNotExists) {
            return Nil
          }

          manageSnapshot.createTag(tag, snapshotId)
        }

        if (tagOptions.snapshotRefRetain.nonEmpty) {
          manageSnapshot.setMaxRefAgeMs(tag, tagOptions.snapshotRefRetain.get)
        }

        manageSnapshot.commit()

      case table =>
        throw new UnsupportedOperationException(s"Cannot create tag to non-Iceberg table: $table")
    }

    Nil
  }

  /** 返回该算子的可读字符串表示，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"Create tag: $tag for table: ${ident.quoted}"
  }
}
