# 提交 0428：Spark: Add support for describing/showing views (#9513)

## 提交信息

- **序号**：0428
- **哈希**：d5a9d34edc5fd944f5c45b9667ddcd8eab33fe37
- **短哈希**：d5a9d34ed
- **日期**：2024 年 1 月 31 日（Wed Jan 31 10:04:08 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Add support for describing/showing views (#9513)

  This adds support for:
  * `DESCRIBE <viewName>` / `DESCRIBE EXTENDED <viewName>`
  * `SHOW VIEWS` / `SHOW VIEWS LIKE <pattern>`
  * `SHOW TBLPROPERTIES <viewName>`
  * `SHOW CREATE TABLE <viewName>`
- **PR/Issue**：#9513

## 总体目的

这个提交为 Iceberg 的 Spark 3.5 集成补齐了视图（View）的元数据查询能力。Iceberg 此前已经支持创建/删除视图（`CreateIcebergView`、`DropIcebergView`），但在视图的"读"侧元数据操作上存在空白：用户无法对 Iceberg 视图执行 `DESCRIBE`、`SHOW VIEWS`、`SHOW TBLPROPERTIES`、`SHOW CREATE TABLE` 这些 SQL 命令。这些是日常数据探索和运维中高频使用的命令——例如查看视图的列与类型、列出命名空间下的视图、查看视图属性、生成视图的重建 DDL。

设计上，Iceberg 通过扩展 Spark 的 Catalyst 分析与执行层来拦截这些命令：当目标是一个 Iceberg 视图（`ResolvedV2View`）或目标 catalog 是 ViewCatalog 时，把 Spark 原生的逻辑计划改写为 Iceberg 自定义的执行节点（`DescribeV2ViewExec`、`ShowV2ViewsExec`、`ShowV2ViewPropertiesExec`、`ShowCreateV2ViewExec`），由这些节点直接调用 `ViewCatalog` API 读取 Iceberg 视图元数据并格式化输出。这种"在扩展层改写命令"的方式与 Iceberg 现有的表命令扩展模式（如 `ExtendedDataSourceV2Strategy`）保持一致，确保视图与表在用户体验上对齐。

一个关键点是 `SparkCatalog.listViews` 此前直接抛 `UnsupportedOperationException`，这会阻塞 `SHOW VIEWS` 的执行，因此本提交也把它改为真正调用底层 `asViewCatalog.listViews`（或返回空数组），打通了视图列表的底层数据通路。这是整个特性得以工作的前提。

## 如何达成设计目的

整体分为三层：(1) 分析层——在 `RewriteViewCommands` 规则中把 `ShowViews` 改写为 `ShowIcebergViews`，并新增 `ShowIcebergViews` 逻辑计划节点；(2) 执行层——在 `ExtendedDataSourceV2Strategy` 中为 `DescribeRelation`/`ShowTableProperties`/`ShowCreateTable`（当目标是 `ResolvedV2View` 时）和 `ShowIcebergViews` 生成对应的物理执行节点，并新增四个 `*Exec` 类实现具体输出逻辑；(3) catalog 层——修复 `SparkCatalog.listViews` 让其真正委托给 `ViewCatalog`。配套补充了覆盖四种命令的测试用例。

## 修改详情

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala

**修改目的**：在分析阶段拦截 `SHOW VIEWS` 命令并改写为 Iceberg 版本。

**工作逻辑**：新增 `ShowViews`、`ResolvedV2View`、`ShowIcebergViews` 的导入，并在 `apply` 方法的 pattern matching 中新增两个分支：
- `ShowViews(UnresolvedNamespace(Seq()), pattern, output) if isViewCatalog(catalogManager.currentCatalog)`：处理不带命名空间的 `SHOW VIEWS`（使用当前 catalog），改写为 `ShowIcebergViews(ResolvedNamespace(currentCatalog, Seq.empty), pattern, output)`。
- `ShowViews(UnresolvedNamespace(CatalogAndNamespace(catalog, ns)), pattern, output) if isViewCatalog(catalog)`：处理带 catalog/namespace 的 `SHOW VIEWS`，改写为 `ShowIcebergViews(ResolvedNamespace(catalog, ns), pattern, output)`。

两个分支都通过 `isViewCatalog` 守卫确保只在当前/指定 catalog 是 ViewCatalog 时才改写，避免影响非 Iceberg catalog。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/views/ShowIcebergViews.scala

**修改目的**：新增 Iceberg 自定义的 `SHOW VIEWS` 逻辑计划节点。

**工作逻辑**：定义 `ShowIcebergViews` case class，继承 `UnaryCommand`，包含 `namespace: LogicalPlan`、`pattern: Option[String]`、`output: Seq[Attribute]`（默认取 `ShowViews.getOutputAttrs` 以保持与 Spark 原生输出 schema 一致）。`child` 指向 namespace，`withNewChildInternal` 用于树重建时复制节点。作为 Iceberg 内部逻辑节点，它会被 `ExtendedDataSourceV2Strategy` 转换为 `ShowV2ViewsExec`。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/DescribeV2ViewExec.scala

**修改目的**：实现 `DESCRIBE [EXTENDED] <view>` 的物理执行。

**工作逻辑**：新增 `DescribeV2ViewExec`（继承 `V2CommandExec` + `LeafExecNode`），持有 `output`、`view: View`、`isExtended: Boolean`。`run()` 方法：非 extended 模式只返回 `describeSchema`（遍历 `view.schema()` 输出列名/类型/注释三列）；extended 模式在 schema 后追加一个空行再加 `describeExtended`，后者输出"# Detailed View Information"、Comment、View Catalog and Namespace（`view.currentCatalog +: view.currentNamespace`）、View Query Output Columns（`view.queryColumnNames`）、View Properties（去除 `ViewCatalog.RESERVED_PROPERTIES` 后排序并转义拼接）、Created By（`PROP_CREATE_ENGINE_VERSION`）。属性通过 `escapeSingleQuotedString` 转义单引号，确保输出可安全展示。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala

**修改目的**：把视图相关的逻辑计划映射到物理执行节点。

**工作逻辑**：新增 `ResolvedNamespace`、`DescribeRelation`、`ShowCreateTable`、`ShowTableProperties`、`ShowIcebergViews` 的导入，并在 `apply` 方法中新增四个 pattern 分支：
- `DescribeRelation(ResolvedV2View(catalog, ident), _, isExtended, output)` → `DescribeV2ViewExec(output, catalog.loadView(ident), isExtended)`
- `ShowTableProperties(ResolvedV2View(catalog, ident), propertyKey, output)` → `ShowV2ViewPropertiesExec(output, catalog.loadView(ident), propertyKey)`
- `ShowIcebergViews(ResolvedNamespace(catalog: ViewCatalog, namespace), pattern, output)` → `ShowV2ViewsExec(output, catalog, namespace, pattern)`
- `ShowCreateTable(ResolvedV2View(catalog, ident), _, output)` → `ShowCreateV2ViewExec(output, catalog.loadView(ident))`

关键设计：通过 `ResolvedV2View` 守卫让这些命令只在目标是 Iceberg 视图时才走 Iceberg 执行节点，否则回退到 Spark 默认行为。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ShowCreateV2ViewExec.scala

**修改目的**：实现 `SHOW CREATE TABLE <view>` 的物理执行，输出可重建视图的 DDL。

**工作逻辑**：新增 `ShowCreateV2ViewExec`，`run()` 用 `StringBuilder` 拼接 `CREATE VIEW <name>` 后依次调用：`showColumns`（输出列及 COMMENT）、`showComment`（PROP_COMMENT）、`showProperties`（去除保留属性，用 `conf.redactOptions` 做敏感信息脱敏后按 key 排序），最后追加 `AS\n<view.query>\n`。列与属性通过 `concatByMultiLines` 格式化为多行括号形式。脱敏处理（`redactOptions`）是一个安全细节，避免在 DDL 输出中泄露密钥等敏感属性。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ShowV2ViewPropertiesExec.scala

**修改目的**：实现 `SHOW TBLPROPERTIES <view> [(key)]` 的物理执行。

**工作逻辑**：新增 `ShowV2ViewPropertiesExec`，持有 `view`、`propertyKey: Option[String]`。`run()`：若指定了 key，则从 `properties`（去除 `RESERVED_PROPERTIES`）中取值，不存在时返回提示信息 `View <name> does not have property: <key>`；若未指定 key，则遍历所有非保留属性输出键值对。这与 Spark 表的 `SHOW TBLPROPERTIES` 行为对齐。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ShowV2ViewsExec.scala

**修改目的**：实现 `SHOW VIEWS [IN namespace] [LIKE pattern]` 的物理执行。

**工作逻辑**：新增 `ShowV2ViewsExec`，持有 `catalog: ViewCatalog`、`namespace: Seq[String]`、`pattern: Option[String]`。`run()` 用 `ArrayBuffer` 收集结果，分三类处理：
- **GLOBAL TEMP 视图**：若 namespace 头部匹配 `globalTempViewManager.database`，则从 `globalTempViewManager.listViewNames` 取视图名，输出 `(globalTemp, name, isTemporary=true)`。
- **Catalog 视图**：否则调用 `catalog.listViews(namespace: _*)`，对每个视图用 `StringUtils.filterPattern` 按 pattern 过滤，输出 `(namespace.quoted, name, isTemporary=false)`。
- **本地 TEMP 视图**：最后无条件追加 `session.sessionState.catalog.listLocalTempViews`，输出 `(database.quoted, table, isTemporary=true)`。

输出三列（namespace、viewName、isTemporary），与 Spark 原生 `SHOW VIEWS` 的 schema 一致，保证兼容性。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java

**修改目的**：修复 `listViews` 使其真正支持视图列表，而非抛异常。

**工作逻辑**：原实现直接 `throw new UnsupportedOperationException("Listing views is not supported by catalog: " + catalogName)`，会阻断 `SHOW VIEWS`。改为：若 `asViewCatalog` 非空，则调用 `asViewCatalog.listViews(Namespace.of(namespace))` 并把 Iceberg `org.apache.iceberg.catalog.Namespace` 下的标识符映射回 Spark `Identifier`（`Identifier.of(ident.namespace().levels(), ident.name())`）；否则返回空数组 `new Identifier[0]`。这是整个 `SHOW VIEWS` 链路的底层支撑。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为新增的四种命令补充测试覆盖，并修正既有测试使用硬编码 "default" 命名空间的问题。

**工作逻辑**：
- 将两处 `sql("SELECT * FROM %s.%s.%s", catalogName, "default", viewName)` 中的硬编码 `"default"` 改为 `NAMESPACE`，使测试与实际使用的命名空间一致。
- 新增 `describeView`：验证 `DESCRIBE <view>` 输出列名/类型/空注释。
- 新增 `describeExtendedView`：验证 `DESCRIBE EXTENDED <view>` 输出列、空行、Detailed View Information（Comment、View Catalog and Namespace、Query Output Columns、View Properties 等）。
- 新增 `showViewProperties`：验证 `SHOW TBLPROPERTIES <view>` 输出自定义属性。
- 新增 `showViewPropertiesByKey`：验证按 key 查询属性（存在/不存在两种情况，后者校验提示信息）。
- 新增 `showViews`：综合验证 `SHOW VIEWS`、`SHOW VIEWS IN catalog`、`SHOW VIEWS IN catalog.namespace`、`SHOW VIEWS LIKE 'pref*'`、不匹配 pattern、`spark_catalog.default`（含 temp view）、`global_temp`（含 global temp view）等多种形态。
- 新增 `showCreateSimpleView`：验证简单视图的 `SHOW CREATE TABLE` 输出格式（列、TBLPROPERTIES 含 format-version/location/provider、AS 查询）。
- 新增 `showCreateComplexView`：验证带列别名+COMMENT、视图 COMMENT、自定义 TBLPROPERTIES 的复杂视图 DDL 输出，属性按字母序排列。

## 小结

这是一个功能增强提交，为 Iceberg Spark 3.5 视图补齐了 `DESCRIBE`、`SHOW VIEWS`、`SHOW TBLPROPERTIES`、`SHOW CREATE TABLE` 四类元数据查询命令。设计模式遵循 Iceberg 既有的"分析层改写 + 执行层自定义节点 + catalog 底层支撑"三层结构，与表命令扩展保持一致，使视图在用户体验上与表对齐。值得注意的细节包括：属性脱敏（`redactOptions`）、保留属性过滤（`RESERVED_PROPERTIES`）、temp/global temp 视图统一纳入 `SHOW VIEWS` 输出、`SparkCatalog.listViews` 从抛异常改为真正委托。本提交是后续 Spark 3.4 同款功能（提交 0430）的先导版本。
