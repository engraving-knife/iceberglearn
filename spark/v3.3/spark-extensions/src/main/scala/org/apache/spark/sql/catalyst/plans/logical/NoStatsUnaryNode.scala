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
 * Spark Catalyst 逻辑计划节点，收集并汇报任务执行指标。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：样例类 NoStatsUnaryNode。
 * <p>上下游：由解析器构造，被分析/优化规则处理。
 */
case class NoStatsUnaryNode(child: LogicalPlan) extends UnaryNode {
  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def output: Seq[Attribute] = child.output
  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def stats: Statistics = Statistics(Long.MaxValue)

  /**
   * 返回带新设置的副本。
   * @return 结果对象
   */
  override protected def withNewChildInternal(newChild: LogicalPlan): LogicalPlan = {
    copy(child = newChild)
  }
}
