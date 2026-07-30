# 提交 0536：Spark 3.4: Fail if temp functions are used in views

## 提交信息

- **序号**：0536 / 4088
- **哈希**：7a7950e40e4c61b4168681476614ee60934e3756
- **短哈希**：7a7950e40
- **日期**：2024-02-26 10:02:35 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Fail if temp functions are used in views (#9809)
- **PR/Issue**：#9809（对应 main 分支 PR #9675，即 0535 号提交 b3c68e5407922b9daf022eaa5d863ab986d7640c 的 Spark 3.4 回port）

## 总体目的

本提交是 main 分支上 PR #9675（提交 b3c68e540，0535 号）向 Spark 3.4 模块的回迁。要解决的问题是：Iceberg 的 Spark 视图扩展（`RewriteViewCommands`）在创建视图（CREATE VIEW）时，原本只校验视图查询体不能引用临时视图（temp view），却遗留了一个 `// TODO: check for temp function names`，并未校验临时函数（temp function）。

这会导致用户可以创建一个引用 `CREATE TEMPORARY FUNCTION` 注册的 Hive UDF 的持久化视图。临时函数的生命周期仅存在于当前 SparkSession，会话结束即消失；而视图是持久化存储在 catalog 中的元数据。一旦视图引用了临时函数，下次重读到该视图时函数已不存在，查询就会失败，留下一个"看上去存在、实际不可用"的坏视图，给数据平台带来隐性数据质量风险。

本次提交的目标就是补齐该校验：在创建视图阶段，若发现查询体引用了任何临时函数，立即抛出 `AnalysisException` 拒绝创建，从源头杜绝此类坏视图的产生。同时对原有临时视图的报错信息做了重构，统一为更清晰的新格式。

## 如何达成设计目的

设计思路分三步：

1. **新增临时函数识别能力**：在 `RewriteViewCommands` 中新增 `isTempFunction(nameParts)` 方法，委托给 `catalogManager.v1SessionCatalog.isTemporaryFunction`。这里有一个关键判断：当 `nameParts.size > 1`（即函数被 schema/catalog 限定）时直接返回 `false`——因为临时函数只能在单段名下被引用，带限定的多段名根本不可能命中临时函数。这一前置短路既符合语义，也避免了把多段名错误地传给 `isTemporaryFunction`。

2. **遍历逻辑计划收集临时函数引用**：新增 `collectTemporaryFunctions(child)` 方法，用 Spark Catalyst 的 `resolveExpressionsWithPruning(_.containsAnyPattern(UNRESOLVED_FUNCTION))` 做基于树模式剪枝的表达式遍历。遍历中：
   - 命中 `UnresolvedFunction(nameParts, _, _, _, _)` 且 `isTempFunction(nameParts)` 为真时，把 `nameParts.head`（函数名）加入结果集；
   - 命中 `SubqueryExpression` 时递归处理子查询计划，保证子查询里的临时函数也能被发现（对应测试 `createViewWithSubqueryExpressionUsingTempFunction`）。
   使用 `mutable.HashSet` 去重，避免同一函数被多次引用时重复报错。

3. **统一报错路径**：把原先 `verifyTemporaryObjectsDontExist` 里针对临时视图"逐个 foreach 抛异常"的写法，重构为先收集再一次性抛出。新增 `invalidRefToTempObject(name, tempObjectNames, tempObjectType)` 辅助方法，统一生成形如 `Cannot create view <view> that references temporary <view|function>: [a, b]` 的错误信息。临时视图和临时函数共用同一个出口，区别只在 `tempObjectType`（"view" 或 "function"）。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

**修改目的**：补齐对临时函数的校验，并重构临时对象报错信息。

**工作逻辑**：

- **新增导入**：引入 `UNRESOLVED_FUNCTION` 树模式常量用于剪枝遍历，引入 `scala.collection.mutable` 用于收集结果，并把 `CatalogV2Implicits._` 从方法内部上提到类级别（原先只在 `verifyTemporaryObjectsDontExist` 方法内 import，现在因为 `isTempFunction` 也要用到 `asFunctionIdentifier` 隐式转换，所以提到类作用域）。

- **`isTempFunction` 方法**：
  ```scala
  private def isTempFunction(nameParts: Seq[String]): Boolean = {
    if (nameParts.size > 1) {
      return false
    }
    catalogManager.v1SessionCatalog.isTemporaryFunction(nameParts.asFunctionIdentifier)
  }
  ```
  多段名直接判 false，单段名交给 session catalog 判断。`asFunctionIdentifier` 是 `CatalogV2Implicits` 提供的隐式转换，把 `Seq[String]` 转成 `FunctionIdentifier`。

- **`verifyTemporaryObjectsDontExist` 重构**：原逻辑是 `tempViews.foreach { nameParts => throw ... }`（注意：foreach 里抛异常实际上只会报第一个就停）。新逻辑改为：
  ```scala
  val tempViews = collectTemporaryViews(child)
  if (tempViews.nonEmpty) {
    throw invalidRefToTempObject(name, tempViews.map(v => v.quoted).mkString("[", ", ", "]"), "view")
  }
  val tempFunctions = collectTemporaryFunctions(child)
  if (tempFunctions.nonEmpty) {
    throw invalidRefToTempObject(name, tempFunctions.mkString("[", ", ", "]"), "function")
  }
  ```
  先校验临时视图，再校验临时函数；任一非空即抛异常。临时对象名用 `mkString("[", ", ", "]")` 拼成列表形式，一次列出全部，信息更完整。`// TODO: check for temp function names` 注释随之删除。

- **`invalidRefToTempObject` 辅助方法**：
  ```scala
  private def invalidRefToTempObject(name: Identifier, tempObjectNames: String, tempObjectType: String) = {
    new AnalysisException(String.format("Cannot create view %s that references temporary %s: %s",
      name, tempObjectType, tempObjectNames))
  }
  ```
  统一错误信息格式。注意 Spark 3.4 这里走的是 `String.format` 老式消息，而 main 分支（Spark 3.5，0535 号提交）原代码用的是 `errorClass = "INVALID_TEMP_OBJ_REFERENCE"` 的错误类机制——这是两个 Spark 版本之间的固有差异，本次回迁统一替换为 `String.format` 形式。

- **`collectTemporaryFunctions` 方法**：
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
    }
    tempFunctions.toSeq
  }
  ```
  `resolveExpressionsWithPruning` 是 Spark Catalyst 的表达式遍历 API，传入的谓词 `_.containsAnyPattern(UNRESOLVED_FUNCTION)` 用于在遍历前对子树做模式检查，不含 `UNRESOLVED_FUNCTION` 的子树直接跳过，提升性能。匹配到 `UnresolvedFunction` 时用守卫 `if isTempFunction(nameParts)` 过滤，命中则记录函数名（`nameParts.head`，因为能命中的必然是单段名）。匹配到 `SubqueryExpression`（如标量子查询、`IN` 子查询、`EXISTS` 子查询等）时递归调用自身，保证子查询内的临时函数也能被捕获。每个 case 都返回原表达式 `f` / `e` 不做改写，因为这里只是收集不是改写。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：覆盖新增的临时函数校验逻辑，并更新原有临时视图校验测试的断言以匹配新错误信息。

**工作逻辑**：

- **新增 `readFromViewReferencingTempFunction`**：通过 ViewCatalog API（绕过 SQL 创建路径的校验）直接创建一个引用临时函数 `test_avg` 的视图，验证视图本身能查（API 直建绕过校验），但 `SELECT * FROM <view>` 读取时因为临时函数已不在而报 `The function ... cannot be found`。这个测试说明：SQL 路径的校验是必要的防线，因为 API 路径无法阻止。

- **新增 `createViewReferencingTempFunction`**：注册临时函数 `test_avg_func`，`CREATE VIEW ... AS SELECT test_avg_func(id) FROM ...`，断言抛 `Cannot create view <ns>.<view> that references temporary function: test_avg_func`。

- **新增 `createViewReferencingQualifiedTempFunction`**：验证临时函数用 `catalog.schema.name` 或 `schema.name` 限定时，根本到不了临时函数校验，而是更早被 `Cannot resolve function` 拦截——这印证了 `isTempFunction` 里 `nameParts.size > 1` 返回 false 的设计是合理的。

- **新增 `createViewWithCTEReferencingTempFunction`**：CTE（WITH 子句）里引用临时函数，验证 CTE 展开后临时函数引用仍能被收集到。

- **新增 `createViewWithSubqueryExpressionUsingTempFunction`**：标量子查询 `SELECT * FROM t WHERE id < (SELECT avg_func(id) FROM t)` 里引用临时函数，验证 `SubqueryExpression` 递归分支生效。

- **更新原有测试断言**：`createViewReferencingTempView`、`createViewReferencingGlobalTempView`、`createViewWithCTERferencingTempView`、以及两个 `createViewWithNonExistingQueryColumn` 相关测试的断言，从旧的 `"Cannot create the persistent object ... of the type VIEW because it references to the temporary object ..."` 改为新的 `"Cannot create view <ns>.<view> that references temporary view: ..."`，与新错误信息格式对齐。

## 小结

本提交补齐了 Iceberg Spark 3.4 视图扩展中对临时函数引用的校验缺口，消除了原 `// TODO`，使持久化视图无法再悄悄引用会话级临时函数，从源头避免了"视图元数据存在但函数已失效"的坏视图问题。同时对临时视图/临时函数的报错信息做了统一重构，信息更清晰、能一次性列出全部违规对象。

**与 0535（main 分支 PR #9675）的对比**：本提交是 0535 的 Spark 3.4 回port，核心逻辑（`isTempFunction`、`collectTemporaryFunctions`、`invalidRefToTempObject`、`verifyTemporaryObjectsDontExist` 重构）完全一致。差异仅在 Spark 版本固有不同：
- Spark 3.5 原代码用 `errorClass = "INVALID_TEMP_OBJ_REFERENCE"` 错误类机制，Spark 3.4 原代码用 `String.format` 老式消息；两者最终都替换为新的 `String.format` 形式。
- Spark 3.4 中 `ResolvedIdent` 是 `object`，Spark 3.5 中是 `private object`，属版本既有差异，本提交未改动。
- 文件路径前缀分别为 `spark/v3.4/` 与 `spark/v3.5/`。

**回迁到 1.4.x 的注意事项**：1.4.x 若同时维护 Spark 3.4 模块，可直接套用本提交；若 1.4.x 的 Spark 3.4 模块 `RewriteViewCommands` 基线与 main 一致，则改动可干净 apply。需注意 `UnresolvedFunction` 在 Spark 3.4 的构造参数签名（5 个参数 `nameParts, _, _, _, _`）与本提交匹配；若 1.4.x 用的 Spark 3.4 小版本签名不同，需相应调整匹配模式。测试侧需保证 Hive UDF 类 `org.apache.hadoop.hive.ql.udf.generic.GenericUDAFAverage` 在测试 classpath 可用。
