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
 * 逻辑算子：删除 Iceberg 表的快照引用 Tag（DROP TAG）。
 *
 * <p>所属模块：iceberg-spark（spark-extensions 扩展包，扩展 Spark Catalyst 逻辑计划，
 * 支持Iceberg 快照引用管理）。
 *
 * <p>职责：承载删除 Tag 命令的参数——目标表标识、Tag 名称，以及 ifExists 容错标志。
 *
 * <p>设计意图：以不可变样例类表达删除命令，自身不产生副作用；ifExists 标志用于
 * 控制 Tag 不存在时是否抛错，对应 SQL 的 IF EXISTS 语义。真正删除由物理算子完成。
 *
 * <p>上下游关系：由 Iceberg SQL 扩展解析器构造，转换为物理算子后调用
 * Iceberg 表的 {@code manageSnapshots} API 移除指定 Tag 引用。
 *
 * @param table 目标表的多段标识
 * @param tag 要删除的 Tag 名称
 * @param ifExists 为 true 时 Tag 不存在也不报错
 */
case class DropTag(table: Seq[String], tag: String, ifExists: Boolean) extends LeafCommand {

  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /** 返回该算子的可读字符串表示，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"DropTag tag: ${tag} for table: ${table.quoted}"
  }
}
