# 提交 0438：Spark 3.4: Support altering view properties (#9610)

## 提交信息

- **序号**：0438
- **哈希**：187be85319db069fc052f5ee1a06f1fb2489bebb
- **短哈希**：187be8531
- **日期**：2024-02-01 13:33:21 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Support altering view properties (#9610)
- **PR/Issue**：#9610

## 总体目的

本提交为 Iceberg 的 Spark 3.4 扩展补齐了对视图（View）属性的修改能力，即 `ALTER VIEW ... SET TBLPROPERTIES` 和 `ALTER VIEW ... UNSET TBLPROPERTIES` 这两类 SQL 语句。在此提交之前，Iceberg 虽然能创建和删除视图，但 `SparkCatalog.alterView` 一旦被调用就会直接抛出 `UnsupportedOperationException`，用户无法对已存在视图的属性做任何增删改。这使得视图的元数据（如自定义标签、描述等）一旦创建就不可调整，使用体验上明显落后于 Spark 原生 catalog 以及 Iceberg 对表（Table）已有的属性管理能力。

补齐此能力的同时，本提交还做了一项重要的属性命名规范化：把内部使用的 `queryColumnNames` 改为带前缀的 `spark.query-column-names`。原命名没有命名空间隔离，容易与用户自定义属性混淆，也更容易被误判为可被用户随意修改的普通属性。改为带 `spark.` 前缀后，可以明确标识这是 Spark catalog 内部维护的保留属性，并在 SET/UNSET 时一并做保留属性校验，防止用户误改导致视图元数据损坏。

此外，本提交还对视图相关分析逻辑做了重构，把原先散落在 `ResolveViews` 和 `RewriteViewCommands` 中的视图加载、catalog 能力判断等公共方法抽取到新的 `ViewUtil` 单例对象中，以便后续多个分析规则复用，并为引入新的 `ResolvedView` 匹配模式（基于已加载视图解析 `UnresolvedView`）铺路。这是为了让 `ALTER VIEW`、`CREATE OR REPLACE VIEW` 等命令能在分析阶段正确把 `UnresolvedView` 解析为 `ResolvedV2View`，避免落到 Spark 原生 Analyzer 时报"V2 views aren't supported"的错误。

## 如何达成设计目的

实现路径分为四部分：一是抽取公共工具类 `ViewUtil`，统一 `loadView`、`isViewCatalog`、`asViewCatalog` 等能力；二是在 `RewriteViewCommands` 中新增对 `UnresolvedView` 的解析分支，将其在扩展规则阶段就转为 `ResolvedV2View`，绕过原生 Analyzer 对 V2 视图的支持缺失；三是在 `ExtendedDataSourceV2Strategy` 中把 Spark 内置的 `SetViewProperties`/`UnsetViewProperties` 逻辑计划映射到新写的 `AlterV2ViewSetPropertiesExec`/`AlterV2ViewUnsetPropertiesExec` 物理算子；四是在 `SparkCatalog.alterView` 中真正落地属性变更，并加入对保留属性（`provider`、`location`、`format-version`、`spark.query-column-names`）的校验，配合 `SparkView` 中保留属性集合的可见性调整。

## 修改详情

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala

**修改目的**：将原本定义在此处的 `loadView` 方法和 `IcebergViewHelper` 隐式类移除，改为引用新抽取的 `ViewUtil` 中的同名能力，消除重复代码并为其他规则复用做准备。

**工作逻辑**：原先 `ResolveViews` 内部既定义了 `loadView(catalog, ident): Option[View]`（带 `NoSuchViewException` 捕获），又定义了隐式类 `IcebergViewHelper` 提供 `asViewCatalog` 转换。本提交删除了这两个定义，改为 `import org.apache.spark.sql.catalyst.analysis.ViewUtil.IcebergViewHelper` 并把对 `loadView` 的两处调用改为 `ViewUtil.loadView(...)`。同时清理了不再需要的 `CatalogPlugin`、`Identifier`、`ViewCatalog` 等 import。这是纯重构，行为不变。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala

**修改目的**：重命名内部的 `ResolvedView` 提取器为 `ResolvedIdent` 以消除歧义，并新增一个基于已加载视图的 `ResolvedView` 提取器，用于在分析阶段把 `UnresolvedView` 解析为 `ResolvedV2View`。

**工作逻辑**：原 `ResolvedView` 对象其实只做"标识符解析"（从 `UnresolvedIdentifier` 中提取 catalog+ident），并未真正加载视图，名字具有误导性。本提交将其重命名为 `ResolvedIdent`，相应地把 `DropView`、`CreateView` 两个 case 分支中的匹配名替换。

新增加的 `ResolvedView` 私有对象则作用于 `Seq[String]`（名字部分），它会先用 `isTempView` 排除临时视图，再通过 `CatalogAndIdentifier` 解析出 catalog 与 ident，若 catalog 是 ViewCatalog 且 `ViewUtil.loadView` 能成功加载到视图，则返回 `ResolvedV2View`。配合新增的 case 分支：

```scala
case u@UnresolvedView(ResolvedView(resolved), _, _, _) =>
  ViewUtil.loadView(resolved.catalog, resolved.identifier)
    .map(_ => ResolvedV2View(resolved.catalog.asViewCatalog, resolved.identifier))
    .getOrElse(u)
```

这样 `ALTER VIEW` 等使用 `UnresolvedView` 的命令在扩展分析阶段就被解析为 `ResolvedV2View`，避免 Spark 原生 Analyzer 接手时报错。注释也明确说明"必须在 ResolveViews 之前完成，否则 Analyzer 会因不支持 V2 视图而报错"。同时把 `isViewCatalog` 的两处调用替换为 `ViewUtil.isViewCatalog`，删除了本地的 `isViewCatalog` 私有方法。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ViewUtil.scala

**修改目的**：新建公共工具对象，集中视图相关的 catalog 能力判断与视图加载逻辑。

**工作逻辑**：新文件定义了 `object ViewUtil`，包含三个成员：
- `loadView(catalog, ident): Option[View]`：若 catalog 是 `ViewCatalog` 则尝试加载视图，捕获 `NoSuchViewException` 返回 `None`；否则返回 `None`。
- `isViewCatalog(catalog): Boolean`：判断 catalog 是否为 `ViewCatalog` 实例。
- 隐式类 `IcebergViewHelper`：提供 `asViewCatalog` 方法，把 `CatalogPlugin` 转为 `ViewCatalog`，非 ViewCatalog 时抛 `missingCatalogAbilityError`。

这三个能力原先散落在 `ResolveViews` 中，抽取后可被 `ResolveViews`、`RewriteViewCommands` 以及后续其他规则统一复用。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/AlterV2ViewSetPropertiesExec.scala

**修改目的**：新建物理执行算子，用于执行 `ALTER VIEW ... SET TBLPROPERTIES`。

**工作逻辑**：定义 `case class AlterV2ViewSetPropertiesExec(catalog: ViewCatalog, ident: Identifier, properties: Map[String, String])`，继承 `LeafV2CommandExec`。`output` 为空（DDL 无返回结果）。`run()` 方法将 `properties` 映射为一组 `ViewChange.setProperty(property, value)`，调用 `catalog.alterView(ident, changes: _*)` 提交变更，返回 `Nil`。`simpleString` 用于 explain 输出。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/AlterV2ViewUnsetPropertiesExec.scala

**修改目的**：新建物理执行算子，用于执行 `ALTER VIEW ... UNSET TBLPROPERTIES`，并在非 `IF EXISTS` 模式下校验待删除属性确实存在。

**工作逻辑**：定义 `case class AlterV2ViewUnsetPropertiesExec(catalog, ident, propertyKeys: Seq[String], ifExists: Boolean)`。`run()` 中先判断 `ifExists`，若为 false 则用 `catalog.loadView(ident).properties.containsKey` 过滤出实际不存在的属性，对每个不存在的属性抛 `AnalysisException("Cannot remove property that is not set: '$property'")`；随后将所有 `propertyKeys` 映射为 `ViewChange.removeProperty`，调用 `catalog.alterView` 提交。这保证 `UNSET` 不带 `IF EXISTS` 时与表属性 unset 的语义一致：删不存在的属性要报错而非静默忽略。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala

**修改目的**：在 Spark 执行策略中把 `SetViewProperties`、`UnsetViewProperties` 两个逻辑计划映射到新建的物理算子。

**工作逻辑**：新增两个 case 分支：
```scala
case SetViewProperties(ResolvedV2View(catalog, ident), properties) =>
  AlterV2ViewSetPropertiesExec(catalog, ident, properties) :: Nil
case UnsetViewProperties(ResolvedV2View(catalog, ident), propertyKeys, ifExists) =>
  AlterV2ViewUnsetPropertiesExec(catalog, ident, propertyKeys, ifExists) :: Nil
```
二者均要求目标已解析为 `ResolvedV2View`（即 Iceberg 视图），从而把 Spark 内置的视图属性命令路由到 Iceberg 自己的执行算子。同时新增了对应的 import。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为新增的 SET/UNSET 视图属性能力添加测试，并修正一处既有断言以适应属性名规范化。

**工作逻辑**：改动分两部分：
1. 把既有 `createOrReplaceView` 相关测试中对 `queryColumnNames` 的断言改为 `spark.query-column-names`，与新命名一致。
2. 新增 5 个测试方法：
   - `alterViewSetProperties`：验证 SET 普通属性（含 comment）能写入并覆盖。
   - `alterViewSetReservedProperties`：验证对 `provider`、`location`（AnalysisException，由 Spark 侧拦截）、`format-version`、`spark.query-column-names`（UnsupportedOperationException，由 Iceberg 侧拦截）的 SET 操作均被拒绝。
   - `alterViewUnsetProperties`：验证 UNSET 普通属性生效且不影响其他属性。
   - `alterViewUnsetUnknownProperty`：验证 UNSET 不存在属性时抛 AnalysisException，加 `IF EXISTS` 时静默通过。
   - `alterViewUnsetReservedProperties`：验证对保留属性（`provider`/`location`/`format-version`/`spark.query-column-names`）的 UNSET 操作被拒绝，并区分 `spark.query-column-names` 在普通 UNSET 与 `IF EXISTS` 下的不同异常路径。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java

**修改目的**：真正实现 `alterView`，把 `ViewChange` 转化为 Iceberg 的 `UpdateViewProperties` 事务并提交，同时加入保留属性校验；并把创建视图时写入的内部属性名改为 `spark.query-column-names`。

**工作逻辑**：原 `alterView` 直接抛 `UnsupportedOperationException`。新实现中：若 `asViewCatalog` 非空，先 `loadView` 取得 Iceberg 视图，开启 `view.updateProperties()` 事务；遍历 `ViewChange`，对 `SetProperty` 调 `verifyNonReservedPropertyIsSet` 后 `set`，对 `RemoveProperty` 调 `verifyNonReservedPropertyIsUnset` 后 `remove`；最后 `commit()` 并返回 `SparkView`。捕获 `org.apache.iceberg.exceptions.NoSuchViewException` 转为 Spark 的 `NoSuchViewException`。

新增的三个私有静态方法 `verifyNonReservedProperty`、`verifyNonReservedPropertyIsUnset`、`verifyNonReservedPropertyIsSet` 通过 `SparkView.RESERVED_PROPERTIES` 集合判断属性是否保留，是则抛 `UnsupportedOperationException`。同时 `createView` 路径中写入的属性键由 `queryColumnNames` 改为 `spark.query-column-names`，并新增了对 `UpdateViewProperties` 的 import。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkView.java

**修改目的**：把内部属性名 `queryColumnNames` 改为 `spark.query-column-names`，并将 `RESERVED_PROPERTIES` 集合从 private 改为 public 以便 `SparkCatalog` 复用。

**工作逻辑**：常量 `QUERY_COLUMN_NAMES` 值改为 `"spark.query-column-names"`；`RESERVED_PROPERTIES` 由 `private static final` 改为 `public static final`，集合内容不变（`provider`、`location`、`FORMAT_VERSION`、`QUERY_COLUMN_NAMES`），但因为 `QUERY_COLUMN_NAMES` 值变了，实际保留属性集合的字符串值也随之改变。这样 `SparkCatalog` 中校验保留属性时可以直接引用 `SparkView.RESERVED_PROPERTIES`，避免重复定义。

## 小结

本提交一次性完成了三件事：补齐 `ALTER VIEW SET/UNSET TBLPROPERTIES` 的端到端能力（分析规则 → 执行策略 → 物理算子 → catalog 实现），规范内部属性命名为 `spark.query-column-names` 并纳入保留属性保护，以及把视图相关分析工具抽取为 `ViewUtil` 公共对象。其设计模式与 Iceberg 对表属性的实现保持一致（同样的 `ViewChange` → `Update*Properties` 事务、同样的保留属性校验、同样的 `LeafV2CommandExec` 算子风格），体现了视图与表在 catalog API 层面的对称性。重构 `ResolvedView` 提取器并新增 `UnresolvedView` 解析分支是支撑后续 `CREATE OR REPLACE VIEW`、`ALTER VIEW AS` 等命令的基础设施，为下一个提交（0439）抛出 `ALTER VIEW AS` 异常做好了准备。
