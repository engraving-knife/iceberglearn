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

package org.apache.iceberg.spark.extensions

import org.apache.spark.sql.SparkSessionExtensions
import org.apache.spark.sql.catalyst.analysis.ProcedureArgumentCoercion
import org.apache.spark.sql.catalyst.analysis.ResolveProcedures
import org.apache.spark.sql.catalyst.optimizer.ReplaceStaticInvoke
import org.apache.spark.sql.catalyst.parser.extensions.IcebergSparkSqlExtensionsParser
import org.apache.spark.sql.execution.datasources.v2.ExtendedDataSourceV2Strategy

/**
 * Iceberg Spark 会话扩展入口。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark v3.5 扩展模块，作为 Spark 会话插件挂载 Iceberg 自定义
 * 解析器/分析器/优化器/执行策略，是 Iceberg SQL 扩展语句得以识别和执行的注册中心）。
 *
 * <p>职责：
 * <ul>
 *   <li>实现 Spark 的 {@code SparkSessionExtensions => Unit} 函数式接口，作为
 *       {@code spark.sql.extensions} 配置项指向的扩展类。</li>
 *   <li>注入 Iceberg 自定义 SQL 解析器（识别 CALL、ALTER TABLE ... ADD PARTITION FIELD 等 Iceberg 语法）。</li>
 *   <li>注入分析期规则 {@code ResolveProcedures} 与 {@code ProcedureArgumentCoercion}，
 *       完成 CALL 语句中存储过程名解析与参数类型强转。</li>
 *   <li>注入优化期规则 {@code ReplaceStaticInvoke}，以及执行策略
 *       {@code ExtendedDataSourceV2Strategy}，把 Iceberg 逻辑算子转换为物理执行计划。</li>
 * </ul>
 *
 * <p>设计意图：通过 Spark 提供的扩展注入机制（inject* 系列），在不修改 Spark 源码的前提下完成
 * Iceberg 语法的端到端支持；解析、分析、优化、执行四个阶段各注入最小集合，保持扩展可关闭、可叠加。
 *
 * <p>上下游关系：被用户通过 {@code spark.sql.extensions=...} 配置激活；上游依赖 Spark Core SQL 模块，
 * 下游产出被 Spark Catalyst 与 Spark SQL 执行器消费的逻辑/物理计划。
 */
class IcebergSparkSessionExtensions extends (SparkSessionExtensions => Unit) {

  /**
   * 把 Iceberg 各类扩展注入到当前 SparkSession 的扩展容器中。
   *
   * <p>逻辑：依次调用 {@code injectParser} 注入 Iceberg SQL 解析器，
   * {@code injectResolutionRule} 注入两条分析期规则，{@code injectOptimizerRule}
   * 注入静态调用替换规则，{@code injectPlannerStrategy} 注入 Iceberg 执行策略。
   *
   * @param extensions 当前 SparkSession 的扩展容器
   */
  override def apply(extensions: SparkSessionExtensions): Unit = {
    // parser extensions
    extensions.injectParser { case (_, parser) => new IcebergSparkSqlExtensionsParser(parser) }

    // analyzer extensions
    extensions.injectResolutionRule { spark => ResolveProcedures(spark) }
    extensions.injectResolutionRule { _ => ProcedureArgumentCoercion }

    // optimizer extensions
    extensions.injectOptimizerRule { _ => ReplaceStaticInvoke }

    // planner extensions
    extensions.injectPlannerStrategy { spark => ExtendedDataSourceV2Strategy(spark) }
  }
}
