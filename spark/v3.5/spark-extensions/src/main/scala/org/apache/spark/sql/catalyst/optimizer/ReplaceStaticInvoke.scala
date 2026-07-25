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
package org.apache.spark.sql.catalyst.optimizer

import org.apache.iceberg.spark.functions.SparkFunctions
import org.apache.spark.sql.catalyst.expressions.ApplyFunctionExpression
import org.apache.spark.sql.catalyst.expressions.BinaryComparison
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.objects.StaticInvoke
import org.apache.spark.sql.catalyst.plans.logical.Filter
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.catalyst.trees.TreePattern.BINARY_COMPARISON
import org.apache.spark.sql.catalyst.trees.TreePattern.FILTER
import org.apache.spark.sql.connector.catalog.functions.ScalarFunction
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType

/**
 * Iceberg 系统函数下推重写规则。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark 3.5 的 Catalyst 扩展模块，向 Spark 引擎
 * 注册自定义优化规则与逻辑算子，使 Iceberg 表能被 Spark 正确识别与下推）。
 *
 * <p>职责：
 * <ul>
 *   <li>Spark 在解析阶段会把 Iceberg 系统函数（如 bucket/truncate 等）解析为
 *       {@link StaticInvoke} 节点，而 {@code StaticInvoke} 无法被数据源下推执行。</li>
 *   <li>本规则在 Filter 条件中识别此类 {@code StaticInvoke}，并将其重写为
 *       {@link ApplyFunctionExpression}，使其能够下推到 Iceberg 数据源。</li>
 * </ul>
 *
 * <p>设计意图：Spark 的函数解析对系统函数采用静态调用形式，与 Iceberg 自身的
 * {@link org.apache.iceberg.spark.functions.SparkFunctions} 注册机制不兼容。通过 Catalyst
 * 规则在物理计划生成前介入重写，避免对 Spark 内核做侵入式改造，同时利用
 * {@code transformWithPruning} 按树模式剪枝，仅命中同时包含二值比较与 Filter 的节点，
 * 降低遍历开销。
 *
 * <p>上下游关系：依赖 {@link org.apache.iceberg.spark.functions.SparkFunctions} 加载函数；
 * 由 Spark Catalyst 优化器在规则批次中调用；其产出的 {@code ApplyFunctionExpression}
 * 会被 Iceberg 数据源进一步识别为可下推的过滤条件。
 */
object ReplaceStaticInvoke extends Rule[LogicalPlan] {

  /**
   * 对逻辑计划中的 Filter 条件进行重写：将其中的 Iceberg 系统 {@link StaticInvoke}
   * 替换为 {@link ApplyFunctionExpression}。
   *
   * <p>逻辑：
   * <ol>
   *   <li>使用 {@code transformWithPruning} 仅在同时包含 BINARY_COMPARISON 与 FILTER 模式
   *       的子树中进入处理，避免无谓遍历。</li>
   *   <li>对 Filter 的 condition 再做一次按 BINARY_COMPARISON 模式剪枝的变换：
   *       当 BinaryComparison 的某一侧是可替换的 StaticInvoke、另一侧是 foldable 常量时，
   *       把该 StaticInvoke 重写为 ApplyFunctionExpression。</li>
   *   <li>若条件未发生变化（fastEquals），直接返回原 Filter，避免产生新对象。</li>
   * </ol>
   *
   * @param plan 待处理的逻辑计划
   * @return 重写后的逻辑计划（可能为原对象）
   */
  override def apply(plan: LogicalPlan): LogicalPlan =
    plan.transformWithPruning (_.containsAllPatterns(BINARY_COMPARISON, FILTER)) {
      case filter @ Filter(condition, _) =>
        val newCondition = condition.transformWithPruning(_.containsPattern(BINARY_COMPARISON)) {
          case c @ BinaryComparison(left: StaticInvoke, right) if canReplace(left) && right.foldable =>
            c.withNewChildren(Seq(replaceStaticInvoke(left), right))

          case c @ BinaryComparison(left, right: StaticInvoke) if canReplace(right) && left.foldable =>
            c.withNewChildren(Seq(left, replaceStaticInvoke(right)))
        }

        if (newCondition fastEquals condition) {
          filter
        } else {
          filter.copy(condition = newCondition)
        }
  }

  /**
   * 将一个 {@link StaticInvoke} 重写为 {@link ApplyFunctionExpression}。
   *
   * <p>逻辑：借鉴 Spark 内部 {@code ResolveFunctions.resolveV2Function} 的实现思路——
   * 通过 StaticInvoke 的 staticObject（函数实现类）加载未绑定的 Iceberg 函数，依据
   * 参数构造输入类型并尝试 bind；若绑定成功且参数数量一致，则把标量函数包装为
   * ApplyFunctionExpression 返回。任何一步失败（找不到函数、绑定异常、参数数不匹配、
   * 非标量函数）都直接返回原 invoke，保证安全降级。
   *
   * @param invoke 待重写的静态调用节点
   * @return 重写后的表达式，或原节点（无法重写时）
   */
  private def replaceStaticInvoke(invoke: StaticInvoke): Expression = {
    // Adaptive from `resolveV2Function` in org.apache.spark.sql.catalyst.analysis.ResolveFunctions
    val unbound = SparkFunctions.loadFunctionByClass(invoke.staticObject)
    if (unbound == null) {
      return invoke
    }

    val inputType = StructType(invoke.arguments.zipWithIndex.map {
      case (exp, pos) => StructField(s"_$pos", exp.dataType, exp.nullable)
    })

    val bound = try {
      unbound.bind(inputType)
    } catch {
      case _: Exception =>
        return invoke
    }

    if (bound.inputTypes().length != invoke.arguments.length) {
      return invoke
    }

    bound match {
      case scalarFunc: ScalarFunction[_] =>
        ApplyFunctionExpression(scalarFunc, invoke.arguments)
      case _ => invoke
    }
  }

  /**
   * 判断给定的 {@link StaticInvoke} 是否可以替换为 Iceberg 函数表达式。
   *
   * <p>判定条件：函数名为 {@link ScalarFunction#MAGIC_METHOD_NAME}（Iceberg 魔法方法约定），
   * 且自身不可折叠（即依赖运行时输入，不能被常量折叠消除），否则无需替换。
   *
   * @param invoke 待判定的静态调用节点
   * @return true 表示可替换
   */
  @inline
  private def canReplace(invoke: StaticInvoke): Boolean = {
    invoke.functionName == ScalarFunction.MAGIC_METHOD_NAME && !invoke.foldable
  }
}
