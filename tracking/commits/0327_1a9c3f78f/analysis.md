# 提交 0327：Spark: Add support for reading Iceberg views (#9340)

## 提交信息

- **序号**：0327 / 4088
- **哈希**：1a9c3f78f5e2d0b831aadd4636eeb70975463e78
- **短哈希**：1a9c3f78f
- **日期**：2024-01-04 08:38:18 -0800
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Add support for reading Iceberg views (#9340)
- **PR/Issue**：#9340

## 总体目的

Iceberg 在更早的版本已经引入了 View 抽象（`org.apache.iceberg.view.View`）和 `ViewCatalog` 接口，允许把 SQL 查询作为视图的元数据存储在 catalog 中，并通过 `sqlFor(sqlDialect)` 为不同引擎（spark、trino、presto 等）提供对应的 SQL 文本。但是 Spark 集成模块此前并不支持读取这些 Iceberg 视图——`SparkCatalog` 没有实现 Spark 的 `ViewCatalog` 接口，因此用户即使通过 Iceberg API 创建了视图，也无法在 Spark SQL 中通过 `SELECT * FROM <view>` 这样的语句去查询它。这造成跨引擎场景下视图能力的不对等：Trino/Spark 之间无法通过 Iceberg 共享视图定义。

本提交的目标正是补齐 Spark 端的"读视图"能力。需要强调的是，本次提交只覆盖读取（read）路径，并不支持在 Spark 中通过 SQL 或 catalog API 创建/修改/删除/重命名视图——`SparkCatalog` 中对应方法（`createView`、`alterView`、`dropView`、`renameView`、`listViews`）都直接抛 `UnsupportedOperationException`。视图仍需通过 Iceberg `ViewCatalog` API（或其它支持写视图的引擎）创建；Spark 这边只负责在解析 `UnresolvedRelation` 时，如果发现引用的是一个 Iceberg 视图，就把视图的 SQL 文本展开成对应的逻辑计划，让查询可以执行。

实现这一功能需要在 Spark Catalyst 层注入一条新的分析规则 `ResolveViews`，让 `SparkCatalog` 实现 Spark 的 `ViewCatalog` SPI，并提供一个 `SparkView` 适配类把 Iceberg `View` 包装成 Spark `View` 接口实例。核心挑战在于视图 SQL 文本中表/函数标识符的限定（qualification）：视图可能在某个 catalog/namespace 下创建，但被另一个会话引用，因此需要在展开视图时把未限定的表名、函数名按视图定义时的默认 catalog/namespace 补齐，使解析结果与视图创建时的语义一致——而不是按当前会话的默认值去解析。

## 如何达成设计目的

整体设计分三层：(1) 在 Spark 扩展入口 `IcebergSparkSessionExtensions` 注入一条新的 analyzer 规则 `ResolveViews`，由它在分析阶段拦截 `UnresolvedRelation`，如果对应 identifier 命中一个 Iceberg 视图，就把视图文本展开为逻辑计划；(2) 让 `SparkCatalog` 实现 Spark 的 `ViewCatalog` 接口，在 `initialize` 时探测底层 Iceberg catalog 是否为 `ViewCatalog`（保留 `asViewCatalog` 引用），`loadView` 时把 Iceberg `View` 用 `SparkView` 适配返回；其余写操作暂不支持；(3) `SparkView` 负责把 Iceberg 视图元数据（schema、SQL 文本、当前 catalog/namespace、属性）适配为 Spark `View` 接口所需的方法。`ResolveViews` 在展开视图时通过 `rewriteIdentifiers` 把视图内未限定的表/函数名按视图自带的 `currentCatalog`/`currentNamespace` 限定，保证跨会话引用视图时解析行为可预期。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/iceberg/spark/extensions/IcebergSparkSessionExtensions.scala`

**修改目的**：把 `ResolveViews` 规则注入 Spark 分析阶段，使 Iceberg 视图能在 SQL 解析后被识别并展开。

**工作逻辑**：新增 `import org.apache.spark.sql.catalyst.analysis.ResolveViews`，并在 `apply` 方法的"analyzer extensions"区块中加一行 `extensions.injectResolutionRule { spark => ResolveViews(spark) }`，与既有的 `ResolveProcedures` 并列。`injectResolutionRule` 注册的规则会在 Spark Catalyst analyzer 解析阶段被执行，从而在 `UnresolvedRelation` 落到下游规则前先把视图替换为对应的子查询计划。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala`（新增）

**修改目的**：实现 Iceberg 视图到 Spark 逻辑计划的展开逻辑——这是整个特性的核心。

**工作逻辑**：
- `case class ResolveViews(spark: SparkSession) extends Rule[LogicalPlan] with LookupCatalog`：定义一条 Catalyst 规则。`LookupCatalog` mixin 提供 `CatalogAndIdentifier` 提取器与 `catalogManager`。
- `apply(plan)` 用 `resolveOperators` 匹配两类 `UnresolvedRelation`：
  1. 命中 v1 session catalog 中的 temp view 时直接返回原节点（不抢 temp view 的解析权）。
  2. 其它情况按 `CatalogAndIdentifier(catalog, ident)` 拆分，调用 `loadView(catalog, ident)` 尝试加载视图；若加载成功则用 `createViewRelation` 展开为子查询计划，否则原样返回（让后续规则当表处理）。
- `loadView`：仅当 catalog 是 `ViewCatalog` 时尝试 `loadView`，捕获 `NoSuchViewException` 转 `None`，其它类型 catalog 返回 `None`。
- `createViewRelation(nameParts, view)`：
  1. 调用 `parseViewText` 用 Spark sqlParser 把 `view.query`（SQL 文本）解析为 `LogicalPlan`。
  2. 用 `rewriteIdentifiers` 把视图内未限定的表/函数名按 `view.currentCatalog +: view.currentNamespace` 补齐。
  3. 用 `view.schema` 字段按位置（`GetColumnByOrdinal(pos, dataType)`）取出字段、做 `UpCast` 后用 `Alias` 重命名并保留原字段 metadata（即列注释）。这种"按位置 + 类型安全 cast + 显式 metadata"的写法比 Spark 内置 `SessionCatalog.fromCatalogTable` 更严格——不允许按字段名解析，只能按位置，从而保证视图列顺序与 schema 一致。
  4. 最终包成 `SubqueryAlias(nameParts, Project(aliases, rewritten))`，给展开后的子查询一个名称，便于后续规则与 explain 输出引用。
- `parseViewText`：在 `CurrentOrigin` 中设置 `objectType = "VIEW"`、`objectName = <view 名>`，再调用 `spark.sessionState.sqlParser.parseQuery(viewText)`，捕获 `ParseException` 转为 `QueryCompilationErrors.invalidViewText`，让报错信息明确指向"视图文本非法"。
- `rewriteIdentifiers`：先 `CTESubstitution` 展开 CTE，再分别调用 `qualifyFunctionIdentifiers` 和 `qualifyTableIdentifiers` 限定函数/表标识符。
  - `qualifyFunctionIdentifiers`：单段名函数若非内置则补默认 catalog/namespace；多段名若首段不是已注册 catalog，则在最前补上 `currentCatalog`。
  - `qualifyTableIdentifiers`：`UnresolvedRelation(Seq(table))` 补完整 `catalog + namespace + table`；多段名首段非 catalog 时在最前补 `currentCatalog`。
- `isCatalog`/`isBuiltinFunction`：分别查询 `catalogManager.isCatalogRegistered` 与 v1 session catalog 的 `isBuiltinFunction`，用于判断是否需要限定。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkView.java`（新增）

**修改目的**：把 Iceberg `View` 适配为 Spark `org.apache.spark.sql.connector.catalog.View` 接口，使 Spark 能拿到视图的 schema、SQL 文本与属性。

**工作逻辑**：
- 字段：`icebergView`（被包装的 Iceberg 视图）、`catalogName`、`lazySchema`（懒加载的 Spark `StructType`）。`RESERVED_PROPERTIES = {"provider", "location", FORMAT_VERSION}` 表示这些属性由本类自己注入，不从 Iceberg 视图属性透传。
- `name()`：透传 `icebergView.name()`。
- `query()`：调用 `icebergView.sqlFor("spark")` 取 Spark 方言的 SQL 文本，校验非空后返回。`sqlFor` 会按引擎优先级回退（如没有 spark 方言则可能回退到 trino 等），这点在测试中也被覆盖。
- `currentCatalog()`：优先用视图版本的 `defaultCatalog`，若为空则回退到构造时传入的 `catalogName`（即 SparkCatalog 名）。
- `currentNamespace()`：透传视图版本的 `defaultNamespace` levels。
- `schema()`：懒加载，用 `SparkSchemaUtil.convert(icebergView.schema())` 把 Iceberg schema 转 Spark StructType。
- `queryColumnNames()`：直接返回空数组（Iceberg 视图暂不维护该信息）。
- `columnAliases()`/`columnComments()`：从 `icebergView.schema().columns()` 取列名/列注释。
- `properties()`：固定写入 `provider=iceberg`、`location=<视图 location>`、`format-version=<ops.current().formatVersion()>`（仅当 icebergView 是 `BaseView` 时才能拿到 ops）；再将 Iceberg 视图自身属性中除 `RESERVED_PROPERTIES` 外的部分透传，避免被用户覆盖关键属性。
- `equals`/`hashCode`：仅按 `icebergView.name()` 比较，注释说明这是为了"正确失效 Spark 缓存"——同名视图即视为同一对象，便于 cache invalidation。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：让 `SparkCatalog` 实现 Spark `ViewCatalog` SPI，提供视图加载入口；其余写操作显式不支持。

**工作逻辑**：
- 类签名改为 `extends BaseCatalog implements org.apache.spark.sql.connector.catalog.ViewCatalog`，并新增 import `ViewCatalog`、`View`、`ViewChange`、`NoSuchViewException`、`ViewAlreadyExistsException`。
- 新增字段 `private ViewCatalog asViewCatalog = null;`。
- `initialize` 中在拿到 `icebergCatalog` 后，新增 `if (catalog instanceof ViewCatalog) { this.asViewCatalog = (ViewCatalog) catalog; }`，把底层 Iceberg catalog 的视图能力探测出来。
- `loadView(Identifier ident)`：若 `asViewCatalog != null`，调用 `asViewCatalog.loadView(buildIdentifier(ident))` 并用 `SparkView` 包装；Iceberg `NoSuchViewException` 转 Spark `NoSuchViewException`。若底层不是 `ViewCatalog`，直接抛 `NoSuchViewException`。
- `listViews`/`createView`/`alterView`/`dropView`/`renameView`：均抛 `UnsupportedOperationException`，并附 catalog 名以提示用户。这意味着只支持读视图，不支持通过 Spark SQL 创建/修改视图。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java`

**修改目的**：新增一个测试用 catalog 配置 `SPARK_WITH_VIEWS`，使视图相关测试能跑在带视图能力的 catalog 上。

**工作逻辑**：在枚举中新增 `SPARK_WITH_VIEWS("spark_with_views", SparkCatalog.class.getName(), ImmutableMap.of(CATALOG_IMPL, InMemoryCatalog.class.getName(), "default-namespace", "default", "cache-enabled", "false"))`。用 `InMemoryCatalog` 作为底层 catalog（它在 Iceberg 中实现了 `ViewCatalog`），这样 `SparkCatalog` 初始化时 `asViewCatalog` 字段才会被赋值，`loadView` 才会工作。`cache-enabled=false` 与其它测试 catalog 保持一致，避免缓存干扰测试断言。同时新增 `CatalogProperties` 和 `InMemoryCatalog` 的 import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkTestBaseWithCatalog.java`

**修改目的**：把 `config.get("type").equalsIgnoreCase("hadoop")` 改成 `"hadoop".equalsIgnoreCase(config.get("type"))`。

**工作逻辑**：当 config 不含 "type" 键时，原写法会先调用 `config.get("type")` 返回 `null`，再调 `null.equalsIgnoreCase(...)` 直接 NPE；新写法把常量放左边、变量放右边，规避 NPE。这是为 `SPARK_WITH_VIEWS` 这种不含 "type" 的配置铺路——否则 `SparkTestBaseWithCatalog` 在初始化时就会 NPE。属于配套修复。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkCatalog.java`

**修改目的**：让测试用的 `TestSparkCatalog` 泛型上界增加 `ViewCatalog`。

**工作逻辑**：原签名 `TestSparkCatalog<T extends TableCatalog & FunctionCatalog & SupportsNamespaces>` 改为 `TestSparkCatalog<T extends TableCatalog & FunctionCatalog & SupportsNamespaces & ViewCatalog>`，因为 `SparkCatalog` 现在实现了 `ViewCatalog`，被 `SparkSessionCatalog` 包装时也需要这个接口在泛型边界上可见。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`（新增）

**修改目的**：为 Iceberg 视图在 Spark 的读取能力编写端到端测试，覆盖正常路径与多种边界情形。

**工作逻辑**：继承 `SparkExtensionsTestBase`，参数化使用 `SparkCatalogConfig.SPARK_WITH_VIEWS`。`@Before` 中切换 catalog、创建 namespace 与基础表 `table (id INT, data STRING)`。每个用例通过 `viewCatalog().buildView(...).withQuery("spark", sql).withDefaultNamespace(...).withDefaultCatalog(...).withSchema(...).create()` 直接走 Iceberg API 创建视图，然后用 `sql("SELECT * FROM <view>")` 验证读取行为。覆盖场景包括：
- `readFromView`：基本读取，且验证只加载 spark 方言 SQL（额外塞了 trino 方言的非法 SQL，确保不被错误回退）。
- `readFromTrinoView`：未定义 spark 方言时回退到 trino 方言 SQL。
- `readFromMultipleViews`：多个视图独立读取。
- `readFromViewUsingNonExistingTable` / `readFromViewUsingNonExistingTableColumn` / `readFromViewUsingInvalidSQL`：视图文本引用了不存在的表/列或非法 SQL 时，应抛 `AnalysisException` 并提示明确信息（其中非法 SQL 走 `parseViewText` 的 `invalidViewText` 路径）。
- `readFromViewWithStaleSchema`：视图定义后表删除了视图依赖的列，读取应失败。
- `readFromViewHiddenByTempView` / `readFromViewWithGlobalTempView`：temp view / global temp view 与同名 Iceberg view 共存时，按 Spark 既有优先级解析（temp view 优先）。
- `readFromViewReferencingAnotherView`：视图引用另一个 Iceberg 视图，验证多视图链式解析。
- `readFromViewReferencingTempView` / `readFromViewReferencingGlobalTempView`：视图文本引用 temp/global temp view 时应失败（因为展开时这些 temp view 在视图命名空间下不可见）。
- `readFromViewReferencingAnotherViewHiddenByTempView`：外层视图引用内层视图名，与同名 temp view 冲突时，应按视图自带的默认 catalog/namespace 解析到内层 Iceberg view，而不是 temp view——这是 `rewriteIdentifiers` 限定标识符的关键验证。
- `readFromViewWithCTE`：视图中含 CTE，验证 `CTESubstitution` 被正确应用。

## 小结

这个提交为 Iceberg 的 Spark 集成补齐了视图读取能力，使通过 Iceberg `ViewCatalog` API 创建的视图能在 Spark SQL 中被 `SELECT` 查询。核心是新增 Catalyst 分析规则 `ResolveViews`（负责把视图文本展开为子查询计划并对标识符做命名空间限定）、新增 `SparkView` 适配类（把 Iceberg 视图适配为 Spark `View` SPI），以及让 `SparkCatalog` 实现 `ViewCatalog` 的读路径。它只支持读、不支持写，但已经能让 Spark 与其它支持 Iceberg 视图的引擎共享视图定义，补齐了跨引擎互操作的关键一环。
