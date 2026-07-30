# 提交 0542：Spark 3.4 视图错误信息补上 catalog 名

## 提交信息

- **序号**：0542 / 4088
- **哈希**：10ec85ffcab6ac12ecac7fc6df9c3e4139f857b7
- **短哈希**：10ec85ffc
- **日期**：2024-02-26（AuthorDate/CommitDate 均 2024-02-26 15:51:43 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Include catalog name in view errors (#9810)
- **PR/Issue**：#9810。这是 main 分支上 PR #9807（提交 `f14c0e553`，对应 0537 号提交）向 Spark 3.4 模块的回port。

## 总体目的

本提交把 0537（PR #9807）在 Spark 3.5 上做的改进回迁到 Spark 3.4 模块：让 Iceberg Spark 视图扩展在抛出视图相关错误时，把 catalog 名一并写入错误信息，从原来的 `namespace.viewName` 两段式升级为 `catalog.namespace.viewName` 三段式全限定名。

**问题背景**：在多 catalog 环境（同时配置 `iceberg_cat`、`hive_cat`、`spark_cat` 等多个 catalog，且各 catalog 下可能有同名 namespace 与同名视图）下，错误信息只显示 `namespace.viewName` 无法唯一定位是哪个 catalog 的视图出问题，用户排错成本高。本次提交让错误信息自包含、可定位。

此外，本提交还顺带对 Spark 3.5 的 `RewriteViewCommands.invalidRefToTempObject` 做了一个纯样式清理（把参数名 `identifier` 改为 `ident` 并合并为一行），让两个 Spark 版本的代码风格保持一致。

## 如何达成设计目的

设计思路与 0537 完全一致：复用已经解析好的 `ResolvedIdentifier`（同时持有 catalog 实例与 `Identifier`），在生成错误信息时从 `ResolvedIdentifier` 中取出 `catalog.name()` 拼到视图名前。为此需要把原先只传 `Identifier` 的方法签名升级为传 `ResolvedIdentifier`，让 catalog 信息一路透传到错误信息生成处。改动很小且自洽，不引入新的解析步骤。

**Spark 3.4 与 Spark 3.5 的差异**：0537 在 Spark 3.5 上的分析已指出，Spark 3.5 用 `errorClass` 错误类机制（`CREATE_VIEW_COLUMN_ARITY_MISMATCH.NOT_ENOUGH_DATA_COLUMNS` 等），而 Spark 3.4 仍用老式 `String.format` 构造错误信息。因此本提交不能直接照搬 0537 的 Spark 3.5 改动，而是要在 Spark 3.4 的 `String.format` 框架下完成同样的"补 catalog 名"目标——具体是把格式串从 `"Cannot create view %s, ..."` 改为 `"Cannot create view %s.%s, ..."`，并相应地把参数列表从 `ident.toString, ...` 改为 `ident.catalog.name(), ident.identifier, ...`。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala`

**修改目的**：让列数不匹配错误的视图名带上 catalog 名。

**工作逻辑**：

- 模式匹配从 `case CreateIcebergView(ResolvedIdentifier(_: ViewCatalog, ident), ...)` 改为 `case CreateIcebergView(resolvedIdent@ResolvedIdentifier(_: ViewCatalog, _), ...)`，用 `@` 绑定整个 `ResolvedIdentifier` 而不是只取出其中的 `ident: Identifier`，这样后续才能拿到 catalog。调用 `verifyColumnCount` 时传 `resolvedIdent` 而不是 `ident`。

- `verifyColumnCount` 形参类型从 `ident: Identifier` 改为 `ident: ResolvedIdentifier`。

- 两个列数不匹配错误（"not enough data columns" 与 "too many data columns"）的格式串从：
  ```
  Cannot create view %s, the reason is not enough data columns:
  View columns: %s
  Data columns: %s
  ```
  改为：
  ```
  Cannot create view %s.%s, the reason is not enough data columns:
  View columns: %s
  Data columns: %s
  ```
  对应参数从 `ident.toString, columns.mkString(", "), query.output.map(c => c.name).mkString(", ")` 改为 `ident.catalog.name(), ident.identifier, columns.mkString(", "), query.output.map(c => c.name).mkString(", ")`。注意 `ident.identifier` 本身已是 `namespace.viewName` 形式（Iceberg 的 `Identifier.toString`），拼接 catalog 名后得到完整的 `catalog.namespace.viewName`。

- 移除不再使用的 `import org.apache.spark.sql.connector.catalog.Identifier`。

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

**修改目的**：让临时对象（临时视图/临时函数）引用错误的视图名带上 catalog 名。

**工作逻辑**：

- 调用点 `verifyTemporaryObjectsDontExist(resolved.identifier, q)` 改为 `verifyTemporaryObjectsDontExist(resolved, q)`，直接传整个 `ResolvedIdentifier` 而不是只取其中的 `identifier` 字段。

- `verifyTemporaryObjectsDontExist` 形参从 `name: Identifier` 改为 `identifier: ResolvedIdentifier`，内部调用 `invalidRefToTempObject` 时也改传 `identifier`。

- `invalidRefToTempObject` 形参从 `name: Identifier` 改为 `ident: ResolvedIdentifier`，错误信息从：
  ```
  Cannot create view %s that references temporary %s: %s
  ```
  改为：
  ```
  Cannot create view %s.%s that references temporary %s: %s
  ```
  对应参数从 `name, tempObjectType, tempObjectNames` 改为 `ident.catalog.name(), ident.identifier, tempObjectType, tempObjectNames`。`ident.catalog.name()` 提供 catalog 名，`ident.identifier` 提供 `namespace.viewName`，拼起来就是 `catalog.namespace.viewName`。

- 移除不再使用的 `import org.apache.spark.sql.connector.catalog.CatalogPlugin`、`Identifier`、`ViewCatalog` 三个 import（这些在此前仅服务于旧的 `Identifier` 形参路径，现已不需要）。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：更新所有视图错误断言，匹配新的三段式视图名。

**工作逻辑**：共 9 处断言从 `String.format("Cannot create view %s.%s", NAMESPACE, viewName)` 改为 `String.format("Cannot create view %s.%s.%s", catalogName, NAMESPACE, viewName)`，覆盖：

- 临时视图引用错误（`createViewReferencingTempView`、`createViewReferencingGlobalTempView`、CTE 引用临时视图的两个测试）
- 临时函数引用错误（`createViewReferencingTempFunction`、CTE 引用临时函数、子查询引用临时函数）
- 列数不匹配错误（`not enough data columns`、`too many data columns` 两个分支）

所有断言都额外补上 `catalogName`，与新错误信息一致。`catalogName` 来自测试类的参数化参数（`@Parameterized.Parameters(name = "catalogName = {0}, ...")`，取自 `SparkCatalogConfig.SPARK_WITH_VIEWS.catalogName()`），在测试方法作用域内可直接引用，无需新增字段。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

**修改目的**：纯样式清理，让 Spark 3.5 的 `invalidRefToTempObject` 与 Spark 3.4 回port 后的版本风格一致。

**工作逻辑**：仅把 `invalidRefToTempObject` 方法的形参名从 `identifier` 改为 `ident`，并把多行参数列表合并为一行：

```scala
// 改前
private def invalidRefToTempObject(
  identifier: ResolvedIdentifier,
  tempObjectNames: String,
  tempObjectType: String) = {
  new AnalysisException(String.format("Cannot create view %s.%s that references temporary %s: %s",
    identifier.catalog.name(), identifier.identifier, tempObjectType, tempObjectNames))
}

// 改后
private def invalidRefToTempObject(ident: ResolvedIdentifier, tempObjectNames: String, tempObjectType: String) = {
  new AnalysisException(String.format("Cannot create view %s.%s that references temporary %s: %s",
    ident.catalog.name(), ident.identifier, tempObjectType, tempObjectNames))
}
```

注意：此处 `%s.%s` 格式与 `ident.catalog.name()`、`ident.identifier` 的实际逻辑来自 0537（PR #9807），本提交对该 Spark 3.5 文件不做任何行为变更，只是参数改名与排版，让两版本对比时更整齐。

## 小结

**成效**：本提交把 Iceberg Spark 3.4 视图扩展的错误信息从 `namespace.viewName` 升级为 `catalog.namespace.viewName`，使多 catalog 环境下的错误可定位性显著提升，与 0537 在 Spark 3.5 上达成的效果对齐。改动面小（4 个文件，核心是把 `Identifier` 形参升级为 `ResolvedIdentifier` 以拿到 catalog），无行为语义变化，仅影响错误信息文本。同时附带对 Spark 3.5 的纯样式清理，使两版本代码风格统一。

**影响范围**：仅影响 Spark 3.4 模块的 `CheckViews` 与 `RewriteViewCommands` 错误信息文本，以及对应的 9 处测试断言；对 Spark 3.5 仅做参数改名，无行为影响。不涉及任何序列化格式、API 兼容性变更。

**与 0537 的关联**：0542 是 0537（PR #9807，Spark 3.5 主线）的 Spark 3.4 回port。两者由同一作者同一时间段完成，设计思路与改动模式一致；差别仅在于 Spark 3.4 用老式 `String.format` 构造错误信息，而 Spark 3.5 用 `errorClass` 错误类机制，因此两版本的具体改法不能直接照搬，但最终错误信息文本一致。本提交还顺带把 Spark 3.5 的 `invalidRefToTempObject` 参数名与排版对齐到 Spark 3.4 风格。

**回迁到 1.4.x 的注意事项**：

1. 本提交本身就在 1.4.x 维护分支的候选回迁清单中（位于 main 已有但 1.4.x 未有的 5 个提交之列）。回迁时可直接 cherry-pick，无冲突风险（1.4.x 上 Spark 3.4 的 `CheckViews`/`RewriteViewCommands` 已具备 0536 的基线，即 `invalidRefToTempObject` 与 `verifyTemporaryObjectsDontExist` 已存在）。
2. 回迁前需确认 1.4.x 上的 `TestViews.java` 测试基类已提供 `catalogName` 参数化变量——该变量来自 `SparkCatalogConfig.SPARK_WITH_VIEWS.catalogName()` 与 `@Parameterized.Parameters` 机制，是测试框架既有能力，无需额外引入。
3. 注意 Spark 3.4 与 Spark 3.5 错误信息构建方式不同（3.4 用 `String.format`，3.5 用 `errorClass`），回迁时不要把 3.5 的 errorClass 形式误抄到 3.4。本提交的 diff 已正确处理此差异。
