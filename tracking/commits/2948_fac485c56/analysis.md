# 提交 2948：Spark: Analyze but don't optimize view body during creation (#14681)

## 提交信息

- **序号**：2948 / 4088
- **哈希**：fac485c56b6e24b9081fed9e001077632945613c
- **短哈希**：fac485c56
- **日期**：2025-12-03
- **作者**：jbewing
- **提交说明**：Spark: Analyze but don't optimize view body during creation (#14681)
- **PR/Issue**：#14681

## 总体目的

Iceberg 在 Spark 的扩展中自定义了 `CreateIcebergView` 命令，用于创建 Iceberg 视图。原本它继承 `BinaryCommand`——这意味着它的两个子节点（`child` 与 `query`）会作为普通逻辑计划节点被 Spark 的 analyzer 分析之后，继续被 optimizer 和 planner 遍历。但视图定义的 query body 本质上只是要被"分析"（解析列、类型、别名），并不希望在创建阶段就被 Spark 优化器改写或物化进执行计划；让 optimizer 介入既无必要，也容易引发与视图存储文本不一致、或对视图体应用不期望的优化（例如把视图体里的表达式折叠、下推，从而改变视图语义）等问题。Spark 官方的 `CreateViewCommand` 早已改用 `AnalysisOnlyCommand` 来精确表达"只分析子节点、分析完就隐藏"的语义。

本提交把 Iceberg 的 `CreateIcebergView` 对齐到 Spark 官方做法：改继承 `AnalysisOnlyCommand`，通过 `childrenToAnalyze` 声明要被分析的子节点，`markAsAnalyzed` 在分析完成后把命令标记为已分析并把子节点从 optimizer 视野中移除，从而保证视图体在创建时只做分析、不被优化。改动同时落到 Spark 3.4、3.5、4.0 三个分支的扩展代码，保持跨版本一致。

## 如何达成设计目的

整体设计是"换基类 + 实现两个回调"。`CreateIcebergView` 从 `BinaryCommand` 改为继承 `AnalysisOnlyCommand`：用 `childrenToAnalyze` 返回 `child :: query :: Nil` 表明哪些子节点需要分析；用 `markAsAnalyzed` 在分析完成后 `copy(isAnalyzed = true)`；`withNewChildrenInternal` 改为接收 `IndexedSeq[LogicalPlan]` 并断言 `!isAnalyzed`（已分析后不应再被替换子节点）。因为新增了 `isAnalyzed` 字段，所有对 `CreateIcebergView` 做模式匹配的地方（`CheckViews`、`ResolveViews`、`ExtendedDataSourceV2Strategy`）都需要在解构里补一个 `_` 占位以匹配新字段，否则编译失败。

## 修改详情

### `spark/v{3.4,3.5,4.0}/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/views/CreateIcebergView.scala` (+24/-8 lines each)

**修改目的**：把 `CreateIcebergView` 从 `BinaryCommand` 改为 `AnalysisOnlyCommand`，使其只分析不优化视图体。

**工作逻辑**：
三个分支的改动完全一致。关键变化：
- 导入从 `BinaryCommand` 改为 `AnalysisContext` 与 `AnalysisOnlyCommand`，并加注释说明"对齐 Spark 的 `CreateViewCommand`，子节点被分析后隐藏，optimizer/planner 不会遍历视图体"。
- 类签名 `extends BinaryCommand` 改为 `extends AnalysisOnlyCommand`，移除 `override def left/right`（`BinaryCommand` 要求的左右子节点抽象）。
- 新增字段 `isAnalyzed: Boolean = false`，并实现 `override def childrenToAnalyze: Seq[LogicalPlan] = child :: query :: Nil`——告诉 Spark 分析器这两个子节点需要被分析。
- 实现 `override def markAsAnalyzed(analysisContext: AnalysisContext): LogicalPlan = copy(isAnalyzed = true)`——分析完成后标记并隐藏子节点，optimizer 再也不会看到 `query`/`child`，从而避免对视图体做任何优化。
- `withNewChildrenInternal` 签名从 `(newLeft, newRight)` 改为 `(newChildren: IndexedSeq[LogicalPlan])`，内部 `assert(!isAnalyzed)` 防止在已分析后被替换子节点，再用 `copy(child = newChildren.head, query = newChildren.last)` 维持原语义。

### `spark/v{3.4,3.5,4.0}/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala` (+1/-0 lines each)

**修改目的**：在 `CheckViews` 对 `CreateIcebergView` 的模式匹配中补一个 `_` 以匹配新增的 `isAnalyzed` 字段。

**工作逻辑**：
原解构到 `replace` 后即结束，现在在 `replace` 之后再加一个 `_`，对应新字段 `isAnalyzed`。这是 Scala case class 模式匹配的字段数对齐：新增字段后所有解构处必须同步，否则编译报错。语义无变化。

### `spark/v{3.4,3.5,4.0}/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala` (+1/-0 lines each)

**修改目的**：在 `ResolveViews` 对 `CreateIcebergView` 的模式匹配中补 `_` 占位 `isAnalyzed` 字段。

**工作逻辑**：同 `CheckViews`，在原解构末尾追加 `_`。`ResolveViews` 负责在 `query.resolved && !c.rewritten` 时给列加别名，新增字段不影响其逻辑，只是为了让解构能编译通过。

### `spark/v{3.4,3.5,4.0}/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala` (+1/-0 lines each)

**修改目的**：在 `ExtendedDataSourceV2Strategy` 把 `CreateIcebergView` 转成 `CreateV2ViewExec` 的模式匹配中补 `_`。

**工作逻辑**：在 `replace` 之后追加 `_` 占位 `isAnalyzed`。这里是把逻辑命令转成物理执行的策略，新增字段不参与策略判断，仅做字段数对齐。

## 总结

通过把 `CreateIcebergView` 的基类从 `BinaryCommand` 换成 `AnalysisOnlyCommand`，本提交精确表达了"视图体在创建时只应被分析、不应被优化"的语义，与 Spark 官方 `CreateViewCommand` 行为对齐，避免 optimizer 误改视图体导致语义漂移。改动同时覆盖 Spark 3.4/3.5/4.0 三个分支，并在所有模式匹配处补齐新增的 `isAnalyzed` 字段占位，保证编译与运行一致。
