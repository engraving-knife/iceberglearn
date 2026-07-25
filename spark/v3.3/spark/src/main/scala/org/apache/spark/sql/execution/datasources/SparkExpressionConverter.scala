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

package org.apache.spark.sql.execution.datasources

import org.apache.iceberg.spark.SparkFilters
import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.catalyst.plans.logical.Filter
import org.apache.spark.sql.catalyst.plans.logical.LeafNode
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation

/**
 * Spark 物理执行相关组件，负责类型或表达式转换。
 *
 * <p>所属模块：iceberg-spark v3.3。
 * 类型：对象 SparkExpressionConverter。
 * <p>设计意图：适配器模式，桥接两套 API。
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
object SparkExpressionConverter {

  /**
   * 把输入转换为另一种表示。
   * @return 结果对象
   */
  def convertToIcebergExpression(sparkExpression: Expression): org.apache.iceberg.expressions.Expression = {
    // Currently, it is a double conversion as we are converting Spark expression to Spark filter
    // and then converting Spark filter to Iceberg expression.
    // But these two conversions already exist and well tested. So, we are going with this approach.
    DataSourceStrategy.translateFilter(sparkExpression, supportNestedPredicatePushdown = true) match {
      case Some(filter) =>
        val converted = SparkFilters.convert(filter)
        if (converted == null) {
          throw new IllegalArgumentException(s"Cannot convert Spark filter: $filter to Iceberg expression")
        }

        converted
      case _ =>
        throw new IllegalArgumentException(s"Cannot translate Spark expression: $sparkExpression to data source filter")
    }
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  @throws[AnalysisException]
  def collectResolvedSparkExpression(session: SparkSession, tableName: String, where: String): Expression = {
    val tableAttrs = session.table(tableName).queryExecution.analyzed.output
    val unresolvedExpression = session.sessionState.sqlParser.parseExpression(where)
    val filter = Filter(unresolvedExpression, DummyRelation(tableAttrs))
    val optimizedLogicalPlan = session.sessionState.executePlan(filter).optimizedPlan
    optimizedLogicalPlan.collectFirst {
      case filter: Filter => filter.condition
      case dummyRelation: DummyRelation => Literal.TrueLiteral
      case localRelation: LocalRelation => Literal.FalseLiteral
    }.getOrElse(throw new AnalysisException("Failed to find filter expression"))
  }

  /**
   * Spark 物理执行相关组件。
   *
   * <p>所属模块：iceberg-spark v3.3。
   * 类型：样例类 DummyRelation。
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  case class DummyRelation(output: Seq[Attribute]) extends LeafNode
}
