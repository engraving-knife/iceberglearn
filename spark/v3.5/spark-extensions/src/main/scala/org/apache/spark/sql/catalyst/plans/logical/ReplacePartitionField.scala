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
 * 替换 Iceberg 表分区字段 的逻辑命令节点。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark 3.5 Catalyst 扩展，定义 Iceberg 特有的
 * 逻辑命令以扩展 Spark SQL 语法）。
 *
 * <p>职责：承载 ALTER TABLE ... REPLACE PARTITION FIELD 语句的解析结果——将原有
 * 分区变换 transformFrom 替换为 transformTo，并可选地指定新分区字段名。
 *
 * <p>设计意图：继承 Spark 的 {@code LeafCommand}，output 为 Nil（无返回结果集）。
 * 将"替换分区字段"这一 Iceberg 特有操作建模为独立的逻辑命令节点，使其能
 * 穿过 Spark Catalyst 优化管线而不被意外改写，最终由对应的物理算子执行。
 *
 * <p>上下游关系：由 Spark 解析器在解析 REPLACE PARTITION FIELD 语法时创建；
 * 被 {@link org.apache.spark.sql.execution.datasources.v2.ExtendedDataSourceV2Strategy}
 * 转换为 {@code ReplacePartitionFieldExec} 执行。
 */
case class ReplacePartitionField(
    table: Seq[String],
    transformFrom: Transform,
    transformTo: Transform,
    name: Option[String]) extends LeafCommand {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /** 返回该命令的可读字符串表示，用于 EXPLAIN 与日志输出。 */
  override def simpleString(maxFields: Int): String = {
    s"ReplacePartitionField ${table.quoted} ${transformFrom.describe} " +
        s"with ${name.map(n => s"$n=").getOrElse("")}${transformTo.describe}"
  }
}
