# 提交 0422：Spark: Rewrite identifier when using Subquery expressions in View (#9587)

## 提交信息

- **序号**：0422
- **哈希**：2af18b318e57f4a8c254342828dc7ade5fa6e308
- **短哈希**：2af18b318
- **日期**：Tue Jan 30 18:54:48 2024 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Rewrite identifier when using Subquery expressions in View (#9587)
- **PR/Issue**：#9587

## 总体目的

Iceberg 的 Spark 集成提供了自己的 View 实现（`CreateIcebergView`、`ResolveViews` 等），允许用户在 Iceberg ViewCatalog 中创建视图。视图的一个核心语义是**存储位置无关性**：视图定义中引用的表需要在创建时被"冻结"为带完整 catalog 和 namespace 前缀的限定标识符（qualified identifier），这样无论后续从哪个 catalog/namespace 上下文查询该视图，视图内的表引用都能正确解析回创建时所在的 catalog。这个"限定化"工作由 `ResolveViews.qualifyTableIdentifiers` 方法完成——它遍历视图逻辑计划中的 `UnresolvedRelation` 节点，把单段式表名（如 `t`）补全为 `catalog.namespace.t`，把未带 catalog 前缀的多段式表名（如 `ns.t`）补上 catalog 前缀。

然而本提交修复的 bug 是：`qualifyTableIdentifiers` 只处理了计划树**顶层**的 `UnresolvedRelation`，却**没有递归进入 `SubqueryExpression` 内部的子计划**。Spark SQL 中，子查询（如 `WHERE id = (SELECT max(id) FROM t)` 中的 scalar subquery，或 `SELECT (SELECT max(id) FROM t) FROM t` 中的标量子查询）在逻辑计划中被表示为 `SubqueryExpression`，其内部持有一个独立的 `LogicalPlan`（`subquery.plan`），该子计划中同样可能包含 `UnresolvedRelation`。由于原方法不递归子查询，这些嵌套的表引用不会被限定化——它们以未限定的形式（如 `t`）被存入视图元数据。后果是：当用户切换到另一个 catalog（如 `USE spark_catalog`）后再查询该 Iceberg 视图，视图内子查询中的 `t` 会按当前上下文（spark_catalog.default）去解析，找不到原 Iceberg catalog 中的表 `t`，导致 `AnalysisException: The table or view 't' cannot be found`。视图在创建时的 catalog 上下文下能正常工作（因为当前 namespace 恰好能解析到 `t`），但跨 catalog 查询就崩溃，这违背了视图应有的"存储位置无关性"语义。

本提交的目的就是补上这条遗漏的递归路径：在 `qualifyTableIdentifiers` 中增加一个 catch-all 分支，对计划中所有 `SubqueryExpression` 递归调用 `qualifyTableIdentifiers` 限定其内部子计划的表标识符，使含子查询的视图也能被正确限定化、支持跨 catalog 查询。值得注意的是，此修复只应用于 v3.5（v3.4 的 `ResolveViews.scala` 存在完全相同的遗漏，但本提交未一并修复）。

## 如何达成设计目的

实现思路是利用 Spark Catalyst 的 `transform` / `transformExpressions` 递归遍历能力。在 `qualifyTableIdentifiers` 的 `child transform { ... }` 模式匹配中，原有两个 `case` 分别处理单段式和多段式 `UnresolvedRelation`；新增一个 `case other =>` 兜底分支，对其他所有节点调用 `other.transformExpressions { case subquery: SubqueryExpression => subquery.withNewPlan(qualifyTableIdentifiers(subquery.plan, catalogAndNamespace)) }`，即：遍历该节点上的所有表达式，一旦遇到 `SubqueryExpression`，就用递归限定后的新子计划替换原计划（`withNewPlan`）。由于外层 `child transform` 本身会递归遍历整棵计划树，每个节点上的表达式都会被检查，因此无论子查询嵌在 WHERE 过滤条件、SELECT 投影、HAVING 还是任何其他位置，都能被覆盖到。配合两个新增测试（过滤子查询和投影子查询各一个）验证修复有效性。

## 修改详情

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala

**修改目的**：让视图标识符限定化逻辑递归处理 `SubqueryExpression` 内部的子计划，使含子查询的视图也能正确限定表引用。

**工作逻辑**：
- 新增 `import org.apache.spark.sql.catalyst.expressions.SubqueryExpression`。
- 在 `qualifyTableIdentifiers(child, catalogAndNamespace)` 方法的 `child transform { ... }` 模式匹配末尾，新增 catch-all 分支：
  ```scala
  case other =>
    other.transformExpressions {
      case subquery: SubqueryExpression =>
        subquery.withNewPlan(qualifyTableIdentifiers(subquery.plan, catalogAndNamespace))
    }
  ```
  原有两个 `case` 只匹配 `UnresolvedRelation`，其他所有 `LogicalPlan` 节点落入 `case other`。对每个这样的节点，`transformExpressions` 遍历其携带的所有表达式（如 `Filter` 的条件表达式、`Project` 的投影表达式列表），若发现 `SubqueryExpression`（scalar/IN/EXISTS 等子查询的统一抽象），则用 `withNewPlan` 把子查询的内部计划替换为递归限定后的版本。递归调用 `qualifyTableIdentifiers(subquery.plan, catalogAndNamespace)` 会对子查询内部计划中的 `UnresolvedRelation` 做同样的限定化，并继续向更深层子查询递归（因为子计划内部如果还有子查询，会再次走 `case other` 分支）。外层 `child transform` 负责遍历整棵计划树的节点，内层 `transformExpressions` 负责处理每个节点表达式中的子查询——两者配合实现完整的深度遍历。

  设计上把递归逻辑放在 `case other` 而非单独的 `case subquery` 是因为 `SubqueryExpression` 是**表达式**（`Expression`），不是 `LogicalPlan`，不会出现在 `child transform` 的计划节点匹配中；必须先走到持有该表达式的计划节点（如 `Filter`），再通过 `transformExpressions` 进入表达式树找到子查询。这也解释了为什么原有的 `UnresolvedRelation` 两个 case 不受影响——它们匹配的是计划节点，新分支匹配的是"其他计划节点上的表达式"，互不冲突。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：新增两个测试覆盖含子查询表达式的视图创建与跨 catalog 查询，并修复一个已有测试的清理逻辑。

**工作逻辑**：
- 在某个已有测试方法末尾（约 877 行）追加 `sql("USE spark_catalog"); sql("DROP TABLE IF EXISTS %s", tableName);` 两行清理代码，切换回 spark_catalog 并删除测试表，避免残留影响后续测试。
- 新增 `createViewWithSubqueryExpressionInFilterThatIsRewritten`：向表插入 5 行（id 1-5），用 `SELECT id FROM t WHERE id = (SELECT max(id) FROM t)` 创建视图（子查询在 WHERE 过滤条件中）。先在 Iceberg catalog 上下文验证视图返回 1 行 `row(5)`；再 `USE spark_catalog` 验证裸 SQL（未限定表名）会抛 `AnalysisException`（`The table or view 't' cannot be found`，因为 spark_catalog.default 下没有 t）；最后通过全限定路径 `catalogName.default.viewName` 查询视图，验证仍返回 `row(5)`——这证明视图内子查询的表引用已被正确限定到 Iceberg catalog，跨 catalog 查询能正常解析。
- 新增 `createViewWithSubqueryExpressionInQueryThatIsRewritten`：向表插入 3 行，用 `SELECT (SELECT max(id) FROM t) max_id FROM t` 创建视图（标量子查询在 SELECT 投影中）。同样三步验证：Iceberg 上下文返回 3 行 `row(3)`；spark_catalog 下裸 SQL 抛 `AnalysisException`；全限定路径查询视图返回 3 行 `row(3)`。两个测试分别覆盖了子查询在过滤条件和投影两种位置的场景，确保修复对不同子查询用法都有效。

## 小结

这是一个精准修复视图标识符限定化遗漏子查询递归的提交。根因是 `qualifyTableIdentifiers` 只遍历计划树顶层的 `UnresolvedRelation`，未进入 `SubqueryExpression` 内部子计划，导致含子查询的视图在跨 catalog 查询时因子查询内的表引用未限定而解析失败。修复利用 Catalyst 的 `transformExpressions` + 递归 `qualifyTableIdentifiers` 实现深度遍历，改动小而完整。两个测试分别覆盖过滤子查询和投影子查询，验证场景全面。值得注意的局限是：修复只应用于 v3.5，v3.4 的 `ResolveViews.scala` 存在完全相同的 bug 但未一并修复（可能是优先级或发布节奏考量），这意味着 v3.4 用户在含子查询的 Iceberg 视图上仍会遇到跨 catalog 查询失败的问题。整体体现了 Spark Catalyst 中"计划节点遍历"与"表达式遍历"两层递归需要协同才能完整覆盖嵌套结构的典型模式。
