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

import java.util.Locale
import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.catalyst.plans.logical.Call
import org.apache.spark.sql.catalyst.plans.logical.CallArgument
import org.apache.spark.sql.catalyst.plans.logical.CallStatement
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.NamedArgument
import org.apache.spark.sql.catalyst.plans.logical.PositionalArgument
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.connector.catalog.CatalogManager
import org.apache.spark.sql.connector.catalog.CatalogPlugin
import org.apache.spark.sql.connector.catalog.LookupCatalog
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureCatalog
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter
import scala.collection.Seq

/**
 * Spark Catalyst 分析规则：解析 Iceberg 存储过程（Procedure）调用。
 *
 * <p>所属模块：iceberg-spark（spark-extensions 扩展包，扩展 Spark Catalyst 分析阶段，
 * 使 Spark SQL 支持 CALL proc(arg => value, ...) 形式的存储过程调用）。
 *
 * <p>职责：
 * <ul>
 *   <li>识别逻辑计划中的 {@link CallStatement}，将 catalog 与标识符解析为具体存储过程。</li>
 *   <li>校验过程参数（无重名、可选参数必须位于必选参数之后）。</li>
 *   <li>将调用参数（命名参数或位置参数）归一化并构建为表达式序列，
 *       生成可执行的 {@link Call} 节点。</li>
 * </ul>
 *
 * <p>设计意图：Iceberg 通过存储过程暴露表维护能力（如 expireSnapshots、rewriteDataFiles），
 * 但 Spark 原生不支持 CALL 语义，故在扩展包中增加该规则将 CALL 语句绑定到
 * {@link ProcedureCatalog} 提供的过程实现。参数名统一转小写以做到大小写不敏感，
 * 并严格区分命名参数与位置参数，二者不可混用。
 *
 * <p>上下游关系：依赖 Spark {@link CatalogManager} 与 {@link ProcedureCatalog}；
 * 作用于 Catalyst 分析阶段，输出 {@link Call} 节点供后续物理执行层调用具体 Procedure。
 */
case class ResolveProcedures(spark: SparkSession) extends Rule[LogicalPlan] with LookupCatalog {

  protected lazy val catalogManager: CatalogManager = spark.sessionState.catalogManager

  /**
   * 对逻辑计划应用本规则，将匹配到的 CALL 语句解析为已绑定的 {@link Call} 节点。
   *
   * <p>逻辑：匹配 {@link CallStatement}，从中解析出 catalog 与标识符，加载存储过程，
   * 归一化并校验参数，最后用构建好的参数表达式生成 {@link Call} 节点。
   *
   * @param plan 待分析的逻辑计划
   * @return 分析后的逻辑计划（未匹配则原样返回）
   */
  override def apply(plan: LogicalPlan): LogicalPlan = plan resolveOperators {
    case CallStatement(CatalogAndIdentifier(catalog, ident), args) =>
      val procedure = catalog.asProcedureCatalog.loadProcedure(ident)

      val params = procedure.parameters
      val normalizedParams = normalizeParams(params)
      validateParams(normalizedParams)

      val normalizedArgs = normalizeArgs(args)
      Call(procedure, args = buildArgExprs(normalizedParams, normalizedArgs).toSeq)
  }

  /**
   * 校验存储过程参数声明的合法性。
   *
   * <p>逻辑：
   * <ol>
   *   <li>按参数名分组，检测是否存在重名参数，有则抛出分析异常。</li>
   *   <li>滑动窗口检查相邻参数，确保可选参数不会出现在必选参数之前，
   *       否则位置参数绑定时会产生歧义。</li>
   * </ol>
   *
   * @param params 存储过程声明的参数序列
   * @throws AnalysisException 当存在重名参数或可选参数位于必选参数之前时抛出
   */
  private def validateParams(params: Seq[ProcedureParameter]): Unit = {
    // should not be any duplicate param names
    val duplicateParamNames = params.groupBy(_.name).collect {
      case (name, matchingParams) if matchingParams.length > 1 => name
    }

    if (duplicateParamNames.nonEmpty) {
      throw new AnalysisException(s"Duplicate parameter names: ${duplicateParamNames.mkString("[", ",", "]")}")
    }

    // optional params should be at the end
    params.sliding(2).foreach {
      case Seq(previousParam, currentParam) if !previousParam.required && currentParam.required =>
        throw new AnalysisException(
          s"Optional parameters must be after required ones but $currentParam is after $previousParam")
      case _ =>
    }
  }

  /**
   * 将调用参数绑定到过程声明参数，生成与声明顺序一致的表达式序列。
   *
   * <p>逻辑：
   * <ol>
   *   <li>构建「参数名 -> 声明位置」映射。</li>
   *   <li>调用 {@link #buildNameToArgMap} 得到「参数名 -> 调用参数」映射。</li>
   *   <li>检查所有必选参数是否都已提供，缺失则抛异常。</li>
   *   <li>按声明位置把参数表达式填入数组。</li>
   *   <li>未被赋值的可选参数填充对应类型的 null 字面量。</li>
   * </ol>
   *
   * @param params 过程声明的参数序列
   * @param args 调用方传入的参数序列
   * @return 与声明顺序对齐的表达式数组
   * @throws AnalysisException 当缺少必选参数时抛出
   */
  private def buildArgExprs(
      params: Seq[ProcedureParameter],
      args: Seq[CallArgument]): Seq[Expression] = {

    // build a map of declared parameter names to their positions
    val nameToPositionMap = params.map(_.name).zipWithIndex.toMap

    // build a map of parameter names to args
    val nameToArgMap = buildNameToArgMap(params, args, nameToPositionMap)

    // verify all required parameters are provided
    val missingParamNames = params.filter(_.required).collect {
      case param if !nameToArgMap.contains(param.name) => param.name
    }

    if (missingParamNames.nonEmpty) {
      throw new AnalysisException(s"Missing required parameters: ${missingParamNames.mkString("[", ",", "]")}")
    }

    val argExprs = new Array[Expression](params.size)

    nameToArgMap.foreach { case (name, arg) =>
      val position = nameToPositionMap(name)
      argExprs(position) = arg.expr
    }

    // assign nulls to optional params that were not set
    params.foreach {
      case p if !p.required && !nameToArgMap.contains(p.name) =>
        val position = nameToPositionMap(p.name)
        argExprs(position) = Literal.create(null, p.dataType)
      case _ =>
    }

    argExprs
  }

  /**
   * 根据调用参数类型（命名或位置）选择对应的映射构建方式。
   *
   * <p>逻辑：检测参数中是否存在命名参数与位置参数，二者不可混用；
   * 全为命名参数走 {@link #buildNameToArgMapUsingNames}，否则走
   * {@link #buildNameToArgMapUsingPositions}。
   *
   * @throws AnalysisException 当命名参数与位置参数混用时抛出
   */
  private def buildNameToArgMap(
      params: Seq[ProcedureParameter],
      args: Seq[CallArgument],
      nameToPositionMap: Map[String, Int]): Map[String, CallArgument] = {

    val containsNamedArg = args.exists(_.isInstanceOf[NamedArgument])
    val containsPositionalArg = args.exists(_.isInstanceOf[PositionalArgument])

    if (containsNamedArg && containsPositionalArg) {
      throw new AnalysisException("Named and positional arguments cannot be mixed")
    }

    if (containsNamedArg) {
      buildNameToArgMapUsingNames(args, nameToPositionMap)
    } else {
      buildNameToArgMapUsingPositions(args, params)
    }
  }

  /**
   * 按命名参数方式构建「参数名 -> 调用参数」映射。
   *
   * <p>逻辑：将参数转为命名参数集合，按名分组检测重名，并校验每个名字都在声明参数中存在；
   * 校验通过后转为映射返回。
   *
   * @throws AnalysisException 当存在重复命名参数或未知参数名时抛出
   */
  private def buildNameToArgMapUsingNames(
      args: Seq[CallArgument],
      nameToPositionMap: Map[String, Int]): Map[String, CallArgument] = {

    val namedArgs = args.asInstanceOf[Seq[NamedArgument]]

    val validationErrors = namedArgs.groupBy(_.name).collect {
      case (name, matchingArgs) if matchingArgs.size > 1 => s"Duplicate procedure argument: $name"
      case (name, _) if !nameToPositionMap.contains(name) => s"Unknown argument: $name"
    }

    if (validationErrors.nonEmpty) {
      throw new AnalysisException(s"Could not build name to arg map: ${validationErrors.mkString(", ")}")
    }

    namedArgs.map(arg => arg.name -> arg).toMap
  }

  /**
   * 按位置参数方式构建「参数名 -> 调用参数」映射。
   *
   * <p>逻辑：先校验参数数量不超过声明数量，再按位置序号将每个调用参数绑定到对应声明参数名。
   *
   * @throws AnalysisException 当参数数量超过声明参数数量时抛出
   */
  private def buildNameToArgMapUsingPositions(
      args: Seq[CallArgument],
      params: Seq[ProcedureParameter]): Map[String, CallArgument] = {

    if (args.size > params.size) {
      throw new AnalysisException("Too many arguments for procedure")
    }

    args.zipWithIndex.map { case (arg, position) =>
      val param = params(position)
      param.name -> arg
    }.toMap
  }

  /**
   * 将过程声明参数名统一转为小写，返回新的参数序列。
   *
   * <p>设计要点：使参数名大小写不敏感，保留必选/可选属性与数据类型不变。
   */
  private def normalizeParams(params: Seq[ProcedureParameter]): Seq[ProcedureParameter] = {
    params.map {
      case param if param.required =>
        val normalizedName = param.name.toLowerCase(Locale.ROOT)
        ProcedureParameter.required(normalizedName, param.dataType)
      case param =>
        val normalizedName = param.name.toLowerCase(Locale.ROOT)
        ProcedureParameter.optional(normalizedName, param.dataType)
    }
  }

  /**
   * 将命名调用参数的参数名统一转为小写，与归一化后的声明参数对齐。
   *
   * <p>设计要点：位置参数无需归一化，原样返回。
   */
  private def normalizeArgs(args: Seq[CallArgument]): Seq[CallArgument] = {
    args.map {
      case a @ NamedArgument(name, _) => a.copy(name = name.toLowerCase(Locale.ROOT))
      case other => other
    }
  }

  /** 隐式类：为 {@link CatalogPlugin} 增加转换为 {@link ProcedureCatalog} 的便捷方法。 */
  implicit class CatalogHelper(plugin: CatalogPlugin) {
    /**
     * 将当前 catalog 插件转换为存储过程 catalog。
     *
     * @throws AnalysisException 当 catalog 未实现 {@link ProcedureCatalog} 时抛出
     */
    def asProcedureCatalog: ProcedureCatalog = plugin match {
      case procedureCatalog: ProcedureCatalog =>
        procedureCatalog
      case _ =>
        throw new AnalysisException(s"Cannot use catalog ${plugin.name}: not a ProcedureCatalog")
    }
  }
}
