# 提交 0412：Spark: Support creating views via SQL (#9423)

## 提交信息

- **序号**：0412
- **哈希**：3f6e377a1e994714bfc41c75d9aec7bdb85c95a9
- **短哈希**：3f6e377a1
- **日期**：Fri Jan 26 16:44:01 2024 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Support creating views via SQL (#9423)
- **PR/Issue**：#9423

## 总体目的

这个提交为 Iceberg 的 Spark 引擎（v3.5 分支）补齐了通过标准 SQL `CREATE VIEW` 语句创建视图的能力。Iceberg 此前已经支持视图的底层存储与读路径（`ViewCatalog`、`View` 元数据、`SparkView` 等），但在 Spark SQL 的扩展侧，并没有把 Spark 原生的 `CREATE VIEW` 命令路由到 Iceberg 的视图目录。这导致用户即便把 catalog 配成 Iceberg，仍然无法用熟悉的 `CREATE VIEW v AS SELECT ...` 语法来持久化一个视图，必须等价于用程序化 API。这个 PR 正是把这条 SQL 路径打通。

实现 SQL 视图创建在 Spark 体系里并不简单：Spark 内部对 v2 catalog 的 `CREATE VIEW` 走的是 `CreateView` 逻辑算子，但 Iceberg 需要的是自己的 `CreateIcebergView` 算子（这样后续才能落到 `ViewCatalog#createView` 而不是 Spark 默认的 session catalog 行为）。因此本提交沿用了 Iceberg 既有的"扩展 Spark Catalyst"模式：注入一组分析期规则（resolve → check → rewrite → strategy → exec），把 Spark 原生 `CreateView` 拦截、转换成 Iceberg 自己的命令链，最终在物理执行层调用 `SparkCatalog#createView`，把视图元数据写入 Iceberg 的 `ViewCatalog`。

整个链路完整覆盖了视图创建的常见约束：列别名与列注释、列数校验、列名重复检测、不允许引用临时视图（temp view / global temp view，包括子查询中的引用）、CTE 支持、`IF NOT EXISTS` 与 `CREATE OR REPLACE` 语义。这相当于把 Spark 标准视图语义完整地投影到 Iceberg 视图模型上，是 Iceberg 视图功能从"能存"到"能用 SQL 写"的关键一步。

上下游影响：对上游用户来说，此 PR 让 Iceberg 视图成为 Spark SQL 的一等公民，配合已有的 `DROP VIEW`、`SELECT FROM view` 能力，闭环了视图的 CRUD；对下游 catalog 实现者来说，需要正确实现 `ViewCatalog#createView`（`SparkCatalog` 已实现），并妥善处理 `queryColumnNames` 等属性以支持后续的 schema evolution。

## 如何达成设计目的

整体设计沿用 Spark Catalyst 的"规则链"模式：在 `IcebergSparkSessionExtensions` 中注册若干规则，对 `CREATE VIEW` 这条 SQL 经过 "解析（ResolveViews）→ 校验（CheckViews）→ 重写（RewriteViewCommands）→ 策略匹配（ExtendedDataSourceV2Strategy）→ 物理执行（CreateV2ViewExec）→ 目录写入（SparkCatalog#createView）" 这一完整流水线，把 Spark 原生命令逐步替换成 Iceberg 自定义命令，最终通过 `ViewCatalog` 持久化视图元数据。每一层只做一件职责清晰的事，便于测试和维护。

## 修改详情

### spark/v3.5/spark-extensions/src/main/scala/org/apache/iceberg/spark/extensions/IcebergSparkSessionExtensions.scala

**修改目的**：注册新的 check 规则到 Spark session。

**工作逻辑**：新增 import `CheckViews`，并在扩展点 `extensions.injectCheckRule(_ => CheckViews)` 中注册。`CheckViews` 是一个检查规则（在分析阶段后期运行），用于校验已经 resolve 完的 `CreateIcebergView` 算子是否符合视图列约束。这是规则链的入口注册。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala

**修改目的**：新建检查规则对象，在分析阶段校验视图列的合法性。

**工作逻辑**：`CheckViews` 是一个 `LogicalPlan => Unit` 的单例对象。它遍历 plan，对匹配 `CreateIcebergView(ResolvedIdentifier(_: ViewCatalog, ident), _, query, columnAliases, _, _, _, _, _, _, _)` 的节点做两件事：
1. `verifyColumnCount` 检查用户显式指定的列别名数量是否与 query 输出列数一致；不一致时抛出 `CREATE_VIEW_COLUMN_ARITY_MISMATCH.NOT_ENOUGH_DATA_COLUMNS` 或 `TOO_MANY_DATA_COLUMNS` 错误，并附带 view 名、view 列、data 列等错误参数，便于用户定位。
2. `SchemaUtils.checkColumnNameDuplication` 检查 query 输出列名是否重复（使用当前 SQLConf 的 resolver），避免视图列名冲突。

这一步只在目标 catalog 是 `ViewCatalog` 时生效，对其他 catalog 静默放过。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala

**修改目的**：在分析阶段把 `CreateIcebergView` 节点的列别名和注释应用到 query 上，并标记为已重写。

**工作逻辑**：
1. 新增匹配分支：当 `CreateIcebergView` 的 query 已 resolved 且 `rewritten=false` 时，调用 `aliasColumns` 给 query 加一层 `Project`，把列别名（`columnAliases`）和列注释（`columnComments`）以 `Alias` 的形式封装，并通过 `MetadataBuilder` 把 comment 写入列元数据。然后用 `query.schema.fieldNames` 设置 `queryColumnNames`，把 `rewritten` 标记为 true，避免重复重写。
2. `aliasColumns` 方法：当列别名为空或长度不匹配时直接返回原 plan（兼容不指定别名的情况）；否则对每个输出属性构造 `Alias(attr, aliasName)`，若该列有注释则把 comment 写到 metadata。
3. 把内部类 `ViewHelper` 重命名为 `IcebergViewHelper`，避免与 Spark 内部同名类冲突（属于命名空间卫生清理）。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala

**修改目的**：把 Spark 原生 `CreateView` 重写为 Iceberg 自定义的 `CreateIcebergView`，并校验临时视图引用。

**工作逻辑**：
1. 新增 import 多个类（`AnalysisException`、`SubqueryExpression`、`CreateView`、`View`、`CreateIcebergView`、`Identifier`）。
2. 新增匹配分支：匹配 `CreateView(ResolvedView(resolved), userSpecifiedColumns, comment, properties, Some(queryText), query, allowExisting, replace)`，对 query 应用 `CTESubstitution`（把 CTE 替换为子查询），调用 `verifyTemporaryObjectsDontExist` 确保永久视图不引用临时对象，最后构造 `CreateIcebergView` 节点，把 `userSpecifiedColumns`（每个元素是 `(name, Option[comment])` 元组）拆成 `columnAliases` 和 `columnComments` 两个序列。
3. `verifyTemporaryObjectsDontExist`：调用 `collectTemporaryViews` 收集 plan 中所有临时视图引用；若有，抛 `INVALID_TEMP_OBJ_REFERENCE`，提示 "Cannot create the persistent object ... because it references to the temporary object ..."。
4. `collectTemporaryViews`：递归遍历 plan，匹配 `UnresolvedRelation`（若是 temp view）、`View`（若是 temp view）以及 `SubqueryExpression` 内部的 plan，distinct 后返回所有临时视图的名字序列。这保证即使临时视图藏在子查询里也能被检测出来。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/views/CreateIcebergView.scala

**修改目的**：定义承载视图创建命令的逻辑算子。

**工作逻辑**：新建 `CreateIcebergView` case class，继承 `BinaryCommand`。字段包括：`child`（被解析的 view 标识符）、`queryText`（原始 SQL 文本）、`query`（已解析的逻辑计划）、`columnAliases`、`columnComments`、`queryColumnNames`、`comment`、`properties`、`allowExisting`、`replace`、以及一个内部状态 `rewritten`（默认 false，用于避免 `ResolveViews` 重复处理）。`left = child`、`right = query`，并实现 `withNewChildrenInternal` 以支持树重写。这个算子是规则链中承上启下的中间表示。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/CreateV2ViewExec.scala

**修改目的**：物理执行算子，调用 `ViewCatalog` 实际持久化视图。

**工作逻辑**：新建 `CreateV2ViewExec`，继承 `LeafV2CommandExec`，`output = Nil`。`run()` 方法核心逻辑：
1. 取当前 catalog 名和当前 namespace，作为视图的 default catalog/namespace 上下文。
2. 计算 `engineVersion = "Spark " + SPARK_VERSION`，并把 `comment`、`PROP_CREATE_ENGINE_VERSION`、`PROP_ENGINE_VERSION` 合入 properties。
3. `replace=true`（`CREATE OR REPLACE VIEW`）：若视图已存在则先 `dropView`，然后调用 `createView`。注释中明确指出 "FIXME: replaceView API doesn't exist in Spark 3.5"，所以采用先删后建的方式模拟 replace。
4. `replace=false`（`CREATE VIEW [IF NOT EXISTS]`）：直接 `createView`，捕获 `ViewAlreadyExistsException`，若 `allowExisting` 则忽略。

这个 exec 节点最终通过 `ViewCatalog#createView` 落到 Iceberg 的 `SparkCatalog` 实现。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala

**修改目的**：把 `CreateIcebergView` 逻辑算子翻译成 `CreateV2ViewExec` 物理算子。

**工作逻辑**：新增 import `CreateIcebergView`，并在 `apply` 方法中新增匹配分支：从 `CreateIcebergView` 中解构出 catalog、ident、queryText、query、columnAliases、columnComments、queryColumnNames、comment、properties、allowExisting、replace 等字段，构造 `CreateV2ViewExec`，其中 `viewSchema = query.schema`。这是 Spark 的 strategy 层，负责逻辑到物理的映射。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java

**修改目的**：实现 `ViewCatalog#createView`，真正把视图元数据写入 Iceberg。

**工作逻辑**：在 `createView` 方法中：
1. 若 `asViewCatalog` 不为 null（即该 catalog 支持视图），则进入实现分支；否则仍抛 `UnsupportedOperationException`。
2. 把 Spark 的 schema 通过 `SparkSchemaUtil.convert` 转成 Iceberg 的 `Schema`。
3. 用 `StringJoiner(",")` 把 `queryColumnNames` 拼成一个逗号分隔字符串，作为视图属性 `queryColumnNames` 存储下来（后续 `SparkView#queryColumnNames()` 会读它）。
4. 用 `Spark3Util.rebuildCreateProperties` 重建 properties（去掉 Iceberg 保留的内部属性），加上 `queryColumnNames`。
5. 通过 `asViewCatalog.buildView(...).withDefaultCatalog(...).withDefaultNamespace(...).withQuery("spark", sql).withSchema(icebergSchema).withLocation(...).withProperties(props).create()` 构建 Iceberg `View`。
6. 异常映射：`NoSuchNamespaceException` → Spark 的 `NoSuchNamespaceException`；`AlreadyExistsException` → `ViewAlreadyExistsException`。
7. 返回 `new SparkView(catalogName, view)`。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkView.java

**修改目的**：让 `SparkView` 正确返回 `queryColumnNames`，并把 `queryColumnNames` 加入保留属性集合。

**工作逻辑**：
1. 新增常量 `QUERY_COLUMN_NAMES = "queryColumnNames"`，并加入 `RESERVED_PROPERTIES` 集合（与 `provider`、`location`、`FORMAT_VERSION` 并列），表示该属性由 Iceberg 内部管理，不暴露给用户属性视图。
2. `queryColumnNames()` 方法原本固定返回空数组，改为从 `icebergView.properties()` 中读取 `queryColumnNames`，若存在则按逗号 `split`，否则返回空数组。这样视图的 query 列名能被 Spark 正确读回，用于后续的 schema 演进与列映射。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为新的 SQL 视图创建能力添加端到端测试覆盖。

**工作逻辑**：新增 import `View`，并在测试类末尾新增一批 `@Test` 方法，覆盖以下场景：
- `createViewIfNotExists`：重复创建同名视图会抛 `AnalysisException`，加 `IF NOT EXISTS` 后不抛错。
- `createViewWithInvalidSQL`：非法 SQL 抛 `Syntax error`。
- `createViewReferencingTempView` / `createViewReferencingGlobalTempView`：永久视图引用 temp view / global temp view 会抛 `INVALID_TEMP_OBJ_REFERENCE`。
- `createViewUsingNonExistingTable`：引用不存在的表抛 "cannot be found"。
- `createViewWithMismatchedColumnCounts`：视图列数与 query 列数不匹配，分别抛 "not enough data columns" / "too many data columns"。
- `createViewWithColumnAliases`：用 `(new_id COMMENT 'ID', new_data COMMENT 'DATA')` 创建带别名和注释的视图，断言 schema、doc、查询结果都正确；并验证列顺序交换的情况。
- `createViewWithDuplicateColumnNames`：视图列名重复抛 "already exists"。
- `createViewWithDuplicateQueryColumnNames`：query 列名重复但提供了别名时可通过；不提供别名则报错。
- `createViewWithCTE`：使用 CTE 的视图能正确创建与查询。
- `createViewWithConflictingNamesForCTEAndTempView`：CTE 与同名 temp view 共存时，CTE 优先。
- `createViewWithCTEReferencingTempView`：CTE 内引用 temp view 也会被拒绝。
- `createViewWithNonExistingQueryColumn`：引用不存在的列抛 "cannot be resolved"。
- `createViewWithSubqueryExpressionUsingTempView` / `createViewWithGlobalTempView`：子查询中引用 temp view / global temp view 也会被 `collectTemporaryViews` 检测到并拒绝。

这批测试既覆盖了正向能力，也覆盖了所有重要的负向边界，质量较高。

## 小结

这是一个分量较重的功能提交（约 600 行新增），它通过 Spark Catalyst 的规则链模式（resolve/check/rewrite/strategy/exec）把原生 `CREATE VIEW` 命令完整地路由到 Iceberg 的 `ViewCatalog`，并补齐了 `SparkCatalog#createView` 与 `SparkView#queryColumnNames` 的实现。设计上严格遵守 Iceberg 既有的"扩展 Spark 而非 fork Spark"模式，每一层职责清晰、可独立测试。对用户而言，此 PR 让 Iceberg 视图在 Spark SQL 中成为一等公民，闭环了视图的 SQL 化创建路径；同时通过严格的临时视图引用检查与列校验，保证了视图语义的安全性与正确性。
