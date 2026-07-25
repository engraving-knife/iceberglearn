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
 * 创建或替换分支的逻辑命令节点。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions（Spark SQL 扩展层）。本类为 Catalyst
 * 逻辑计划（LogicalPlan）中的叶子命令节点，由 Iceberg 扩展解析器在解析
 * CREATE/REPLACE BRANCH 语法时构造，经分析后转换为对应的物理执行节点。
 *
 * <p>职责：携带创建/替换分支所需的全部信息（表标识、分支名、分支选项、create/replace/ifNotExists
 * 标志），作为逻辑计划在 Catalyst 树中传递，本身不执行副作用。
 *
 * <p>设计意图：将 SQL 语义与实际表操作解耦——逻辑节点只描述"做什么"，由 Exec 节点负责
 * 调用 Iceberg 表 API 完成实际分支管理，符合 Spark Catalyst 逻辑/物理分离的设计范式。
 *
 * <p>上下游关系：由 {@link org.apache.spark.sql.catalyst.parser.extensions.IcebergSqlExtensionsAstBuilder}
 * 构造，被 Spark 执行层转换为 {@link org.apache.spark.sql.execution.datasources.v2.CreateOrReplaceBranchExec}。
 */
case class CreateOrReplaceBranch(
    table: Seq[String],
    branch: String,
    branchOptions: BranchOptions,
    create: Boolean,
    replace: Boolean,
    ifNotExists: Boolean) extends LeafCommand {

  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /** 返回该命令的简要字符串描述，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"CreateOrReplaceBranch branch: ${branch} for table: ${table.quoted}"
  }
}
