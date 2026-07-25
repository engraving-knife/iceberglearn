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
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：不携带统计的一元逻辑计划节点基类，为 Iceberg 命令节点提供公共骨架。
 * <p>设计意图：屏蔽 Spark 对命令节点统计信息的查询，避免不必要计算。
 * <p>上下游关系：被 Iceberg 命令逻辑节点继承。
 */

case class NoStatsUnaryNode(child: LogicalPlan) extends UnaryNode {
  /** 执行 output 相关操作。 */
  override def output: Seq[Attribute] = child.output
  /** 执行 stats 相关操作。 */
  override def stats: Statistics = Statistics(Long.MaxValue)
  /** 返回带 NewChildInternal 设置的副本。 */

  override protected def withNewChildInternal(newChild: LogicalPlan): LogicalPlan = {
    copy(child = newChild)
  }
}
