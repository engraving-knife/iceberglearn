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

package org.apache.spark.sql.catalyst.analysis

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.expressions.Cast
import org.apache.spark.sql.catalyst.plans.logical.Call
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.rules.Rule

/**
 * 存储过程参数类型强制转换规则。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions（Spark SQL 扩展层，向 Spark Catalyst
 * 注入 Iceberg 特有的语法解析、分析规则与执行节点，使 Spark 能识别 Iceberg 的 CALL/ALTER
 * 等扩展语句）。本类运行于 Spark 分析阶段（Analyzer）。
 *
 * <p>职责：
 * <ul>
 *   <li>对已解析的 {@link Call} 存储过程调用节点，按过程定义的形参类型逐一校验并转换实参。</li>
 *   <li>当实参类型与形参类型不一致时，若可向上转换则插入 {@link Cast}；若无法转换则抛出
 *       {@link AnalysisException}。</li>
 * </ul>
 *
 * <p>设计意图：Spark 调用 Iceberg 存储过程时，SQL 中书写的字面量（如字符串时间戳）类型
 * 可能与过程形参声明类型不完全一致。本规则统一在分析阶段做一次类型适配，避免各过程实现
 * 各自处理类型转换，并保证不可转换时尽早报错。仅对 resolved 的 Call 节点生效，保证类型信息已就绪。
 *
 * <p>上下游关系：作为 Catalyst {@link Rule} 被 Spark Analyzer 调用；产出仍是
 * {@link LogicalPlan}，供后续优化与物理执行阶段使用。
 */
object ProcedureArgumentCoercion extends Rule[LogicalPlan] {
  /**
   * 对已解析的 Call 节点进行参数类型强制转换。
   *
   * <p>逻辑：
   * <ol>
   *   <li>匹配已 resolved 的 {@link Call} 节点，取出过程形参列表。</li>
   *   <li>遍历实参，按下标与对应形参类型比对：类型不同且不可向上转换时抛出
   *       {@link AnalysisException}；类型不同但可向上转换时，将该实参包装为
   *       {@link Cast}。</li>
   *   <li>仅当有参数被改写时才返回新的 Call 节点，否则原样返回以避免不必要的树重建。</li>
   * </ol>
   *
   * @param plan 待分析的逻辑计划
   * @return 可能被改写参数类型的逻辑计划
   */
  override def apply(plan: LogicalPlan): LogicalPlan = plan resolveOperators {
    case c @ Call(procedure, args) if c.resolved =>
      val params = procedure.parameters

      val newArgs = args.zipWithIndex.map { case (arg, index) =>
        val param = params(index)
        val paramType = param.dataType
        val argType = arg.dataType

        if (paramType != argType && !Cast.canUpCast(argType, paramType)) {
          throw new AnalysisException(
            s"Wrong arg type for ${param.name}: cannot cast $argType to $paramType")
        }

        if (paramType != argType) {
          Cast(arg, paramType)
        } else {
          arg
        }
      }

      if (newArgs != args) {
        c.copy(args = newArgs)
      } else {
        c
      }
  }
}
