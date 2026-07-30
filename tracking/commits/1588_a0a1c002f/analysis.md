# 提交 1588：Spark 3.4: Add view support to SparkSessionCatalog (#11797)

## 提交信息

- **序号**：1588
- **哈希**：a0a1c002f01d70b428c63b4802fae536e543cda6
- **短哈希**：a0a1c002f
- **日期**：2025-01-15（Wed Jan 15 17:26:08 2025 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Add view support to SparkSessionCatalog (#11797)
- **PR/Issue**：#11797

## 总体目的

本提交为 Spark 3.4 模块的 `SparkSessionCatalog` 添加完整的 View（视图）支持，使 `SparkSessionCatalog`（Iceberg 作为 Spark 内置 `spark_catalog` 的"会话 catalog 扩展"）能够像独立的 `SparkCatalog` 一样管理 Iceberg 视图。

背景：Spark 的 catalog 体系中有两种角色：
- `SparkCatalog`：Iceberg 自有的 catalog 实现，直接作为某个命名 catalog（如 `spark_with_views`）注册，此前已实现 `ViewCatalog` + `SupportsReplaceView` 接口，支持视图的创建/加载/替换/修改/删除/重命名。
- `SparkSessionCatalog`：作为 `CatalogExtension` 包装 Spark 内置的 session catalog（即 `spark_catalog`），把 Parquet/Avro/ORC 表的创建"劫持"为 Iceberg 表，同时把 Iceberg 表的操作代理给内部的 `icebergCatalog`。它此前**不**实现 `ViewCatalog`，因此用户用 `spark_catalog` 时无法创建/操作 Iceberg 视图，只能切到独立的 `SparkCatalog`。

随着视图能力在 Spark 3.4 生态中成熟，社区希望 `spark_catalog` 也能直接管理 Iceberg 视图，避免用户在两套 catalog 之间切换。本提交把 `ViewCatalog` 相关接口实现下沉到 `BaseCatalog` 基类，并让 `SparkSessionCatalog` 实现完整的 `ViewCatalog` + `SupportsReplaceView` 接口，所有视图操作优先走 Iceberg 的 `asViewCatalog`，若 Iceberg catalog 不支持视图则回退到 session catalog（如果 session catalog 本身是 `ViewCatalog`）。

此外顺带修正了 `SmokeTest.showView` 中的一个测试顺序问题（原来先 DROP 再 CREATE+SHOW，应改为 CREATE+SHOW 后再 DROP），并新增 `SPARK_SESSION_WITH_VIEWS` 测试配置，把 `SparkSessionCatalog` 纳入 `TestViews` 参数化矩阵。

## 如何达成设计目的

整体思路是把视图接口实现从 `SparkCatalog` 上移到 `BaseCatalog` 基类（这样 `SparkSessionCatalog` 作为 `BaseCatalog` 子类自动获得 `ViewCatalog` 类型声明），然后 `SparkSessionCatalog` 自行实现 `ViewCatalog` 的方法体，通过 `asViewCatalog`（Iceberg catalog 的视图能力）与 `getSessionCatalog()`（Spark 内置 session catalog 的视图能力）两层回退来调度。

### 修改详情

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/BaseCatalog.java`

**修改目的**：把 `ViewCatalog` 和 `SupportsReplaceView` 接口上移到基类。

**工作逻辑**：
```java
abstract class BaseCatalog
    implements CatalogPlugin, ... , SupportsFunctions,
        ViewCatalog,            // 新增
        SupportsReplaceView {   // 新增
```
这样所有 `BaseCatalog` 子类（`SparkCatalog`、`SparkSessionCatalog`）在类型系统上都声明为视图 catalog。注意接口上移只是类型声明，具体方法实现仍由各子类提供（`SparkCatalog` 原本就实现了，`SparkSessionCatalog` 本次新增实现）。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：去掉重复的接口声明（已上移到 `BaseCatalog`）。

**工作逻辑**：
```java
// 旧
public class SparkCatalog extends BaseCatalog
    implements org.apache.spark.sql.connector.catalog.ViewCatalog, SupportsReplaceView {
// 新
public class SparkCatalog extends BaseCatalog {
```
`SparkCatalog` 原有方法实现不变，仅去掉冗余的 `implements`。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSessionCatalog.java`

**修改目的**：让 `SparkSessionCatalog` 真正支持视图操作。

**主要变更**：
1. **泛型约束收紧**：类型参数 `T` 从 `extends TableCatalog & FunctionCatalog & SupportsNamespaces` 扩展为 `extends TableCatalog & FunctionCatalog & SupportsNamespaces & ViewCatalog`。这要求被包装的 session catalog 本身也是 `ViewCatalog`（Spark 3.4 的内置 session catalog 满足此条件）。
2. **新增字段 `asViewCatalog`**（`ViewCatalog` 类型），在 `initialize()` 中初始化：
   ```java
   if (icebergCatalog instanceof ViewCatalog) {
     this.asViewCatalog = (ViewCatalog) icebergCatalog;
   }
   ```
   即若内部的 Iceberg catalog 支持视图，则缓存其 `ViewCatalog` 视图。
3. **新增辅助方法 `isViewCatalog()`**：判断 session catalog 是否是 `ViewCatalog`，用于回退路径。
4. **实现 7 个 `ViewCatalog`/`SupportsReplaceView` 方法**，每个方法遵循"先 Iceberg、后 session catalog、再抛异常"的三段式：
   - `listViews(namespace)`：若 `asViewCatalog != null` 则用它列视图；否则若 session catalog 是 ViewCatalog 则委托 session catalog；若都没有返回空数组。
   - `loadView(ident)`：若 `asViewCatalog` 存在且该视图存在于 Iceberg，则从 Iceberg 加载；否则若 session catalog 是 ViewCatalog 且视图存在，从 session catalog 加载；否则抛 `NoSuchViewException`。
   - `createView(...)`：优先 `asViewCatalog.createView`，否则若 session catalog 是 ViewCatalog 则委托，否则抛 `UnsupportedOperationException`。
   - `replaceView(...)`：仅当 `asViewCatalog instanceof SupportsReplaceView` 时才替换；否则抛 `UnsupportedOperationException`。注意这里**不**回退到 session catalog——因为 session catalog 不实现 `SupportsReplaceView`（替换视图是 Iceberg 扩展能力）。
   - `alterView(ident, changes)`：若 Iceberg 持有该视图则改 Iceberg；否则若 session catalog 是 ViewCatalog 则委托；否则抛异常。
   - `dropView(ident)`：若 Iceberg 持有则删 Iceberg；否则若 session catalog 是 ViewCatalog 则委托；否则返回 false。
   - `renameView(from, to)`：若 Iceberg 持有 from 则在 Iceberg 内重命名；否则若 session catalog 是 ViewCatalog 则委托；否则抛异常。
   
   这一调度逻辑确保：Iceberg 视图由 Iceberg catalog 管理，非 Iceberg（如 Spark 内置 V1 view）由 session catalog 管理，二者共存且不互相干扰。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java`

**修改目的**：新增测试用 catalog 配置 `SPARK_SESSION_WITH_VIEWS`。

**工作逻辑**：
```java
SPARK_SESSION_WITH_VIEWS(
    "spark_catalog",
    SparkSessionCatalog.class.getName(),
    ImmutableMap.of("type", "rest", "default-namespace", "default", "cache-enabled", "false"));
```
该配置以 `spark_catalog` 为名注册一个 `SparkSessionCatalog`，底层 Iceberg catalog 类型为 `rest`，使其成为 `TestViews` 参数化测试的一个维度。注意它用 `spark_catalog` 作为 catalogName，与 `SPARK_WITH_VIEWS`（独立 catalog）形成对照。

#### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：把 `SPARK_SESSION_WITH_VIEWS` 纳入参数化测试矩阵，并针对 session catalog 的行为差异调整若干测试。

**主要变更**：
1. **参数化矩阵新增一项**：`SPARK_SESSION_WITH_VIEWS`，并在 properties 中追加 `CatalogProperties.URI = REST_SERVER_RULE.uri()`（因为 REST catalog 需要 URI）。
2. **`@Before` 建表语句改造**：对 `spark_catalog`（session catalog）建表时显式加 `USING iceberg`，因为 session catalog 默认会把表建成 V1 Parquet 表；并改用 `NAMESPACE.tableName` 全限定名，再 `USE catalogName.NAMESPACE`。
3. **`@After` 删表**改用全限定名。
4. **多个测试用 `assumeThat(catalogName).isNotEqualTo(SPARK_CATALOG)` 跳过**：`rewriteFunctionIdentifier`、`rewriteFunctionIdentifierWithNamespace`、`fullFunctionIdentifier`、`createViewWithRecursiveCycleToV1View` 等。原因是 session catalog 下不存在 `system` namespace，或递归 V1 view 场景在 session catalog 下行为不同。
5. **错误消息适配**：`createOrReplaceViewWithTempFunctionUsedInQuery` 等测试中，session catalog 下函数找不到的错误消息不同（V1 函数 `[ROUTINE_NOT_FOUND] The function ... cannot be found`），用条件分支选择期望消息。
6. **跨 catalog rename 测试改造**：原 `renameViewToDifferentCatalog` 固定用 `spark_catalog` 作为目标，现在根据当前 catalogName 动态选择目标 catalog（若当前是 spark_catalog 则目标改为 `SPARK_WITH_VIEWS`，反之亦然），并参数化错误消息。
7. **删除 `dropV1View` 测试**：该测试与 session catalog 场景冲突或不再适用。
8. **location 期望值动态化**：原 `SHOW CREATE TABLE` / `DESCRIBE EXTENDED` 测试硬编码 `location = '/NAMESPACE/viewName'`，现在改为先 `viewCatalog().loadView(...).location()` 取实际 location 再填入期望，适配 REST catalog 下 location 不再是简单路径的情况。
9. **多处 `sql("USE spark_catalog")` 块加 `if (!catalogName.equals(SPARK_CATALOG))` 守卫**：当测试本身就在 `spark_catalog` 下运行时，无需切换且期望的"切到 spark_catalog 后找不到表"断言不成立，跳过。

#### `spark/v3.4/spark-runtime/src/integration/java/org/apache/iceberg/spark/SmokeTest.java` 与 `spark/v3.5/.../SmokeTest.java`

**修改目的**：修复 `showView` 测试的语句顺序。

**工作逻辑**：
```java
// 旧
sql("DROP VIEW IF EXISTS %s", "test");
sql("CREATE VIEW %s AS SELECT 1 AS id", "test");
assertThat(sql("SHOW VIEWS")).contains(row("default", "test", false));
// 新
sql("CREATE VIEW %s AS SELECT 1 AS id", "test");
assertThat(sql("SHOW VIEWS")).contains(row("default", "test", false));
sql("DROP VIEW %s", "test");
```
原来先 DROP 后 CREATE+SHOW，DROP 在测试开头执行无意义（视图可能不存在），且测试结束后留下未清理的视图。新顺序是 CREATE → SHOW 验证 → DROP 清理，更正确。这是顺带修的测试 hygiene 问题。

## 小结

- **成效**：`SparkSessionCatalog` 现在完整支持 Iceberg 视图的 CRUD（创建、加载、替换、修改、删除、重命名），用户在使用 `spark_catalog` 时可直接创建和管理 Iceberg 视图，无需切换到独立 catalog。视图操作通过 `asViewCatalog`（Iceberg）与 session catalog 两层回退，正确区分 Iceberg 视图与 Spark V1 视图。`SparkCatalog` 与 `SparkSessionCatalog` 的视图接口声明统一到 `BaseCatalog`，结构更清晰。
- **影响范围**：Spark 3.4（主）与 Spark 3.5（SmokeTest 顺带修）模块。`BaseCatalog`、`SparkCatalog`、`SparkSessionCatalog` 三个生产类有变更，其中 `SparkSessionCatalog` 新增约 140 行视图方法实现。测试侧 `TestViews` 大幅改造以覆盖 session catalog 场景。这是一次**功能性增强**，扩展了 `spark_catalog` 的能力面。
- **回迁到 1.4.x 的注意事项**：本提交是 Spark 3.4 视图功能补全，**应回迁**到 1.4.x（1.4.x 支持 Spark 3.4）。回迁后 1.4.x 用户用 `spark_catalog` 即可操作 Iceberg 视图，体验与 main 一致。需注意：
  1. 1.4.x 的 `SparkSessionCatalog` 泛型约束变更可能影响下游自定义 session catalog 包装器（要求被包装者实现 `ViewCatalog`），需评估兼容性。
  2. `BaseCatalog` 接口上移是二进制兼容的接口扩展（新增 implements），但若下游有按旧接口集编译的代码需重新编译。
  3. 测试侧改动较大，回迁时需同步 `SparkCatalogConfig` 与 `TestViews`。
  4. 若 1.4.x 的 Spark 3.4 集成对视图支持有不同基线，需对照确认。
