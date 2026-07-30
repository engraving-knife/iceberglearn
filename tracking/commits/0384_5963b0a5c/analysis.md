# 提交 0384：Spark 3.4: Support dropping views

## 提交信息

- **序号**：0384
- **哈希**：5963b0a5c2b3efc6a925b4d3a3cc683a76c88b7e
- **短哈希**：5963b0a5c
- **日期**：Thu Jan 18 15:06:19 2024 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Support dropping views (#9508)
- **PR/Issue**：#9508

## 总体目的

这个提交填补 Spark 3.4 集成层在 Iceberg 视图（view）DDL 操作上的能力空白。在 1.4.x 之前，Iceberg 的 Spark 3.4 扩展已经能创建视图（`CREATE VIEW`），但 `DROP VIEW` 与 `ALTER VIEW ... RENAME TO` 这两类操作在 `SparkCatalog` 里被直接抛出 `UnsupportedOperationException`——这意味着用户在 Spark SQL 中执行 `DROP VIEW` 会被拒。而 Iceberg 自 1.4.0 起（与 `ViewCatalog` API 一同）已经把视图作为一等公民，要求所有支持的方言都能完整执行视图的生命周期管理。

更深一层的设计动因来自 Spark 引擎本身：Spark 的 `ResolveSessionCatalog` 对部分 v2 View 命令会提前退出或绕过 Iceberg 的扩展，导致 Iceberg 拦截不到这些命令。要正确处理 `DROP VIEW`，必须比 Spark 的会话 catalog 解析更早介入，把命令改写成 Iceberg 自定义的逻辑计划节点，再由 Iceberg 的执行策略生成执行算子。因此这个提交不只是"把异常改成实现"，而是一套完整的"解析器前置改写 → 逻辑计划节点 → 执行策略 → 物理算子"的扩展链路，并把 rename 一并补齐，使视图的 DDL 与表 DDL 在体验上保持一致。

从用户视角看，提交完成后，`DROP VIEW [IF EXISTS] v` 与 `ALTER VIEW v RENAME TO v2` 在 Iceberg Spark 3.4 catalog 下都能正常工作，并支持"视图与临时视图同名时优先操作临时视图"的 Spark 语义。

## 如何达成设计目的

整体路径是经典的 Spark Catalyst 扩展模式：1）新增逻辑计划节点 `DropIcebergView` 与 `ResolvedV2View`，分别表示"待执行的 drop 命令"和"已解析为 ViewCatalog 的视图标识"；2）新增解析期规则 `RewriteViewCommands`，在 Iceberg 解析器把 SQL 文本委托给 Spark 默认解析器后、立即把 Spark 原生的 `DropView(UnresolvedIdentifier)` 改写为 `DropIcebergView(ResolvedIdentifier(...))`，从而绕过 `ResolveSessionCatalog` 对 v2 view 命令的提前退出；3）新增执行算子 `DropV2ViewExec` 与 `RenameV2ViewExec`，调用底层 `ViewCatalog.dropView` / `renameView` 完成实际操作；4）在 `ExtendedDataSourceV2Strategy` 中把 `DropIcebergView` 和 `RenameTable(ResolvedV2View, ...)` 分别映射到这两个 exec；5）在 `ResolveViews` 中新增对 `UnresolvedTableOrView` 的解析分支，把已解析的视图替换为 `ResolvedV2View`；6）在 `SparkCatalog` 中实现 `dropView` / `renameView`，把请求委托给底层 Iceberg catalog 的 `ViewCatalog` 实例；7）补充大量 TestViews 用例覆盖 rename、drop、ifExists、跨 catalog、临时视图遮蔽等场景。

## 修改详情

### spark/v3.4/spark-extensions/src/main/scala/org/apache/iceberg/spark/sql/catalyst/plans/logical/views/DropIcebergView.scala（新增）

**修改目的**：定义 Iceberg 自有的"drop view"逻辑计划节点，区别于 Spark 原生 `DropView`，便于在执行策略中精准匹配。

**工作逻辑**：声明 `case class DropIcebergView(child: LogicalPlan, ifExists: Boolean) extends UnaryCommand`，持有子计划与 `ifExists` 标志。继承 `UnaryCommand` 是 Spark 3.4 对命令型节点的标准基类，提供 `runCommand` 等能力；`withNewChildInternal` 实现 Spark Catalyst 树重建协议（强制要求），返回 `copy(child = newChild)` 保证 transform 时能正确生成新节点。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/iceberg/spark/sql/catalyst/plans/logical/views/ResolvedV2View.scala（新增）

**修改目的**：定义"已解析为 v2 ViewCatalog 的视图引用"节点，供 rename 等命令在执行策略中携带 catalog 与 identifier。

**工作逻辑**：声明 `case class ResolvedV2View(catalog: ViewCatalog, identifier: Identifier) extends LeafNodeWithoutStats`，`output` 返回空 `Seq`（视图引用本身不产出数据行）。`LeafNodeWithoutStats` 是 Spark 用于不需要统计信息的叶子节点基类。该节点既被 `ResolveViews` 用于"把 UnresolvedTableOrView 解析为 view 后替换"，也被 `ExtendedDataSourceV2Strategy` 用于匹配 `RenameTable(ResolvedV2View, ...)`。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala（新增）

**修改目的**：在 Spark 默认解析器解析完 SQL 后，立即把原生 `DropView` 改写为 Iceberg 的 `DropIcebergView`，绕过 `ResolveSessionCatalog` 对 v2 view 命令的提前退出。

**工作逻辑**：`case class RewriteViewCommands(spark: SparkSession) extends Rule[LogicalPlan] with LookupCatalog`，`apply` 用 `resolveOperatorsUp` 自底向上匹配 `DropView(ResolvedView(resolved), ifExists) => DropIcebergView(resolved, ifExists)`。其中私有 `ResolvedView` 提取器负责把 `UnresolvedIdentifier` 解析成 `ResolvedIdentifier(catalog, ident)`：若 nameParts 是临时视图则返回 `None`（保持原生 DropView 由 Spark 处理临时视图），若 catalog 是 `ViewCatalog` 则包装为 `ResolvedIdentifier`，否则返回 `None`。这种"先于 ResolveSessionCatalog 改写"是关键设计点，确保 drop view 命令能被 Iceberg 拦截处理，同时让临时视图仍然走 Spark 原生路径。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala

**修改目的**：把 `RewriteViewCommands` 规则挂入 Iceberg 解析器，对非 Iceberg 自定义命令的 SQL 文本，在委托给 Spark 默认解析器后立即应用该规则。

**工作逻辑**：导入 `RewriteViewCommands`，并把原 `val parsedPlan = delegate.parsePlan(sqlText)` 改为 `val parsedPlan = RewriteViewCommands(SparkSession.active).apply(delegate.parsePlan(sqlText))`。这里使用 `SparkSession.active` 获取当前会话，对 Spark 解析出的逻辑计划做一次规则应用，从而把 `DropView` 改写为 `DropIcebergView`。注意只对"非 Iceberg 自定义命令"分支生效，Iceberg 自定义命令（如 `call ...`）走自己的解析路径不受影响。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala

**修改目的**：扩展视图解析规则，使其除了把视图引用解析为 `ViewRelation`（数据访问路径），还能把 `UnresolvedTableOrView` 解析为 `ResolvedV2View`（DDL 操作路径），并新增 ViewCatalog 类型转换助手。

**工作逻辑**：导入 `ResolvedV2View`，在 `apply` 中新增匹配分支 `case u@UnresolvedTableOrView(CatalogAndIdentifier(catalog, ident), _, _) => loadView(catalog, ident).map(_ => ResolvedV2View(catalog.asViewCatalog, ident)).getOrElse(u)`——这里如果视图存在就替换为 `ResolvedV2View`，否则保留原 `UnresolvedTableOrView` 让后续报"未找到"错误。新增 `implicit class ViewHelper(plugin: CatalogPlugin)` 提供 `asViewCatalog` 方法：若 plugin 是 `ViewCatalog` 直接返回，否则抛出 `QueryCompilationErrors.missingCatalogAbilityError(plugin, "views")`，保证类型安全转换。同时把 `isCatalog`/`isBuiltinFunction` 中对 `spark.sessionState.catalogManager` 的两次访问改为使用 `catalogManager` 字段（来自 `LookupCatalog` trait），属于轻微的代码清理，减少重复访问。`qualifyFunctionIdentifiers` 的参数缩进调整属于格式化。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/iceberg/spark/sql/execution/datasources/v2/DropV2ViewExec.scala（新增）

**修改目的**：实现视图删除的物理算子，调用底层 `ViewCatalog.dropView`。

**工作逻辑**：`case class DropV2ViewExec(catalog: ViewCatalog, ident: Identifier, ifExists: Boolean) extends LeafV2CommandExec`，`output` 为 `Nil`。`run()` 调用 `catalog.dropView(ident)` 拿到布尔返回值：若返回 false 且 `!ifExists`，则抛出 `NoSuchViewException(ident)`（实现 `DROP VIEW` 不存在时报错、`DROP VIEW IF EXISTS` 不存在时静默通过的语义）；成功则返回 `Nil`。`simpleString` 给出算子描述便于 explain。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/iceberg/spark/sql/execution/datasources/v2/RenameV2ViewExec.scala（新增）

**修改目的**：实现视图重命名的物理算子，调用底层 `ViewCatalog.renameView`。

**工作逻辑**：`case class RenameV2ViewExec(catalog: ViewCatalog, oldIdent: Identifier, newIdent: Identifier) extends LeafV2CommandExec`，`output` 为 `Nil`。`run()` 调用 `catalog.renameView(oldIdent, newIdent)` 后返回空序列。该算子依赖底层 `ViewCatalog` 对"目标已存在"、"源不存在"等错误情况抛出合适异常，自己不做额外检查。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/iceberg/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala

**修改目的**：在 Iceberg 的执行策略中把 `DropIcebergView` 与涉及视图的 `RenameTable` 映射到对应物理算子。

**工作逻辑**：新增两个匹配分支。第一个：`case RenameTable(ResolvedV2View(oldCatalog: ViewCatalog, oldIdent), newName, isView@true) =>` 先用 `Spark3Util.catalogAndIdentifier(spark, newName.toList.asJava)` 解析目标标识符，若 `oldCatalog.name != newIdent.catalog().name()` 则抛 `AnalysisException("Cannot move view between catalogs: ...")`（禁止跨 catalog 移动视图），否则生成 `RenameV2ViewExec(oldCatalog, oldIdent, newIdent.identifier())`。第二个：`case DropIcebergView(ResolvedIdentifier(viewCatalog: ViewCatalog, ident), ifExists) => DropV2ViewExec(viewCatalog, ident, ifExists) :: Nil`，把逻辑计划直接转成执行算子。同时新增若干 import（`AnalysisException`、`ResolvedIdentifier`、`RenameTable`、`DropIcebergView`、`ResolvedV2View`、`ViewCatalog`）以支持上述匹配。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java

**修改目的**：在 `SparkCatalog`（Spark ↔ Iceberg catalog 适配层）中真正实现 `dropView` 与 `renameView`，把之前抛 `UnsupportedOperationException` 的存根替换为对底层 Iceberg `ViewCatalog` 的委托。

**工作逻辑**：`SparkCatalog` 持有 `private ViewCatalog asViewCatalog = null;` 字段（在初始化时若底层 catalog 是 `ViewCatalog` 实例则赋值）。`dropView(ident)` 改为：`if (null != asViewCatalog) return asViewCatalog.dropView(buildIdentifier(ident)); return false;`——存在 view catalog 时委托执行并返回是否真的删除了，否则返回 false。`renameView(from, to)` 改为：若 `asViewCatalog != null` 则调用 `asViewCatalog.renameView(buildIdentifier(from), buildIdentifier(to))`，并把 Iceberg 自有异常 `org.apache.iceberg.exceptions.NoSuchViewException` 与 `AlreadyExistsException` 分别映射为 Spark 的 `NoSuchViewException` 和 `ViewAlreadyExistsException`（保证 Spark 用户看到的是 Spark 语义的异常）；若没有 view catalog 仍抛 `UnsupportedOperationException`。`buildIdentifier` 是已有的把 Spark `Identifier` 转 Iceberg `TableIdentifier` 的工具方法，复用保证视图与表使用一致的标识规则。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为新增的 drop/rename 视图能力补充测试用例，覆盖正向流程、边界条件与异常路径。

**工作逻辑**：新增大量 `@Test` 方法。`renameView` 验证基本重命名后查询能命中改名后的视图；`renameViewHiddenByTempView` 验证当临时视图与持久视图同名时，`ALTER VIEW ... RENAME TO` 优先作用于临时视图，第二次才作用于 Iceberg 视图（符合 Spark 语义）；`renameViewToDifferentTargetCatalog` 验证跨 catalog 重命名会抛 `AnalysisException` 且消息包含 "Cannot move view between catalogs"；`renameNonExistingView` 验证重命名不存在的视图报"cannot be found"；`renameViewTargetAlreadyExistsAsView` 验证目标名已被视图占用时报错。同时调整 `fullFunctionIdentifierNotRewrittenLoadFailure` 用例的 SQL（去掉多余的 `String.format`），并新增 import（`assertThatNoException`、`Random`、`SessionCatalog`）。这些测试共同保证视图 DDL 在各种用户输入下行为正确。

## 小结

这个提交是 1.4.x 落后于 main 的 Spark 3.4 视图能力补齐补丁，采用标准的 Spark Catalyst 扩展链路（解析器前置改写 → 逻辑计划节点 → 执行策略 → 物理算子 + 适配层实现 + 测试），把之前抛 `UnsupportedOperationException` 的 `dropView`/`renameView` 真正落地。其最有技术含量的一点是 `RewriteViewCommands`——通过在 Spark 默认解析器之后立即改写 `DropView` 为 `DropIcebergView`，绕过 `ResolveSessionCatalog` 对 v2 view 命令的提前退出，这是让 Iceberg 能"接管" drop view 命令的关键设计。提交规模较大（10 文件、+529/-13 行），并配套大量边界场景测试，是 1.4.x 维护分支需要 backport 的重要功能补齐。
