# 提交 0535：视图引用临时函数时报错

## 提交信息

- **序号**：0535 / 4088
- **哈希**：b3c68e5407922b9daf022eaa5d863ab986d7640c
- **短哈希**：b3c68e540
- **日期**：2024-02-25（Sun Feb 25 19:22:43 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Fail if temp functions are used in views (#9675)
- **PR/Issue**：#9675

## 总体目的

Iceberg 的 Spark 扩展支持创建持久化的 Iceberg 视图（`CreateIcebergView`）。持久化视图的定义会被写入 catalog 元数据，可在任意会话中读取。然而，Spark 的临时函数（temp function / session-scoped UDF，通过 `CREATE TEMPORARY FUNCTION` 注册）仅存在于创建它的会话中，会话结束即失效。

此前的 `RewriteViewCommands` 规则在校验视图定义时，只检查了视图是否引用了临时视图（temp view），代码中明确留有 `// TODO: check for temp function names` 注释，表明"禁止视图引用临时函数"这一校验已知但未实现。这导致用户可以创建一个引用临时函数的持久化视图，创建时不会报错，但当其他会话（或当前会话注销该函数后）尝试读取该视图时，会因函数无法解析而失败，产生难以排查的运行时错误。

本提交的目的，是补齐这一校验缺口：在创建视图时（`CREATE VIEW` 解析阶段）主动检测视图查询中是否引用了临时函数，若引用则立即抛出 `AnalysisException`，阻止创建，从而把"会话级临时对象泄漏到持久化视图"的问题前置到创建时暴露，而不是延迟到读取时。

## 如何达成设计目的

整体设计在已有的 `verifyTemporaryObjectsDontExist` 校验方法中新增临时函数检测分支，与既有的临时视图检测并列。实现思路分四部分：

1. **识别临时函数**：新增 `isTempFunction(nameParts)` 方法，通过 `catalogManager.v1SessionCatalog.isTemporaryFunction` 判断一个函数名是否为已注册的临时函数。由于临时函数是会话级、无 schema/catalog 限定的，只有单段名称（如 `test_avg`）才可能是临时函数；多段名称（如 `db.func`）直接返回 false。

2. **收集视图查询中的所有临时函数引用**：新增 `collectTemporaryFunctions(child)` 方法，遍历视图查询计划的表达式树，用 Spark Catalyst 的 `resolveExpressionsWithPruning` + `UNRESOLVED_FUNCTION` 模式剪枝来高效定位 `UnresolvedFunction` 节点，对每个函数调用用 `isTempFunction` 判定，命中则收集其名称；同时递归处理 `SubqueryExpression`（子查询），覆盖临时函数出现在子查询中的场景。

3. **统一报错**：将临时视图与临时函数的报错逻辑抽取为共享的 `invalidRefToTempObject` 方法，产生统一格式的错误消息：`Cannot create view <name> that references temporary <view|function>: [<names>]`。这同时简化了既有临时视图的报错代码，并把临时视图报错从 Spark 标准错误类 `INVALID_TEMP_OBJ_REFERENCE` 改为自定义字符串消息，使两类临时对象的报错口径一致。

4. **测试覆盖**：在 `TestViews` 中新增多个测试，覆盖临时函数出现在视图查询各位置（顶层 SELECT、CTE、子查询）的场景，以及通过 API 绕过 SQL 校验创建视图后读取失败的端到端场景；同步更新既有临时视图测试的断言以匹配新的错误消息格式。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

**修改目的**：在视图创建的解析规则中补齐临时函数检测，阻止持久化视图引用会话级临时函数。

**工作逻辑**：

#### 新增 `isTempFunction` 方法

```scala
private def isTempFunction(nameParts: Seq[String]): Boolean = {
  if (nameParts.size > 1) {
    return false
  }
  catalogManager.v1SessionCatalog.isTemporaryFunction(nameParts.asFunctionIdentifier)
}
```

判断逻辑：名称分段数大于 1（即带 schema 或 catalog 限定）直接返回 false——临时函数注册在会话级 `v1SessionCatalog` 中，只能用单段名称引用，不可能有限定前缀；单段名称则通过 `asFunctionIdentifier`（来自 `CatalogV2Implicits`，因此该 import 从方法内上移到类级别）转成 `FunctionIdentifier`，再查 `v1SessionCatalog.isTemporaryFunction` 确认是否为已注册临时函数。

#### 新增 `collectTemporaryFunctions` 方法

```scala
private def collectTemporaryFunctions(child: LogicalPlan): Seq[String] = {
  val tempFunctions = new mutable.HashSet[String]()
  child.resolveExpressionsWithPruning(_.containsAnyPattern(UNRESOLVED_FUNCTION)) {
    case f @ UnresolvedFunction(nameParts, _, _, _, _) if isTempFunction(nameParts) =>
      tempFunctions += nameParts.head
      f
    case e: SubqueryExpression =>
      tempFunctions ++= collectTemporaryFunctions(e.plan)
      e
    e
  }
  tempFunctions.toSeq
}
```

工作逻辑：
- `resolveExpressionsWithPruning(_.containsAnyPattern(UNRESOLVED_FUNCTION))` 是 Spark Catalyst 的表达式遍历方法，传入的谓词 `_.containsAnyPattern(UNRESOLVED_FUNCTION)` 作为剪枝条件，使遍历器跳过不含 `UNRESOLVED_FUNCTION` 模式的子树，避免全量扫描表达式树，提升性能。
- `case f @ UnresolvedFunction(nameParts, _, _, _, _) if isTempFunction(nameParts)`：匹配未解析的函数调用节点，提取其名称分段 `nameParts`，用 `isTempFunction` 过滤；命中则把函数名（`nameParts.head`，即单段名）加入可变集合。`f` 原样返回——此遍历用于收集而非改写。
- `case e: SubqueryExpression`：匹配子查询表达式（如 `WHERE id < (SELECT ...)` 中的子查询），递归调用 `collectTemporaryFunctions(e.plan)` 处理子查询计划内的临时函数，保证嵌套子查询中的临时函数也能被发现。`e` 原样返回。
- 结果用 `mutable.HashSet` 去重后转 `Seq` 返回。

注意：此处用 `resolveExpressionsWithPruning` 而非 `collectTemporaryViews` 用的 `flatMap` 手动遍历，是因为函数调用是表达式（`Expression`）节点，需用表达式级别的遍历器；而视图引用是关系（`LogicalPlan`/`UnresolvedRelation`）节点，用 plan 级别的 `flatMap` 遍历。两者针对不同节点类型，分别采用合适的遍历 API。

#### 修改 `verifyTemporaryObjectsDontExist` 方法

```scala
private def verifyTemporaryObjectsDontExist(
    name: Identifier, child: LogicalPlan): Unit = {
  val tempViews = collectTemporaryViews(child)
  if (tempViews.nonEmpty) {
    throw invalidRefToTempObject(name, tempViews.map(v => v.quoted).mkString("[", ", ", "]"), "view")
  }

  val tempFunctions = collectTemporaryFunctions(child)
  if (tempFunctions.nonEmpty) {
    throw invalidRefToTempObject(name, tempFunctions.mkString("[", ", ", "]"), "function")
  }
}
```

在原有临时视图检测之后，新增临时函数检测分支。两者串行执行：先查临时视图，再查临时函数；任一非空即抛出。函数名列表用 `mkString("[", ", ", "]")` 拼成 `[func1, func2]` 形式，与临时视图的列表格式一致（临时视图用 `v.quoted` 带引号，函数用裸名）。

#### 新增 `invalidRefToTempObject` 方法（统一报错）

```scala
private def invalidRefToTempObject(name: Identifier, tempObjectNames: String, tempObjectType: String) = {
  new AnalysisException(String.format("Cannot create view %s that references temporary %s: %s",
    name, tempObjectType, tempObjectNames))
}
```

接收视图标识 `name`、临时对象名列表字符串、临时对象类型（`"view"` 或 `"function"`），用 `String.format` 生成统一消息。注意 `name` 是 `Identifier`，作为 `%s` 会被其 `toString` 输出（通常为 `namespace.viewName` 形式）。

**行为变更（重要）**：既有临时视图的报错原本使用 Spark 标准错误类：
```scala
throw new AnalysisException(
  errorClass = "INVALID_TEMP_OBJ_REFERENCE",
  messageParameters = Map("obj" -> "VIEW", "objName" -> name.name(), ...))
```
其消息形如 `Cannot create the persistent object <view> of the type VIEW because it references to the temporary object <tempView>`。改为 `invalidRefToTempObject` 后，消息变为 `Cannot create view <name> that references temporary view: [<tempView>]`。这是用户可见的错误消息变更，下游若按旧消息文本匹配会受影响，测试断言已同步更新。

#### import 调整

- 新增 `import org.apache.spark.sql.catalyst.trees.TreePattern.UNRESOLVED_FUNCTION`（用于剪枝谓词）。
- 新增 `import scala.collection.mutable`（用于 `mutable.HashSet`）。
- `import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._` 从 `verifyTemporaryObjectsDontExist` 方法体内上移到类级别，因为新方法 `isTempFunction` 也需要 `asFunctionIdentifier`（`CatalogV2Implicits` 提供的隐式转换）。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：为临时函数检测新增测试覆盖，并更新既有临时视图测试以匹配新的错误消息格式。

**工作逻辑**：新增 5 个测试方法 + 更新 4 处既有断言。

#### 新增 `readFromViewReferencingTempFunction`

验证端到端场景：通过 Iceberg View Catalog API（而非 SQL `CREATE VIEW`，因为 SQL 路径会被新校验拦截）直接构建一个引用临时函数 `test_avg` 的视图并 `create()`。先确认直接执行该 SQL 能正常工作（临时函数存在，`SELECT test_avg(id) FROM table` 返回 `5.5`）；再确认读取该视图 `SELECT * FROM view` 会因临时函数在视图读取上下文中无法解析而失败，断言抛出 `AnalysisException` 且消息包含 `The function`、函数名、`cannot be found`。此测试说明：即使绕过创建时校验强行写入视图，读取时仍会因函数缺失而失败——这正是本提交要在创建时提前拦截的根本原因。

#### 新增 `createViewReferencingTempFunction`

通过 SQL `CREATE TEMPORARY FUNCTION` 注册 `test_avg_func`，再 `CREATE VIEW ... AS SELECT test_avg_func(id) FROM table`，断言抛出 `AnalysisException`，消息包含 `Cannot create view <namespace>.<viewName>`、`that references temporary function:`、函数名。验证核心拦截逻辑。

#### 新增 `createViewReferencingQualifiedTempFunction`

验证临时函数不能用限定名引用的两层场景：
- `catalog.namespace.func(id)` 形式：因多段名称不被识别为临时函数，校验放行，但后续 Spark 解析阶段无法解析该函数，报 `Cannot resolve function` + `` `catalog`.`namespace`.`func` ``。
- `namespace.func(id)` 形式：同理报 `Cannot resolve function` + `` `namespace`.`func` ``。

这印证了 `isTempFunction` 中 `nameParts.size > 1` 直接返回 false 的设计——带限定的函数名不会误判为临时函数，而是交给 Spark 正常的函数解析流程处理（并因找不到而报错）。

#### 新增 `createViewWithCTEReferencingTempFunction`

验证临时函数出现在 CTE（`WITH avg_data AS (SELECT func(id) AS avg FROM table) ...`）中也能被检测到。CTE 经 `CTESubstitution` 替换后，临时函数引用仍存在于查询计划中，`collectTemporaryFunctions` 的表达式遍历能覆盖到。断言消息包含 `that references temporary function:` 与函数名。

#### 新增 `createViewWithSubqueryExpressionUsingTempFunction`

验证临时函数出现在子查询中（`SELECT * FROM table WHERE id < (SELECT func(id) FROM table)`）也能被检测到。这专门覆盖 `collectTemporaryFunctions` 中 `case e: SubqueryExpression => collectTemporaryFunctions(e.plan)` 的递归分支。断言消息包含 `that references temporary function:` 与函数名。

#### 更新既有临时视图测试断言

4 处既有测试（`createViewReferencingTempView`、`createViewReferencingGlobalTempView`、`createViewWithCTEReferencingTempView`、`createViewWithSubqueryExpression`、`createViewWithGlobalTempViewInCTE` 等）的断言从匹配旧消息片段：
```
"Cannot create the persistent object" / "of the type VIEW because it references to the temporary object"
```
改为匹配新消息片段：
```
"Cannot create view <namespace>.<viewName>" / "that references temporary view:"
```
与新报错格式保持一致。同时全局临时视图的引用名格式从 `global_temp.<view>` 调整为 `<global_temp>.<view>` 的显式拼接形式。

## 小结

**成效**：补齐了 `RewriteViewCommands` 中长期存在的 `// TODO: check for temp function names` 缺口，使持久化视图在创建时即被禁止引用会话级临时函数，避免"创建成功、读取失败"的延迟错误。新增的 `collectTemporaryFunctions` 通过 Catalyst 表达式剪枝遍历 + 子查询递归，覆盖了函数出现在顶层、CTE、子查询等各位置的场景。同时统一了临时视图与临时函数的报错格式，简化了代码。

**影响范围**：
- 行为变更：`CREATE VIEW` 引用临时函数现在会立即报错（此前放行，读取时才报错）；临时视图的报错消息文本变更（从 Spark `INVALID_TEMP_OBJ_REFERENCE` 错误类改为自定义消息）。
- 兼容性：依赖旧错误消息文本的下游脚本/测试会受影响；正常用户查询不受影响，仅创建非法视图时行为更严格。
- 范围：仅 Spark v3.5 spark-extensions 模块，仅影响视图创建路径。

**回迁到 1.4.x 的注意事项**：
1. **错误消息变更的兼容性**：本提交改变了临时视图的报错消息（从 Spark 标准错误类 `INVALID_TEMP_OBJ_REFERENCE` 改为自定义字符串）。若 1.4.x 用户或下游测试依赖旧消息文本，回迁会破坏匹配。需评估 1.4.x 是否有用户依赖该错误类，谨慎决定是否一并回迁消息变更，或仅回迁临时函数检测而保留旧临时视图消息格式（但这样需拆分本提交，因为代码已统一到 `invalidRefToTempObject`）。
2. **Spark 版本 API 适配**：`UnresolvedFunction` 的构造参数个数（此处为 5 个：`nameParts, _, _, _, _`）与 Spark 版本强相关。1.4.x 若对应不同 Spark 版本（v3.3/v3.4），`UnresolvedFunction` 的签名可能不同，需调整模式匹配的参数数量。同理 `resolveExpressionsWithPruning`、`containsAnyPattern`、`UNRESOLVED_FUNCTION` 等 Catalyst API 的可用性需按 Spark 版本核对。
3. **`isTemporaryFunction` API**：`v1SessionCatalog.isTemporaryFunction` 是 Spark 内部 API，在不同 Spark 版本中签名/可见性可能不同，回迁前需确认目标 Spark 版本可用。
4. **`SubqueryExpression` 导入**：该类已在既有 import 中（`collectTemporaryViews` 已用），无需额外处理。
5. **测试依赖**：新增测试用到 `GenericUDAFAverage`（Hive UDF）与 Hive 会话支持，1.4.x 测试环境需具备 Hive 支持才能运行这些测试。
6. 该提交是纯防御性增强（更严格的创建校验），回迁风险主要在 Spark 版本 API 差异与错误消息兼容性，逻辑本身独立、无前置代码依赖。
