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

package org.apache.spark.sql.execution.datasources.v2

import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.catalog.TableCatalog
import scala.jdk.CollectionConverters._

/**
 * 设置标识字段（Identifier Fields）的物理执行节点。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions。本类为 Spark V2 命令执行节点，
 * 负责将 Iceberg 表的标识字段集合整体替换为指定字段列表。
 *
 * <p>职责：加载目标 Iceberg 表，调用 updateSchema().setIdentifierFields(...).commit() 完成设置。
 *
 * <p>设计意图：标识字段用于 Iceberg 行级 upsert 的主键语义，本节点把 SQL 语义映射到 schema 更新。
 * 对非 Iceberg 表抛出 UnsupportedOperationException。
 *
 * <p>上下游关系：由 {@link org.apache.spark.sql.catalyst.plans.logical.SetIdentifierFields}
 * 转换而来，操作 {@link org.apache.iceberg.spark.source.SparkTable} 的 schema 更新 API。
 */
case class SetIdentifierFieldsExec(
    catalog: TableCatalog,
    ident: Identifier,
    fields: Seq[String]) extends LeafV2CommandExec {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行标识字段设置。
   *
   * <p>逻辑：加载表并匹配为 {@link SparkTable}，将字段列表转为 Java 集合后调用
   * updateSchema().setIdentifierFields(...).commit()；非 Iceberg 表抛出异常。
   *
   * @return 空行列表（该命令无结果集）
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        iceberg.table.updateSchema()
          .setIdentifierFields(fields.asJava)
          .commit();
      case table =>
        throw new UnsupportedOperationException(s"Cannot set identifier fields in non-Iceberg table: $table")
    }

    Nil
  }

  /** 返回该命令的简要字符串描述，用于 explain 输出。 */
  override def simpleString(maxFields: Int): String = {
    s"SetIdentifierFields ${catalog.name}.${ident.quoted} (${fields.quoted})";
  }
}
