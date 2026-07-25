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

package org.apache.spark.sql.catalyst.parser.extensions

import java.util.Locale
import java.util.concurrent.TimeUnit
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.apache.iceberg.DistributionMode
import org.apache.iceberg.NullOrder
import org.apache.iceberg.SortDirection
import org.apache.iceberg.expressions.Term
import org.apache.iceberg.spark.Spark3Util
import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.catalyst.parser.ParserInterface
import org.apache.spark.sql.catalyst.parser.extensions.IcebergParserUtils.withOrigin
import org.apache.spark.sql.catalyst.parser.extensions.IcebergSqlExtensionsParser._
import org.apache.spark.sql.catalyst.plans.logical.AddPartitionField
import org.apache.spark.sql.catalyst.plans.logical.BranchOptions
import org.apache.spark.sql.catalyst.plans.logical.CallArgument
import org.apache.spark.sql.catalyst.plans.logical.CallStatement
import org.apache.spark.sql.catalyst.plans.logical.CreateOrReplaceBranch
import org.apache.spark.sql.catalyst.plans.logical.CreateOrReplaceTag
import org.apache.spark.sql.catalyst.plans.logical.DropBranch
import org.apache.spark.sql.catalyst.plans.logical.DropIdentifierFields
import org.apache.spark.sql.catalyst.plans.logical.DropPartitionField
import org.apache.spark.sql.catalyst.plans.logical.DropTag
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.NamedArgument
import org.apache.spark.sql.catalyst.plans.logical.PositionalArgument
import org.apache.spark.sql.catalyst.plans.logical.ReplacePartitionField
import org.apache.spark.sql.catalyst.plans.logical.SetIdentifierFields
import org.apache.spark.sql.catalyst.plans.logical.SetWriteDistributionAndOrdering
import org.apache.spark.sql.catalyst.plans.logical.TagOptions
import org.apache.spark.sql.catalyst.trees.CurrentOrigin
import org.apache.spark.sql.catalyst.trees.Origin
import org.apache.spark.sql.connector.expressions
import org.apache.spark.sql.connector.expressions.ApplyTransform
import org.apache.spark.sql.connector.expressions.FieldReference
import org.apache.spark.sql.connector.expressions.IdentityTransform
import org.apache.spark.sql.connector.expressions.LiteralValue
import org.apache.spark.sql.connector.expressions.Transform
import scala.jdk.CollectionConverters._

/**
 * Iceberg SQL 扩展语法的 AST 构建器。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions（Spark SQL 扩展层）。本类是 Iceberg 自定义
 * ANTLR 语法（IcebergSqlExtensions）的访问者，负责把语法树节点转换成 Catalyst 逻辑计划
 * （LogicalPlan）/表达式，从而让 Spark 识别 Iceberg 扩展的 CALL、ALTER TABLE ... ADD/DROP
 * PARTITION FIELD、CREATE/REPLACE BRANCH/TAG、SET IDENTIFIER FIELDS、写入分布与排序等语句。
 *
 * <p>职责：
 * <ul>
 *   <li>实现 {@link IcebergSqlExtensionsBaseVisitor} 的各 visit 方法，将解析树映射为对应逻辑命令。</li>
 *   <li>对委托给 Spark 原生解析器的部分（如普通表达式、常量），通过 delegate 重新解析以复用 Spark 能力。</li>
 *   <li>维护解析位置（Origin）信息，便于错误定位。</li>
 * </ul>
 *
 * <p>设计意图：Iceberg 需要扩展 Spark SQL 语法但又不希望侵入 Spark 内部，因此采用独立 ANTLR
 * 语法 + 独立 AstBuilder 的方式，仅在解析扩展语句时介入，其余交回 Spark 原生解析器。委托
 * （delegate）模式使其能与不同 Spark 版本的解析器协作。
 *
 * <p>上下游关系：由 {@link org.apache.spark.sql.catalyst.parser.extensions.IcebergSparkSqlExtensionsParser}
 * 调用；产出的逻辑计划进入 Spark Catalyst 分析与执行阶段，部分命令由本扩展对应的 Exec 节点执行。
 */
class IcebergSqlExtensionsAstBuilder(delegate: ParserInterface) extends IcebergSqlExtensionsBaseVisitor[AnyRef] {
  /** 转换为 Buffer。 */

  private def toBuffer[T](list: java.util.List[T]): scala.collection.mutable.Buffer[T] = list.asScala
  /** 转换为 Seq。 */
  private def toSeq[T](list: java.util.List[T]): Seq[T] = toBuffer(list).toSeq

  /**
   * 构造存储过程调用对应的 {@link CallStatement}。
   */
  override def visitCall(ctx: CallContext): CallStatement = withOrigin(ctx) {
    val name = toSeq(ctx.multipartIdentifier.parts).map(_.getText)
    val args = toSeq(ctx.callArgument).map(typedVisit[CallArgument])
    CallStatement(name, args)
  }

  /**
   * 构造 ADD PARTITION FIELD 逻辑命令。
   */
  override def visitAddPartitionField(ctx: AddPartitionFieldContext): AddPartitionField = withOrigin(ctx) {
    AddPartitionField(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      typedVisit[Transform](ctx.transform),
      Option(ctx.name).map(_.getText))
  }

  /**
   * 构造 DROP PARTITION FIELD 逻辑命令。
   */
  override def visitDropPartitionField(ctx: DropPartitionFieldContext): DropPartitionField = withOrigin(ctx) {
    DropPartitionField(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      typedVisit[Transform](ctx.transform))
  }

  /**
   * 构造 CREATE OR REPLACE BRANCH 逻辑命令。
   *
   * <p>逻辑：解析分支名、分支选项（快照 ID、最小保留快照数、最大快照年龄、分支引用保留时长），
   * 以及 create/replace/ifNotExists 标志，组装成 {@link CreateOrReplaceBranch}。
   */
  override def visitCreateOrReplaceBranch(ctx: CreateOrReplaceBranchContext): CreateOrReplaceBranch = withOrigin(ctx) {
    val createOrReplaceBranchClause = ctx.createReplaceBranchClause()

    val branchName = createOrReplaceBranchClause.identifier()
    val branchOptionsContext = Option(createOrReplaceBranchClause.branchOptions())
    val snapshotId = branchOptionsContext.flatMap(branchOptions => Option(branchOptions.snapshotId()))
      .map(_.getText.toLong)
    val snapshotRetention = branchOptionsContext.flatMap(branchOptions => Option(branchOptions.snapshotRetention()))
    val minSnapshotsToKeep = snapshotRetention.flatMap(retention => Option(retention.minSnapshotsToKeep()))
      .map(minSnapshots => minSnapshots.number().getText.toLong)
    val maxSnapshotAgeMs = snapshotRetention
      .flatMap(retention => Option(retention.maxSnapshotAge()))
      .map(retention => TimeUnit.valueOf(retention.timeUnit().getText.toUpperCase(Locale.ENGLISH))
        .toMillis(retention.number().getText.toLong))
    val branchRetention = branchOptionsContext.flatMap(branchOptions => Option(branchOptions.refRetain()))
    val branchRefAgeMs = branchRetention.map(retain =>
      TimeUnit.valueOf(retain.timeUnit().getText.toUpperCase(Locale.ENGLISH)).toMillis(retain.number().getText.toLong))
    val create = createOrReplaceBranchClause.CREATE() != null
    val replace = ctx.createReplaceBranchClause().REPLACE() != null
    val ifNotExists = createOrReplaceBranchClause.EXISTS() != null

    val branchOptions = BranchOptions(
      snapshotId,
      minSnapshotsToKeep,
      maxSnapshotAgeMs,
      branchRefAgeMs
    )

    CreateOrReplaceBranch(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      branchName.getText,
      branchOptions,
      create,
      replace,
      ifNotExists)
  }

  /**
   * 构造 CREATE OR REPLACE TAG 逻辑命令。
   *
   * <p>逻辑：解析标签名、标签选项（快照 ID、标签引用保留时长）及 create/replace/ifNotExists
   * 标志，组装成 {@link CreateOrReplaceTag}。
   */
  override def visitCreateOrReplaceTag(ctx: CreateOrReplaceTagContext): CreateOrReplaceTag = withOrigin(ctx) {
    val createTagClause = ctx.createReplaceTagClause()

    val tagName = createTagClause.identifier().getText

    val tagOptionsContext = Option(createTagClause.tagOptions())
    val snapshotId = tagOptionsContext.flatMap(tagOptions => Option(tagOptions.snapshotId()))
      .map(_.getText.toLong)
    val tagRetain = tagOptionsContext.flatMap(tagOptions => Option(tagOptions.refRetain()))
    val tagRefAgeMs = tagRetain.map(retain =>
      TimeUnit.valueOf(retain.timeUnit().getText.toUpperCase(Locale.ENGLISH)).toMillis(retain.number().getText.toLong))
    val tagOptions = TagOptions(
      snapshotId,
      tagRefAgeMs
    )

    val create = createTagClause.CREATE() != null
    val replace = createTagClause.REPLACE() != null
    val ifNotExists = createTagClause.EXISTS() != null

    CreateOrReplaceTag(typedVisit[Seq[String]](ctx.multipartIdentifier),
      tagName,
      tagOptions,
      create,
      replace,
      ifNotExists)
  }

  /**
   * 构造 DROP BRANCH 逻辑命令。
   */
  override def visitDropBranch(ctx: DropBranchContext): DropBranch = withOrigin(ctx) {
    DropBranch(typedVisit[Seq[String]](ctx.multipartIdentifier), ctx.identifier().getText, ctx.EXISTS() != null)
  }

  /**
   * 构造 DROP TAG 逻辑命令。
   */
  override def visitDropTag(ctx: DropTagContext): DropTag = withOrigin(ctx) {
    DropTag(typedVisit[Seq[String]](ctx.multipartIdentifier), ctx.identifier().getText, ctx.EXISTS() != null)
  }

  /**
   * 构造 REPLACE PARTITION FIELD 逻辑命令。
   */
  override def visitReplacePartitionField(ctx: ReplacePartitionFieldContext): ReplacePartitionField = withOrigin(ctx) {
    ReplacePartitionField(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      typedVisit[Transform](ctx.transform(0)),
      typedVisit[Transform](ctx.transform(1)),
      Option(ctx.name).map(_.getText))
  }

  /**
   * 构造 SET IDENTIFIER FIELDS 逻辑命令。
   */
  override def visitSetIdentifierFields(ctx: SetIdentifierFieldsContext): SetIdentifierFields = withOrigin(ctx) {
    SetIdentifierFields(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      toSeq(ctx.fieldList.fields).map(_.getText))
  }

  /**
   * 构造 DROP IDENTIFIER FIELDS 逻辑命令。
   */
  override def visitDropIdentifierFields(ctx: DropIdentifierFieldsContext): DropIdentifierFields = withOrigin(ctx) {
    DropIdentifierFields(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      toSeq(ctx.fieldList.fields).map(_.getText))
  }

  /**
   * 构造修改写入分布与排序的 {@link SetWriteDistributionAndOrdering} 命令。
   *
   * <p>逻辑：从 writeSpec 中提取分布与排序子句，校验不能同时缺失、不能重复；根据是否存在
   * 分布子句或 UNORDERED/LOCALLY 标记推导 {@link DistributionMode}（HASH/NONE/RANGE），
   * 再解析排序字段列表，最终组装命令。
   */
  override def visitSetWriteDistributionAndOrdering(
      ctx: SetWriteDistributionAndOrderingContext): SetWriteDistributionAndOrdering = {

    val tableName = typedVisit[Seq[String]](ctx.multipartIdentifier)

    val (distributionSpec, orderingSpec) = toDistributionAndOrderingSpec(ctx.writeSpec)

    if (distributionSpec == null && orderingSpec == null) {
      throw new AnalysisException(
        "ALTER TABLE has no changes: missing both distribution and ordering clauses")
    }

    val distributionMode = if (distributionSpec != null) {
      DistributionMode.HASH
    } else if (orderingSpec.UNORDERED != null || orderingSpec.LOCALLY != null) {
      DistributionMode.NONE
    } else {
      DistributionMode.RANGE
    }

    val ordering = if (orderingSpec != null && orderingSpec.order != null) {
      toSeq(orderingSpec.order.fields).map(typedVisit[(Term, SortDirection, NullOrder)])
    } else {
      Seq.empty
    }

    SetWriteDistributionAndOrdering(tableName, distributionMode, ordering)
  }

  /**
   * 从 writeSpec 中提取分布与排序子句，并校验各自最多出现一次。
   *
   * @return (分布子句, 排序子句)，缺失时对应元素为 null
   */
  private def toDistributionAndOrderingSpec(
      writeSpec: WriteSpecContext): (WriteDistributionSpecContext, WriteOrderingSpecContext) = {

    if (writeSpec.writeDistributionSpec.size > 1) {
      throw new AnalysisException("ALTER TABLE contains multiple distribution clauses")
    }

    if (writeSpec.writeOrderingSpec.size > 1) {
      throw new AnalysisException("ALTER TABLE contains multiple ordering clauses")
    }

    val distributionSpec = toBuffer(writeSpec.writeDistributionSpec).headOption.orNull
    val orderingSpec = toBuffer(writeSpec.writeOrderingSpec).headOption.orNull

    (distributionSpec, orderingSpec)
  }

  /**
   * 构造一个排序字段，返回 (Term, SortDirection, NullOrder) 三元组。
   *
   * <p>逻辑：将 Transform 转为 Iceberg Term，按 ASC/DESC 决定排序方向（默认 ASC），
   * 按 FIRST/LAST 决定空值顺序（默认随方向：ASC→NULLS_FIRST，DESC→NULLS_LAST）。
   */
  override def visitOrderField(ctx: OrderFieldContext): (Term, SortDirection, NullOrder) = {
    val term = Spark3Util.toIcebergTerm(typedVisit[Transform](ctx.transform))
    val direction = Option(ctx.ASC).map(_ => SortDirection.ASC)
        .orElse(Option(ctx.DESC).map(_ => SortDirection.DESC))
        .getOrElse(SortDirection.ASC)
    val nullOrder = Option(ctx.FIRST).map(_ => NullOrder.NULLS_FIRST)
        .orElse(Option(ctx.LAST).map(_ => NullOrder.NULLS_LAST))
        .getOrElse(if (direction == SortDirection.ASC) NullOrder.NULLS_FIRST else NullOrder.NULLS_LAST)
    (term, direction, nullOrder)
  }

  /**
   * 为列引用构造 IdentityTransform（恒等分区变换）。
   */
  override def visitIdentityTransform(ctx: IdentityTransformContext): Transform = withOrigin(ctx) {
    IdentityTransform(FieldReference(typedVisit[Seq[String]](ctx.multipartIdentifier())))
  }

  /**
   * 从参数表达式构造命名 Transform（如 bucket/truncate 等 apply 变换）。
   */
  override def visitApplyTransform(ctx: ApplyTransformContext): Transform = withOrigin(ctx) {
    val args = toSeq(ctx.arguments).map(typedVisit[expressions.Expression])
    ApplyTransform(ctx.transformName.getText, args)
  }

  /**
   * 构造变换参数：列引用或常量。
   */
  override def visitTransformArgument(ctx: TransformArgumentContext): expressions.Expression = withOrigin(ctx) {
    val reference = Option(ctx.multipartIdentifier())
        .map(typedVisit[Seq[String]])
        .map(FieldReference(_))
    val literal = Option(ctx.constant)
        .map(visitConstant)
        .map(lit => LiteralValue(lit.value, lit.dataType))
    reference.orElse(literal)
        .getOrElse(throw new IcebergParseException(s"Invalid transform argument", ctx))
  }

  /**
   * 将多段标识符解析为 Seq[String]。
   */
  override def visitMultipartIdentifier(ctx: MultipartIdentifierContext): Seq[String] = withOrigin(ctx) {
    toSeq(ctx.parts).map(_.getText)
  }

  /** 解析单个 order 子句，返回排序字段三元组列表。 */
  override def visitSingleOrder(ctx: SingleOrderContext): Seq[(Term, SortDirection, NullOrder)] = withOrigin(ctx) {
    toSeq(ctx.order.fields).map(typedVisit[(Term, SortDirection, NullOrder)])
  }

  /**
   * 构造存储过程调用中的位置参数。
   */
  override def visitPositionalArgument(ctx: PositionalArgumentContext): CallArgument = withOrigin(ctx) {
    val expr = typedVisit[Expression](ctx.expression)
    PositionalArgument(expr)
  }

  /**
   * 构造存储过程调用中的命名参数。
   */
  override def visitNamedArgument(ctx: NamedArgumentContext): CallArgument = withOrigin(ctx) {
    val name = ctx.identifier.getText
    val expr = typedVisit[Expression](ctx.expression)
    NamedArgument(name, expr)
  }

  /** 访问单条语句并返回其逻辑计划。 */
  override def visitSingleStatement(ctx: SingleStatementContext): LogicalPlan = withOrigin(ctx) {
    visit(ctx.statement).asInstanceOf[LogicalPlan]
  }

  /** 将常量上下文委托给 Spark 原生解析器解析为 {@link Literal}。 */
  def visitConstant(ctx: ConstantContext): Literal = {
    delegate.parseExpression(ctx.getText).asInstanceOf[Literal]
  }

  /**
   * 访问表达式节点。
   *
   * <p>逻辑：通过 {@link #reconstructSqlString} 递归重建表达式对应的 SQL 文本，再交给
   * Spark 原生解析器解析，避免在本扩展中重新实现 Spark 表达式构造逻辑。不能直接用
   * ctx.getText，因其无法正确还原空格。
   */
  override def visitExpression(ctx: ExpressionContext): Expression = {
    // reconstruct the SQL string and parse it using the main Spark parser
    // while we can avoid the logic to build Spark expressions, we still have to parse them
    // we cannot call ctx.getText directly since it will not render spaces correctly
    // that's why we need to recurse down the tree in reconstructSqlString
    val sqlString = reconstructSqlString(ctx)
    delegate.parseExpression(sqlString)
  }

  /** 递归遍历解析树，按子节点顺序用空格连接重建原始 SQL 文本。 */
  private def reconstructSqlString(ctx: ParserRuleContext): String = {
    toBuffer(ctx.children).map {
      case c: ParserRuleContext => reconstructSqlString(c)
      case t: TerminalNode => t.getText
    }.mkString(" ")
  }

  /** 类型化的 visit 封装，接受访问结果并按 T 强转。 */
  private def typedVisit[T](ctx: ParseTree): T = {
    ctx.accept(this).asInstanceOf[T]
  }
}

/**
 * 解析工具集（部分复制自 Apache Spark 以避免对 Spark 内部的依赖）。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions。提供解析过程中常用的位置信息维护与
 * 原始命令文本提取能力，供 AstBuilder 使用。
 */
object IcebergParserUtils {

  /** 在执行 f 期间设置 CurrentOrigin 为当前规则的位置，结束后恢复，保证错误定位准确。 */
  private[sql] def withOrigin[T](ctx: ParserRuleContext)(f: => T): T = {
    val current = CurrentOrigin.get
    CurrentOrigin.set(position(ctx.getStart))
    try {
      f
    } finally {
      CurrentOrigin.set(current)
    }
  }

  /** 由 token 构造 {@link Origin}（行号、列号）。 */
  private[sql] def position(token: Token): Origin = {
    val opt = Option(token)
    Origin(opt.map(_.getLine), opt.map(_.getCharPositionInLine))
  }

  /** 获取创建该 token 的完整命令文本。 */
  private[sql] def command(ctx: ParserRuleContext): String = {
    val stream = ctx.getStart.getInputStream
    stream.getText(Interval.of(0, stream.size() - 1))
  }
}
