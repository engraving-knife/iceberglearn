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
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：删除分区字段的逻辑计划节点，对应 ALTER TABLE ... DROP PARTITION FIELD 语句。
 * <p>设计意图：封装分区字段删除语义。
 * <p>上下游关系：由 IcebergSqlExtensionsAstBuilder 创建；由 DropPartitionFieldExec 执行。
 */

case class DropPartitionField(table: Seq[String], transform: Transform) extends LeafCommand {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"DropPartitionField ${table.quoted} ${transform.describe}"
  }
}
