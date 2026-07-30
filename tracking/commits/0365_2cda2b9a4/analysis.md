# 提交 0365：Spark: Support dropping Views (#9421)

## 提交信息

- **序号**：0365
- **哈希**：2cda2b9a4c975e40d102d6c3e734cebb186e393a
- **短哈希**：2cda2b9a4
- **日期**：2024-01-16 12:39:44 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Support dropping Views (#9421)
- **PR/Issue**：#9421

## 总体目的

本提交为 Iceberg Spark 3.5 集成模块补齐"删除视图（`DROP VIEW [IF EXISTS] ...`）"的能力，进一步解除 #9422（本系列 0335）引入视图读取时对视图写入操作的限制。继 #9343（本系列 0363）实现 `ALTER VIEW ... RENAME TO ...` 之后，本提交实现 `DROP VIEW`，让用户能在 Spark SQL 中删除已存在的 Iceberg 视图，由底层 Iceberg `ViewCatalog.dropView` 持久化删除视图元数据。至此 Iceberg 视图在 Spark 端已支持读取、重命名、删除三项核心操作（`CREATE VIEW` 仍由其他提交处理）。

实现 `DROP VIEW` 比 `ALTER VIEW RENAME` 更复杂，原因在于 Spark 解析管道对 `DROP VIEW` 的处理路径不同。`ALTER VIEW RENAME` 由 Iceberg 的 `IcebergSparkSqlExtensionsParser` 解析为 `RenameTable`（Iceberg 自有语法扩展），而 `DROP VIEW` 不在 Iceberg 语法扩展中——它由 Spark 默认解析器解析为 `DropView(UnresolvedIdentifier, ifExists)` 逻辑计划。问题在于 Spark 内置的 `ResolveSessionCatalog` 解析规则对"v2 View 命令"会提前退出（不做完整解析），导致 `DropView` 中的 `UnresolvedIdentifier` 无法被正确解析为 Iceberg `ViewCatalog` 中的视图引用。本提交通过引入一个新的解析规则 `RewriteViewCommands`，在解析器层面（而非 analyzer 阶段）就把 `DropView` 拦截并改写为 Iceberg 自有的 `DropIcebergView` 逻辑计划，绕开 `ResolveSessionCatalog` 的提前退出问题。`RewriteViewCommands` 中的 `ResolvedView` 提取器负责把 `UnresolvedIdentifier` 解析为 `ResolvedIdentifier(catalog, ident)`，并对临时视图、v1 视图、非 ViewCatalog 的 catalog 做短路（返回 `None` 让 Spark 默认逻辑处理），只对 Iceberg `ViewCatalog` 中的视图产出 `DropIcebergView`。这样既支持删除 Iceberg 视图，又不破坏 Spark 原生对 temp view / global temp view / v1 view 的删除路径。

设计上分四层对应 Spark Catalyst 的"解析 → 逻辑计划 → 策略 → 物理"四阶段：(1) **解析层拦截**——在 `IcebergSparkSqlExtensionsParser.parsePlan` 中，对非 Iceberg 命令（即不匹配 Iceberg 语法扩展的 SQL，如 `DROP VIEW`）调用 `delegate.parsePlan(sqlText)` 后立即应用 `RewriteViewCommands(SparkSession.active).apply(...)`，把 `DropView` 改写为 `DropIcebergView`；(2) **逻辑计划层**——新增 `DropIcebergView(child, ifExists) extends UnaryCommand` 作为 Iceberg 自有的删除视图逻辑节点，`child` 是已解析的 `ResolvedIdentifier(catalog, ident)`；(3) **策略层**——在 `ExtendedDataSourceV2Strategy` 中新增 `case DropIcebergView(ResolvedIdentifier(viewCatalog: ViewCatalog, ident), ifExists) => DropV2ViewExec(viewCatalog, ident, ifExists) :: Nil`；(4) **物理执行层**——新增 `DropV2ViewExec(catalog, ident, ifExists) extends LeafV2CommandExec`，`run()` 调用 `catalog.dropView(ident)`，根据返回值与 `ifExists` 标志决定是否抛 `NoSuchViewException`。同时 `SparkCatalog.dropView` 从抛 `UnsupportedOperationException` 改为委托 `asViewCatalog.dropView(buildIdentifier(ident))`。新增 6 个测试覆盖 Iceberg 视图删除、`IF EXISTS` 语义、不存在视图报错、以及确保 temp view/global temp view/v1 view 的删除不受 Iceberg 路径影响。

## 如何达成设计目的

实现的关键在于"在解析器层面提前改写 `DropView`"。`RewriteViewCommands` 是一个 `Rule[LogicalPlan]`，在 `IcebergSparkSqlExtensionsParser.parsePlan` 中对非 Iceberg 命令应用。它的 `apply` 方法用 `plan.resolveOperatorsUp { case DropView(ResolvedView(resolved), ifExists) => DropIcebergView(resolved, ifExists) }` 匹配 `DropView` 逻辑计划，其中 `ResolvedView` 是一个自定义 `unapply` 提取器：对 `UnresolvedIdentifier(nameParts, true)`（`true` 表示可能是 temp view）若 `isTempView(nameParts)` 则返回 `None`（让 Spark 默认逻辑处理 temp view 删除）；对 `UnresolvedIdentifier(CatalogAndIdentifier(catalog, ident), _)` 若 `isViewCatalog(catalog)`（catalog 是 `ViewCatalog` 实例）则返回 `Some(ResolvedIdentifier(catalog, ident))`（交给 Iceberg 处理）；否则返回 `None`（让 Spark 处理 v1 view 等情况）。这种"提取器 + 短路"设计让 `DROP VIEW` 既能路由到 Iceberg `ViewCatalog.dropView`，又不破坏 Spark 原生 temp view / v1 view 的删除路径。改写后的 `DropIcebergView(ResolvedIdentifier(catalog, ident), ifExists)` 进入 analyzer 后，由 `ExtendedDataSourceV2Strategy` 翻译为 `DropV2ViewExec`，后者调用 `catalog.dropView(ident)`——Iceberg `ViewCatalog.dropView` 返回 `boolean` 表示是否实际删除，若返回 `false` 且 `ifExists` 为 `false` 则抛 `NoSuchViewException`，若 `ifExists` 为 `true` 则静默忽略。`SparkCatalog.dropView` 委托底层 `asViewCatalog.dropView`，把 Spark `Identifier` 转 Iceberg `TableIdentifier`，底层 catalog 不支持视图时返回 `false`（不抛异常，与 Spark `ViewCatalog.dropView` 契约一致）。附带地，`ResolveViews` 中两处 `spark.sessionState.catalogManager` 改为 `catalogManager`（复用 `LookupCatalog` mixin 提供的字段，代码更简洁），以及一处 `qualifyFunctionIdentifiers` 的缩进调整。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`（新文件，65 行）

**修改目的**：实现解析阶段的视图命令改写规则，把 Spark 默认解析器产出的 `DropView` 改写为 Iceberg 自有的 `DropIcebergView`，绕开 Spark `ResolveSessionCatalog` 对 v2 view 命令的提前退出问题。这是本提交的核心逻辑文件。

**工作逻辑**：`case class RewriteViewCommands(spark: SparkSession) extends Rule[LogicalPlan] with LookupCatalog`，通过 `LookupCatalog` mixin 获得 `CatalogAndIdentifier` 提取器与 `catalogManager` 字段。`override def apply(plan: LogicalPlan): LogicalPlan = plan.resolveOperatorsUp { case DropView(ResolvedView(resolved), ifExists) => DropIcebergView(resolved, ifExists) }`——用 `resolveOperatorsUp`（自底向上解析算子）匹配 `DropView`，若其 `UnresolvedIdentifier` 能被 `ResolvedView` 提取器解析为 `ResolvedIdentifier`，则改写为 `DropIcebergView(resolved, ifExists)`；否则不匹配（原样保留，让 Spark 默认逻辑处理 temp view / v1 view）。

`private def isTempView(nameParts: Seq[String]): Boolean = catalogManager.v1SessionCatalog.isTempView(nameParts)`——判断多段名是否是 Spark session catalog 中的临时视图。`private def isViewCatalog(catalog: CatalogPlugin): Boolean = catalog.isInstanceOf[ViewCatalog]`——判断 catalog 是否实现 `ViewCatalog` 接口。

`object ResolvedView` 是核心提取器，`def unapply(unresolved: UnresolvedIdentifier): Option[ResolvedIdentifier]` 分三条匹配：
- `case UnresolvedIdentifier(nameParts, true) if isTempView(nameParts) => None`——若 `UnresolvedIdentifier` 的第二个参数（`isTempView` 标志）为 `true` 且确实是 temp view，返回 `None`，让 Spark 默认逻辑处理 temp view 删除（`DROP VIEW tempView` 应删 temp view 而非 Iceberg view）。
- `case UnresolvedIdentifier(CatalogAndIdentifier(catalog, ident), _) if isViewCatalog(catalog) => Some(ResolvedIdentifier(catalog, ident))`——用 `CatalogAndIdentifier` 提取 catalog 与 identifier，若 catalog 是 `ViewCatalog`，返回 `Some(ResolvedIdentifier(catalog, ident))`，交给 Iceberg 处理。`ResolvedIdentifier` 是 Spark 内置的已解析标识符节点（含 catalog 句柄与 identifier）。
- `case _ => None`——其他情况（v1 view、非 ViewCatalog 的 catalog）返回 `None`，让 Spark 默认逻辑处理。

类注释 "ResolveSessionCatalog exits early for some v2 View commands, thus they are pre-substituted here and then handled in ResolveViews" 解释了为何要在解析阶段就改写——Spark `ResolveSessionCatalog` 对 v2 view 命令提前退出，不在 Iceberg catalog 上做完整解析，所以必须先把 `DropView` 改写为 Iceberg 自有节点 `DropIcebergView`，让它走 Iceberg 自己的解析与策略路径。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/views/DropIcebergView.scala`（新文件，30 行）

**修改目的**：定义 Iceberg 自有的"删除视图"逻辑计划节点，作为 `RewriteViewCommands` 改写的目标节点与 `ExtendedDataSourceV2Strategy` 匹配的源节点。

**工作逻辑**：`case class DropIcebergView(child: LogicalPlan, ifExists: Boolean) extends UnaryCommand`。`UnaryCommand` 是 Spark Catalyst 中"一元命令"基类（有一个 child 子计划，命令式语义无数据输出）。`child` 是已解析的 `ResolvedIdentifier(catalog, ident)`。`ifExists` 标志来自原 `DropView`，表示是否使用 `IF EXISTS` 语义（视图不存在时不报错）。`override protected def withNewChildInternal(newChild: LogicalPlan): DropIcebergView = copy(child = newChild)`——Spark Catalyst 树重构要求的样板方法，用于生成带新 child 的副本。把它放在 `org.apache.spark.sql.catalyst.plans.logical.views` 包下，与 `ResolvedV2View`（#9343 引入）同包，体现 Iceberg 视图逻辑计划的统一组织。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/DropV2ViewExec.scala`（新文件，48 行）

**修改目的**：实现删除视图的物理执行节点，调用 Iceberg `ViewCatalog.dropView` 完成实际删除。

**工作逻辑**：`case class DropV2ViewExec(catalog: ViewCatalog, ident: Identifier, ifExists: Boolean) extends LeafV2CommandExec`。`LeafV2CommandExec` 是 Spark V2 命令节点基类，提供 `run()` 方法执行命令。`override lazy val output: Seq[Attribute] = Nil`——命令无输出。`override protected def run(): Seq[InternalRow] = { val dropped = catalog.dropView(ident); if (!dropped && !ifExists) { throw new NoSuchViewException(ident) }; Nil }`——调用 `ViewCatalog.dropView(ident)` 返回 `Boolean` 表示是否实际删除；若未删除（`dropped == false`）且未指定 `IF EXISTS`（`ifExists == false`），抛 `NoSuchViewException(ident)`；若指定了 `IF EXISTS` 则静默忽略（不抛异常）；返回空行序列。`override def simpleString(maxFields: Int): String = s"DropV2View: ${ident}"`——为 explain 输出提供可读字符串。注意 `ViewCatalog.dropView` 在 Spark 接口中是 `boolean` 返回值（而非抛异常），与 `renameView` 抛异常的契约不同，本实现正确遵循了该契约。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala`

**修改目的**：在 Spark Strategy 阶段把 `DropIcebergView` 逻辑计划翻译为 `DropV2ViewExec` 物理执行节点。

**工作逻辑**：新增 import `org.apache.spark.sql.catalyst.analysis.ResolvedIdentifier`、`org.apache.spark.sql.catalyst.plans.logical.views.DropIcebergView`、`org.apache.spark.sql.connector.catalog.ViewCatalog`（后者已在 #9343 引入）。在 `apply` 方法中新增匹配（紧随 #9343 的 `RenameTable` 匹配之后）：
```scala
case DropIcebergView(ResolvedIdentifier(viewCatalog: ViewCatalog, ident), ifExists) =>
  DropV2ViewExec(viewCatalog, ident, ifExists) :: Nil
```
匹配 `DropIcebergView` 逻辑计划，其 `child` 必须是 `ResolvedIdentifier(viewCatalog: ViewCatalog, ident)`（由 `RewriteViewCommands` 在解析阶段产出），从中提取 `viewCatalog` 与 `ident`，连同 `ifExists` 标志构造 `DropV2ViewExec`。`ResolvedIdentifier` 是 Spark 内置的已解析标识符节点，模式匹配时同时做类型检查（`viewCatalog: ViewCatalog` 确保是视图 catalog）与字段提取（`ident`）。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala`

**修改目的**：在 Iceberg SQL 解析器扩展中，对非 Iceberg 命令应用 `RewriteViewCommands`，拦截 `DROP VIEW` 并改写为 `DropIcebergView`。

**工作逻辑**：新增 import `org.apache.spark.sql.catalyst.analysis.RewriteViewCommands`。原 `parsePlan` 方法中，对非 Iceberg 命令的分支 `delegate.parsePlan(sqlText)` 改为 `RewriteViewCommands(SparkSession.active).apply(delegate.parsePlan(sqlText))`。`SparkSession.active` 获取当前活跃的 `SparkSession`（解析器无 session 引用，需用静态方法）。`delegate.parsePlan(sqlText)` 让 Spark 默认解析器解析 SQL（如 `DROP VIEW ...` → `DropView(UnresolvedIdentifier, ifExists)`），随后 `RewriteViewCommands.apply` 改写其中的 `DropView` 为 `DropIcebergView`。这一步在解析阶段（parser 层）完成，早于 analyzer 阶段，确保 `DropIcebergView` 进入 analyzer 时已是 Iceberg 自有节点，不会被 Spark `ResolveSessionCatalog` 提前退出影响。对 Iceberg 自有命令（`isIcebergCommand(sqlTextAfterSubstitution)` 为 true，如 `ALTER VIEW ... RENAME TO ...`）仍走原 `parse(sqlTextAfterSubstitution) { ... }` 路径，不经过 `RewriteViewCommands`。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala`

**修改目的**：清理既有代码——复用 `LookupCatalog` 提供的 `catalogManager` 字段，并修正缩进。

**工作逻辑**：
- `private def isCatalog(name: String): Boolean = spark.sessionState.catalogManager.isCatalogRegistered(name)` → `catalogManager.isCatalogRegistered(name)`——`LookupCatalog` mixin 已提供 `catalogManager` 字段，无需再经 `spark.sessionState.catalogManager` 访问，代码更简洁。
- `private def isBuiltinFunction(name: String): Boolean = spark.sessionState.catalogManager.v1SessionCatalog.isBuiltinFunction(FunctionIdentifier(name))` → `catalogManager.v1SessionCatalog.isBuiltinFunction(FunctionIdentifier(name))`——同上。
- `qualifyFunctionIdentifiers` 方法的参数列表缩进从 8 空格调整为 4 空格（`plan: LogicalPlan,` 与 `catalogAndNamespace: Seq[String]): LogicalPlan = plan transformExpressions {`），纯格式清理，无语义变化。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：把 `SparkCatalog.dropView` 从抛 `UnsupportedOperationException` 改为委托底层 Iceberg `ViewCatalog.dropView`。

**工作逻辑**：原实现 `throw new UnsupportedOperationException("Dropping a view is not supported by catalog: " + catalogName)` 改为：
```java
if (null != asViewCatalog) {
  return asViewCatalog.dropView(buildIdentifier(ident));
}
return false;
```
`asViewCatalog` 是在 `initialize` 阶段缓存的底层 Iceberg `ViewCatalog` 句柄。`buildIdentifier(ident)` 把 Spark `Identifier` 转 Iceberg `TableIdentifier`。`asViewCatalog.dropView(...)` 返回 `boolean`（Iceberg `ViewCatalog.dropView` 契约），直接返回给 Spark。若 `asViewCatalog == null`（底层 catalog 不支持视图），返回 `false`（表示未删除任何视图）——这与 Spark `ViewCatalog.dropView` 的契约一致（返回 `false` 表示视图不存在，由上层 `DropV2ViewExec` 根据 `ifExists` 决定是否抛 `NoSuchViewException`）。注意此处不再抛 `UnsupportedOperationException`，因为 `DropV2ViewExec` 已在 `ifExists == false && dropped == false` 时抛 `NoSuchViewException`，若底层 catalog 不支持视图则 `dropped` 始终为 `false`，行为与"视图不存在"一致。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：新增 6 个测试方法覆盖 `DROP VIEW [IF EXISTS] ...` 的各种场景，验证 Iceberg 视图删除、`IF EXISTS` 语义、不存在视图报错，以及确保 temp view/global temp view/v1 view 的删除不受 Iceberg 路径影响。

**工作逻辑**：新增 import `static org.assertj.core.api.Assertions.assertThatNoException`、`org.apache.spark.sql.catalyst.catalog.SessionCatalog`。新增私有辅助方法 `private SessionCatalog v1SessionCatalog() { return spark.sessionState().catalogManager().v1SessionCatalog(); }`——获取 Spark v1 session catalog 句柄，用于验证 temp view / v1 view 的删除。新增 6 个测试：
- **`dropView`**：用 `viewCatalog.buildView(...).create()` 创建 Iceberg 视图 `viewToBeDropped`，验证 `viewCatalog.viewExists(identifier)` 为 true；执行 `DROP VIEW viewToBeDropped`；验证 `viewCatalog.viewExists(identifier)` 为 false。基础正确性测试。
- **`dropNonExistingView`**：执行 `DROP VIEW non_existing`（无 `IF EXISTS`），验证抛 `AnalysisException` 含 "The view default.non_existing cannot be found"——验证 `DropV2ViewExec` 在 `dropped == false && ifExists == false` 时抛 `NoSuchViewException`，由 Spark 转为 `AnalysisException`。
- **`dropViewIfExists`**：创建视图后执行 `DROP VIEW IF EXISTS viewToBeDropped`，验证视图被删除；再次执行 `DROP VIEW IF EXISTS viewToBeDropped`（视图已不存在），用 `assertThatNoException().isThrownBy(...)` 验证不抛异常——验证 `IF EXISTS` 语义在"视图存在"与"视图不存在"两种情况下都正确。
- **`dropGlobalTempView`**：用 `CREATE GLOBAL TEMPORARY VIEW globalViewToBeDropped AS ...` 创建 Spark 全局临时视图，验证 `v1SessionCatalog().getGlobalTempView(globalTempView).isDefined()` 为 true；执行 `DROP VIEW global_temp.globalViewToBeDropped`；验证 `getGlobalTempView` 返回 `None`。注释 "make sure that normal view deletion isn't messed up" 说明该测试目的是确保 Iceberg 的 `RewriteViewCommands` 改写不会破坏 Spark 原生 global temp view 的删除路径——`RewriteViewCommands.ResolvedView` 对 temp view 返回 `None`，让 Spark 默认逻辑处理。
- **`dropTempView`**：类似 `dropGlobalTempView`，但针对 session-scoped temp view（`CREATE TEMPORARY VIEW tempViewToBeDropped AS ...`），验证 `DROP VIEW tempViewToBeDropped` 能正确删除 temp view，不被 Iceberg 路径拦截。
- **`dropV1View`**：用 `USE spark_catalog` 切到 v1 catalog，`CREATE VIEW v1ViewToBeDropped AS ...` 创建 v1 视图（Spark 原生视图，非 Iceberg），切回 Iceberg catalog，验证 `v1SessionCatalog().tableExists(...)` 为 true；执行 `DROP VIEW spark_catalog.default.v1ViewToBeDropped`；验证 `tableExists` 为 false。注释同上，确保 v1 view 删除不被 Iceberg 路径影响——`RewriteViewCommands.ResolvedView` 对非 `ViewCatalog` 的 `spark_catalog` 返回 `None`，让 Spark 默认逻辑处理。

测试用 `viewCatalog()` 辅助方法获取 `ViewCatalog`，用 `viewCatalog.buildView(TableIdentifier.of(NAMESPACE, viewName)).withQuery("spark", sql).withDefaultNamespace(NAMESPACE).withDefaultCatalog(catalogName).withSchema(schema(sql)).create()` 直接通过 Iceberg API 创建视图（因本提交不实现 `CREATE VIEW` SQL）。`v1SessionCatalog()` 辅助方法用于断言 temp view / v1 view 的存在与删除状态。三个"确保不破坏原生路径"的测试（`dropGlobalTempView`/`dropTempView`/`dropV1View`）体现了 `RewriteViewCommands.ResolvedView` 提取器"短路返回 None"设计的关键性——必须让非 Iceberg 视图走 Spark 原生路径。

## 小结

本次提交为 Iceberg Spark 3.5 集成模块补齐 `DROP VIEW [IF EXISTS] ...` 能力，进一步解除 #9422 引入视图读取时对视图写入操作的限制（继 #9343 的 `RENAME` 之后）。实现的关键难点在于 `DROP VIEW` 由 Spark 默认解析器解析（非 Iceberg 语法扩展），且 Spark `ResolveSessionCatalog` 对 v2 view 命令提前退出，因此本提交创新性地在解析器层面（`IcebergSparkSqlExtensionsParser.parsePlan`）引入 `RewriteViewCommands` 规则，把 `DropView(UnresolvedIdentifier, ifExists)` 改写为 Iceberg 自有的 `DropIcebergView(ResolvedIdentifier, ifExists)`，绕开 `ResolveSessionCatalog` 的问题。`RewriteViewCommands.ResolvedView` 提取器通过"temp view 短路 + ViewCatalog 匹配 + 其他短路"三分法，既把 Iceberg 视图删除路由到 `ViewCatalog.dropView`，又不破坏 Spark 原生对 temp view / global temp view / v1 view 的删除路径。新增 `DropIcebergView`（逻辑计划）、`DropV2ViewExec`（物理执行，含 `IF EXISTS` 语义）两个节点，`ExtendedDataSourceV2Strategy` 新增策略匹配，`SparkCatalog.dropView` 委托底层 `asViewCatalog.dropView` 并遵循 `boolean` 返回值契约。6 个测试覆盖 Iceberg 视图删除、`IF EXISTS` 语义、不存在报错，以及三类原生视图（temp/global temp/v1）的删除回归保护。至此 Iceberg 视图在 Spark 端已支持读取、重命名、删除三项核心操作，视图 SQL 写入能力基本成形。
