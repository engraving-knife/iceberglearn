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

import org.apache.spark.sql.catalyst.expressions.Expression
/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：Iceberg 行级命令逻辑计划基类，承载目标表与行级操作信息。
 * <p>设计意图：作为行级命令（UPDATE/DELETE/MERGE）的公共父类，供重写规则识别。
 * <p>上下游关系：被 UpdateIcebergTable / MergeIntoIcebergTable 等继承。
 */

trait RowLevelCommand extends Command with SupportsSubquery {
  /** 执行 condition 相关操作。 */
  def condition: Option[Expression]
  /** 执行 rewritePlan 相关操作。 */
  def rewritePlan: Option[LogicalPlan]
  /** 返回带 NewRewritePlan 设置的副本。 */
  def withNewRewritePlan(newRewritePlan: LogicalPlan): RowLevelCommand
}
