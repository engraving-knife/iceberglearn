# 提交 0544：Spark 3.4/3.5 SHOW VIEWS 命令使用当前命名空间

## 提交信息

- **序号**：0544 / 4088
- **哈希**：b788b5f9e1d7da1a30cc4ada0509f2febb75b2ac
- **短哈希**：b788b5f9e
- **日期**：2024-02-27（AuthorDate/CommitDate 均 2024-02-27 08:32:18 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4, 3.5: Use current namespace for SHOW VIEWS cmd (#9787)
- **PR/Issue**：#9787。提交说明里引用了 Spark 上游 V1 `ShowViews` 的对齐实现：https://github.com/apache/spark/blob/branch-3.5/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveCatalogs.scala#L39-L44

## 总体目的

本提交修复 Iceberg Spark 视图扩展中 `SHOW VIEWS` 命令的一个行为缺陷：当用户执行 `SHOW VIEWS` 但**未显式指定命名空间**，且此前已通过 `USE <namespace>` 设置了当前命名空间时，命令应当列出当前命名空间下的视图，而不是根命名空间（空命名空间）下的视图。

**问题背景**：Iceberg 的 Spark 视图扩展在 `RewriteViewCommands` 规则中处理 `ShowViews` 逻辑计划。此前的实现有两个 `ShowViews` case 分支：

1. `case ShowViews(UnresolvedNamespace(Seq()), ...)` —— 匹配 `SHOW VIEWS`（未指定命名空间），解析为 `ResolvedNamespace(catalog, Seq.empty)`，即**空命名空间**
2. `case ShowViews(UnresolvedNamespace(CatalogAndNamespace(catalog, ns)), ...)` —— 匹配 `SHOW VIEWS IN cat.ns`（显式指定命名空间），解析为 `ResolvedNamespace(catalog, ns)`

第一个分支的 Bug 在于：即使用户已通过 `USE myns` 设置了当前命名空间，`SHOW VIEWS` 仍会去查空命名空间（`Seq.empty`），而不是 `myns`。这导致用户在 `USE myns` 后执行 `SHOW VIEWS` 看不到 `myns` 下的视图，行为既不符合直觉，也与 Spark V1 `ShowViews` 的处理（`ResolveCatalogs.scala` 中会用当前命名空间）不一致。

本提交的目标是让 `SHOW VIEWS`（不带命名空间时）尊重 `USE <namespace>` 设置的当前命名空间，与 Spark V1 行为对齐，与 `SHOW TABLES` 等命令的语义保持一致。

## 如何达成设计目的

设计思路极为直接：把第一个 case 分支里构造 `ResolvedNamespace` 时传入的 `Seq.empty` 改为 `catalogManager.currentNamespace`。`catalogManager.currentNamespace` 是 Spark `CatalogManager` 维护的"当前命名空间"，由 `USE <namespace>` 语句设置，未设置时默认为 catalog 的默认命名空间（通常是 `default`）。

改动之所以只发生在第一个 case 分支（未指定命名空间的情形），是因为第二个 case 分支（显式指定命名空间 `SHOW VIEWS IN cat.ns`）已经使用用户显式传入的 `ns`，不受此 Bug 影响。

这个修复与 Spark 上游 `ResolveCatalogs.scala` 第 39-44 行对 V1 `ShowViews` 的处理逻辑一致——上游也是在没有指定命名空间时回退到当前命名空间。本提交把 Iceberg 视图扩展的行为对齐到上游 V1 行为，保证用户在 V1 表和 Iceberg 视图上看到一致的 `SHOW VIEWS` 语义。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

**修改目的**：让 `SHOW VIEWS`（不带命名空间）使用当前命名空间。

**工作逻辑**：修改第一个 `ShowViews` case 分支。该分支匹配 `ShowViews(UnresolvedNamespace(Seq()), pattern, output)`，即 `SHOW VIEWS` 未指定命名空间、且当前 catalog 是视图 catalog 的情形。改动前后对比：

```scala
// 改前
case ShowViews(UnresolvedNamespace(Seq()), pattern, output)
  if ViewUtil.isViewCatalog(catalogManager.currentCatalog) =>
  ShowIcebergViews(ResolvedNamespace(catalogManager.currentCatalog, Seq.empty), pattern, output)

// 改后
case ShowViews(UnresolvedNamespace(Seq()), pattern, output)
  if ViewUtil.isViewCatalog(catalogManager.currentCatalog) =>
  ShowIcebergViews(ResolvedNamespace(catalogManager.currentCatalog, catalogManager.currentNamespace),
    pattern, output)
```

- `UnresolvedNamespace(Seq())` 表示用户没有在 `SHOW VIEWS` 语句里写命名空间部分（如 `SHOW VIEWS` 而非 `SHOW VIEWS IN ns`），`Seq()` 是空序列。
- `ViewUtil.isViewCatalog(catalogManager.currentCatalog)` 守卫确保只对 Iceberg 视图 catalog 生效，非视图 catalog 走 Spark 默认逻辑。
- 改前构造 `ResolvedNamespace(catalogManager.currentCatalog, Seq.empty)`，第二个参数是空命名空间，导致后续 `listViews` 在根命名空间下查找。
- 改后构造 `ResolvedNamespace(catalogManager.currentCatalog, catalogManager.currentNamespace)`，第二个参数是当前命名空间（由 `USE <namespace>` 设置），后续 `listViews` 在当前命名空间下查找。

第二个 `ShowViews` case 分支（`SHOW VIEWS IN cat.ns`）保持不变，仍使用用户显式指定的 `ns`。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

**修改目的**：与 Spark 3.4 完全相同。

**工作逻辑**：Spark 3.5 的 `RewriteViewCommands.scala` 在该 case 分支上的代码与 Spark 3.4 完全一致，改动也完全一致——把 `Seq.empty` 改为 `catalogManager.currentNamespace`。两版本同步修复。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：新增测试覆盖 `SHOW VIEWS` 尊重当前命名空间的行为。

**工作逻辑**：新增 `showViewsWithCurrentNamespace` 测试方法。测试步骤：

1. 创建两个命名空间 `show_views_ns1`、`show_views_ns2`。
2. 在 `ns1` 下创建视图 `viewOne`，在 `ns2` 下创建视图 `viewTwo`（两个视图都引用基础表 `NAMESPACE.tableName`）。
3. 构造期望行 `v1 = row(ns1, viewOne, false)`、`v2 = row(ns2, viewTwo, false)`（`false` 表示不是临时视图）。
4. **验证显式命名空间**：`SHOW VIEWS IN cat.ns1` 包含 `v1`、不包含 `v2`；`SHOW VIEWS IN cat.ns2` 包含 `v2`、不包含 `v1`。这部分是基线验证，确认显式指定命名空间始终正确。
5. **验证当前命名空间（核心）**：
   - `USE ns1` 后，`SHOW VIEWS`（不带命名空间）包含 `v1`、不包含 `v2`
   - `SHOW VIEWS LIKE 'viewOne*'`（带 LIKE 模式）包含 `v1`、不包含 `v2` —— 验证模式匹配也尊重当前命名空间
   - `USE ns2` 后，`SHOW VIEWS` 包含 `v2`、不包含 `v1`
   - `SHOW VIEWS LIKE 'viewTwo*'` 包含 `v2`、不包含 `v1`

测试覆盖了三个维度：显式命名空间（基线）、当前命名空间（修复点）、当前命名空间 + LIKE 模式（修复点的延伸）。`catalogName` 变量来自测试类的参数化参数。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：与 Spark 3.4 测试完全相同。

**工作逻辑**：Spark 3.5 的 `TestViews.java` 新增的 `showViewsWithCurrentNamespace` 测试方法与 Spark 3.4 版本逐字相同，覆盖同样的三个维度。

## 小结

**成效**：本提交以最小代价（每个版本 1 行核心改动 + 31 行测试）修复了 `SHOW VIEWS` 不尊重 `USE <namespace>` 设置的当前命名空间的问题。修复后用户在 `USE myns` 后执行 `SHOW VIEWS` 能正确列出 `myns` 下的视图，行为与 Spark V1 `ShowViews` 对齐，与 `SHOW TABLES` 等命令语义一致。Spark 3.4 与 Spark 3.5 同步修复，两版本行为一致。

**影响范围**：仅影响 Spark 3.4/3.5 模块的 `RewriteViewCommands` 中 `SHOW VIEWS`（不带命名空间）的解析行为。对显式指定命名空间的 `SHOW VIEWS IN cat.ns` 无影响，对其它视图命令（CREATE VIEW、DROP VIEW、ALTER VIEW 等）无影响。不涉及序列化格式或 API 兼容性变更。

**回迁到 1.4.x 的注意事项**：

1. 本提交是纯 Bug 修复，无冲突风险，cherry-pick 到 1.4.x 安全。回迁时需同时 cherry-pick Spark 3.4 和 Spark 3.5 两个版本的改动（两版本改动相同）。
2. 回迁前需确认 1.4.x 上的 `RewriteViewCommands.scala` 已有这两个 `ShowViews` case 分支的基线（即 `case ShowViews(UnresolvedNamespace(Seq()), ...) => ShowIcebergViews(ResolvedNamespace(..., Seq.empty), ...)` 已存在）。这些分支由此前引入 Iceberg 视图扩展的提交（如 0327/0335 等 `ResolveViews`/`RewriteViewCommands` 系列）建立。
3. 测试依赖 `catalogName` 参数化变量与 `viewName()`、`row()`、`sql()` 辅助方法，这些是 `SparkExtensionsTestBase`/`TestViews` 既有能力，无需额外引入。
4. 该修复与 0542（Include catalog name in view errors）是同一作者同一时间段对 Iceberg Spark 视图扩展的不同方面改进，两者互不依赖，可独立回迁。但若同时回迁，注意 0542 对 `RewriteViewCommands.invalidRefToTempObject` 的改动与本提交对 `ShowViews` case 的改动不在同一代码区域，不会冲突。
