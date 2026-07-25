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
 * Spark Catalyst 分析阶段的规则或检查，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：对象 ProcedureArgumentCoercion。
 * <p>上下游：由 Spark SparkSessionExtensions 注册，作用于 Catalyst 计划。
 */
object ProcedureArgumentCoercion extends Rule[LogicalPlan] {
  /**
   * 执行核心逻辑。
   * @return 结果对象
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
