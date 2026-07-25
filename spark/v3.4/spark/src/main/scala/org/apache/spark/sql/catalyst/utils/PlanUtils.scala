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

package org.apache.spark.sql.catalyst.utils

import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.SubqueryAlias
import org.apache.spark.sql.execution.datasources.v2.DataSourceV2Relation
import scala.annotation.tailrec
/**
 * 所属模块：iceberg-spark v3.4
 * <p>职责：计划工具对象，提供 Iceberg 扩展层常用的计划构建与转换辅助方法。
 * <p>设计意图：集中放置逻辑计划相关公共操作，供分析/重写规则复用。
 * <p>上下游关系：由 RewriteMergeIntoTable / RewriteUpdateTable 等使用。
 */

object PlanUtils {
  /** 判断是否 IcebergRelation。 */
  @tailrec
  def isIcebergRelation(plan: LogicalPlan): Boolean = {
    /** 判断是否 IcebergTable。 */
    def isIcebergTable(relation: DataSourceV2Relation): Boolean = relation.table match {
      case _: SparkTable => true
      case _ => false
    }

    plan match {
      case s: SubqueryAlias => isIcebergRelation(s.child)
      case r: DataSourceV2Relation => isIcebergTable(r)
      case _ => false
    }
  }
}
