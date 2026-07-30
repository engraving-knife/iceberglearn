# 提交 0429：Spark 3.4: Rewrite identifier when using Subquery expressions in View (#9594)

## 提交信息

- **序号**：0429
- **哈希**：0536ff39896f70afb77d3670894369c2d177980a
- **短哈希**：0536ff398
- **日期**：2024 年 1 月 31 日（Wed Jan 31 10:30:02 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Rewrite identifier when using Subquery expressions in View (#9594)
- **PR/Issue**：#9594

## 总体目的

这个提交修复了 Iceberg Spark 3.4 视图在处理子查询表达式（SubqueryExpression）时的标识符改写遗漏问题。背景是：Iceberg 视图在创建时会通过 `ResolveViews` 中的 `qualifyTableIdentifiers` 方法，把视图定义 SQL 中的未限定表名补全为完整的 `catalog.namespace.table` 三段式标识符，这样无论后续在哪个 catalog/namespace 上下文中查询该视图，视图内部引用的表都能被正确解析到原始表。

然而 `qualifyTableIdentifiers` 此前只处理了 `UnresolvedRelation`（直接出现在 plan 树顶层的表引用），却忽略了表引用可能嵌套在子查询表达式内部的情况。子查询表达式（`SubqueryExpression`，如标量子查询 `SELECT (SELECT max(id) FROM t)` 或 `WHERE id = (SELECT max(id) FROM t)` 中的子查询）自身持有一个子 `LogicalPlan`（`subquery.plan`），这个子 plan 里同样可能包含 `UnresolvedRelation`。由于 `child transform { ... }` 只对直接子节点做模式匹配，不会递归进表达式内部的子查询 plan，导致子查询里的表名未被补全。

后果是：当用户在 Iceberg 视图中使用含子查询的 SQL，并切换到别的 catalog（如 `USE spark_catalog`）再查询该视图时，子查询里的未限定表名会因为当前上下文找不到而抛 `AnalysisException: The table or view ... cannot be found`。本提交通过在 `qualifyTableIdentifiers` 中新增一个 `case other` 分支，递归处理所有表达式中嵌套的 `SubqueryExpression`，对其子 plan 同样调用 `qualifyTableIdentifiers`，从而把子查询内的表名也补全。这是 Iceberg 视图正确性（视图可跨上下文查询）的关键修复。

## 如何达成设计目的

在 `qualifyTableIdentifiers` 方法的 pattern matching 末尾新增一个兜底分支 `case other =>`，对当前节点执行 `transformExpressions`，匹配其中的 `SubqueryExpression`，并调用 `subquery.withNewPlan(qualifyTableIdentifiers(subquery.plan, catalogAndNamespace))` —— 即把子查询的子 plan 原地递归改写。配合新增的 `SubqueryExpression` import 即可。由于 `qualifyTableIdentifiers` 是递归方法，子查询内的多层嵌套也能被逐层处理。同时新增两个测试用例覆盖子查询在 filter 和 query 中的改写，并对既有测试做了清理。

## 修改详情

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala

**修改目的**：让 `qualifyTableIdentifiers` 能改写嵌套在子查询表达式内部的表标识符。

**工作逻辑**：
- 新增 `import org.apache.spark.sql.catalyst.expressions.SubqueryExpression`。
- 在 `qualifyTableIdentifiers` 方法的 `child transform { ... }` 中，于两个 `UnresolvedRelation` 分支之后新增：
  ```scala
  case other =>
    other.transformExpressions {
      case subquery: SubqueryExpression =>
        subquery.withNewPlan(qualifyTableIdentifiers(subquery.plan, catalogAndNamespace))
    }
  ```
  这个兜底分支对所有未被前两个分支匹配的节点，对其内部表达式做 `transformExpressions`，找到 `SubqueryExpression` 后用 `withNewPlan` 替换其子 plan 为"递归改写后的子 plan"。`transformExpressions` 会遍历节点持有的所有表达式，因此无论子查询藏在哪一层表达式里都能被处理。递归调用 `qualifyTableIdentifiers` 保证子查询 plan 内若再有子查询也会被处理。这样视图定义中所有表引用（无论直接还是嵌套在子查询中）都会被补全为完整三段式标识符。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：覆盖子查询表达式的标识符改写，并修复既有测试的清理问题。

**工作逻辑**：
- 在 `v1SessionCatalog` 相关测试末尾新增 `sql("USE spark_catalog"); sql("DROP TABLE IF EXISTS %s", tableName);`，用于在测试间清理 spark_catalog 下可能残留的同名表，避免后续测试相互污染。
- 新增 `createViewWithSubqueryExpressionInFilterThatIsRewritten`：构造 `SELECT id FROM <table> WHERE id = (SELECT max(id) FROM <table>)`（filter 中的标量子查询），创建视图后验证：当前 catalog 下查询返回 1 行（id=5）；切换到 `spark_catalog` 后原 SQL 因表找不到而抛 `AnalysisException`；但通过完整三段式 `catalogName.default.viewName` 查询视图能正确返回 1 行（id=5），证明子查询内的表名已被改写为完整标识符。
- 新增 `createViewWithSubqueryExpressionInQueryThatIsRewritten`：构造 `SELECT (SELECT max(id) FROM <table>) max_id FROM <table>`（query 列表中的标量子查询），创建视图后验证类似逻辑：当前 catalog 返回 3 行（均为 3）；切换 spark_catalog 后原 SQL 抛异常；完整三段式查询返回 3 行（3,3,3）。
- 两个测试都通过"切换上下文后原 SQL 失败、但带完整 catalog 前缀的视图查询成功"这一对比，明确证明改写生效。

## 小结

这是一个正确性 bug 修复，针对视图定义中嵌套子查询表达式的表名改写遗漏。修复方式精简（一个递归兜底分支 + import），但覆盖了多层嵌套子查询的场景。它体现了 Iceberg 视图"视图定义需自包含、可跨上下文查询"的设计原则——视图内部所有表引用都必须被限定为完整标识符，不依赖查询时的当前 catalog/namespace。该修复仅针对 Spark 3.4（Spark 3.5 可能在主分支已含或后续补齐），并配套补足了测试覆盖与测试间清理。属于 1.4.x 维护分支上的视图能力补强。
