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

import org.apache.spark.sql.catalyst.analysis.NamedRelation
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.expressions.AttributeSet

// a node similar to V2WriteCommand in Spark but does not extend Command
// as ReplaceData and WriteDelta that extend this trait are nested within other commands
/**
 * Spark Catalyst 逻辑计划节点的写入组件，负责数据写入与提交。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。
 * 类型：特质 V2WriteCommandLike。
 * <p>上下游：由解析器构造，被分析/优化规则处理。
 */
trait V2WriteCommandLike extends UnaryNode {
  def table: NamedRelation
  def query: LogicalPlan
  def outputResolved: Boolean

  override lazy val resolved: Boolean = table.resolved && query.resolved && outputResolved

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def child: LogicalPlan = query
  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def output: Seq[Attribute] = Seq.empty
  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def producedAttributes: AttributeSet = outputSet
  // Commands are eagerly executed. They will be converted to LocalRelation after the DataFrame
  // is created. That said, the statistics of a command is useless. Here we just return a dummy
  // statistics to avoid unnecessary statistics calculation of command's children.
  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def stats: Statistics = Statistics.DUMMY
}
