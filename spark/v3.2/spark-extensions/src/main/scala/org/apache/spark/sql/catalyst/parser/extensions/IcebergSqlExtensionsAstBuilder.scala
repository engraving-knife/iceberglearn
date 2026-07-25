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
 * 文件级说明：IcebergSqlExtensionsAstBuilder —— Iceberg Spark SQL 扩展的类。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。
 * 类型：类 IcebergSqlExtensionsAstBuilder。
 * <p>设计意图：为 Spark SQL 提供 Iceberg 特有的语法扩展支持，
 * 包括分支/标签管理、存储过程调用、分区字段变更等 DDL 操作。
 * <p>上下游：由 IcebergSparkSessionExtensions 注册，作用于 Spark Catalyst 解析与分析阶段。
 */
class IcebergSqlExtensionsAstBuilder(delegate: ParserInterface) extends IcebergSqlExtensionsBaseVisitor[AnyRef] {

  /**
   * toBuffer：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  private def toBuffer[T](list: java.util.List[T]): scala.collection.mutable.Buffer[T] = list.asScala
  /**
   * toSeq：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  private def toSeq[T](list: java.util.List[T]): Seq[T] = toBuffer(list).toSeq

  /**
   * visitCall：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitCall(ctx: CallContext): CallStatement = withOrigin(ctx) {
    val name = toSeq(ctx.multipartIdentifier.parts).map(_.getText)
    val args = toSeq(ctx.callArgument).map(typedVisit[CallArgument])
    CallStatement(name, args)
  }

  /**
   * visitAddPartitionField：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitAddPartitionField(ctx: AddPartitionFieldContext): AddPartitionField = withOrigin(ctx) {
    AddPartitionField(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      typedVisit[Transform](ctx.transform),
      Option(ctx.name).map(_.getText))
  }

  /**
   * visitDropPartitionField：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitDropPartitionField(ctx: DropPartitionFieldContext): DropPartitionField = withOrigin(ctx) {
    DropPartitionField(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      typedVisit[Transform](ctx.transform))
  }

  /**
   * visitCreateOrReplaceBranch：执行 Iceberg SQL 扩展的解析/构建逻辑。
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
   * visitCreateOrReplaceTag：执行 Iceberg SQL 扩展的解析/构建逻辑。
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
   * visitDropBranch：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitDropBranch(ctx: DropBranchContext): DropBranch = withOrigin(ctx) {
    DropBranch(typedVisit[Seq[String]](ctx.multipartIdentifier), ctx.identifier().getText, ctx.EXISTS() != null)
  }

  /**
   * visitDropTag：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitDropTag(ctx: DropTagContext): DropTag = withOrigin(ctx) {
    DropTag(typedVisit[Seq[String]](ctx.multipartIdentifier), ctx.identifier().getText, ctx.EXISTS() != null)
  }

  /**
   * visitReplacePartitionField：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitReplacePartitionField(ctx: ReplacePartitionFieldContext): ReplacePartitionField = withOrigin(ctx) {
    ReplacePartitionField(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      typedVisit[Transform](ctx.transform(0)),
      typedVisit[Transform](ctx.transform(1)),
      Option(ctx.name).map(_.getText))
  }

  /**
   * visitSetIdentifierFields：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitSetIdentifierFields(ctx: SetIdentifierFieldsContext): SetIdentifierFields = withOrigin(ctx) {
    SetIdentifierFields(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      toSeq(ctx.fieldList.fields).map(_.getText))
  }

  /**
   * visitDropIdentifierFields：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitDropIdentifierFields(ctx: DropIdentifierFieldsContext): DropIdentifierFields = withOrigin(ctx) {
    DropIdentifierFields(
      typedVisit[Seq[String]](ctx.multipartIdentifier),
      toSeq(ctx.fieldList.fields).map(_.getText))
  }

  /**
   * visitSetWriteDistributionAndOrdering：执行 Iceberg SQL 扩展的解析/构建逻辑。
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
   * toDistributionAndOrderingSpec：执行 Iceberg SQL 扩展的解析/构建逻辑。
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
   * visitOrderField：执行 Iceberg SQL 扩展的解析/构建逻辑。
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
   * visitIdentityTransform：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitIdentityTransform(ctx: IdentityTransformContext): Transform = withOrigin(ctx) {
    IdentityTransform(FieldReference(typedVisit[Seq[String]](ctx.multipartIdentifier())))
  }

  /**
   * visitApplyTransform：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitApplyTransform(ctx: ApplyTransformContext): Transform = withOrigin(ctx) {
    val args = toSeq(ctx.arguments).map(typedVisit[expressions.Expression])
    ApplyTransform(ctx.transformName.getText, args)
  }

  /**
   * visitTransformArgument：执行 Iceberg SQL 扩展的解析/构建逻辑。
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
   * visitMultipartIdentifier：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitMultipartIdentifier(ctx: MultipartIdentifierContext): Seq[String] = withOrigin(ctx) {
    toSeq(ctx.parts).map(_.getText)
  }

  /**
   * visitSingleOrder：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitSingleOrder(ctx: SingleOrderContext): Seq[(Term, SortDirection, NullOrder)] = withOrigin(ctx) {
    toSeq(ctx.order.fields).map(typedVisit[(Term, SortDirection, NullOrder)])
  }

  /**
   * visitPositionalArgument：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitPositionalArgument(ctx: PositionalArgumentContext): CallArgument = withOrigin(ctx) {
    val expr = typedVisit[Expression](ctx.expression)
    PositionalArgument(expr)
  }

  /**
   * visitNamedArgument：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitNamedArgument(ctx: NamedArgumentContext): CallArgument = withOrigin(ctx) {
    val name = ctx.identifier.getText
    val expr = typedVisit[Expression](ctx.expression)
    NamedArgument(name, expr)
  }

  /**
   * visitSingleStatement：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitSingleStatement(ctx: SingleStatementContext): LogicalPlan = withOrigin(ctx) {
    visit(ctx.statement).asInstanceOf[LogicalPlan]
  }

  /**
   * visitConstant：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  def visitConstant(ctx: ConstantContext): Literal = {
    delegate.parseExpression(ctx.getText).asInstanceOf[Literal]
  }

  /**
   * visitExpression：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  override def visitExpression(ctx: ExpressionContext): Expression = {
    // reconstruct the SQL string and parse it using the main Spark parser
    // while we can avoid the logic to build Spark expressions, we still have to parse them
    // we cannot call ctx.getText directly since it will not render spaces correctly
    // that's why we need to recurse down the tree in reconstructSqlString
    val sqlString = reconstructSqlString(ctx)
    delegate.parseExpression(sqlString)
  }

  /**
   * reconstructSqlString：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  private def reconstructSqlString(ctx: ParserRuleContext): String = {
    toBuffer(ctx.children).map {
      case c: ParserRuleContext => reconstructSqlString(c)
      case t: TerminalNode => t.getText
    }.mkString(" ")
  }

  /**
   * typedVisit：执行 Iceberg SQL 扩展的解析/构建逻辑。
   */
  private def typedVisit[T](ctx: ParseTree): T = {
    ctx.accept(this).asInstanceOf[T]
  }
}

/* Partially copied from Apache Spark's Parser to avoid dependency on Spark Internals */
object IcebergParserUtils {

  private[sql] def withOrigin[T](ctx: ParserRuleContext)(f: => T): T = {
    val current = CurrentOrigin.get
    CurrentOrigin.set(position(ctx.getStart))
    try {
      f
    } finally {
      CurrentOrigin.set(current)
    }
  }

  private[sql] def position(token: Token): Origin = {
    val opt = Option(token)
    Origin(opt.map(_.getLine), opt.map(_.getCharPositionInLine))
  }

  /** Get the command which created the token. */
  private[sql] def command(ctx: ParserRuleContext): String = {
    val stream = ctx.getStart.getInputStream
    stream.getText(Interval.of(0, stream.size() - 1))
  }
}
