# 提交 0631：Spark: Fail on recursive cycle in view

## 提交信息

- **序号**：0631 / 4088
- **哈希**：4579b7a1e941780496547f313e6fa07e712b09c5
- **短哈希**：4579b7a1e
- **日期**：2024-03-27 07:58:18 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Fail on recursive cycle in view (#9834)
- **PR/Issue**：#9834

## 总体目的

本提交为 Spark 3.5 的 Iceberg 扩展增加了视图递归循环（recursive cycle）检测能力，避免 `CREATE OR REPLACE VIEW` 语句产生自引用循环导致的查询无限递归与运行期栈溢出。

背景动机：Iceberg 视图（Iceberg View）是 1.4 引入的较新能力，允许在 Iceberg Catalog 中持久化一段 SQL 表示。由于 `CREATE OR REPLACE VIEW` 可以替换已存在视图的查询定义，用户完全可能写出"viewOne 引用 viewTwo，viewTwo 又引用 viewOne"的循环依赖：

```sql
CREATE VIEW viewOne AS SELECT * FROM tbl;
CREATE VIEW viewTwo AS SELECT * FROM viewOne;
CREATE OR REPLACE VIEW viewOne AS SELECT * FROM viewTwo;
```

在替换前 `viewOne → viewTwo → viewOne` 已经构成循环。一旦允许这种 DDL 通过分析阶段，后续任何对该视图的查询都会在 Spark 解析视图时无限递归展开，最终导致 `StackOverflowError`。本提交前，Spark 3.5 的 Iceberg `CheckViews` 规则只做了列数和列名重复检查，并未检测视图引用图中的环。本提交补上这一空缺，在分析期就把循环依赖显式失败出来，错误信息中携带完整的环路径，便于用户定位。

## 如何达成设计目的

设计思路是利用 Spark Catalyst 分析阶段已经在 `CreateIcebergView` 的 `query` 子树里把视图引用解析为 `SubqueryAlias`/`View` 节点这一事实，对 query 子树做一次深度遍历，识别其中所有"被引用视图的标识"，与"正在被创建/替换的视图标识"做比对，发现相等即抛 `AnalysisException`。

关键设计点：

1. **仅在 `replace=true` 时触发**：普通 `CREATE VIEW` 时目标视图尚不存在，不可能成为自身 query 的引用，无需检测。`CREATE OR REPLACE VIEW` 才会替换一个已存在视图，因而才有可能形成新的环。所以 `apply` 中只在 `replace` 为 true 时调用 `checkCyclicViewReference`。

2. **以"目标视图标识"作为闭环判定基准**：把 `viewIdent`（catalog 名 + namespace + view name 的多段标识）作为入参传入递归遍历，遍历中遇到的每个被引用视图都与之比较，相等即视为递归。这比构建完整的视图依赖图简单且足够——只要新视图在替换瞬间其 query 子树中能"间接看到自己"，就是环。

3. **维护 `cyclePath`**：递归过程中维护 `Seq[Seq[String]]` 记录从根 view 到当前节点的引用路径，遇到环时把它格式化进错误信息（用 `" -> "` 串联），让用户一眼看到完整环。

4. **同时覆盖 V1/V2 视图与子查询表达式**：Spark 解析后，V2 视图引用通常表现为 `SubqueryAlias(_, Project(_, _))`（带别名包裹的 Project），V1 视图引用表现为 `View` 节点。两者都要识别。另外，视图引用可能藏在标量子查询里（`SELECT * FROM t WHERE id = (SELECT id FROM v)`），所以还要遍历 `plan.expressions` 中的 `SubqueryExpression`，对 `e.plan` 同样做循环检测。

5. **递归进入被引用视图的子节点**：发现一个 `SubqueryAlias` 或 `View` 节点后，不比较完就停，而是继续递归它的 `children`（即被引用视图已解析好的 query 子树），因为环可能跨越多跳（A→B→C→A）。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala`

**修改目的**：在 Spark 3.5 的 Iceberg 视图分析规则 `CheckViews` 中新增递归循环检测逻辑。

**工作逻辑**：

1. 新增 import：`SubqueryExpression`、`Project`、`SubqueryAlias`、`View`，并在 `object CheckViews` 体内引入 `CatalogV2Implicits._`，主要为了使用 `asMultipartIdentifier` 与 `asIdentifier` 这两个隐式转换。

2. 修改 `CreateIcebergView` case 子句的模式匹配：原本最后一个匹配位用占位 `_`，现改为绑定 `replace` 字段。随后保持原有的列数与列名重复检查不变；新增：当 `replace == true` 时，构造 `viewIdent`（`catalog.name() +: identifier.asMultipartIdentifier`），以 `Seq(viewIdent)` 作为初始 cyclePath 调用 `checkCyclicViewReference`。

3. 新增私有方法 `checkCyclicViewReference(viewIdent, plan, cyclePath)`：

   - **case `SubqueryAlias(_, Project(_, _))`**：对应已解析的 V2 视图引用。从 `sub.identifier.qualifier`（命名空间部分）和 `sub.identifier.name`（视图名）拼出当前被引用视图的完整标识 `currentViewIdent`，调用 `checkIfRecursiveView` 处理。注意它传入的 `sub.children` 是被引用视图的 query 子树（Project 的子节点），所以递归能进入"被引用视图的内部查询"。

   - **case `v1View: View`**：对应 V1 视图引用（Spark 内置 session catalog 的 view）。从 `v1View.desc.identifier.nameParts` 取出多段标识作为 `currentViewIdent`，同样调用 `checkIfRecursiveView`，传入 `v1View.children`。

   - **case `_`**：对其他普通节点（Project、Filter、Join 等），不视作视图引用，但继续对 `plan.children` 递归调用 `checkCyclicViewReference`，把 cyclePath 原样传递下去。

   - 方法末尾还有一段对 `plan.expressions` 中 `SubqueryExpression` 的遍历：`flatMap(_.flatMap { case e: SubqueryExpression => checkCyclicViewReference(viewIdent, e.plan, cyclePath); None; case _ => None })`。这是为了覆盖标量子查询、IN 子查询等场景——子查询的计划藏在表达式里，不会出现在 `plan.children` 中，必须通过 `expressions` 才能拿到，所以这里显式地以同样的 cyclePath 对子查询计划做一次递归检测。

4. 新增私有方法 `checkIfRecursiveView(viewIdent, currentViewIdent, cyclePath, children)`：

   - 先 `val newCyclePath = cyclePath :+ currentViewIdent`，把当前被引用视图追加到路径。
   - 如果 `currentViewIdent == viewIdent`，说明沿着 query 子树回到了正在创建/替换的视图本身，形成环。抛 `AnalysisException`，消息格式为 `"Recursive cycle in view detected: %s (cycle: %s)"`，其中第二个 `%s` 是把 `newCyclePath` 中每段标识 `mkString(".")` 后再用 `" -> "` 串联得到的环路径字符串。
   - 否则，对 `children`（被引用视图的 query 子树）逐个继续调用 `checkCyclicViewReference`，传递新的 `newCyclePath`，以便检测更深的多跳环。

整个检测算法本质是带路径追踪的 DFS，把"被替换视图自身"作为闭环判定基准；由于 Spark 分析阶段已经把视图引用展开到 query 子树中，遍历这棵已解析的子树即可发现任意深度的环。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：为新增的递归循环检测能力补齐端到端测试。

**工作逻辑**：新增 4 个测试用例，覆盖不同的环构造路径：

1. `createViewWithRecursiveCycle`：最基础的 V2-V2 双视图循环。建 `viewOne → tbl`、`viewTwo → viewOne`，再 `CREATE OR REPLACE VIEW viewOne AS SELECT * FROM viewTwo`，期望抛 `AnalysisException` 且消息以 `"Recursive cycle in view detected: <view1> (cycle: <view1> -> <view2> -> <view1>)"` 开头。

2. `createViewWithRecursiveCycleToV1View`：跨 catalog 的环。`viewOne` 在 Iceberg catalog，`viewTwo` 在 `spark_catalog`（V1 视图）。通过 `USE spark_catalog` 切到 V1 catalog 建 viewTwo，再 `USE <icebergCatalog>` 回到 Iceberg catalog 执行 `CREATE OR REPLACE`，期望错误消息里环路径正确地把 `spark_catalog.ns.view_two` 与 `<icebergCatalog>.ns.view_one` 串联起来。这条用例验证了 `View` 节点分支（V1 视图）的检测。

3. `createViewWithRecursiveCycleInCTE`：环经由 CTE（WITH 子句）形成。建 `viewTwo → viewOne`，然后用一段带 `WITH max_by_data AS (SELECT max(id) FROM viewTwo) SELECT ... FROM max_by_data ...` 的 SQL `CREATE OR REPLACE VIEW viewOne`。期望错误显示环 `viewOne → viewTwo → viewOne`。验证了 CTE 展开（Project 之上的 SubqueryAlias）下检测仍然有效。

4. `createViewWithRecursiveCycleInSubqueryExpression`：环经由标量子查询形成。`CREATE OR REPLACE VIEW viewOne AS SELECT * FROM tbl WHERE id = (SELECT id FROM viewTwo)`。验证了 `SubqueryExpression` 分支的检测能力——这是检测算法必须显式遍历 `plan.expressions` 中子查询的根本原因。

四个用例都用 `assertThatThrownBy(...).isInstanceOf(AnalysisException.class).hasMessageStartingWith(...)` 断言异常类型与消息前缀。

## 小结

这个提交为 Spark 3.5 的 Iceberg 视图补上了"递归循环检测"这一关键安全网，把原本会让查询栈溢出的错误前置到分析期、并给出可读的环路径。检测算法复用了 Spark 已解析的 query 子树结构，实现简洁，并完整覆盖 V1/V2 视图、CTE、标量子查询四种引用形态。

回迁到 1.4.x 的注意事项：
- 文件路径为 `spark/v3.5/spark-extensions/...`，1.4.x 同样维护 Spark 3.5 模块，目录结构一致，可整体平移。
- 该改动只新增分析期检查，不改变任何持久化元数据或运行期行为，向后兼容。
- 注意与 0633（Spark 3.4 同款回port）保持一致：0633 是本提交面向 Spark 3.4 模块的对应版本，两份代码除包路径（`spark/v3.5` vs `spark/v3.4`）与测试基类（`ExtensionsTestBase` vs `SparkExtensionsTestBase`、`@TestTemplate` vs `@Test`）外完全相同，回迁时需两份一起迁，确保 1.4.x 支持的各 Spark 版本行为统一。
