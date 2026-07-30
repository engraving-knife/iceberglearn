# 提交 0633：Spark 3.4: Fail on recursive cycle in view

## 提交信息

- **序号**：0633 / 4088
- **哈希**：9987314e540cf8b0d4f4d61b561e0ebf273148e1
- **短哈希**：9987314e5
- **日期**：2024-03-27 16:59:55 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Fail on recursive cycle in view (#10048)
- **PR/Issue**：#10048

## 总体目的

本提交是 0631（PR #9834，针对 Spark 3.5 模块）向 Spark 3.4 模块的等价回port，为 Spark 3.4 的 Iceberg 扩展补上同样的视图递归循环检测能力。提交在同一天（2024-03-27）紧跟 0631 之后合入，目的是让 Iceberg 支持的各 Spark 版本（3.4 / 3.5）在视图循环依赖上的行为完全统一：任何 `CREATE OR REPLACE VIEW` 形成的循环引用都必须在分析期失败，避免后续查询触发栈溢出。

背景动机与 0631 完全相同：`CREATE OR REPLACE VIEW` 可以替换已存在视图，可能产生 viewOne → viewTwo → viewOne 的循环依赖，若不在分析期阻断，后续查询会无限递归展开视图导致 `StackOverflowError`。Iceberg 同时维护 Spark 3.4 与 3.5 两套扩展模块（`spark/v3.4/` 与 `spark/v3.5/`），它们各自有独立的 `CheckViews.scala` 与 `TestViews.java`，因此修复必须分别落地到两个分支，本提交负责 Spark 3.4。

## 如何达成设计目的

设计思路与 0631 完全一致：在 `CheckViews` 规则中新增 `checkCyclicViewReference` 与 `checkIfRecursiveView` 两个私有方法，对 `CreateIcebergView` 在 `replace=true` 时（即 `CREATE OR REPLACE VIEW`）触发递归遍历。遍历识别两种视图引用节点：

- `SubqueryAlias(_, Project(_, _))`：对应 V2 视图引用，从 `sub.identifier.qualifier` 与 `sub.identifier.name` 拼出被引用视图标识；
- `v1View: View`：对应 V1 视图引用（Spark 内置 session catalog 的 view），从 `v1View.desc.identifier.nameParts` 取多段标识。

发现某被引用视图标识与"正在被创建/替换的视图标识"相等，即抛 `AnalysisException`，错误消息携带完整环路径（`view1 -> view2 -> view1` 形式）。同时显式遍历 `plan.expressions` 中的 `SubqueryExpression`，以覆盖标量子查询中的视图引用。整体算法是带 `cyclePath` 追踪的 DFS，详细工作逻辑见 0631 的分析文档。

与 0631 的差异仅在以下三处：

1. **代码所在路径**：`spark/v3.4/spark-extensions/...` 而非 `spark/v3.5/spark-extensions/...`。
2. **测试基类**：Spark 3.4 的 `TestViews.java` 继承自 `SparkExtensionsTestBase`（JUnit 4 风格），而 Spark 3.5 的版本继承自 `ExtensionsTestBase`（JUnit 5 风格）。
3. **测试注解**：Spark 3.4 的 4 个新增测试方法用 JUnit 4 的 `@Test`，而 Spark 3.5 版本用 JUnit 5 的 `@TestTemplate`。

`CheckViews.scala` 的主体逻辑（`apply`、`verifyColumnCount`、`checkCyclicViewReference`、`checkIfRecursiveView`）与 0631 中的实现逐字相同，差异仅是文件所在路径与上下文行号。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala`

**修改目的**：在 Spark 3.4 的 Iceberg 视图分析规则 `CheckViews` 中新增递归循环检测逻辑（与 0631 等价）。

**工作逻辑**：与 0631 中 `spark/v3.5/.../CheckViews.scala` 的修改完全一致。具体细节：

1. 新增 import：`SubqueryExpression`、`Project`、`SubqueryAlias`、`View`，并在 `object CheckViews` 体内引入 `CatalogV2Implicits._`（用于 `asMultipartIdentifier` / `asIdentifier` 隐式转换）。

2. `CreateIcebergView` case 子句的模式匹配把最后一个占位 `_` 改为绑定 `replace`；在 `replace == true` 时，构造 `viewIdent`（`catalog.name() +: identifier.asMultipartIdentifier`），以 `Seq(viewIdent)` 作为初始 cyclePath 调用 `checkCyclicViewReference`。

3. 新增 `checkCyclicViewReference(viewIdent, plan, cyclePath)`：
   - `SubqueryAlias(_, Project(_, _))` 分支：拼出 `currentViewIdent`，调用 `checkIfRecursiveView`，传入 `sub.children`。
   - `v1View: View` 分支：取 `v1View.desc.identifier.nameParts` 作为 `currentViewIdent`，调用 `checkIfRecursiveView`，传入 `v1View.children`。
   - 其他节点：对 `plan.children` 递归调用 `checkCyclicViewReference`，cyclePath 原样传递。
   - 末尾遍历 `plan.expressions` 中的 `SubqueryExpression`，对 `e.plan` 同样递归调用 `checkCyclicViewReference`。

4. 新增 `checkIfRecursiveView(viewIdent, currentViewIdent, cyclePath, children)`：
   - `newCyclePath = cyclePath :+ currentViewIdent`。
   - 若 `currentViewIdent == viewIdent`，抛 `AnalysisException`，消息格式为 `"Recursive cycle in view detected: %s (cycle: %s)"`，环路径把每段标识 `mkString(".")` 后用 `" -> "` 串联。
   - 否则对 `children` 逐个继续调用 `checkCyclicViewReference`，传递新的 `newCyclePath`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：为 Spark 3.4 的递归循环检测能力补齐端到端测试，与 0631 测试用例一一对应。

**工作逻辑**：新增 4 个测试方法，覆盖与 0631 相同的 4 种环构造路径。差别仅在测试注解和测试基类：

1. `createViewWithRecursiveCycle`（注解 `@Test`）：V2-V2 双视图循环。建 `viewOne → tbl`、`viewTwo → viewOne`，再 `CREATE OR REPLACE VIEW viewOne AS SELECT * FROM viewTwo`，期望 `AnalysisException` 且消息以 `"Recursive cycle in view detected: <view1> (cycle: <view1> -> <view2> -> <view1>)"` 开头。

2. `createViewWithRecursiveCycleToV1View`（注解 `@Test`）：跨 catalog 的环。`viewOne` 在 Iceberg catalog，`viewTwo` 在 `spark_catalog`（V1 视图）。通过 `USE spark_catalog` 切换到 V1 catalog 建 viewTwo，再 `USE <icebergCatalog>` 回到 Iceberg catalog 执行 `CREATE OR REPLACE`，期望错误消息中环路径正确串联 `spark_catalog.ns.view_two` 与 `<icebergCatalog>.ns.view_one`。验证了 `View` 节点（V1 视图）分支的检测。

3. `createViewWithRecursiveCycleInCTE`（注解 `@Test`）：环经由 CTE 形成。建 `viewTwo → viewOne`，再用 `WITH max_by_data AS (SELECT max(id) FROM viewTwo) SELECT ... FROM max_by_data ...` 的 SQL `CREATE OR REPLACE VIEW viewOne`，期望错误显示环 `viewOne → viewTwo → viewOne`。验证了 CTE 展开（Project 之上的 SubqueryAlias）下检测仍然有效。

4. `createViewWithRecursiveCycleInSubqueryExpression`（注解 `@Test`）：环经由标量子查询形成。`CREATE OR REPLACE VIEW viewOne AS SELECT * FROM tbl WHERE id = (SELECT id FROM viewTwo)`，期望错误显示环 `viewOne → viewTwo → viewOne`。验证了 `SubqueryExpression` 分支的检测能力——这是检测算法必须显式遍历 `plan.expressions` 中子查询的根本原因。

四个用例都用 `assertThatThrownBy(...).isInstanceOf(AnalysisException.class).hasMessageStartingWith(...)` 断言异常类型与消息前缀。

## 小结

本提交是 0631（Spark 3.5）向 Spark 3.4 模块的等价回port，两个提交在同一天由同一作者合入，确保 Iceberg 支持的各 Spark 版本在视图循环检测上行为统一。检测算法、错误消息格式、测试用例数量与覆盖范围与 0631 完全相同，差异只在文件路径、测试基类与测试注解。

回迁到 1.4.x 的注意事项：
- 1.4.x 同时维护 Spark 3.4 与 3.5 模块，建议把 0631 与 0633 一起回迁，保证两个分支的 `CheckViews` 与 `TestViews` 同步演进；若只回迁其中一个，会导致两套 Spark 扩展在视图循环依赖行为上不一致，给依赖 Iceberg 的下游应用埋坑。
- 该改动只新增分析期检查，不改变任何持久化元数据或运行期行为，向后兼容。
- 回迁时注意区分两套测试基类（Spark 3.4 用 `SparkExtensionsTestBase` + JUnit 4 `@Test`，Spark 3.5 用 `ExtensionsTestBase` + JUnit 5 `@TestTemplate`），不要互相混用。
- Spark 3.4 在 Iceberg 1.4.x 之后的版本中会逐步被废弃（参考 Iceberg 后续版本的 Spark 支持矩阵），但只要 1.4.x 仍维护 3.4 模块，本提交就应被回迁以保持模块行为一致性。
