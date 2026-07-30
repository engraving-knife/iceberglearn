# 提交 3829：Spark 4.1: Bind parameters in IcebergSparkSqlExtensionsParser (#16626)

## 提交信息

- **序号**：3829 / 4088
- **哈希**：4918866c08787521b131920ae94c76106afc24d5
- **短哈希**：4918866c0
- **日期**：2026-06-05 16:48:16 -0700
- **作者**：Jiwon Park <jpark92@outlook.kr>
- **提交说明**：Spark 4.1: Bind parameters in IcebergSparkSqlExtensionsParser (#16626)
- **PR/Issue**：#16626

## 总体目的

本提交为 Iceberg Spark 4.1 扩展 SQL 解析器 `IcebergSparkSqlExtensionsParser` 增加对参数化查询（parameterized queries）的支持。Spark 4.1 引入了参数化 SQL 能力，允许在 SQL 中使用参数标记（如 `?` 位置参数或 `:name` 命名参数），并通过 `spark.sql(sqlText, args)` 在执行时绑定参数值。这要求 `ParserInterface` 实现 `parsePlanWithParameters(sqlText, ParameterContext)` 方法，把参数上下文传递给解析器进行绑定。

Iceberg 的 `IcebergSparkSqlExtensionsParser` 是一个包装 Spark 原生解析器的扩展解析器，用于识别和路由 Iceberg 特有的 DDL/命令（如 `ALTER TABLE ... WRITE ORDERED BY`、`CALL ...` 等）。在 Spark 4.1 引入参数化查询后，该扩展解析器没有实现 `parsePlanWithParameters`，导致使用 Iceberg 扩展解析器的 Spark 会话中执行参数化查询时会失败（方法缺失或参数上下文丢失）。

本提交实现 `parsePlanWithParameters`，把参数上下文转发给被包装的 Spark 原生解析器。由于 Iceberg 自身的 DDL 语法不接受参数标记，参数上下文仅在非 Iceberg 命令路径上转发给 delegate。同时把 `parsePlan` 重构为与 `parsePlanWithParameters` 共用分发逻辑，避免代码重复。

## 如何达成设计目的

设计上提取一个私有的 `parsePlanWithDelegate(sqlText)(delegateParse)` 方法作为统一分发入口：先做变量替换（`substitutor.substitute`），判断是否为 Iceberg 命令（`isIcebergCommand`），是则用 Iceberg 扩展解析器解析（不涉及参数），否则把 SQL 交给 `delegateParse` 函数处理。`parsePlan` 传入 `delegate.parsePlan`，`parsePlanWithParameters` 传入 `delegate.parsePlanWithParameters(sql, parameterContext)`。这样 Iceberg 命令路径不接触参数上下文，非 Iceberg 路径正确转发参数，逻辑统一且无重复。

## 修改详情

### `spark/v4.1/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala` (+25/-2 lines)

**修改目的**：实现 `parsePlanWithParameters`，统一分发逻辑。

**工作逻辑**：
- 新增 import `ParameterContext`。
- `parsePlan` 改为调用 `parsePlanWithDelegate(sqlText)(delegate.parsePlan)`。
- 新增 `parsePlanWithParameters(sqlText, parameterContext)`，调用 `parsePlanWithDelegate` 并传入 `delegate.parsePlanWithParameters(sql, parameterContext)`：
```scala
override def parsePlanWithParameters(
    sqlText: String,
    parameterContext: ParameterContext): LogicalPlan =
  parsePlanWithDelegate(sqlText) { sql =>
    delegate.parsePlanWithParameters(sql, parameterContext)
  }
```
- 新增私有 `parsePlanWithDelegate(sqlText)(delegateParse)` 统一分发：变量替换后，Iceberg 命令走扩展解析，非 Iceberg 命令走 `delegateParse`：
```scala
private def parsePlanWithDelegate(sqlText: String)(
    delegateParse: String => LogicalPlan): LogicalPlan = {
  val sqlTextAfterSubstitution = substitutor.substitute(sqlText)
  if (isIcebergCommand(sqlTextAfterSubstitution)) {
    parse(sqlTextAfterSubstitution) { parser => astBuilder.visit(parser.singleStatement()) }
      .asInstanceOf[LogicalPlan]
  } else {
    RewriteViewCommands(SparkSession.active).apply(delegateParse(sqlText))
  }
}
```
- Javadoc 说明：Iceberg DDL 语法不接受参数标记，因此 parameterContext 仅在非 Iceberg 路径转发给 delegate。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/TestExtendedParser.java` (+46/-0 lines)

**修改目的**：覆盖参数化查询的转发与绑定行为。

**工作逻辑**：
新增三个测试：
- `testParsePlanWithParametersDelegatesForNonIcebergSql`：mock delegate，验证非 Iceberg SQL（`SELECT 1 WHERE 1 = ?`）的 `parsePlanWithParameters` 调用会转发给 delegate 并保留 parameterContext。
- `testParsePlanWithParametersBindsPositionalParameter`：用真实 Iceberg 扩展解析器执行 `SELECT ? AS id` 绑定 `42`，验证返回一行值为 42。
- `testParsePlanWithParametersBindsNamedParameter`：用真实 Iceberg 扩展解析器执行 `SELECT :id AS id` 绑定命名参数 `{id: 42}`，验证返回一行值为 42。

## 总结

本提交使 Iceberg Spark 4.1 扩展解析器与 Spark 4.1 引入的参数化查询能力兼容。通过实现 `parsePlanWithParameters` 并统一分发逻辑，非 Iceberg SQL 的参数上下文能正确转发给 Spark 原生解析器，而 Iceberg 命令路径保持不变。测试覆盖 mock 转发与真实绑定（位置参数与命名参数）。这是 Iceberg 与 Spark 4.1 新特性兼容性的重要补全，确保使用 Iceberg 扩展解析器的会话能正常使用参数化查询。
