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

import org.apache.spark.sql.catalyst.expressions.Attribute

/**
 * 逻辑算子：创建或替换 Iceberg 表的快照引用 Tag（CREATE TAG / REPLACE TAG）。
 *
 * <p>所属模块：iceberg-spark（spark-extensions 扩展包，扩展 Spark Catalyst 逻辑计划，
 * 支持Iceberg 快照引用管理）。
 *
 * <p>职责：承载创建/替换 Tag 命令的全部参数——目标表、Tag 名、Tag 选项，以及
 * create/replace/ifNotExists 三个互斥的执行模式标志。
 *
 * <p>设计意图：将 create、replace、ifNotExists 三个布尔标志合并在一个算子中，
 * 由物理执行层根据标志组合决定具体语义（仅创建、仅替换、不存在则创建等），
 * 避免为每种组合定义独立算子。配合 {@link TagOptions} 表达可选配置，保持算子签名稳定。
 *
 * <p>上下游关系：由 Iceberg SQL 扩展解析器构造，转换为物理算子后调用
 * Iceberg 表的 {@code manageSnapshots} API 执行 Tag 操作。
 *
 * @param table 目标表的多段标识
 * @param tag 要创建或替换的 Tag 名称
 * @param tagOptions Tag 配置选项（目标快照、保留时长）
 * @param create 是否为创建模式
 * @param replace 是否为替换模式
 * @param ifNotExists 仅在 Tag 不存在时创建，避免报错
 */
case class CreateOrReplaceTag(
    table: Seq[String],
    tag: String,
    tagOptions: TagOptions,
    create: Boolean,
    replace: Boolean,
    ifNotExists: Boolean) extends LeafCommand {

  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /** 返回该算子的可读字符串表示，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"CreateOrReplaceTag tag: ${tag} for table: ${table.quoted}"
  }
}
