# 提交 0417：Spark 3.4: Support creating views via SQL (#9580)

## 提交信息

- **序号**：0417
- **哈希**：10ee51630ff4925b655c0379f7929faea08c7329
- **短哈希**：10ee51630
- **日期**：2024-01-29 20:39:20 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Support creating views via SQL (#9580)
- **PR/Issue**：#9580

## 总体目的

本提交为 Iceberg 的 Spark 3.4 集成模块新增了通过 SQL `CREATE VIEW` 语句创建 Iceberg 视图的能力。在此提交之前，Iceberg 的 Spark 3.4 扩展已经支持 `DROP VIEW` 等视图相关操作（参见 `RewriteViewCommands` 中已有的 `DropView` → `DropIcebergView` 重写），但 `CREATE VIEW` 路径尚未打通——`SparkCatalog.createView` 直接抛出 `UnsupportedOperationException`，`SparkView.queryColumnNames()` 也只是返回空数组。这意味着用户无法通过标准 SQL 语法在 Iceberg catalog 中创建持久化视图（persistent view），只能依赖 Iceberg 的 Java/Scala API 或 procedure。

背景上，Iceberg 视图（View）是 Iceberg 1.x 引入的特性，允许将 SQL 查询作为命名对象存储在 catalog 中，与表（Table）并列管理。Spark 3.4 的 DataSource V2 catalog API 提供了 `ViewCatalog.createView` 接口，Iceberg 的 `SparkCatalog` 实现了 `ViewCatalog`，但缺少从 Spark SQL `CREATE VIEW` 语句到 Iceberg View 创建的完整编译链路。本提交补齐了这条链路：从 SQL 解析、分析（analysis）、重写（rewrite）到物理执行（execution）的全套规则与节点，并在 `SparkCatalog` 中真正落地 `createView` 的实现。

上下游影响：上游影响所有通过 Spark SQL 在 Iceberg catalog 中创建视图的用户，使他们无需切换到编程式 API；下游影响视图的存储、属性（如 `queryColumnNames`）、列别名与注释、临时视图引用校验等行为。这是 Iceberg 视图功能走向完整可用的重要一步。

## 如何达成设计目的

实现遵循 Spark Catalyst 的标准扩展模式：注入自定义的分析规则（resolution rule）、检查规则（check rule）、重写规则（rewrite rule）和执行策略（strategy），将 Spark 原生的 `CreateView` 逻辑计划逐步转化为 Iceberg 专有的 `CreateIcebergView` 逻辑计划，最终通过 `CreateV2ViewExec` 物理节点调用 `ViewCatalog.createView` 完成实际创建。核心设计思路是：

1. 在 `RewriteViewCommands` 中拦截 Spark 的 `CreateView`，做 CTE 替换与临时视图引用校验，产出新的逻辑节点 `CreateIcebergView`；
2. 在 `ResolveViews` 中对 `CreateIcebergView` 做列别名/注释投影，并记录原始查询列名 `queryColumnNames`，标记 `rewritten=true` 防止重复处理；
3. 在 `CheckViews` 中做最终校验（列数匹配、列名去重）；
4. 在 `ExtendedDataSourceV2Strategy` 中将 `CreateIcebergView` 转换为 `CreateV2ViewExec` 物理节点；
5. `CreateV2ViewExec.run()` 调用 `ViewCatalog.createView`，处理 `CREATE OR REPLACE`（先 drop 再 create）与 `IF NOT EXISTS`（捕获 `ViewAlreadyExistsException`）两种语义；
6. `SparkCatalog.createView` 真正实现视图创建，将 schema、SQL 文本、列别名/注释、属性（含 `queryColumnNames`）通过 `asViewCatalog.buildView(...).create()` 落地到 Iceberg View；
7. `SparkView.queryColumnNames()` 从 View 属性中读取并解析 `queryColumnNames`，使 Spark 能正确返回视图的查询列名。

## 修改详情

### spark/v3.4/spark-extensions/src/main/scala/org/apache/iceberg/spark/extensions/IcebergSparkSessionExtensions.scala

**修改目的**：将新的 `CheckViews` 检查规则注入 Spark Session 扩展。

**工作逻辑**：`IcebergSparkSessionExtensions` 是 Iceberg 向 Spark 注册自定义 Catalyst 规则的入口。本提交新增 `import ...CheckViews` 并调用 `extensions.injectCheckRule { _ => CheckViews }`，将 `CheckViews` 作为检查规则注入。检查规则在分析阶段后期执行，用于对已解析的计划做最终合法性校验（此处为视图列数与列名重复检查）。注入位置位于其他 check 规则（`MergeIntoIcebergTableResolutionCheck`、`AlignedRowLevelIcebergCommandCheck`）之前。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala（新文件）

**修改目的**：在分析阶段对 `CreateIcebergView` 计划做最终校验，确保视图列别名与查询输出列数匹配且查询列名无重复。

**工作逻辑**：`CheckViews` 是一个 `LogicalPlan => Unit` 的检查规则。它遍历计划，匹配 `CreateIcebergView(ResolvedIdentifier(_: ViewCatalog, ident), _, query, columnAliases, _, _, _, _, _, _, _)` 模式（即已解析到具体 ViewCatalog 的创建视图命令），执行两项校验：
- `verifyColumnCount`：若指定了列别名（`columnAliases.nonEmpty`），检查别名数量与查询输出列数是否一致。别名多于查询列时抛出 "not enough data columns"，少于时抛出 "too many data columns"，错误信息中列出视图列名与数据列名便于定位。
- `SchemaUtils.checkColumnNameDuplication`：检查查询本身的字段名是否有重复（使用 `SQLConf.get.resolver` 作为名称比较器）。

这两项校验保证视图定义的列结构合法，避免后续读取时出现歧义。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala

**修改目的**：扩展 `ResolveViews` 规则，对 `CreateIcebergView` 做列别名/注释投影并记录查询列名，同时将隐式类 `ViewHelper` 重命名为 `IcebergViewHelper` 以避免命名冲突。

**工作逻辑**：新增一个匹配分支处理 `CreateIcebergView`：当查询已解析（`query.resolved`）且尚未被重写（`!c.rewritten`）时，调用 `aliasColumns(query, columnAliases, columnComments)` 对查询输出做投影，把原始列重命名为用户指定的别名并附加注释（通过 `MetadataBuilder` 构建 `comment` 元数据），然后返回 `c.copy(query = aliased, queryColumnNames = query.schema.fieldNames, rewritten = true)`，记录原始查询列名并标记已重写防止重复处理。

`aliasColumns` 方法的逻辑：若未指定别名或别名数与输出列数不符则原样返回计划；否则构建 `Project` 节点，对每个输出属性用 `Alias(attr, aliasName)` 包装，若有注释则通过 `explicitMetadata = Some(meta)` 附带 `comment` 元数据。

此外，将 `implicit class ViewHelper` 重命名为 `IcebergViewHelper`，这是一个规避与 Spark 内部同名类潜在冲突的防御性改动。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala

**修改目的**：扩展 `RewriteViewCommands` 规则，将 Spark 原生的 `CreateView` 重写为 Iceberg 的 `CreateIcebergView`，并校验永久视图不引用临时对象。

**工作逻辑**：新增匹配分支处理 `CreateView(ResolvedView(resolved), userSpecifiedColumns, comment, properties, Some(queryText), query, allowExisting, replace)`：
1. 对查询应用 `CTESubstitution`（处理 CTE 内联替换）；
2. 调用 `verifyTemporaryObjectsDontExist(resolved.identifier, q)` 校验永久视图不引用临时视图/全局临时视图；
3. 构造 `CreateIcebergView`，将 `userSpecifiedColumns`（`Seq[(String, Option[String])]`）拆分为 `columnAliases`（列别名）和 `columnComments`（列注释，转为 `Option[String]`），并传入 `queryText`、`query`、`comment`、`properties`、`allowExisting`、`replace`。

新增私有方法 `verifyTemporaryObjectsDontExist`：调用 `collectTemporaryViews(child)` 收集计划中所有引用的临时视图标识，若非空则抛出 `AnalysisException`，提示永久视图不能引用临时对象，需将临时对象转为永久或将永久对象转为临时。错误信息格式化时使用 `CatalogV2Implicits._` 的 `quoted` 方法美化标识。

新增私有方法 `collectTemporaryViews`：递归遍历计划，匹配 `UnresolvedRelation`（若 `isTempView` 为真则收集）、`View`（若 `isTempView` 为真则收集其标识）、以及 `SubqueryExpression`（递归处理子查询计划），最终 `distinct` 去重返回。这使得子查询中引用临时视图也能被检测到。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/views/CreateIcebergView.scala（新文件）

**修改目的**：定义 Iceberg 专有的"创建视图"逻辑计划节点，作为重写规则与执行策略之间的中间表示。

**工作逻辑**：`CreateIcebergView` 是一个 `case class`，继承 `BinaryCommand`（双子节点命令）。字段包括：`child`（目标 `ResolvedView`/标识）、`queryText`（原始 SQL 文本）、`query`（解析后的查询计划）、`columnAliases`（列别名）、`columnComments`（列注释）、`queryColumnNames`（原始查询列名，默认空）、`comment`（视图注释）、`properties`（属性）、`allowExisting`、`replace`、`rewritten`（默认 false，标记是否已被 `ResolveViews` 处理）。通过 `left`/`right` 分别返回 `child` 与 `query`，并实现 `withNewChildrenInternal` 支持计划树复制。`rewritten` 标志是关键设计，防止 `ResolveViews` 对同一节点重复做列别名投影。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/CreateV2ViewExec.scala（新文件）

**修改目的**：定义"创建视图"的物理执行节点，调用 `ViewCatalog.createView` 完成实际视图创建。

**工作逻辑**：`CreateV2ViewExec` 继承 `LeafV2CommandExec`（叶子 V2 命令执行节点），`output` 为空（创建视图不返回结果集）。`run()` 方法逻辑：
1. 获取当前 catalog 名称与当前 namespace（用于视图的默认 catalog/namespace 上下文）；
2. 构造 `engineVersion = "Spark " + SPARK_VERSION`；
3. 合并属性：将 `comment` 映射为 `ViewCatalog.PROP_COMMENT`，并加入 `PROP_CREATE_ENGINE_VERSION` 与 `PROP_ENGINE_VERSION`；
4. 若 `replace` 为真（`CREATE OR REPLACE VIEW`）：先检查视图是否存在，存在则 `catalog.dropView(ident)`，然后调用 `catalog.createView(...)`。注释中标注 FIXME：`replaceView` API 在 Spark 3.5 不存在，故用 drop+create 模拟；
5. 否则（`CREATE VIEW [IF NOT EXISTS]`）：`try` 调用 `createView`，若抛出 `ViewAlreadyExistsException` 且 `allowExisting` 为真则忽略（实现 `IF NOT EXISTS` 语义）；
6. 返回 `Nil`（无输出行）。

`createView` 调用传入 ident、queryText、currentCatalog、currentNamespace、viewSchema、queryColumnNames、columnAliases、columnComments、properties。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala

**修改目的**：在执行策略中将 `CreateIcebergView` 逻辑计划转换为 `CreateV2ViewExec` 物理计划。

**工作逻辑**：新增 `import ...CreateIcebergView`，并在 `apply` 方法中新增匹配分支 `case CreateIcebergView(ResolvedIdentifier(viewCatalog: ViewCatalog, ident), queryText, query, columnAliases, columnComments, queryColumnNames, comment, properties, allowExisting, replace, _)`，构造 `CreateV2ViewExec` 并传入所有参数（`viewSchema = query.schema`），返回 `CreateV2ViewExec(...) :: Nil`。这与已有的 `DropIcebergView → DropV2ViewExec` 模式一致。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为 `CREATE VIEW` 功能新增覆盖测试，验证正常创建、错误场景、列别名、CTE、临时视图引用校验等。

**工作逻辑**：新增 13 个测试用例：
- `createViewIfNotExists`：验证视图已存在时 `CREATE VIEW` 抛 `AnalysisException`，`CREATE VIEW IF NOT EXISTS` 不抛异常。
- `createViewWithInvalidSQL`：验证非法 SQL 抛 `AnalysisException` 含 "Syntax error"。
- `createViewReferencingTempView`：验证永久视图引用临时视图时抛异常，错误信息含视图名与临时视图名。
- `createViewReferencingGlobalTempView`：验证引用全局临时视图（`global_temp.xxx`）同样被禁止。
- `createViewUsingNonExistingTable`：验证引用不存在的表抛异常。
- `createViewWithMismatchedColumnCounts`：验证列别名数与查询列数不匹配时抛异常，分别覆盖"别名多于查询列"（not enough data columns）与"别名少于查询列"（too many data columns）。
- `createViewWithColumnAliases`：验证带列别名与注释的视图创建，检查 `View` 的 `queryColumnNames` 属性、schema 列名与 doc，并验证查询结果；还测试了别名顺序与查询列顺序不一致时（`(new_data, new_id) AS SELECT data, id`）能正确映射。
- `createViewWithDuplicateColumnNames`：验证列别名重复时抛异常含 "The column `new_id` already exists"。
- `createViewWithDuplicateQueryColumnNames`：验证查询本身列名重复（`SELECT id, id`）且未指定别名时抛异常；指定别名后可创建并查询。
- `createViewWithCTE`：验证带 CTE（`WITH ... AS (...) SELECT ...`）的视图创建与查询结果。
- `createViewWithConflictingNamesForCTEAndTempView`：验证 CTE 与同名临时视图共存时，CTE 优先于临时视图。
- `createViewWithCTEReferencingTempView`：验证 CTE 内引用临时视图时，创建永久视图抛异常（因为永久视图整体不能依赖临时对象）。
- `createViewWithNonExistingQueryColumn`：验证查询中引用不存在的列时抛异常。
- `createViewWithSubqueryExpressionUsingTempView`：验证子查询表达式中引用临时视图时，创建永久视图抛异常。
- `createViewWithSubqueryExpressionUsingGlobalTempView`：验证子查询引用全局临时视图同样被禁止。

这些测试覆盖了正常路径与大量边界场景，充分验证了 `RewriteViewCommands`、`ResolveViews`、`CheckViews`、`CreateV2ViewExec` 的协同行为。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java

**修改目的**：真正实现 `createView` 方法，将其从抛 `UnsupportedOperationException` 改为通过 `asViewCatalog` 创建 Iceberg View。

**工作逻辑**：在 `createView` 方法中，首先检查 `asViewCatalog != null`（即 catalog 支持视图）。若支持：
1. 用 `SparkSchemaUtil.convert(schema)` 将 Spark `StructType` 转为 Iceberg `Schema`；
2. 用 `StringJoiner` 将 `queryColumnNames` 数组拼成逗号分隔字符串；
3. 构建属性 `props`：先用 `Spark3Util.rebuildCreateProperties(properties)` 重建属性，再 `put("queryColumnNames", joiner.toString())` 将查询列名存入属性（这是关键，使 `SparkView.queryColumnNames()` 能读回）；
4. 调用 `asViewCatalog.buildView(buildIdentifier(ident)).withDefaultCatalog(currentCatalog).withDefaultNamespace(Namespace.of(currentNamespace)).withQuery("spark", sql).withSchema(icebergSchema).withLocation(properties.get("location")).withProperties(props).create()` 创建视图；
5. 返回 `new SparkView(catalogName, view)`；
6. 异常处理：`NoSuchNamespaceException` 转为 Spark 的 `NoSuchNamespaceException`，`AlreadyExistsException` 转为 `ViewAlreadyExistsException`。

`queryColumnNames` 存入属性而非 View 的原生字段，是一种轻量级持久化方案，便于跨引擎读取。新增 `import java.util.StringJoiner` 与 `import java.util.Arrays`（间接）。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkView.java

**修改目的**：实现 `queryColumnNames()` 方法，使其从 View 属性中读取并解析查询列名，而非返回空数组。

**工作逻辑**：新增常量 `QUERY_COLUMN_NAMES = "queryColumnNames"`，并将其加入 `RESERVED_PROPERTIES` 集合（与 `provider`、`location`、`FORMAT_VERSION` 并列），表示该属性由 Iceberg 内部管理，不对外暴露为普通属性。`queryColumnNames()` 方法改为：检查 `icebergView.properties()` 是否含 `QUERY_COLUMN_NAMES` 键，若有则按逗号 `split` 返回数组，否则返回空数组。这与 `SparkCatalog.createView` 中写入 `queryColumnNames` 属性的逻辑对应，形成读写闭环。

## 小结

这是本批 5 个提交中规模最大、设计最复杂的一个，共修改 10 个文件、新增 600 行。它完整实现了 Spark 3.4 下通过 SQL `CREATE VIEW` 创建 Iceberg 视图的功能，覆盖了从 SQL 解析、Catalyst 分析（resolve/check）、重写（rewrite）、到物理执行（strategy/exec）的全链路，并在 catalog 层落地视图创建。设计上严格遵循 Spark Catalyst 的扩展模式（注入 rule + strategy + 自定义 logical/physical 节点），与已有的 `DropIcebergView` 路径保持一致的风格。几个值得注意的设计点：用 `rewritten` 标志防止 `ResolveViews` 重复处理；将 `queryColumnNames` 持久化为 View 属性实现跨引擎共享；用 drop+create 模拟 `CREATE OR REPLACE`（因 Spark 3.5 缺 `replaceView` API）；通过递归收集临时视图引用确保永久视图不依赖临时对象（包括子查询场景）。测试覆盖全面，包含正常路径与大量错误边界场景。这个提交使 Iceberg 的 Spark 3.4 视图功能从"只能删不能建"变为完整的创建-删除闭环，是视图特性走向生产可用的关键里程碑。
