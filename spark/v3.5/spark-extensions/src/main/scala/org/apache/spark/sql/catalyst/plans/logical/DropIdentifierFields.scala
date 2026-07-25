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
 * 表示 ALTER TABLE ... DROP IDENTIFIER FIELDS 语句的逻辑计划节点。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark v3.5 扩展模块），位于 Spark Catalyst 逻辑计划层。
 *
 * <p>职责：记录要从指定 Iceberg 表中移除的标识字段（identifier fields）名称列表，
 * 作为该 DDL 解析后的逻辑命令叶子节点。
 *
 * <p>设计意图：把"删除标识字段"建模为无输出的 LeafCommand，由执行层
 * {@code DropIdentifierFieldsExec} 调用 Iceberg {@code updateSchema} 提交变更。
 *
 * @param table 目标表的多段标识符
 * @param fields 要移除的标识字段名列表
 */
case class DropIdentifierFields(
    table: Seq[String],
    fields: Seq[String]) extends LeafCommand {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  /** 该命令不产生输出行。 */
  override lazy val output: Seq[Attribute] = Nil

  /** 简要字符串表示，显示目标表与字段列表。 */
  override def simpleString(maxFields: Int): String = {
    s"DropIdentifierFields ${table.quoted} (${fields.quoted})"
  }
}
