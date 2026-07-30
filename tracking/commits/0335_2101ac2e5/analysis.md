# 提交 0335：Spark 3.4: Add support for reading Iceberg views (#9422)

## 提交信息

- **序号**：0335
- **哈希**：2101ac2e5528049688d4ce3ea2b4db861ea3c78b
- **短哈希**：2101ac2e5
- **日期**：2024-01-05 08:21:31 -0800
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Add support for reading Iceberg views (#9422)
- **PR/Issue**：#9422

## 总体目的

本提交为 Iceberg Spark 3.4 集成模块新增"读取 Iceberg 视图（View）"的能力。Iceberg 在更早的版本中已经在核心 API 层引入了 `View` / `ViewCatalog` / `ViewRepresentation` 等接口（位于 `org.apache.iceberg.view` 包），允许通过 `ViewCatalog.buildView(...).withQuery("spark", sql)...create()` 这样的 API 创建并持久化视图元数据（视图 SQL、schema、默认 catalog/namespace、属性等）。但 Spark 3.4 的 Iceberg 集成此前并不识别这些视图——当用户在 Spark SQL 中执行 `SELECT * FROM <catalog>.<namespace>.<view>` 时，Spark 会把视图名当作普通表名去 `TableCatalog` 加载，结果是 `NoSuchTableException`，无法把视图 SQL 展开为子查询去执行。本提交补齐了这一关键能力，让 Iceberg 视图在 Spark 3.4 中可以被读取（注意：仅读取，写入/修改视图的 SQL 操作如 `CREATE VIEW`、`ALTER VIEW`、`DROP VIEW` 仍抛 `UnsupportedOperationException`，本提交只实现 `loadView` 与读取路径）。

设计上采用"Spark Catalog API + Spark Analyzer 扩展"双层架构：(1) 让 `SparkCatalog` 实现 Spark 的 `org.apache.spark.sql.connector.catalog.ViewCatalog` 接口（至少实现 `loadView`，其他方法抛 `UnsupportedOperationException`），这样 Spark 框架在需要加载视图时可以通过 `ViewCatalog` 协议找到 Iceberg 视图；(2) 新增一条 Spark Analyzer 解析规则 `ResolveViews`，作为 `IcebergSparkSessionExtensions` 注入的 resolution rule，在 Spark 解析阶段拦截 `UnresolvedRelation`，若该 relation 对应的 identifier 在所属 catalog 中是一个已注册的视图，则把视图 SQL 解析为 `LogicalPlan`、按视图 schema 做字段别名投影、用 `SubqueryAlias` 包裹后替换原 `UnresolvedRelation`，让后续 Spark 优化与物理执行直接对视图 SQL 展开后的子查询工作。这一设计的关键点是把"视图展开"放在 Spark Catalyst 的 resolution 阶段，让视图 SQL 经过完整的 Spark 优化器（CBO、谓词下推、列裁剪等），与 Spark 原生视图的处理路径基本对齐，而不是在更底层（如 scan 层）做替换。

附带地，本提交还新增了 `SparkView` 适配类（把 Iceberg `View` 适配为 Spark `View` 接口），新增了 `SPARK_WITH_VIEWS` 测试 catalog 配置（使用 `InMemoryCatalog` 作为底层 Iceberg catalog 实现，因 `InMemoryCatalog` 同时实现 `TableCatalog` 与 `ViewCatalog`，便于测试），并新增 647 行的 `TestViews` 测试套件覆盖各种视图读取场景。

## 如何达成设计目的

实现路径分四层：(1) **`SparkCatalog` 实现 `ViewCatalog`**——在 `initialize` 阶段检测底层 Iceberg catalog 是否为 `ViewCatalog` 实例，若是则保存到 `asViewCatalog` 字段；`loadView(Identifier)` 委托给 `asViewCatalog.loadView(buildIdentifier(ident))` 取 Iceberg `View`，再用 `SparkView` 包装返回；其他视图操作（`listViews`、`createView`、`alterView`、`dropView`、`renameView`）一律抛 `UnsupportedOperationException`，体现"只读"语义。(2) **`SparkView` 适配器**——实现 Spark `View` 接口，把 Iceberg `View` 的 `name()`、`sqlFor("spark").sql()`、`currentVersion().defaultCatalog()`/`defaultNamespace()`、`schema()`、`properties()` 等映射到 Spark 端；`schema()` 用 `SparkSchemaUtil.convert` 懒加载转换；`currentCatalog()` 在视图未显式指定 default catalog 时回退到 catalogName；`properties()` 注入 `provider=iceberg`、`location`、`format-version` 等保留属性；`equals`/`hashCode` 仅基于视图名（注释说明是为了正确触发 Spark 缓存失效）。(3) **`ResolveViews` Analyzer 规则**——作为 `Rule[LogicalPlan]`，匹配两类 `UnresolvedRelation`：若是 v1 session catalog 中的临时视图则原样返回（让 Spark 默认逻辑处理）；否则通过 `CatalogAndIdentifier` 提取 catalog 与 identifier，调用 `loadView` 尝试加载视图，若加载到则调用 `createViewRelation` 把视图 SQL 文本解析为 `LogicalPlan`、对函数与表标识符做"限定"重写（让视图 SQL 中的未限定名按视图所属 catalog/namespace 解析，而不是按当前会话 catalog/namespace 解析——这是视图语义正确性的关键），最后用 `Project` + `Alias` + `UpCast` 按视图 schema 做字段投影，再用 `SubqueryAlias` 包裹。(4) **`IcebergSparkSessionExtensions` 注册规则**——在 `injectResolutionRule` 中注入 `ResolveViews(spark)`，让它与其他 Iceberg 解析规则（`ResolveProcedures`、`ResolveMergeIntoTableReferences` 等）一起在 Spark Analyzer 阶段执行。测试侧用 `SPARK_WITH_VIEWS` 配置（`InMemoryCatalog`）启动 Spark catalog，通过 `ViewCatalog.buildView(...).withQuery("spark", sql).withSchema(schema).create()` 直接用 Iceberg API 创建视图，再用 `sql("SELECT * FROM %s", viewName)` 验证 Spark 能正确读取并展开视图。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/iceberg/spark/extensions/IcebergSparkSessionExtensions.scala`

**修改目的**：在 Iceberg Spark Session 扩展中注册 `ResolveViews` 解析规则。

**工作逻辑**：新增 import `org.apache.spark.sql.catalyst.analysis.ResolveViews`。在 `apply` 方法的 analyzer extensions 段落中，紧随 `ResolveProcedures` 之后新增 `extensions.injectResolutionRule { spark => ResolveViews(spark) }`。这让 `ResolveViews` 在 Spark Analyzer 的 resolution 阶段被调用，拦截 `UnresolvedRelation` 并尝试解析为视图。注意 `injectResolutionRule` 注入的规则在 Spark 内置 resolution 规则之后执行，确保 `UnresolvedRelation` 已带完整 multipart identifier。

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala`（新文件，146 行）

**修改目的**：实现视图解析规则，把对视图名的引用展开为视图 SQL 对应的 `LogicalPlan` 子查询。这是本提交的核心逻辑文件。

**工作逻辑**：`case class ResolveViews(spark: SparkSession) extends Rule[LogicalPlan] with LookupCatalog`，通过 `LookupCatalog` mixin 获得 `CatalogAndIdentifier` 提取能力。核心 `apply` 方法定义两条匹配规则：

1. `case u@UnresolvedRelation(nameParts, _, _) if catalogManager.v1SessionCatalog.isTempView(nameParts) => u`——若 identifier 是 v1 session catalog 中的临时视图，原样返回让 Spark 默认逻辑处理（避免与 Spark 内置 temp view 解析冲突）。
2. `case u@UnresolvedRelation(parts@CatalogAndIdentifier(catalog, ident), _, _) => loadView(catalog, ident).map(createViewRelation(parts, _)).getOrElse(u)`——用 `CatalogAndIdentifier` 提取 catalog 与 identifier，尝试 `loadView`；若加载到视图则调用 `createViewRelation` 构建子查询 `LogicalPlan` 替换原 `UnresolvedRelation`，否则原样返回。

`loadView(catalog, ident)` 检查 catalog 是否为 `ViewCatalog` 实例，若是则调用 `viewCatalog.loadView(ident)` 并用 `Option(...)` 包裹返回；捕获 `NoSuchViewException` 返回 `None`；非 `ViewCatalog` 直接返回 `None`。

`createViewRelation(nameParts, view)` 是视图展开的核心：(1) 调用 `parseViewText(nameParts.quoted, view.query)` 把视图 SQL 文本通过 `spark.sessionState.sqlParser.parseQuery` 解析为 `LogicalPlan`，解析失败抛 `QueryCompilationErrors.invalidViewText`；(2) 计算 `viewCatalogAndNamespace = view.currentCatalog +: view.currentNamespace.toSeq`，作为视图内部标识符的限定前缀；(3) 调用 `rewriteIdentifiers(parsed, viewCatalogAndNamespace)` 对解析后的 plan 做 CTE 替换、函数与表标识符的限定重写；(4) 按 `view.schema.fields` 顺序，对每个字段用 `Alias(UpCast(GetColumnByOrdinal(pos, expected.dataType), expected.dataType), expected.name)(explicitMetadata = Some(expected.metadata))` 构建别名投影——`GetColumnByOrdinal` 按位置取字段（而非按名，注释说明这比 Spark 默认 SessionCatalog.fromCatalogTable 更严格，不允许按字段名解析），`UpCast` 做类型强制转换，`explicitMetadata` 保留字段元数据（如注释）；(5) 用 `SubqueryAlias(nameParts, Project(aliases, rewritten))` 包裹，给子查询命名以保持 plan 可读性与命名空间隔离。

`rewriteIdentifiers(plan, catalogAndNamespace)` 分两步：(1) `qualifyFunctionIdentifiers(CTESubstitution.apply(plan), catalogAndNamespace)`——先做 CTE 替换（让 WITH 子句中的 CTE 名按视图作用域解析），再对 `UnresolvedFunction` 做限定重写：单段名（如 `iceberg_version`）若非内置函数则加上 catalog+namespace 前缀；多段名（如 `system.bucket`）若首段不是已注册 catalog 则在头部插入视图的当前 catalog；(2) `qualifyTableIdentifiers(...)`——对 `UnresolvedRelation` 做同样的限定重写：单段表名加完整 catalog+namespace 前缀；多段名若首段非 catalog 则在头部插入当前 catalog。这套限定逻辑确保视图 SQL 中的未限定名按"视图定义时的 catalog/namespace"解析，而非"视图被查询时的会话 catalog/namespace"——这是视图可移植性的核心语义保证（不同会话 USE 不同 catalog 时，同一视图的查询结果保持一致）。

`isCatalog(name)` 调用 `catalogManager.isCatalogRegistered` 判断字符串是否为已注册 catalog 名；`isBuiltinFunction(name)` 调用 `v1SessionCatalog.isBuiltinFunction(FunctionIdentifier(name))` 判断是否为 Spark 内置函数（内置函数不加前缀，让 Spark 直接解析）。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`（新文件，647 行）

**修改目的**：覆盖 Iceberg 视图在 Spark 中的读取场景，验证 `ResolveViews` 规则、`SparkCatalog.loadView`、`SparkView` 适配器以及函数/表标识符限定重写的正确性。

**工作逻辑**：测试类 `TestViews extends SparkExtensionsTestBase`，用 `@Parameterized.Parameters` 提供 `SPARK_WITH_VIEWS` catalog 配置（单组参数）。`@Before before()` 设置 `spark.sql.defaultCatalog`、USE catalog、CREATE NAMESPACE、CREATE TABLE。`@After removeTable()` 清理表。共 17 个测试方法，覆盖以下场景：

- **基础读取**：`readFromView`（spark 方言视图，含 trino 方言的干扰项验证只加载 spark 方言）、`readFromTrinoView`（仅定义 trino 方言时 Spark 回退到 trino 方言）、`readFromMultipleViews`（多视图独立查询）。
- **错误处理**：`readFromViewUsingNonExistingTable`（视图 SQL 引用不存在的表 → `AnalysisException` "table or view cannot be found"）、`readFromViewUsingNonExistingTableColumn`（视图 SQL 引用不存在列 → `AnalysisException` "A column or function parameter with name `non_existing` cannot be resolved"）、`readFromViewUsingInvalidSQL`（视图 SQL 文本本身非法 → `AnalysisException` "Invalid view text"）、`readFromViewWithStaleSchema`（视图依赖的列被 ALTER TABLE DROP COLUMN 后再读 → `AnalysisException`）。
- **与 Spark temp view 交互**：`readFromViewHiddenByTempView`（同名 temp view 优先级高于 catalog view，验证 `isTempView` 短路逻辑）、`readFromViewWithGlobalTempView`（GLOBAL TEMP VIEW 在 `global_temp` namespace 下访问，catalog view 在原 namespace 下访问，互不冲突）。
- **视图引用视图**：`readFromViewReferencingAnotherView`（视图 B 的 SQL 引用视图 A，验证 `ResolveViews` 递归解析）、`readFromViewReferencingTempView`（视图 SQL 引用 temp view，验证 temp view 在视图上下文中无法解析——`AnalysisException` "cannot be found"，因 temp view 不在 catalog 作用域）、`readFromViewReferencingAnotherViewHiddenByTempView`（视图 B 引用视图 A 时，同名 temp view 不会影响 B 内对 A 的解析——A 通过 B 的视图 catalog/namespace 限定解析，绕过 temp view）、`readFromViewReferencingGlobalTempView`（视图 SQL 引用 GLOBAL TEMP VIEW，验证 `global_temp.xxx` 在视图上下文中也无法解析）。
- **CTE 支持**：`readFromViewWithCTE`（视图 SQL 含 `WITH max_by_data AS (...)`，验证 `CTESubstitution` 在视图内正确展开）。
- **函数标识符限定**：`rewriteFunctionIdentifier`（视图 SQL `SELECT iceberg_version()`，视图默认 namespace 为 `system`，验证未限定函数被重写为 `catalog.system.iceberg_version` 解析成功；直接 `sql(sql)` 不通过视图则解析失败）、`builtinFunctionIdentifierNotRewritten`（视图 SQL `SELECT trim('  abc   ')`，验证内置函数 `trim` 不被重写、直接被 Spark 解析）、`rewriteFunctionIdentifierWithNamespace`（视图 SQL `SELECT system.bucket(100, 'a')`，验证多段函数名首段 `system` 非 catalog 时被插入当前 catalog 前缀变为 `catalog.system.bucket`，在 spark_catalog 下直接执行会失败但在视图中能解析）、`fullFunctionIdentifier`（视图 SQL `SELECT catalog.system.bucket(100, 'a')`，完整三段名直接解析）、`fullFunctionIdentifierNotRewrittenLoadFailure`（视图 SQL 用 `spark_catalog.system.bucket`，验证完整名不被重写但因 spark_catalog 下无 system 命名空间的 bucket 函数而失败）。

测试使用 `viewCatalog()` 辅助方法通过 `Spark3Util.loadIcebergCatalog(spark, catalogName)` 获取 `ViewCatalog`，用 `viewCatalog.buildView(TableIdentifier.of(NAMESPACE, viewName)).withQuery("spark", sql).withDefaultNamespace(NAMESPACE).withDefaultCatalog(catalogName).withSchema(schema(sql)).create()` 创建视图。`schema(sql)` 辅助方法通过 `spark.sql(sql).schema()` 推导 schema。`insertRows(n)` 用 `SimpleRecord` 写入 n 行测试数据。断言用 AssertJ 静态导入 `assertThat(sql(...)).hasSize(n).containsExactlyInAnyOrderElementsOf(expected)` 与 `assertThatThrownBy(() -> sql(...)).isInstanceOf(AnalysisException.class).hasMessageContaining(...)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：让 `SparkCatalog` 实现 Spark 的 `ViewCatalog` 接口，提供 `loadView` 能力，其他视图写入操作抛 `UnsupportedOperationException`。

**工作逻辑**：
- 类签名变更：`public class SparkCatalog extends BaseCatalog` → `public class SparkCatalog extends BaseCatalog implements org.apache.spark.sql.connector.catalog.ViewCatalog`，让 Spark 框架在 `ViewCatalog` 协议下能识别该 catalog。
- 新增字段 `private ViewCatalog asViewCatalog = null;`，缓存底层 Iceberg catalog 的 `ViewCatalog` 视图（若底层 catalog 不支持视图则为 null）。
- 新增 import：`org.apache.iceberg.catalog.ViewCatalog`、`org.apache.iceberg.spark.source.SparkView`、`org.apache.spark.sql.catalyst.analysis.NoSuchViewException`/`ViewAlreadyExistsException`、`org.apache.spark.sql.connector.catalog.View`/`ViewChange`。
- `initialize` 方法中在 catalog 实例化后新增 `if (catalog instanceof ViewCatalog) { this.asViewCatalog = (ViewCatalog) catalog; }`——把底层 catalog 是否支持视图的能力探测出来缓存到字段，供 `loadView` 使用。
- 新增 `loadView(Identifier ident)`：若 `asViewCatalog != null`，调用 `asViewCatalog.loadView(buildIdentifier(ident))` 取 Iceberg `View`，用 `new SparkView(catalogName, view)` 包装返回；捕获 Iceberg 自有的 `org.apache.iceberg.exceptions.NoSuchViewException` 转换为 Spark 的 `NoSuchViewException(ident)` 重新抛出（保持异常类型与 Spark 框架契约一致）。若 `asViewCatalog == null`（底层 catalog 不支持视图）直接抛 `NoSuchViewException(ident)`。
- 其他视图操作一律抛 `UnsupportedOperationException` 并附带 catalog 名：`listViews`、`createView`、`alterView`、`dropView`、`renameView`——明确表达"本 catalog 仅支持读视图，不支持创建/修改/删除/重命名视图"的语义。这些方法签名严格匹配 Spark `ViewCatalog` 接口要求（如 `createView` 接受 `Identifier ident, String sql, String currentCatalog, String[] currentNamespace, StructType schema, String[] queryColumnNames, String[] columnAliases, String[] columnComments, Map<String,String> properties` 参数列表）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkView.java`（新文件，148 行）

**修改目的**：实现 Spark `View` 接口的适配器，把 Iceberg `View` 暴露给 Spark 框架。

**工作逻辑**：`public class SparkView implements org.apache.spark.sql.connector.catalog.View`，持有 `View icebergView` 与 `String catalogName` 两个字段。各方法实现：

- `name()`：直接委托 `icebergView.name()`。
- `query()`：调用 `icebergView.sqlFor("spark")` 取 spark 方言的 `SQLViewRepresentation`，`Preconditions.checkState(sqlRepr != null, "Cannot load SQL for view %s", name())` 保证非空，返回 `sqlRepr.sql()`。这里 `sqlFor("spark")` 是 Iceberg `View` 的方法，会按方言优先级返回对应的 SQL 表示（若 spark 方言未定义则可能回退到其他方言，具体由 Iceberg `SQLViewRepresentation` 实现决定）。
- `currentCatalog()`：若 `icebergView.currentVersion().defaultCatalog() != null` 则返回该值，否则回退到 `catalogName`——保证视图在被 Spark 加载时总能拿到一个非空的当前 catalog 名。
- `currentNamespace()`：返回 `icebergView.currentVersion().defaultNamespace().levels()`（`Namespace.levels()` 返回 `String[]`）。
- `schema()`：懒加载 `StructType lazySchema`，首次调用时 `SparkSchemaUtil.convert(icebergView.schema())` 把 Iceberg `Schema` 转 Spark `StructType` 缓存。懒加载避免在构造时立即转换（若 Spark 不需要 schema 信息时不浪费 CPU）。
- `queryColumnNames()`：返回空 `String[0]`——Iceberg 视图不区分 query column names 与 column aliases（不像 Spark 原生视图可能有 SELECT 表达式与别名分离），故空数组。
- `columnAliases()`：返回 `icebergView.schema().columns().stream().map(Types.NestedField::name).toArray(String[]::new)`，即 schema 中所有字段名作为别名。
- `columnComments()`：返回 `icebergView.schema().columns().stream().map(Types.NestedField::doc).toArray(String[]::new)`，字段注释（可能含 null）。
- `properties()`：用 `ImmutableMap.Builder` 构建，先注入三个保留属性 `provider=iceberg`、`location=<view location>`、`format-version=<view format version>`（若 `icebergView instanceof BaseView` 则通过 `ViewOperations` 取 `current().formatVersion()`），再追加 Iceberg 视图自身的 properties（过滤掉与保留属性同名的 key，避免重复）。`RESERVED_PROPERTIES = ImmutableSet.of("provider", "location", FORMAT_VERSION)`。
- `equals`/`hashCode`：注释明确"only use name in order to correctly invalidate Spark cache"——仅按 `icebergView.name()` 比较。这是因为 Spark 缓存以 `View` 实例为 key，若两个 `SparkView` 实例指向同一 Iceberg 视图但 `equals` 返回 false，会导致缓存无法失效、读到旧 schema；仅按 name 比较确保同一视图名始终 equals，缓存键稳定。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java`

**修改目的**：新增 `SPARK_WITH_VIEWS` 测试 catalog 配置，使用 `InMemoryCatalog` 作为底层 Iceberg catalog 实现，让测试能同时拥有 `TableCatalog` 与 `ViewCatalog` 能力。

**工作逻辑**：在 `SparkCatalogConfig` 枚举中新增 `SPARK_WITH_VIEWS("spark_with_views", SparkCatalog.class.getName(), ImmutableMap.of(CatalogProperties.CATALOG_IMPL, InMemoryCatalog.class.getName(), "default-namespace", "default", "cache-enabled", "false"))`。关键配置：(1) `CatalogProperties.CATALOG_IMPL = InMemoryCatalog.class.getName()`——通过 `catalog-impl` 指定底层 catalog 实现类为 `InMemoryCatalog`（位于 `org.apache.iceberg.inmemory`），该类同时实现 `TableCatalog` 与 `ViewCatalog`，适合测试；(2) `default-namespace = default`——设置默认 namespace；(3) `cache-enabled = false`——禁用 catalog 缓存避免测试间状态泄露。新增 import `org.apache.iceberg.CatalogProperties` 与 `org.apache.iceberg.inmemory.InMemoryCatalog`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkTestBaseWithCatalog.java`

**修改目的**：兼容 `SPARK_WITH_VIEWS` 配置（无 `type` key）。

**工作逻辑**：原代码 `if (config.get("type").equalsIgnoreCase("hadoop"))` 改为 `if ("hadoop".equalsIgnoreCase(config.get("type")))`——把常量放左侧、`config.get("type")` 放右侧。`SPARK_WITH_VIEWS` 配置不含 `type` key，`config.get("type")` 返回 null，原写法会触发 `null.equalsIgnoreCase("hadoop")` 抛 NPE；改写后 `"hadoop".equalsIgnoreCase(null)` 返回 false，安全跳过 warehouse 路径设置分支（`SPARK_WITH_VIEWS` 用 `InMemoryCatalog`，本就不需要 warehouse 路径）。这是一个典型的"常量在左、变量在右"的 NPE 防御写法。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkCatalog.java`

**修改目的**：让测试用的 `TestSparkCatalog` 类型参数同时包含 `ViewCatalog` 边界，让该测试 catalog 在 `SparkSessionCatalog` 桥接下也能暴露 view 能力。

**工作逻辑**：原 `public class TestSparkCatalog<T extends TableCatalog & FunctionCatalog & SupportsNamespaces>` 改为 `public class TestSparkCatalog<T extends TableCatalog & FunctionCatalog & SupportsNamespaces & ViewCatalog>`——类型参数新增 `& ViewCatalog` 交集边界。新增 import `org.apache.spark.sql.connector.catalog.ViewCatalog`。这让 `TestSparkCatalog` 的泛型 T 必须同时实现四个接口，与 Spark 3.4 中完整的多接口 catalog 契约保持一致。

## 小结

本次提交为 Iceberg Spark 3.4 集成模块新增"读取 Iceberg 视图"能力，是 Iceberg 视图生态在 Spark 端落地的关键一步。设计上采用"`SparkCatalog` 实现 Spark `ViewCatalog` 接口 + `ResolveViews` Analyzer 解析规则"双层架构：前者让 Spark 框架能通过 `loadView` 协议找到 Iceberg 视图，后者在 Catalyst resolution 阶段把视图引用展开为视图 SQL 对应的子查询 `LogicalPlan`，让视图 SQL 经过完整的 Spark 优化器（CBO、谓词下推、列裁剪）处理。`ResolveViews` 中的 `rewriteIdentifiers` 逻辑（限定函数与表标识符到视图所属 catalog/namespace）是视图可移植性的核心保证——确保视图在不同会话 catalog 上下文中查询结果一致。新增 `SparkView` 适配器把 Iceberg `View` 映射为 Spark `View` 接口，`equals`/`hashCode` 仅按 name 比较以保证 Spark 缓存正确失效。`SparkCatalog.loadView` 是唯一实现的视图操作，其他写入操作（create/alter/drop/rename/list）一律抛 `UnsupportedOperationException`，体现"只读"语义。`TestViews` 647 行测试覆盖 17 种场景，包括基础读取、错误处理、与 Spark temp view/global temp view 的优先级交互、视图引用视图、CTE、函数标识符限定等复杂场景。本提交让 Iceberg 视图在 Spark 3.4 中具备完整的读取能力，为后续视图写入 SQL 支持奠定基础。
