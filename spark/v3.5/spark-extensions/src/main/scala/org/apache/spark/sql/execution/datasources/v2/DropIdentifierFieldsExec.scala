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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions
import org.apache.iceberg.relocated.com.google.common.collect.Sets
import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.catalog.TableCatalog

/**
 * ALTER TABLE ... DROP IDENTIFIER FIELDS 语句的物理执行算子。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark v3.5 扩展模块），位于 Spark SQL 物理执行层。
 *
 * <p>职责：在执行阶段加载目标 Iceberg 表，校验待删除字段确为当前标识字段，
 * 然后通过 {@code updateSchema().setIdentifierFields(...)} 提交 schema 变更。
 *
 * <p>设计意图：作为 {@code LeafV2CommandExec} 叶子物理命令，在 driver 端同步执行 DDL。
 * 通过 Preconditions 校验字段存在且属于标识字段，避免提交非法 schema。
 *
 * <p>上下游关系：由 {@code ExtendedDataSourceV2Strategy} 从逻辑节点
 * {@code DropIdentifierFields} 转换而来；调用 Iceberg {@code updateSchema} API 提交元数据变更。
 *
 * @param catalog 目标表所在的 catalog
 * @param ident 目标表标识符
 * @param fields 要移除的标识字段名列表
 */
case class DropIdentifierFieldsExec(
    catalog: TableCatalog,
    ident: Identifier,
    fields: Seq[String]) extends LeafV2CommandExec {
  import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._

  /** 该命令不产生输出行。 */
  override lazy val output: Seq[Attribute] = Nil

  /**
   * 执行删除标识字段操作。
   *
   * <p>逻辑：加载目标表，若为 {@link SparkTable} 则取出当前 schema 的标识字段集合，
   * 逐个校验待删字段存在且属于标识字段，从集合中移除后调用
   * {@code updateSchema().setIdentifierFields(set).commit()} 提交；非 Iceberg 表抛出异常。
   *
   * @return 空行序列
   */
  override protected def run(): Seq[InternalRow] = {
    catalog.loadTable(ident) match {
      case iceberg: SparkTable =>
        val schema = iceberg.table.schema
        val identifierFieldNames = Sets.newHashSet(schema.identifierFieldNames)

        for (name <- fields) {
          Preconditions.checkArgument(schema.findField(name) != null,
            "Cannot complete drop identifier fields operation: field %s not found", name)
          Preconditions.checkArgument(identifierFieldNames.contains(name),
            "Cannot complete drop identifier fields operation: %s is not an identifier field", name)
          identifierFieldNames.remove(name)
        }

        iceberg.table.updateSchema()
          .setIdentifierFields(identifierFieldNames)
          .commit();
      case table =>
        throw new UnsupportedOperationException(s"Cannot drop identifier fields in non-Iceberg table: $table")
    }

    Nil
  }
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"DropIdentifierFields ${catalog.name}.${ident.quoted} (${fields.quoted})";
  }
}
