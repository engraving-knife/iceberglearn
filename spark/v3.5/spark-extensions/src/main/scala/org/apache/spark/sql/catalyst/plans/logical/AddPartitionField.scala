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
 * 逻辑算子：为 Iceberg 表添加分区字段（ADD PARTITION FIELD）。
 *
 * <p>所属模块：iceberg-spark（spark-extensions 扩展包，扩展 Spark Catalyst 逻辑计划，
 * 使 Spark SQL 支持Iceberg 的分区演化操作）。
 *
 * <p>职责：作为逻辑计划节点，承载「为指定表新增分区字段」这一命令所需的全部信息——
 * 目标表标识、分区变换（Transform）以及可选的自定义分区字段名。
 *
 * <p>设计意图：采用不可变样例类承载命令参数，符合 Spark 逻辑计划树不可变约定；
 * 自身不执行任何副作用，真正的表元数据修改由后续物理算子完成，实现解析与执行的解耦。
 *
 * <p>上下游关系：由 Iceberg SQL 扩展解析器在 SQL 解析阶段构造，经 Catalyst 规则处理后，
 * 转换为对应的物理执行算子调用 Iceberg 表 API 完成分区字段添加。
 *
 * @param table 目标表的多段标识（catalog.database.table）
 * @param transform 分区变换，例如 identity/bucket/truncate 等
 * @param name 自定义分区字段名，为 None 时使用变换默认名
 */
case class AddPartitionField(table: Seq[String], transform: Transform, name: Option[String]) extends LeafCommand {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /** 返回该算子的可读字符串表示，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"AddPartitionField ${table.quoted} ${name.map(n => s"$n=").getOrElse("")}${transform.describe}"
  }
}
