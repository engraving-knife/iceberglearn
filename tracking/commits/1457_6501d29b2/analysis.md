# 提交 1457：Spark: Add view support to SparkSessionCatalog (#11388)

## 提交信息

- **序号**：1457 / 4088
- **哈希**：6501d29b2d46c8d57f46ad646e2daf7d8865f646
- **短哈希**：6501d29b2
- **日期**：2024-12-03（Tue Dec 3 18:54:47 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Add view support to SparkSessionCatalog (#11388)
- **PR/Issue**：#11388

## 总体目的

Iceberg 的 Spark 集成中有两个 Catalog 入口：
- `SparkCatalog`：直接作为独立 catalog 注册（如 `spark.sql.catalog.my_catalog = ...SparkCatalog`），已实现 `ViewCatalog` + `SupportsReplaceView`，支持 view 的增删改查；
- `SparkSessionCatalog`：作为 Spark 内置 session catalog（`spark_catalog`）的**扩展/代理**（实现 `CatalogExtension`），把 Parquet/Avro/ORC 等格式的建表"劫持"为 Iceberg 表，其余操作委托给 Spark 原生 session catalog。它此前**不支持 view**——view 相关操作直接走 Spark 原生 session catalog（V1 view），无法与 Iceberg 的 view 实现互通。

Spark 3.5 在 DSv2 catalog plugin API 中引入了 `ViewCatalog` 接口（`listViews/loadView/createView/replaceView/alterView/dropView/renameView`）。本提交让 `SparkSessionCatalog` 也实现 `ViewCatalog`，使其能：

1. 当被代理的 Iceberg catalog（`icebergCatalog`）本身是 `ViewCatalog` 时（如 REST Catalog、JDBC Catalog 等），把 view 操作转发给 Iceberg catalog，实现 Iceberg view 的统一管理；
2. 否则回退到 Spark 原生 session catalog 的 view 能力（若 session catalog 也实现了 `ViewCatalog`）；
3. 都不支持时，对 `createView`/`replaceView` 等抛 `UnsupportedOperationException`。

这样当用户配置 `spark.sql.catalog.spark_catalog = SparkSessionCatalog`（即把 `spark_catalog` 交给 Iceberg 扩展）时，`CREATE VIEW`/`DROP VIEW`/`ALTER VIEW` 等 SQL 也能路由到 Iceberg 的 view 实现中，与 `SparkCatalog` 的行为对齐。

提交说明中的 "Don't replace view non-atomically" 指第二个 commit 的修正：`replaceView` 只在 Iceberg catalog 实现了 `SupportsReplaceView` 时才走原子替换，否则抛 `UnsupportedOperationException`，而非用"先 drop 再 create"的非原子方式替代——避免替换过程中出现 view 短暂不存在的窗口。

## 如何达成设计目的

通过四个层面的改动：

1. **`BaseCatalog` 上移 view 接口**：把 `ViewCatalog` 与 `SupportsReplaceView` 从 `SparkCatalog` 的 `implements` 上移到基类 `BaseCatalog`，使 `SparkSessionCatalog`（也继承 `BaseCatalog`）自动获得这两个接口声明。`SparkCatalog` 去掉显式 `implements`，行为不变。
2. **`SparkSessionCatalog` 泛型约束加 `ViewCatalog`**：类型参数 `T` 增加 `& ViewCatalog` 约束，表明被代理的 session catalog 必须是 `ViewCatalog`（Spark 3.5 的 session catalog 已实现该接口）。
3. **`SparkSessionCatalog` 实现 7 个 view 方法**：每个方法按"先 Iceberg catalog、再 session catalog、否则不支持"的优先级路由。
4. **测试基础设施**：新增 `SPARK_SESSION_WITH_VIEWS` 测试配置（用 `SparkSessionCatalog` + REST catalog），并在 `TestViews` 中加入该参数化配置，调整部分测试以兼容 session catalog 的差异。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/BaseCatalog.java`（修改，+2 行）

**修改目的**：把 view 相关接口声明上移到基类，使 `SparkSessionCatalog` 也声明为 `ViewCatalog`。

**工作逻辑**：

```java
abstract class BaseCatalog
    implements ProcedureCatalog, SupportsNamespaces, HasIcebergCatalog, SupportsFunctions,
        ViewCatalog,            // 新增
        SupportsReplaceView {   // 新增
```

`SupportsReplaceView` 是 Spark 3.5 的接口，声明 `replaceView` 方法，用于原子替换 view。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`（修改，-2 行）

**修改目的**：去除冗余的 `implements`，因基类已声明。

```java
-public class SparkCatalog extends BaseCatalog
-    implements org.apache.spark.sql.connector.catalog.ViewCatalog, SupportsReplaceView {
+public class SparkCatalog extends BaseCatalog {
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSessionCatalog.java`（修改，+151 行）

**修改目的**：让 session catalog 代理能路由 view 操作到 Iceberg catalog 或 session catalog。

**工作逻辑**：

1. **泛型约束**：
```java
public class SparkSessionCatalog<
        T extends TableCatalog & FunctionCatalog & SupportsNamespaces & ViewCatalog>
    extends BaseCatalog implements CatalogExtension {
```
增加 `& ViewCatalog`，确保 `T sessionCatalog` 有 view 能力。

2. **新增字段与初始化**：
```java
private ViewCatalog asViewCatalog = null;
```
在 `initialize` 中，若 `icebergCatalog instanceof ViewCatalog`，则赋值 `asViewCatalog`。

3. **辅助方法**：
```java
private boolean isViewCatalog() {
  return getSessionCatalog() instanceof ViewCatalog;
}
```

4. **7 个 ViewCatalog 方法实现**，每个遵循统一路由策略：

- **`listViews(namespace)`**：优先 `asViewCatalog.listViews`，其次 `sessionCatalog.listViews`，都不行返回空数组。`NoSuchNamespaceException` 包装为 `RuntimeException`。
- **`loadView(ident)`**：优先 Iceberg（`asViewCatalog.viewExists` 时加载），其次 session catalog，都不存在抛 `NoSuchViewException`。
- **`createView(...)`**：优先 Iceberg，其次 session catalog，都不支持抛 `UnsupportedOperationException`。
- **`replaceView(...)`**：**仅当 `asViewCatalog instanceof SupportsReplaceView` 时**走 Iceberg 的原子替换；否则抛 `UnsupportedOperationException`。这就是 "Don't replace view non-atomically" 的体现——不退化为 drop+create。
- **`alterView(ident, changes)`**：优先 Iceberg（view 存在时），其次 session catalog，否则抛 `UnsupportedOperationException`。
- **`dropView(ident)`**：优先 Iceberg，其次 session catalog，都不匹配返回 `false`。
- **`renameView(from, to)`**：优先 Iceberg，其次 session catalog，否则抛 `UnsupportedOperationException`。

路由优先级的设计意图：Iceberg catalog 管理的 view 优先由 Iceberg 处理（保持与 `SparkCatalog` 一致的行为），只有 Iceberg catalog 不支持 view 或 view 不在 Iceberg catalog 中时，才回退到 Spark 原生 session catalog。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java`（修改，+5 行）

**修改目的**：新增测试用 catalog 配置 `SPARK_SESSION_WITH_VIEWS`。

```java
SPARK_SESSION_WITH_VIEWS(
    "spark_catalog",
    SparkSessionCatalog.class.getName(),
    ImmutableMap.of("type", "rest", "default-namespace", "default", "cache-enabled", "false"));
```

该配置以 `spark_catalog` 为名注册 `SparkSessionCatalog`，底层 Iceberg catalog 类型为 `rest`，用于在测试中验证 session catalog 的 view 路由。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`（修改，+90/-37 行）

**修改目的**：把 `SPARK_SESSION_WITH_VIEWS` 纳入参数化测试，并调整现有测试以兼容 session catalog 的差异。

**主要调整**：

1. **参数化配置新增 `SPARK_SESSION_WITH_VIEWS`**：在 `@Parameters` 中追加一组配置，动态注入 REST catalog 的 URI。
2. **`before()` 适配**：当 catalog 名为 `spark_catalog` 时，建表语句需显式 `USING iceberg`（否则 Spark session catalog 默认用 Hive/Parquet），且 `USE` 语句需带 namespace。
3. **`removeTable()` 清理**：增加 `spark.sessionState().catalogManager().reset()` 与 `spark.conf().unset("spark.sql.catalog.spark_catalog")`，避免 session catalog 配置污染后续测试。
4. **错误消息适配**：session catalog 加载函数失败时的错误消息与 `SparkCatalog` 不同（V1 函数路径），测试中按 `catalogName` 分别断言。
5. **`assumeThat` 跳过不适用测试**：`rewriteFunctionIdentifier`、`rewriteFunctionIdentifierWithNamespace`、`fullFunctionIdentifier` 等测试依赖 `system` 命名空间下的函数（如 `iceberg_version()`、`system.bucket`），而 `SparkSessionCatalog` 下 `system` namespace 不存在，用 `assumeThat(catalogName).isNotEqualTo(SPARK_CATALOG)` 跳过。
6. **删除 `dropV1View` 测试**：该测试专门验证 V1 view 删除不被干扰，在 session catalog 支持 Iceberg view 后不再适用。
7. **跨 catalog rename 测试适配**：`renameViewToDifferentCatalog` 测试中，当 catalog 是 `spark_catalog` 时，目标 catalog 改为 `spark_with_views`（反之亦然），保持"跨 catalog"语义。
8. **`USE spark_catalog` 检查跳过**：当 catalog 本身就是 `spark_catalog` 时，部分测试中"切换到 spark_catalog 验证 view 不可见"的步骤需跳过。

## 小结

- **成效**：`SparkSessionCatalog`（Iceberg 对 Spark session catalog 的扩展代理）现在实现了 `ViewCatalog` 与 `SupportsReplaceView`，能把 view 操作路由到底层 Iceberg catalog（如 REST/JDBC）或 Spark 原生 session catalog。用户在 `spark_catalog` 下使用 `CREATE VIEW`/`DROP VIEW`/`ALTER VIEW`/`RENAME VIEW` 等 SQL 时，view 可由 Iceberg 统一管理，与独立 `SparkCatalog` 的 view 行为一致。`replaceView` 仅走原子替换，避免非原子窗口。
- **影响范围**：Spark 3.5 模块。`BaseCatalog` 接口声明变更影响所有子类（`SparkCatalog`、`SparkSessionCatalog`），但 `SparkCatalog` 行为不变（只是 `implements` 上移）。`SparkSessionCatalog` 新增约 150 行 view 方法实现。测试侧新增一组参数化配置并调整若干测试。依赖 Spark 3.5 的 `ViewCatalog`/`SupportsReplaceView` API，不适用于 3.3/3.4。
- **回迁到 1.4.x 的注意事项**：本提交强依赖 Spark 3.5 的 `ViewCatalog`/`SupportsReplaceView` 接口，仅适用于 `spark/v3.5` 模块，不可回迁到 3.3/3.4 模块。回迁到 1.4.x 的 `spark/v3.5` 时需确认：① 1.4.x 的 `BaseCatalog`/`SparkCatalog` 结构与 main 一致（`SparkCatalog` 此前显式 `implements ViewCatalog, SupportsReplaceView`）；② 1.4.x 的 `SparkSessionCatalog` 泛型 `T` 此前不含 `ViewCatalog` 约束，回迁后需确认被代理的 session catalog（Spark 3.5 的 `HiveSessionCatalog`/`V2SessionCatalog`）确实实现了 `ViewCatalog`；③ 测试配置 `SPARK_SESSION_WITH_VIEWS` 依赖 REST catalog 测试基础设施（`restCatalog` 在 `ExtensionsTestBase` 中启动），1.4.x 需具备同等基础设施；④ 部分测试调整（`assumeThat` 跳过、错误消息适配）需同步回迁，否则参数化测试会在 `spark_catalog` 配置下失败。
