# 提交 0363：Spark: Support renaming views (#9343)

## 提交信息

- **序号**：0363
- **哈希**：a60ee5d6833c648ddc7d3d0d7f52f6c705e48d63
- **短哈希**：a60ee5d68
- **日期**：2024-01-16 11:42:25 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Support renaming views (#9343)
- **PR/Issue**：#9343

## 总体目的

本提交为 Iceberg Spark 3.5 集成模块补齐"重命名视图（`ALTER VIEW ... RENAME TO ...`）"的能力。在更早的提交（#9422，本系列 0335）中，Iceberg 已经实现了在 Spark 中读取 Iceberg 视图的能力——`SparkCatalog` 实现 Spark 的 `ViewCatalog` 接口提供 `loadView`，`ResolveViews` Analyzer 规则把对视图名的引用展开为视图 SQL 子查询——但当时所有视图写入操作（`createView`/`alterView`/`dropView`/`renameView`/`listViews`）一律抛 `UnsupportedOperationException`，只读。本提交专门解除 `renameView` 的"只读"限制，让用户能在 Spark SQL 中通过标准 `ALTER VIEW <old> RENAME TO <new>` 语句把一个已存在的 Iceberg 视图改名，由底层 Iceberg `ViewCatalog.renameView` 持久化到视图元数据。

实现这条 SQL 的难点在于 Spark Catalyst 的执行管道分工：`ALTER VIEW ... RENAME TO ...` 在 Spark 解析阶段被 `IcebergSparkSqlExtensionsParser` 解析为通用的 `RenameTable` 逻辑计划（`RenameTable` 同时承载表重命名与视图重命名，通过 `isView: Boolean` 区分），其中源标识符是 `UnresolvedTableOrView`（一个"既可能是表也可能是视图"的未解析节点）。要让该计划落到 Iceberg 的 `ViewCatalog.renameView` 上，需要：(1) 在 Analyzer 阶段把 `UnresolvedTableOrView` 解析为"已解析的 Iceberg 视图"节点——本提交新增 `ResolvedV2View(catalog, identifier)` 逻辑节点作为该解析结果；(2) 在 Strategy 阶段把 `RenameTable(ResolvedV2View, ..., isView=true)` 计划翻译为物理执行节点 `RenameV2ViewExec`，后者调用 `ViewCatalog.renameView`。同时还要校验目标视图名与源视图名属于同一 catalog（Iceberg 不支持跨 catalog 移动视图），并把 Iceberg 抛出的 `NoSuchViewException`/`AlreadyExistsException` 翻译为 Spark 对应异常类型以保持框架契约。本提交新增 6 个测试方法覆盖正常重命名、被临时视图遮蔽时的优先级、跨 catalog 重命名报错、源视图不存在报错、目标名已作为视图存在报错、目标名已作为表存在报错等场景。

## 如何达成设计目的

实现路径分四层，对应 Spark Catalyst 的"解析 → 逻辑计划 → 策略 → 物理"四阶段：(1) **逻辑计划层**——新增 `ResolvedV2View(catalog: ViewCatalog, identifier: Identifier)` 作为 `LeafNodeWithoutStats`（叶子节点，`output = Nil`），它是"已解析的 Iceberg 视图引用"的逻辑表示，类比 Spark 内置的 `ResolvedTable`，但专门用于视图。把它放在 `org.apache.spark.sql.catalyst.plans.logical.views` 包下，与 Spark 3.5 内置视图计划包路径对齐。(2) **Analyzer 解析层**——在 `ResolveViews.apply` 中新增第二条匹配规则 `case u@UnresolvedTableOrView(CatalogAndIdentifier(catalog, ident), _, _) => loadView(catalog, ident).map(_ => ResolvedV2View(catalog.asViewCatalog, ident)).getOrElse(u)`：当遇到 `UnresolvedTableOrView`（`ALTER VIEW RENAME TO` 的源标识符就是这种节点）时，尝试用 `loadView` 加载视图；若加载到，则用 `ResolvedV2View` 替换原未解析节点；否则原样返回（让后续规则或默认逻辑处理，最终可能报"table or view not found"）。新增 `implicit class ViewHelper(plugin: CatalogPlugin)` 提供 `asViewCatalog` 隐式转换，把 `CatalogPlugin` 强转为 `ViewCatalog`，若 catalog 不支持视图则抛 `QueryCompilationErrors.missingCatalogAbilityError(plugin, "views")`——这把"catalog 是否支持视图"的检查收敛到一处。(3) **Strategy 层**——在 `ExtendedDataSourceV2Strategy` 中新增 `case RenameTable(ResolvedV2View(oldCatalog: ViewCatalog, oldIdent), newName, isView@true) => ...`：匹配到"对已解析视图的重命名"计划时，先用 `Spark3Util.catalogAndIdentifier` 解析目标名 `newName` 为 `CatalogAndIdentifier`，校验 `oldCatalog.name != newIdent.catalog().name()` 时抛 `AnalysisException("Cannot move view between catalogs: from=... and to=...")`（禁止跨 catalog 重命名），最后产出 `RenameV2ViewExec(oldCatalog, oldIdent, newIdent.identifier())`。(4) **物理执行层**——新增 `RenameV2ViewExec(catalog, oldIdent, newIdent) extends LeafV2CommandExec`，`run()` 方法调用 `catalog.renameView(oldIdent, newIdent)` 完成实际重命名，返回 `Seq.empty`（命令节点无输出）。同时 `SparkCatalog.renameView` 从抛 `UnsupportedOperationException` 改为委托 `asViewCatalog.renameView(buildIdentifier(from), buildIdentifier(to))`，并把 Iceberg 的 `NoSuchViewException`/`AlreadyExistsException` 翻译为 Spark 的 `NoSuchViewException`/`ViewAlreadyExistsException`，保留原 `UnsupportedOperationException` 作为"底层 catalog 不支持视图"的兜底分支。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/views/ResolvedV2View.scala`（新文件，31 行）

**修改目的**：定义"已解析的 Iceberg 视图引用"逻辑节点，作为 `ALTER VIEW RENAME TO` 等命令在逻辑计划阶段的视图表示。

**工作逻辑**：`case class ResolvedV2View(catalog: ViewCatalog, identifier: Identifier) extends LeafNodeWithoutStats`。`LeafNodeWithoutStats` 是 Spark Catalyst 中"无统计信息的叶子节点"基类，适合命令类节点（不需要 CBO 统计）。`override def output: Seq[Attribute] = Nil`——视图引用节点本身不产出数据行，输出属性为空。该节点携带 `ViewCatalog`（已强转好的视图 catalog 句柄）与 `Identifier`（视图标识符），让下游 Strategy/Exec 可以直接拿到调用 `renameView` 所需的全部信息，无需再次解析或强转。把它放在 `org.apache.spark.sql.catalyst.plans.logical.views` 包下，与 Spark 3.5 内置的视图相关逻辑计划（如 `CreateView`、`AlterView`）同包，体现"这是 Spark 视图计划体系的一部分"。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala`

**修改目的**：扩展 `ResolveViews` 解析规则，把 `ALTER VIEW RENAME TO` 产生的 `UnresolvedTableOrView` 源节点解析为 `ResolvedV2View`。

**工作逻辑**：
- 新增 import `org.apache.spark.sql.catalyst.plans.logical.views.ResolvedV2View`。
- 在 `apply` 方法中新增第二条匹配规则（紧随原 `UnresolvedRelation` 规则之后）：
  ```scala
  case u@UnresolvedTableOrView(CatalogAndIdentifier(catalog, ident), _, _) =>
    loadView(catalog, ident)
      .map(_ => ResolvedV2View(catalog.asViewCatalog, ident))
      .getOrElse(u)
  ```
  `UnresolvedTableOrView` 是 Spark 3.5 中"既可能是表也可能是视图"的未解析节点，`ALTER VIEW RENAME TO` 的源标识符就是这种节点（因为 `ALTER VIEW` 语义上要求源必须是视图，但解析器在不知道 catalog 内容时无法确定）。`CatalogAndIdentifier(catalog, ident)` 是 `LookupCatalog` 提供的提取器，把多段名拆为 catalog + identifier。`loadView` 复用既有逻辑（检查 catalog 是否为 `ViewCatalog`、调用 `loadView`、`NoSuchViewException` 返回 `None`）。若加载到视图，则用 `ResolvedV2View(catalog.asViewCatalog, ident)` 替换原节点——注意这里丢弃了加载到的 `View` 对象（`_ =>`），因为 `ResolvedV2View` 只需 catalog 与 identifier，`renameView` 不需要视图内容。`catalog.asViewCatalog` 是新增的隐式转换（见下文）。若未加载到视图，原样返回 `u`，让后续规则或默认逻辑处理（最终 `ALTER VIEW non_existing RENAME TO ...` 会报 "table or view cannot be found"）。
- 新增隐式类 `implicit class ViewHelper(plugin: CatalogPlugin)` 与方法 `def asViewCatalog: ViewCatalog`：把 `CatalogPlugin` 强转为 `ViewCatalog`，匹配成功返回 `viewCatalog`，否则抛 `QueryCompilationErrors.missingCatalogAbilityError(plugin, "views")`。这是把"catalog 是否支持视图"的强转检查收敛到一处的工程做法，避免在多处分散写 `match { case vc: ViewCatalog => vc; case _ => throw ... }`。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala`

**修改目的**：在 Spark Strategy 阶段把"对已解析视图的重命名"逻辑计划翻译为 `RenameV2ViewExec` 物理执行节点。

**工作逻辑**：
- 新增 import：`org.apache.spark.sql.AnalysisException`、`org.apache.spark.sql.catalyst.plans.logical.RenameTable`、`org.apache.spark.sql.catalyst.plans.logical.views.ResolvedV2View`、`org.apache.spark.sql.connector.catalog.ViewCatalog`。
- 在 `apply(plan: LogicalPlan): Seq[SparkPlan]` 中新增匹配：
  ```scala
  case RenameTable(ResolvedV2View(oldCatalog: ViewCatalog, oldIdent), newName, isView@true) =>
    val newIdent = Spark3Util.catalogAndIdentifier(spark, newName.toList.asJava)
    if (oldCatalog.name != newIdent.catalog().name()) {
      throw new AnalysisException(
        s"Cannot move view between catalogs: from=${oldCatalog.name} and to=${newIdent.catalog().name()}")
    }
    RenameV2ViewExec(oldCatalog, oldIdent, newIdent.identifier()) :: Nil
  ```
  `RenameTable(child, newName, isView)` 是 Spark 内置逻辑计划，`child` 是源（表或视图）的已解析节点，`newName` 是目标名（多段字符串序列），`isView` 标识这是否是视图重命名。匹配条件 `ResolvedV2View(oldCatalog: ViewCatalog, oldIdent)` 确保 child 是 Iceberg 已解析视图，`isView@true` 确保这是视图重命名（而非表重命名）。`Spark3Util.catalogAndIdentifier(spark, newName.toList.asJava)` 把目标名序列解析为 `CatalogAndIdentifier`（含 catalog 与 identifier）。校验 `oldCatalog.name != newIdent.catalog().name()` 抛 `AnalysisException`——Iceberg 视图元数据与 catalog 绑定，跨 catalog 移动需要复制元数据，本提交不支持，明确报错。最后产出 `RenameV2ViewExec(oldCatalog, oldIdent, newIdent.identifier())`。`newIdent.identifier()` 取目标 identifier 部分（去掉 catalog 句柄，因为 `RenameV2ViewExec` 已持有 `oldCatalog`，且已校验同 catalog）。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/RenameV2ViewExec.scala`（新文件，45 行）

**修改目的**：实现视图重命名的物理执行节点，调用 Iceberg `ViewCatalog.renameView` 完成实际改名。

**工作逻辑**：`case class RenameV2ViewExec(catalog: ViewCatalog, oldIdent: Identifier, newIdent: Identifier) extends LeafV2CommandExec`。`LeafV2CommandExec` 是 Spark V2 命令节点的基类，提供 `run()` 方法执行命令并返回行序列。`override lazy val output: Seq[Attribute] = Nil`——命令无输出。`override protected def run(): Seq[InternalRow] = { catalog.renameView(oldIdent, newIdent); Seq.empty }`——调用 `ViewCatalog.renameView` 把视图从 `oldIdent` 改名为 `newIdent`，返回空行序列。`override def simpleString(maxFields: Int): String = s"RenameV2View ${oldIdent} to {newIdent}"`——为 explain 输出提供可读字符串（注意：此处 `{newIdent}` 缺 `$` 符号，是个小瑕疵，不影响功能，仅 explain 显示不正确）。整个节点是无副作用的纯命令节点，不读数据也不写数据，只调用 catalog API 改元数据。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：把 `SparkCatalog.renameView` 从抛 `UnsupportedOperationException` 改为委托底层 Iceberg `ViewCatalog.renameView`，并翻译异常类型。

**工作逻辑**：原实现 `throw new UnsupportedOperationException("Renaming a view is not supported by catalog: " + catalogName)` 改为：
```java
if (null != asViewCatalog) {
  try {
    asViewCatalog.renameView(buildIdentifier(fromIdentifier), buildIdentifier(toIdentifier));
  } catch (org.apache.iceberg.exceptions.NoSuchViewException e) {
    throw new NoSuchViewException(fromIdentifier);
  } catch (org.apache.iceberg.exceptions.AlreadyExistsException e) {
    throw new ViewAlreadyExistsException(toIdentifier);
  }
} else {
  throw new UnsupportedOperationException("Renaming a view is not supported by catalog: " + catalogName);
}
```
`asViewCatalog` 是在 `initialize` 阶段缓存的底层 Iceberg `ViewCatalog` 句柄（若底层 catalog 实现 `ViewCatalog` 则非 null）。`buildIdentifier(fromIdentifier)` 把 Spark `Identifier` 转 Iceberg `TableIdentifier`（视图标识符复用 `TableIdentifier`）。`renameView` 调用底层 catalog 的改名实现（如 `InMemoryCatalog` 或 `JdbcCatalog` 等具体实现）。异常翻译：Iceberg `NoSuchViewException`（源视图不存在）→ Spark `NoSuchViewException(fromIdentifier)`；Iceberg `AlreadyExistsException`（目标视图已存在）→ Spark `ViewAlreadyExistsException(toIdentifier)`——这保持 Spark 框架对 `ViewCatalog.renameView` 异常契约的期望，让 Spark 上层能正确识别错误类型并生成用户友好的错误消息。若 `asViewCatalog == null`（底层 catalog 不支持视图），仍抛 `UnsupportedOperationException` 作为兜底。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：新增 6 个测试方法覆盖 `ALTER VIEW ... RENAME TO ...` 的各种场景，验证正常重命名、与临时视图的优先级交互、跨 catalog 报错、源不存在报错、目标冲突报错等。

**工作逻辑**：新增 `import java.util.Random` 与私有辅助方法 `private String viewName(String viewName) { return viewName + new Random().nextInt(1000000); }`——给视图名加随机后缀避免测试间名称冲突（因为 `TestViews` 共享 catalog 状态）。新增 6 个测试：
- **`renameView`**：用 `viewCatalog.buildView(...).create()` 创建视图 `originalView`，执行 `ALTER VIEW originalView RENAME TO renamedView`，验证 `SELECT * FROM renamedView` 返回 10 行原数据。这是基础正确性测试。
- **`renameViewHiddenByTempView`**：同时创建同名 Iceberg 视图与 Spark 临时视图（`CREATE TEMPORARY VIEW originalView AS ...`，临时视图选 id<=5，Iceberg 视图选 id>5），执行 `ALTER VIEW originalView RENAME TO renamedView`——验证 Spark 临时视图优先级更高，被重命名的是临时视图（`SELECT * FROM renamedView` 返回 5 行 id<=5），而 Iceberg 视图仍以原名存在（`viewCatalog.viewExists(originalView)` 为 true，`SELECT * FROM originalView` 返回 5 行 id>5）；再次执行 `ALTER VIEW originalView RENAME TO renamedView` 才重命名 Iceberg 视图。这验证了 `ResolveViews` 中 `UnresolvedTableOrView` 解析路径不会错误地把临时视图当作 Iceberg 视图处理（Spark 内置 temp view 解析优先）。
- **`renameViewToDifferentTargetCatalog`**：创建视图后执行 `ALTER VIEW originalView RENAME TO spark_catalog.renamedView`（目标 catalog 是 `spark_catalog`，源是 `spark_with_views`），验证抛 `AnalysisException` 含 "Cannot move view between catalogs: from=spark_with_views and to=spark_catalog"——验证 `ExtendedDataSourceV2Strategy` 中的跨 catalog 校验。
- **`renameNonExistingView`**：执行 `ALTER VIEW non_existing RENAME TO target`，验证抛 `AnalysisException` 含 "The table or view `non_existing` cannot be found"——验证 `ResolveViews` 在 `loadView` 返回 `None` 时原样返回，由 Spark 默认逻辑报错。
- **`renameViewTargetAlreadyExistsAsView`**：创建两个视图 source 与 target，执行 `ALTER VIEW source RENAME TO target`，验证抛 `AnalysisException` 含 "Cannot create view default.<target> because it already exists"——验证 Iceberg `AlreadyExistsException` 被翻译为 Spark `ViewAlreadyExistsException` 后由 Spark 生成友好错误消息。
- **`renameViewTargetAlreadyExistsAsTable`**：创建视图 source 与同名表 target，执行 `ALTER VIEW source RENAME TO target`，验证同样抛 `AnalysisException` 含 "Cannot create view default.<target> because it already exists"——验证目标名为表时也被正确识别为冲突（Iceberg `ViewCatalog.renameView` 在目标名被表占用时抛 `AlreadyExistsException`）。

测试用 `viewCatalog()` 辅助方法获取 `ViewCatalog`，用 `viewCatalog.buildView(TableIdentifier.of(NAMESPACE, viewName)).withQuery("spark", sql).withDefaultNamespace(NAMESPACE).withDefaultCatalog(catalogName).withSchema(schema(sql)).create()` 直接通过 Iceberg API 创建视图（绕过 Spark SQL，因为本提交不实现 `CREATE VIEW`），再用 `sql("ALTER VIEW %s RENAME TO %s", ...)` 触发重命名路径。`insertRows(10)` 与 `row(i)` 复用既有辅助方法构造测试数据。

另有一个小修正：`fullFunctionIdentifierNotRewrittenLoadFailure` 测试中，原 `String sql = String.format("SELECT spark_catalog.system.bucket(100, 'a') AS bucket_result, 'a' AS value", catalogName)` 的 `String.format` 多余（SQL 文本无 `%s` 占位符），改为直接字符串字面量，是顺手清理。

## 小结

本次提交为 Iceberg Spark 3.5 集成模块补齐 `ALTER VIEW ... RENAME TO ...` 能力，解除 #9422 引入视图读取时对 `renameView` 的"只读"限制。实现遵循 Spark Catalyst 四阶段管道：(1) 新增 `ResolvedV2View(catalog, identifier)` 逻辑节点作为"已解析视图引用"表示；(2) 在 `ResolveViews` Analyzer 规则中新增匹配 `UnresolvedTableOrView` 的解析分支，调用 `loadView` 后产出 `ResolvedV2View`，并引入 `ViewHelper.asViewCatalog` 隐式转换收敛 catalog 强转检查；(3) 在 `ExtendedDataSourceV2Strategy` 中新增 `RenameTable(ResolvedV2View, ..., isView=true)` → `RenameV2ViewExec` 的策略匹配，含跨 catalog 重命名校验；(4) 新增 `RenameV2ViewExec` 物理节点调用 `ViewCatalog.renameView`。`SparkCatalog.renameView` 从抛 `UnsupportedOperationException` 改为委托底层 `asViewCatalog.renameView`，并翻译 Iceberg 异常为 Spark 异常类型。6 个测试覆盖正常重命名、临时视图优先级、跨 catalog 报错、源不存在、目标为视图/表冲突等场景。这是 Iceberg 视图在 Spark 端从"只读"走向"可写入"的逐步演进（后续 #9421 补 `DROP VIEW`，本系列 0365），保持了与 Spark 原生视图 SQL 语义的对齐。
