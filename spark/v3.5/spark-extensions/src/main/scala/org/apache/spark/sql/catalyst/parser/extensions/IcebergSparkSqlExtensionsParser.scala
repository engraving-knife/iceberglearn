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
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.antlr.v4.runtime.tree.TerminalNodeImpl
import org.apache.iceberg.common.DynConstructors
import org.apache.iceberg.spark.ExtendedParser
import org.apache.iceberg.spark.ExtendedParser.RawOrderField
import org.apache.iceberg.spark.Spark3Util
import org.apache.iceberg.spark.source.SparkTable
import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.FunctionIdentifier
import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.catalyst.analysis.EliminateSubqueryAliases
import org.apache.spark.sql.catalyst.analysis.UnresolvedRelation
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.parser.ParserInterface
import org.apache.spark.sql.catalyst.parser.extensions.IcebergSqlExtensionsParser.NonReservedContext
import org.apache.spark.sql.catalyst.parser.extensions.IcebergSqlExtensionsParser.QuotedIdentifierContext
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.trees.Origin
import org.apache.spark.sql.connector.catalog.Table
import org.apache.spark.sql.connector.catalog.TableCatalog
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.internal.VariableSubstitution
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.types.StructType
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Iceberg Spark SQL 扩展解析器。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark v3.5 扩展模块），位于 Spark Catalyst 解析器扩展层。
 *
 * <p>职责：
 * <ul>
 *   <li>实现 Spark 的 {@link ParserInterface}，作为委托式解析器包装 Spark 原生解析器。</li>
 *   <li>识别 Iceberg 自定义 SQL 语法（CALL 存储过程、ALTER TABLE 分区/标识字段/写分布与排序、
 *       branch/tag 快照引用 DDL 等），交由 ANTLR 生成的 {@code IcebergSqlExtensionsParser} 解析。</li>
 *   <li>非 Iceberg 命令直接转发给 Spark 原生 delegate，保证普通 SQL 行为不受影响。</li>
 *   <li>提供 {@code UnresolvedIcebergTable} 提取器，便于分析期判断 LogicalPlan 是否对应 Iceberg 表。</li>
 * </ul>
 *
 * <p>设计意图：采用委托+前缀嗅探模式。先对 SQL 文本做轻量关键字判断（{@code isIcebergCommand}），
 * 命中 Iceberg 语法才走自定义 ANTLR 解析，否则转交 Spark 原生解析器，避免无谓开销与行为破坏。
 * 解析失败时先用 SLL 快速模式，失败再回退到 LL 精确模式（与 Spark 一致的两阶段策略）。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions} 通过
 * {@code injectParser} 注入；上游依赖 Spark Catalyst 与 ANTLR，下游产出 {@link LogicalPlan} 供分析器消费。
 */
class IcebergSparkSqlExtensionsParser(delegate: ParserInterface) extends ParserInterface with ExtendedParser {

  import IcebergSparkSqlExtensionsParser._

  private lazy val substitutor = substitutorCtor.newInstance(SQLConf.get)
  private lazy val astBuilder = new IcebergSqlExtensionsAstBuilder(delegate)

  /**
   * 将 SQL 文本解析为 Spark {@link DataType}，直接委托给 Spark 原生解析器。
   *
   * @param sqlText SQL 类型表达式文本
   * @return 解析得到的 DataType
   */
  override def parseDataType(sqlText: String): DataType = {
    delegate.parseDataType(sqlText)
  }

  /**
   * 解析为原始 DataType（不做 CHAR/VARCHAR 替换）。
   *
   * <p>当前扩展解析器未实现该能力，统一抛出 {@link UnsupportedOperationException}。
   */
  def parseRawDataType(sqlText: String): DataType = throw new UnsupportedOperationException()

  /**
   * 将 SQL 文本解析为 Spark {@link Expression}，委托给 Spark 原生解析器。
   *
   * @param sqlText SQL 表达式文本
   * @return 解析得到的 Expression
   */
  override def parseExpression(sqlText: String): Expression = {
    delegate.parseExpression(sqlText)
  }

  /**
   * 将 SQL 文本解析为 {@link TableIdentifier}，委托给 Spark 原生解析器。
   *
   * @param sqlText 表标识符 SQL 文本
   * @return 解析得到的 TableIdentifier
   */
  override def parseTableIdentifier(sqlText: String): TableIdentifier = {
    delegate.parseTableIdentifier(sqlText)
  }

  /**
   * 将 SQL 文本解析为 {@link FunctionIdentifier}，委托给 Spark 原生解析器。
   *
   * @param sqlText 函数标识符 SQL 文本
   * @return 解析得到的 FunctionIdentifier
   */
  override def parseFunctionIdentifier(sqlText: String): FunctionIdentifier = {
    delegate.parseFunctionIdentifier(sqlText)
  }

  /**
   * 将 SQL 文本解析为多段标识符（catalog.database.table 等），委托给 Spark 原生解析器。
   *
   * @param sqlText 多段标识符 SQL 文本
   * @return 各段标识符字符串序列
   */
  override def parseMultipartIdentifier(sqlText: String): Seq[String] = {
    delegate.parseMultipartIdentifier(sqlText)
  }

  /**
   * 将逗号分隔的字段定义 SQL 字符串解析为 {@link StructType}，保留正确的 Hive 元信息。
   *
   * @param sqlText 字段定义 SQL 文本
   * @return 解析得到的 StructType
   */
  override def parseTableSchema(sqlText: String): StructType = {
    delegate.parseTableSchema(sqlText)
  }

  /**
   * 解析排序规则文本为 Iceberg 原始排序字段列表。
   *
   * <p>逻辑：调用 {@code parse} 走 Iceberg ANTLR 解析器得到 (term, direction, order) 三元组，
   * 再包装为 {@link RawOrderField} 列表返回。
   *
   * @param sqlText 排序规则 SQL 文本
   * @return RawOrderField 列表
   */
  override def parseSortOrder(sqlText: String): java.util.List[RawOrderField] = {
    val fields = parse(sqlText) { parser => astBuilder.visitSingleOrder(parser.singleOrder()) }
    fields.map { field =>
      val (term, direction, order) = field
      new RawOrderField(term, direction, order)
    }.asJava
  }

  /**
   * 将 SQL 文本解析为 {@link LogicalPlan}。
   *
   * <p>逻辑：先通过 {@code substitutor} 做 SQL 变量替换，再用 {@code isIcebergCommand} 判断是否
   * Iceberg 扩展语句。若是则走 Iceberg ANTLR 解析器；否则转发给 Spark 原生 {@code delegate.parsePlan}。
   *
   * @param sqlText SQL 文本
   * @return 解析得到的 LogicalPlan
   */
  override def parsePlan(sqlText: String): LogicalPlan = {
    val sqlTextAfterSubstitution = substitutor.substitute(sqlText)
    if (isIcebergCommand(sqlTextAfterSubstitution)) {
      parse(sqlTextAfterSubstitution) { parser => astBuilder.visit(parser.singleStatement()) }.asInstanceOf[LogicalPlan]
    } else {
      delegate.parsePlan(sqlText)
    }
  }

  /**
   * 提取器对象：从 LogicalPlan 中识别未解析的 Iceberg 表关系。
   *
   * <p>设计意图：用于分析期规则中判断某个 LogicalPlan 是否引用 Iceberg 表，
   * 通过消除子查询别名后判断 UnresolvedRelation 对应的表能否被加载为 {@link SparkTable}。
   */
  object UnresolvedIcebergTable {

    /**
     * 解构 LogicalPlan，若其指向 Iceberg 表则返回 Some(plan)，否则返回 None。
     *
     * @param plan 待判断的逻辑计划
     * @return 命中时返回 Some(plan)
     */
    def unapply(plan: LogicalPlan): Option[LogicalPlan] = {
      EliminateSubqueryAliases(plan) match {
        case UnresolvedRelation(multipartIdentifier, _, _) if isIcebergTable(multipartIdentifier) =>
          Some(plan)
        case _ =>
          None
      }
    }

    /**
     * 判断多段标识符指向的表是否为 Iceberg 表。
     *
     * <p>逻辑：通过 {@link Spark3Util#catalogAndIdentifier} 解析 catalog 与 identifier，
     * 若 catalog 是 {@link TableCatalog}，尝试 loadTable 并检查是否为 {@link SparkTable}。
     * 加载失败返回 false。
     *
     * @param multipartIdent 多段表标识符
     * @return true 表示是 Iceberg 表
     */
    private def isIcebergTable(multipartIdent: Seq[String]): Boolean = {
      val catalogAndIdentifier = Spark3Util.catalogAndIdentifier(SparkSession.active, multipartIdent.asJava)
      catalogAndIdentifier.catalog match {
        case tableCatalog: TableCatalog =>
          Try(tableCatalog.loadTable(catalogAndIdentifier.identifier))
            .map(isIcebergTable)
            .getOrElse(false)

        case _ =>
          false
      }
    }

    /** 判断已加载的 Table 是否为 {@link SparkTable}（即 Iceberg 表）。 */
    private def isIcebergTable(table: Table): Boolean = table match {
      case _: SparkTable => true
      case _ => false
    }
  }

  /**
   * 通过关键字前缀嗅探判断 SQL 是否为 Iceberg 扩展命令。
   *
   * <p>逻辑：把 SQL 转小写并去除 -- 行注释、块注释与多余空白，再判断是否以
   * {@code call} 开头，或以 {@code alter table} 开头且包含 Iceberg 扩展子命令
   * （add/drop/replace partition field、write ordered/distributed、set/drop identifier fields、
   * 快照引用 DDL）。
   *
   * @param sqlText 原始 SQL 文本
   * @return true 表示该 SQL 由 Iceberg 扩展解析器处理
   */
  private def isIcebergCommand(sqlText: String): Boolean = {
    val normalized = sqlText.toLowerCase(Locale.ROOT).trim()
      // Strip simple SQL comments that terminate a line, e.g. comments starting with `--` .
      .replaceAll("--.*?\\n", " ")
      // Strip newlines.
      .replaceAll("\\s+", " ")
      // Strip comments of the form  /* ... */. This must come after stripping newlines so that
      // comments that span multiple lines are caught.
      .replaceAll("/\\*.*?\\*/", " ")
      .trim()
    normalized.startsWith("call") || (
        normalized.startsWith("alter table") && (
            normalized.contains("add partition field") ||
            normalized.contains("drop partition field") ||
            normalized.contains("replace partition field") ||
            normalized.contains("write ordered by") ||
            normalized.contains("write locally ordered by") ||
            normalized.contains("write distributed by") ||
            normalized.contains("write unordered") ||
            normalized.contains("set identifier fields") ||
            normalized.contains("drop identifier fields") ||
            isSnapshotRefDdl(normalized)))
  }

  /** 判断规范化后的 SQL 是否包含 branch/tag 快照引用相关 DDL 关键字。 */
  private def isSnapshotRefDdl(normalized: String): Boolean = {
    normalized.contains("create branch") ||
      normalized.contains("replace branch") ||
      normalized.contains("create tag") ||
      normalized.contains("replace tag") ||
      normalized.contains("drop branch") ||
      normalized.contains("drop tag")
  }

  /**
   * 通用 ANTLR 解析入口，执行 Iceberg 自定义语法解析。
   *
   * <p>逻辑：构造 {@code IcebergSqlExtensionsLexer} 与 {@code IcebergSqlExtensionsParser}，
   * 注册错误监听器与后处理器；先用 SLL 快速预测模式尝试解析，失败时回退到 LL 精确模式重试。
   * 解析异常统一包装为 {@link IcebergParseException} 或带位置的 AnalysisException。
   *
   * @param command 待解析的 SQL 命令文本
   * @param toResult 用解析器产出结果的函数
   * @tparam T 结果类型
   * @return 解析结果
   */
  protected def parse[T](command: String)(toResult: IcebergSqlExtensionsParser => T): T = {
    val lexer = new IcebergSqlExtensionsLexer(new UpperCaseCharStream(CharStreams.fromString(command)))
    lexer.removeErrorListeners()
    lexer.addErrorListener(IcebergParseErrorListener)

    val tokenStream = new CommonTokenStream(lexer)
    val parser = new IcebergSqlExtensionsParser(tokenStream)
    parser.addParseListener(IcebergSqlExtensionsPostProcessor)
    parser.removeErrorListeners()
    parser.addErrorListener(IcebergParseErrorListener)

    try {
      try {
        // first, try parsing with potentially faster SLL mode
        parser.getInterpreter.setPredictionMode(PredictionMode.SLL)
        toResult(parser)
      }
      catch {
        case _: ParseCancellationException =>
          // if we fail, parse with LL mode
          tokenStream.seek(0) // rewind input stream
          parser.reset()

          // Try Again.
          parser.getInterpreter.setPredictionMode(PredictionMode.LL)
          toResult(parser)
      }
    }
    catch {
      case e: IcebergParseException if e.command.isDefined =>
        throw e
      case e: IcebergParseException =>
        throw e.withCommand(command)
      case e: AnalysisException =>
        val position = Origin(e.line, e.startPosition)
        throw new IcebergParseException(Option(command), e.message, position, position)
    }
  }

  /** 解析查询 SQL，等价于 {@link #parsePlan}。 */
  override def parseQuery(sqlText: String): LogicalPlan = {
    parsePlan(sqlText)
  }
}

/**
 * IcebergSparkSqlExtensionsParser 的伴生对象。
 *
 * <p>设计意图：用 {@link DynConstructors} 跨 Spark 版本兼容地构造
 * {@link VariableSubstitution} 实例（用于 SQL 变量替换），屏蔽不同 Spark 版本构造器签名差异。
 */
object IcebergSparkSqlExtensionsParser {
  private val substitutorCtor: DynConstructors.Ctor[VariableSubstitution] =
    DynConstructors.builder()
      .impl(classOf[VariableSubstitution])
      .impl(classOf[VariableSubstitution], classOf[SQLConf])
      .build()
}

/* Copied from Apache Spark's to avoid dependency on Spark Internals */
/**
 * 大写字符流包装器（复制自 Apache Spark 内部实现以避免对 Spark 内部类的依赖）。
 *
 * <p>设计意图：将底层 {@link CodePointCharStream} 的字符在词法分析时统一转为大写，
 * 使 Iceberg SQL 关键字大小写不敏感，同时保留标识符原文。
 */
class UpperCaseCharStream(wrapped: CodePointCharStream) extends CharStream {
  override def consume(): Unit = wrapped.consume
  override def getSourceName(): String = wrapped.getSourceName
  override def index(): Int = wrapped.index
  override def mark(): Int = wrapped.mark
  override def release(marker: Int): Unit = wrapped.release(marker)
  override def seek(where: Int): Unit = wrapped.seek(where)
  override def size(): Int = wrapped.size

  override def getText(interval: Interval): String = wrapped.getText(interval)

  // scalastyle:off
  /**
   * 前瞻第 i 个字符，并把字母转为大写返回其码点。
   *
   * <p>逻辑：底层流返回 0 或 EOF 时直接透传；否则用 {@link Character#toUpperCase} 转大写。
   *
   * @param i 前瞻偏移
   * @return 字符码点（已大写化）
   */
  override def LA(i: Int): Int = {
    val la = wrapped.LA(i)
    if (la == 0 || la == IntStream.EOF) la
    else Character.toUpperCase(la)
  }
  // scalastyle:on
}

/**
 * 解析树后处理器（case object），在解析过程中校验并清理解析树。
 *
 * <p>所属模块：iceberg-spark-extensions。负责把反引号包裹的标识符与未保留关键字规整为
 * IDENTIFIER token，保证后续 AST 构建得到统一的标识符节点。
 */
case object IcebergSqlExtensionsPostProcessor extends IcebergSqlExtensionsBaseListener {

  /** 退出反引号标识符节点时，去除首尾反引号并把内部 `` 还原为单个 `。 */
  override def exitQuotedIdentifier(ctx: QuotedIdentifierContext): Unit = {
    replaceTokenByIdentifier(ctx, 1) { token =>
      // Remove the double back ticks in the string.
      token.setText(token.getText.replace("``", "`"))
      token
    }
  }

  /** 把非保留关键字当作普通标识符处理。 */
  override def exitNonReserved(ctx: NonReservedContext): Unit = {
    replaceTokenByIdentifier(ctx, 0)(identity)
  }

  /**
   * 把当前节点替换为新的 IDENTIFIER token。
   *
   * <p>逻辑：移除父节点最后一个子节点，根据原 token 的起止位置剥离指定边距后构造新的
   * {@link CommonToken}，并通过 f 做可选变换后挂回父节点。
   *
   * @param ctx 当前解析规则上下文
   * @param stripMargins 首尾各剥离的字符数
   * @param f 对新 token 的可选变换函数
   */
  private def replaceTokenByIdentifier(
      ctx: ParserRuleContext,
      stripMargins: Int)(
      f: CommonToken => CommonToken = identity): Unit = {
    val parent = ctx.getParent
    parent.removeLastChild()
    val token = ctx.getChild(0).getPayload.asInstanceOf[Token]
    val newToken = new CommonToken(
      new org.antlr.v4.runtime.misc.Pair(token.getTokenSource, token.getInputStream),
      IcebergSqlExtensionsParser.IDENTIFIER,
      token.getChannel,
      token.getStartIndex + stripMargins,
      token.getStopIndex - stripMargins)
    parent.addChild(new TerminalNodeImpl(f(newToken)))
  }
}

/* Partially copied from Apache Spark's Parser to avoid dependency on Spark Internals */
/**
 * 解析错误监听器（case object，部分复制自 Apache Spark）。
 *
 * <p>设计意图：在 ANTLR 报告语法错误时，把错误位置（行、列、起止 origin）包装为
 * {@link IcebergParseException} 抛出，便于上层统一处理。
 */
case object IcebergParseErrorListener extends BaseErrorListener {
  /**
   * 语法错误回调：构造起止 Origin 并抛出 {@link IcebergParseException}。
   *
   * @param recognizer ANTLR 识别器
   * @param offendingSymbol 出错的符号
   * @param line 行号
   * @param charPositionInLine 列号
   * @param msg 错误信息
   * @param e 触发的识别异常
   */
  override def syntaxError(
      recognizer: Recognizer[_, _],
      offendingSymbol: scala.Any,
      line: Int,
      charPositionInLine: Int,
      msg: String,
      e: RecognitionException): Unit = {
    val (start, stop) = offendingSymbol match {
      case token: CommonToken =>
        val start = Origin(Some(line), Some(token.getCharPositionInLine))
        val length = token.getStopIndex - token.getStartIndex + 1
        val stop = Origin(Some(line), Some(token.getCharPositionInLine + length))
        (start, stop)
      case _ =>
        val start = Origin(Some(line), Some(charPositionInLine))
        (start, start)
    }
    throw new IcebergParseException(None, msg, start, stop)
  }
}

/**
 * Iceberg SQL 解析异常（部分复制自 Apache Spark 的 ParseException）。
 *
 * <p>设计意图：扩展 {@link AnalysisException}，附带原始命令文本、起止 Origin 等信息，
 * 使错误报告与诊断更友好。{@link #getMessage} 会拼装带 `^^^` 指示符的 SQL 上下文片段。
 */
class IcebergParseException(
    val command: Option[String],
    message: String,
    val start: Origin,
    val stop: Origin) extends AnalysisException(message, start.line, start.startPosition) {

  def this(message: String, ctx: ParserRuleContext) = {
    this(Option(IcebergParserUtils.command(ctx)),
      message,
      IcebergParserUtils.position(ctx.getStart),
      IcebergParserUtils.position(ctx.getStop))
  }

  override def getMessage: String = {
    val builder = new StringBuilder
    builder ++= "\n" ++= message
    start match {
      case Origin(
          Some(l), Some(p), Some(startIndex), Some(stopIndex), Some(sqlText), Some(objectType), Some(objectName)) =>
        builder ++= s"(line $l, pos $p)\n"
        command.foreach { cmd =>
          val (above, below) = cmd.split("\n").splitAt(l)
          builder ++= "\n== SQL ==\n"
          above.foreach(builder ++= _ += '\n')
          builder ++= (0 until p).map(_ => "-").mkString("") ++= "^^^\n"
          below.foreach(builder ++= _ += '\n')
        }
      case _ =>
        command.foreach { cmd =>
          builder ++= "\n== SQL ==\n" ++= cmd
        }
    }
    builder.toString
  }

  /** 返回一个新的异常实例，附带原始命令文本。 */
  def withCommand(cmd: String): IcebergParseException = {
    new IcebergParseException(Option(cmd), message, start, stop)
  }
}