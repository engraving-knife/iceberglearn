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

import java.util.Optional
import java.util.UUID
import org.apache.spark.sql.catalyst.expressions.PredicateHelper
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.Project
import org.apache.spark.sql.catalyst.plans.logical.ReplaceIcebergData
import org.apache.spark.sql.catalyst.plans.logical.WriteIcebergDelta
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.catalyst.util.WriteDeltaProjections
import org.apache.spark.sql.connector.catalog.Table
import org.apache.spark.sql.connector.write.DeltaWriteBuilder
import org.apache.spark.sql.connector.write.LogicalWriteInfoImpl
import org.apache.spark.sql.connector.write.WriteBuilder
import org.apache.spark.sql.types.StructType

/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：扩展的 V2 写入规则对象，为 Iceberg 写命令配置分布与排序并接入提交。
 * <p>设计意图：在物理计划阶段注入写入分布/排序与提交信息。
 * <p>上下游关系：由 IcebergSparkSessionExtensions 注册。
 */
object ExtendedV2Writes extends Rule[LogicalPlan] with PredicateHelper {

  import DataSourceV2Implicits._
  /** 应用转换。 */

  override def apply(plan: LogicalPlan): LogicalPlan = plan transformDown {
    case rd @ ReplaceIcebergData(r: DataSourceV2Relation, query, _, None) =>
      val rowSchema = StructType.fromAttributes(rd.dataInput)
      val writeBuilder = newWriteBuilder(r.table, rowSchema, Map.empty)
      val write = writeBuilder.build()
      val newQuery = DistributionAndOrderingUtils.prepareQuery(write, query, r.funCatalog)
      rd.copy(write = Some(write), query = Project(rd.dataInput, newQuery))

    case wd @ WriteIcebergDelta(r: DataSourceV2Relation, query, _, projections, None) =>
      val deltaWriteBuilder = newDeltaWriteBuilder(r.table, Map.empty, projections)
      val deltaWrite = deltaWriteBuilder.build()
      val newQuery = DistributionAndOrderingUtils.prepareQuery(deltaWrite, query, r.funCatalog)
      wd.copy(write = Some(deltaWrite), query = newQuery)
  }
  /** 创建 WriteBuilder 实例。 */

  private def newWriteBuilder(
      table: Table,
      rowSchema: StructType,
      writeOptions: Map[String, String],
      queryId: String = UUID.randomUUID().toString): WriteBuilder = {

    val info = LogicalWriteInfoImpl(queryId, rowSchema, writeOptions.asOptions)
    table.asWritable.newWriteBuilder(info)
  }
  /** 创建 DeltaWriteBuilder 实例。 */

  private def newDeltaWriteBuilder(
      table: Table,
      writeOptions: Map[String, String],
      projections: WriteDeltaProjections,
      queryId: String = UUID.randomUUID().toString): DeltaWriteBuilder = {

    val rowSchema = projections.rowProjection.map(_.schema).getOrElse(StructType(Nil))
    val rowIdSchema = projections.rowIdProjection.schema
    val metadataSchema = projections.metadataProjection.map(_.schema)

    val info = LogicalWriteInfoImpl(
      queryId,
      rowSchema,
      writeOptions.asOptions,
      Optional.of(rowIdSchema),
      Optional.ofNullable(metadataSchema.orNull))

    val writeBuilder = table.asWritable.newWriteBuilder(info)
    assert(writeBuilder.isInstanceOf[DeltaWriteBuilder], s"$writeBuilder must be DeltaWriteBuilder")
    writeBuilder.asInstanceOf[DeltaWriteBuilder]
  }
}
