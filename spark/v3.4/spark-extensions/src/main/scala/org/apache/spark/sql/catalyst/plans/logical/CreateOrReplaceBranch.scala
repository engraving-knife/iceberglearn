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
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：创建或替换分支的逻辑计划节点，对应 CREATE OR REPLACE BRANCH 语句。
 * <p>设计意图：封装分支创建/替换语义，支持若存在则替换。
 * <p>上下游关系：由 IcebergSqlExtensionsAstBuilder 创建；由 CreateOrReplaceBranchExec 执行。
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
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"CreateOrReplaceBranch branch: ${branch} for table: ${table.quoted}"
  }
}
