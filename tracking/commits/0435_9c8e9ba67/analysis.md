# 提交 0435：Spark: Support altering view properties (#9582)

## 提交信息

- **序号**：0435
- **哈希**：9c8e9ba67f41797ded61c11fa90af2d8df0da1cd
- **短哈希**：9c8e9ba67
- **日期**：2024-02-01 09:20:33 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Support altering view properties (#9582)。补充说明指出本提交为以下两种 `ALTER` 语法增加支持（依据 Spark 官方 DDL 文档 sql-ref-syntax-ddl-alter-view）：`ALTER VIEW <viewName> SET TBLPROPERTIES (...)` 与 `ALTER VIEW <viewName> UNSET TBLPROPERTIES (...)`。
- **PR/Issue**：#9582

## 总体目的

本提交为 Iceberg 的 Spark 3.5 视图（View）扩展补齐了"修改视图属性"的能力。在此之前，Iceberg 视图已支持创建、删除、列举，但 `ALTER VIEW ... SET/UNSET TBLPROPERTIES` 这两类标准 Spark DDL 尚未落地——`SparkCatalog.alterView` 直接抛 `UnsupportedOperationException`。这意味着用户无法通过 SQL 增量调整视图的自定义属性（如添加业务标签、注释），只能重建视图，体验不完整。

本提交沿着 Spark 的逻辑执行链路打通了整条通路：从 Analyzer 的规则解析（把 `SetViewProperties`/`UnsetViewProperties` 逻辑计划解析到具体视图），到 Strategy 层翻译成物理执行节点（新增两个 `AlterV2View*Exec`），再到 Catalog 层真正落库（`SparkCatalog.alterView` 调用 Iceberg 的 `UpdateViewProperties` 提交）。同时引入了对保留属性（reserved properties）的保护：`provider`、`location`、`format-version`、`spark.query-column-names` 这类由系统/Spark 内部维护的属性不允许用户通过 `SET`/`UNSET` 改动，避免破坏视图元数据一致性。

附带地，本提交把 Spark 内部使用的查询列名属性键从 `queryColumnNames` 重命名为 `spark.query-column-names`。原先的键名没有命名空间前缀，容易与用户自定义属性冲突，也不符合 Spark 表属性以 `spark.` 为前缀的惯例。重命名后该属性明确归属 Spark，并作为保留属性受保护。

## 如何达成设计目的

整体设计遵循 Spark 的"逻辑计划 → Analyzer 规则 → 物理执行节点 → Catalog 落库"分层。新增能力分三层落地：（1）把原先散落在 `ResolveViews` 中的视图加载/判定辅助逻辑抽到独立的 `ViewUtil` 对象，并在 `RewriteViewCommands` 中新增 `UnresolvedView` → `ResolvedV2View` 的解析（注释说明这步必须在此处做，以赶在 Analyzer 报"V2 视图不支持"之前完成解析）；（2）新增两个 `LeafV2CommandExec` 物理节点 `AlterV2ViewSetPropertiesExec`/`AlterV2ViewUnsetPropertiesExec`，在 `ExtendedDataSourceV2Strategy` 中把 `SetViewProperties`/`UnsetViewProperties` 逻辑计划翻译为这两个节点；（3）在 `SparkCatalog.alterView` 中真正调用 Iceberg 的 `UpdateViewProperties` 提交，并对保留属性做校验。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ViewUtil.scala`（新增）

**修改目的**：集中视图相关的辅助逻辑，供多个分析规则复用，消除重复。

**工作逻辑**：新对象 `ViewUtil` 提供三个成员：
- `loadView(catalog, ident)`：尝试从 `ViewCatalog` 加载视图，捕获 `NoSuchViewException` 返回 `None`，非 `ViewCatalog` 返回 `None`。原是 `ResolveViews` 内的方法，现抽出复用。
- `isViewCatalog(catalog)`：判断 catalog 是否为 `ViewCatalog` 实例。
- `implicit class IcebergViewHelper(plugin)`：提供 `asViewCatalog` 方法，把 `CatalogPlugin` 安全转换为 `ViewCatalog`，非视图 catalog 时抛 `missingCatalogAbilityError`。原是 `ResolveViews` 内的隐式类，现抽出。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala`

**修改目的**：改为使用新抽出的 `ViewUtil`，删除内联的 `loadView` 方法与 `IcebergViewHelper` 隐式类。

**工作逻辑**：`import ViewUtil.IcebergViewHelper` 后，原 `loadView(catalog, ident)` 调用改为 `ViewUtil.loadView(...)`，`UnresolvedRelation`/`UnresolvedTableOrView` 两处分支同步替换。删除了原 27 行的 `loadView` 方法与 13 行的 `IcebergViewHelper` 隐式类定义（含若干不再需要的 import：`CatalogPlugin`、`Identifier`、`ViewCatalog`）。逻辑行为不变，仅是位置迁移。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

**修改目的**：复用 `ViewUtil`，并新增对 `UnresolvedView` 的解析，使 `ALTER VIEW` 等命令能在 Analyzer 报错前解析到具体 V2 视图。

**工作逻辑**：

1. 复用 `ViewUtil`：`isViewCatalog` 调用改为 `ViewUtil.isViewCatalog(...)`，`DropView`/`CreateView` 等模式匹配中的 `ResolvedView` 改为 `ResolvedIdent`（见下）。

2. 原先的 `ResolvedView` unapply 对象（匹配 `UnresolvedIdentifier`，提取出 `ResolvedIdentifier(catalog, ident)`）被重命名为 `ResolvedIdent`，逻辑不变——仅是名字调整，避免与新 `ResolvedView` 冲突。

3. 新增 `ResolvedView` unapply 对象，匹配 `Seq[String]`（视图名分段）：若是临时视图返回 `None`；否则用 `CatalogAndIdentifier` 拆出 catalog 与 ident，若是 ViewCatalog 且 `ViewUtil.loadView` 成功，则返回 `ResolvedV2View(catalog.asViewCatalog, ident)`。配套新增模式：`case u @ UnresolvedView(ResolvedView(resolved), _, _, _) => ViewUtil.loadView(...).map(_ => ResolvedV2View(...)).getOrElse(u)`。注释明确：这一步必须放在 `RewriteViewCommands` 而非 `ResolveViews`，以赶在 Analyzer 因"V2 视图不支持"报错前完成解析。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/AlterV2ViewSetPropertiesExec.scala`（新增）

**修改目的**：实现 `ALTER VIEW ... SET TBLPROPERTIES` 的物理执行。

**工作逻辑**：`case class AlterV2ViewSetPropertiesExec(catalog, ident, properties)` 继承 `LeafV2CommandExec`，`output` 为空（无返回行）。`run()` 把 `properties` 映射为 `ViewChange.setProperty(property, value)` 序列，调用 `catalog.alterView(ident, changes: _*)` 提交，返回 `Nil`。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/AlterV2ViewUnsetPropertiesExec.scala`（新增）

**修改目的**：实现 `ALTER VIEW ... UNSET TBLPROPERTIES` 的物理执行，含 `IF EXISTS` 语义。

**工作逻辑**：`case class AlterV2ViewUnsetPropertiesExec(catalog, ident, propertyKeys, ifExists)`。`run()` 中：若 `!ifExists`，则过滤出"视图当前不包含的 key"逐个抛 `AnalysisException("Cannot remove property that is not set: '...'")`，即默认严格模式——移除不存在的属性直接报错；带 `IF EXISTS` 时则跳过不存在的 key。随后把 `propertyKeys` 映射为 `ViewChange.removeProperty`，调用 `catalog.alterView` 提交。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala`

**修改目的**：把 `SetViewProperties`/`UnsetViewProperties` 逻辑计划翻译为上述两个执行节点。

**工作逻辑**：新增两个 `case` 分支：`SetViewProperties(ResolvedV2View(catalog, ident), properties) => AlterV2ViewSetPropertiesExec(catalog, ident, properties) :: Nil`；`UnsetViewProperties(ResolvedV2View(catalog, ident), propertyKeys, ifExists) => AlterV2ViewUnsetPropertiesExec(...) :: Nil`。并补充对应 import。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：真正实现 `alterView`（原直接抛异常），并加入保留属性校验。

**工作逻辑**：

1. `alterView` 实现：若 `asViewCatalog` 非空，加载视图，取 `UpdateViewProperties`，遍历 `ViewChange`：`SetProperty` 调 `verifyNonReservedPropertyIsSet` 后 `set(property, value)`；`RemoveProperty` 调 `verifyNonReservedPropertyIsUnset` 后 `remove(property)`。最后 `commit()` 并返回 `SparkView`。捕获 `NoSuchViewException` 转为 Spark 的 `NoSuchViewException`。仍无 `asViewCatalog` 时抛 `UnsupportedOperationException`。

2. 新增静态校验方法：`verifyNonReservedProperty(property, errorMsg)` 检查属性是否在 `SparkView.RESERVED_PROPERTIES` 中，是则抛 `UnsupportedOperationException`；`verifyNonReservedPropertyIsSet` 用 "Cannot set reserved property: '%s'"，`verifyNonReservedPropertyIsUnset` 用 "Cannot unset reserved property: '%s'"。

3. 顺带把创建视图时写入的查询列名属性键从 `queryColumnNames` 改为 `spark.query-column-names`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkView.java`

**修改目的**：重命名内部属性键，并公开保留属性集合供 `SparkCatalog` 校验。

**工作逻辑**：`QUERY_COLUMN_NAMES` 常量由 `"queryColumnNames"` 改为 `"spark.query-column-names"`；`RESERVED_PROPERTIES` 由 `private` 改为 `public`（集合含 `provider`、`location`、`format-version`、`QUERY_COLUMN_NAMES`），以便 `SparkCatalog` 引用做保留属性校验。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：覆盖新增的 `SET`/`UNSET TBLPROPERTIES` 能力与保留属性保护，并适配属性键重命名。

**工作逻辑**：

1. 既有断言适配：原断言 `containsEntry("queryColumnNames", "id,data")` 改为 `containsEntry("spark.query-column-names", "id,data")`。

2. 新增 5 个测试用例：
   - `alterViewSetProperties`：验证 SET 可新增/更新属性（含 comment）。
   - `alterViewSetReservedProperties`：验证 SET 保留属性被拒——`provider`/`location` 抛 `AnalysisException`（"reserved table property"），`format-version`/`spark.query-column-names` 抛 `UnsupportedOperationException`（"Cannot set reserved property"）。
   - `alterViewUnsetProperties`：验证 UNSET 可移除属性，其余属性保留。
   - `alterViewUnsetUnknownProperty`：验证 UNSET 不存在属性默认报错，带 `IF EXISTS` 则静默通过。
   - `alterViewUnsetReservedProperties`：验证 UNSET 保留属性被拒，分别覆盖 `provider`/`location`（`AnalysisException`）、`format-version`（`UnsupportedOperationException` "Cannot unset"）、`spark.query-column-names`（不存在时 `AnalysisException`，`IF EXISTS` 时 `UnsupportedOperationException` "Cannot unset"）。

## 小结

本提交补齐了 Iceberg Spark 视图的属性变更能力，是视图功能走向功能完备的重要一步。设计上沿 Spark 标准分层（分析规则 → Strategy → 物理节点 → Catalog）端到端打通，并把视图辅助逻辑收敛到 `ViewUtil`、执行节点拆成独立类，结构清晰可维护。保留属性保护机制防止用户破坏系统维护的元数据。属性键 `queryColumnNames` → `spark.query-column-names` 的重命名规范化了命名空间，但属于行为变更，依赖该键的外部消费者需同步适配。测试覆盖了正常 SET/UNSET、IF EXISTS 语义、保留属性拒绝等关键路径，较为完整。
