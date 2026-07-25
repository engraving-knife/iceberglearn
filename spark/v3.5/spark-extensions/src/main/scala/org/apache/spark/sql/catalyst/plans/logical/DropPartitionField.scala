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
import org.apache.spark.sql.connector.expressions.Transform

/**
 * 删除分区字段（Drop Partition Field）的逻辑命令节点。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions（Spark SQL 扩展层）。本类为 Catalyst
 * 逻辑计划中的叶子命令节点，表示从 Iceberg 表的分区规范中移除一个分区字段。
 *
 * <p>职责：携带表标识与待删除的分区 Transform，作为逻辑计划在 Catalyst 树中传递，本身不执行副作用。
 *
 * <p>设计意图：将 DROP PARTITION FIELD 语法解析结果以逻辑节点形式承载，由 Exec 节点调用
 * Iceberg {@code updateSpec} 完成实际移除，保持逻辑/物理分离。
 *
 * <p>上下游关系：由 {@link org.apache.spark.sql.catalyst.parser.extensions.IcebergSqlExtensionsAstBuilder}
 * 构造，被转换为 {@link org.apache.spark.sql.execution.datasources.v2.DropPartitionFieldExec}。
 */
case class DropPartitionField(table: Seq[String], transform: Transform) extends LeafCommand {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /** 返回该命令的简要字符串描述，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"DropPartitionField ${table.quoted} ${transform.describe}"
  }
}
