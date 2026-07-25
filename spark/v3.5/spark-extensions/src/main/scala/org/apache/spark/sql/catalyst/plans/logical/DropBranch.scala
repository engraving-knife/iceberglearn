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
 * 删除 Iceberg 表分支的逻辑命令节点。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark 3.5 Catalyst 扩展，定义 Iceberg 特有的
 * 逻辑命令以扩展 Spark SQL 语法）。
 *
 * <p>职责：承载 ALTER TABLE ... DROP BRANCH 语句的解析结果（表名、分支名、ifExists
 * 标志），作为逻辑计划叶子节点参与 Catalyst 优化与物理计划转换。
 *
 * <p>设计意图：继承 Spark 的 {@code LeafCommand}，使该命令能被 Spark 的命令执行
 * 框架识别；output 为 Nil 表示该命令不返回结果集。通过 ifExists 标志支持
 * "分支不存在时不报错"的幂等语义。
 *
 * <p>上下游关系：由 Spark 解析器在解析 DROP BRANCH 语法时创建；被
 * {@link org.apache.spark.sql.execution.datasources.v2.ExtendedDataSourceV2Strategy}
 * 转换为物理算子 {@code DropBranchExec} 执行。
 */
case class DropBranch(table: Seq[String], branch: String, ifExists: Boolean) extends LeafCommand {

  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /** 返回该命令的可读字符串表示，用于 EXPLAIN 与日志输出。 */
  override def simpleString(maxFields: Int): String = {
    s"DropBranch branch: ${branch} for table: ${table.quoted}"
  }
}
