# 提交 0537：Spark: Include catalog name in view errors

## 提交信息

- **序号**：0537 / 4088
- **哈希**：f14c0e55361f89dc5cfff3640b0e612befaa4d98
- **短哈希**：f14c0e553
- **日期**：2024-02-26 10:54:18 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Include catalog name in view errors (#9807)
- **PR/Issue**：#9807

## 总体目的

本提交解决 Iceberg Spark 视图扩展在报错信息中遗漏 catalog 名称的问题。在此之前的实现里，视图相关错误信息（包括 `CheckViews` 的列数不匹配错误，以及 `RewriteViewCommands` 的临时对象引用错误）只展示 `namespace.viewName`，没有带上 catalog 名。

在多 catalog 环境（例如同时配置了 `iceberg_cat`、`hive_cat`、`spark_cat` 等多个 catalog，且各 catalog 下都可能有同名 namespace 和同名视图）下，仅凭 `namespace.viewName` 无法唯一定位是哪个 catalog 里的视图出了问题。用户看到 `Cannot create view ns.view_xxx ...` 时，需要额外上下文才能判断报错来源，排错成本高。本次提交把 catalog 名称补进错误信息，使错误信息自包含、可定位，改善多 catalog 场景下的可调试性。

## 如何达成设计目的

设计思路是复用已经解析好的 `ResolvedIdentifier`（它同时持有 catalog 实例和 identifier），在生成错误信息时从 `ResolvedIdentifier` 中取出 `catalog.name()` 拼到视图名前面，形成 `catalog.namespace.viewName` 的三段式全限定名。为此需要把原先只传 `Identifier` 的方法签名升级为传 `ResolvedIdentifier`，让 catalog 信息一路透传到错误信息生成处。改动很小且自洽，不引入新的解析步骤。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala`

**修改目的**：让列数不匹配错误的 `viewName` 参数带上 catalog 名。

**工作逻辑**：

- 模式匹配从 `case CreateIcebergView(ResolvedIdentifier(_: ViewCatalog, ident), ...)` 改为 `case CreateIcebergView(resolvedIdent@ResolvedIdentifier(_: ViewCatalog, _), ...)`，用 `@` 绑定整个 `ResolvedIdentifier` 而不是只取出 `ident: Identifier`，这样后续能拿到 catalog。

- `verifyColumnCount` 形参类型从 `ident: Identifier` 改为 `ident: ResolvedIdentifier`。

- 两个 `CREATE_VIEW_COLUMN_ARITY_MISMATCH` 错误的 `viewName` 参数从 `ident.toString` 改为 `String.format("%s.%s", ident.catalog.name(), ident.identifier)`，即 `catalog名.identifier`。注意这里 `ident.identifier` 本身已是 `namespace.viewName` 的形式（Iceberg 的 `Identifier.toString`），拼接后得到完整的 `catalog.namespace.viewName`。

- 移除不再使用的 `import org.apache.spark.sql.connector.catalog.Identifier`。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

**修改目的**：让临时对象引用错误的视图名带上 catalog 名。

**工作逻辑**：

- 调用点 `verifyTemporaryObjectsDontExist(resolved.identifier, q)` 改为 `verifyTemporaryObjectsDontExist(resolved, q)`，直接传整个 `ResolvedIdentifier` 而不是只取其中的 `identifier` 字段。

- `verifyTemporaryObjectsDontExist` 形参从 `name: Identifier` 改为 `identifier: ResolvedIdentifier`，内部调用 `invalidRefToTempObject` 时也改传 `identifier`。

- `invalidRefToTempObject` 形参从 `name: Identifier` 改为 `identifier: ResolvedIdentifier`，错误信息从：
  ```
  Cannot create view %s that references temporary %s: %s
  ```
  改为：
  ```
  Cannot create view %s.%s that references temporary %s: %s
  ```
  其中 `identifier.catalog.name()` 提供 catalog 名，`identifier.identifier` 提供 `namespace.viewName`，拼起来就是 `catalog.namespace.viewName`。

- 移除不再使用的 `import org.apache.spark.sql.connector.catalog.Identifier`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：更新所有视图错误断言，匹配新的三段式视图名。

**工作逻辑**：共 9 处断言从 `String.format("Cannot create view %s.%s", NAMESPACE, viewName)` 改为 `String.format("Cannot create view %s.%s.%s", catalogName, NAMESPACE, viewName)`，覆盖：
- 临时视图引用错误（`createViewReferencingTempView`、`createViewReferencingGlobalTempView`、CTE 引用临时视图的两个测试）
- 临时函数引用错误（`createViewReferencingTempFunction`、CTE 引用临时函数、子查询引用临时函数）
- 列数不匹配错误（`not enough data columns`、`too many data columns` 两个分支）

所有断言都额外补上 `catalogName`，与新错误信息一致。

## 小结

本提交把 Iceberg Spark 3.5 视图扩展的错误信息从 `namespace.viewName` 升级为 `catalog.namespace.viewName`，使多 catalog 环境下的错误可定位性显著提升。改动面小（3 个文件，核心是把 `Identifier` 形参升级为 `ResolvedIdentifier` 以拿到 catalog），无行为语义变化，仅影响错误信息文本。

**与 0536 的关联**：0536（Spark 3.4 回port）和 0537（Spark 3.5 主线）都改动了 `RewriteViewCommands.invalidRefToTempObject`。0536 在 Spark 3.4 上把这个方法从老式 `String.format("Cannot create view %s ...", name, ...)` 形式（`name` 是 `Identifier`）建立起来；0537 紧随其后在 Spark 3.5 上把同一个方法的形参从 `Identifier` 升级为 `ResolvedIdentifier` 并补上 catalog 名。两个提交是同一作者同一时间段的工作，0537 是对 0536 错误信息质量的进一步打磨。本提交仅作用于 `spark/v3.5/`，未同步到 `spark/v3.4/`。

**回迁到 1.4.x 的注意事项**：1.4.x 若要回迁此改进，需同时确认 1.4.x 上的 `CheckViews` 与 `RewriteViewCommands` 已具备 0536 的基线（即 `invalidRefToTempObject` 已存在）。回迁时要注意 Spark 3.5 用 `errorClass` 错误类机制（`CREATE_VIEW_COLUMN_ARITY_MISMATCH.NOT_ENOUGH_DATA_COLUMNS` 等），而 Spark 3.4 用老式 `String.format`，两版本错误信息构建方式不同，不能直接照搬。测试侧需保证测试基类提供了 `catalogName` 变量。
