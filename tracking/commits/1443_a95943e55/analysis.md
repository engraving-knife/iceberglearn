# 提交 1443：Core: Propagate custom metrics reporter when table is created/replaced through Transaction (#11671)

## 提交信息

- **序号**：1443
- **哈希**：a95943e5561c78c18062852e7f8027a191562e08
- **短哈希**：a95943e55
- **日期**：2024-11-28（Thu Nov 28 15:59:42 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Propagate custom metrics reporter when table is created/replaced through Transaction (#11671)
- **PR/Issue**：#11671

## 总体目的

Iceberg 的 `BaseMetastoreCatalog` 中有一个 `metricsReporter()` 方法，会按 catalog 属性 `metrics-reporter-impl` 加载自定义 `MetricsReporter`，并在 `loadTable`/`registerTable` 等返回 `BaseTable` 的路径上把 reporter 传入，使表上发生的 commit/scan 操作能被自定义 reporter 上报。然而，通过 `Transaction` 路径创建或替换表（`createTransaction`、`createOrReplaceTransaction`、`replaceTransaction`）时，并未把 catalog 加载到的 reporter 透传给 `BaseTransaction`，导致通过这些事务接口提交的 commit 不会触发用户配置的自定义 metrics reporter，commit metrics 出现"漏报"。

本提交修复该缺陷：在 `BaseMetastoreCatalog` 的 `createTransaction`、`createOrReplaceTransaction`、`replaceTransaction` 三个调用点把 `metricsReporter()` 透传给 `Transactions` 工厂方法；并在 `Transactions` 中新增带 `MetricsReporter` 参数的 `createOrReplaceTableTransaction` 重载（其他两个重载此前已存在）。同时补全 `InMemoryCatalog` 缺失的 `properties()` 实现，使 `BaseMetastoreCatalog.metricsReporter()` 能从 `InMemoryCatalog` 取到 catalog 属性，从而让 `InMemoryCatalog` 也能正确加载并传播自定义 reporter。

为防止回归，本提交还把此前分散在 `TestJdbcCatalog`、`TestRESTCatalog` 中的自定义 reporter 测试抽到公共基类 `CatalogTests` 中，扩展为对四种事务/直接 commit 路径（`newFastAppend`、`createTransaction`、`createOrReplaceTransaction`、`replaceTransaction`）和 scan 路径的完整覆盖，并要求所有 `CatalogTests` 子类实现 `initCatalog(catalogName, additionalProperties)`，以便用 `metrics-reporter-impl` 属性构造带自定义 reporter 的 catalog 实例。

## 如何达成设计目的

1. **核心修复**：在 `BaseMetastoreCatalog.BaseMetastoreCatalogTableBuilder` 的 `createTransaction()`、`createOrReplaceTransaction()`、`replaceTransaction()` 中，调用 `Transactions.createTableTransaction/createOrReplaceTableTransaction/replaceTableTransaction` 时把 `metricsReporter()` 作为额外参数传入。`metricsReporter()` 已存在，会从 `properties()` 读取 `CatalogProperties.METRICS_REPORTER_IMPL` 反射加载 reporter。
2. **工厂方法补齐**：`Transactions` 此前已有 `createTableTransaction(..., MetricsReporter)` 和 `replaceTableTransaction(..., MetricsReporter)` 重载，但缺少 `createOrReplaceTableTransaction(..., MetricsReporter)` 重载。本提交补上该重载，内部委托给 `new BaseTransaction(tableName, ops, TransactionType.CREATE_OR_REPLACE_TABLE, start, reporter)`。
3. **InMemoryCatalog 补 properties()**：`BaseMetastoreCatalog.metricsReporter()` 依赖 `properties()` 方法返回 catalog 属性；`InMemoryCatalog` 此前未覆盖 `properties()`（继承的是基类默认实现，可能返回空 map），导致通过 InMemoryCatalog 也无法加载到 reporter。本提交在 `InMemoryCatalog` 中保存 `catalogProperties` 字段（`initialize` 时 `ImmutableMap.copyOf(properties)`），并覆盖 `properties()` 返回该字段。
4. **测试上提与统一**：在 `CatalogTests` 抽象基类中新增抽象方法 `initCatalog(String catalogName, Map<String,String> additionalProperties)`，所有子类（`TestInMemoryCatalog`、`TestJdbcCatalog`、`TestJdbcCatalogWithV1Schema`、`TestRESTCatalog`、`TestHiveCatalog`、`TestNessieCatalog`、`RESTCompatibilityKitCatalogTests`）改为覆盖该方法，统一暴露"用额外属性初始化 catalog"的能力。同时把原先 `TestJdbcCatalog`、`TestRESTCatalog` 中的局部 `testCatalogWithCustomMetricsReporter` 测试搬到 `CatalogTests`，扩展为对 `newFastAppend`、`createTransaction().newFastAppend()`、`createOrReplaceTransaction().newFastAppend()`、`replaceTransaction().newFastAppend()` 四种 commit 路径，外加 `newScan()` scan 路径的 reporter 调用断言。`CustomMetricsReporter` 也改为分别用 `SCAN_COUNTER` 和 `COMMIT_COUNTER` 区分 scan 与 commit 上报。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetastoreCatalog.java`

**修改目的**：在事务创建路径上传播 catalog 的 metrics reporter。

**工作逻辑**：
- `createTransaction()`：`Transactions.createTableTransaction(identifier.toString(), ops, metadata, metricsReporter())`。
- `createOrReplaceTransaction()` 中 `orCreate=true` 分支：`Transactions.createOrReplaceTableTransaction(identifier.toString(), ops, metadata, metricsReporter())`。
- `replaceTransaction()` 中 `orCreate=false` 分支：`Transactions.replaceTableTransaction(identifier.toString(), ops, metadata, metricsReporter())`。

### `core/src/main/java/org/apache/iceberg/Transactions.java`

**修改目的**：补齐带 reporter 的 `createOrReplaceTableTransaction` 重载。

**工作逻辑**：新增静态方法 `createOrReplaceTableTransaction(String tableName, TableOperations ops, TableMetadata start, MetricsReporter reporter)`，实现为 `new BaseTransaction(tableName, ops, TransactionType.CREATE_OR_REPLACE_TABLE, start, reporter)`。其余两个重载（`createTableTransaction`、`replaceTableTransaction` 的 reporter 版本）此前已存在，未改动。

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java`

**修改目的**：让 `InMemoryCatalog` 也支持 `metrics-reporter-impl`。

**工作逻辑**：
- 新增字段 `private Map<String, String> catalogProperties;`。
- `initialize(String name, Map<String,String> properties)` 中赋值 `this.catalogProperties = ImmutableMap.copyOf(properties);`。
- 覆盖 `protected Map<String, String> properties()`，返回 `catalogProperties == null ? ImmutableMap.of() : catalogProperties`。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：把自定义 reporter 测试上提到公共基类并扩展覆盖范围。

**工作逻辑**：
- 新增抽象方法 `protected abstract C initCatalog(String catalogName, Map<String, String> additionalProperties);`。
- 新增测试 `testCatalogWithCustomMetricsReporter`：用 `initCatalog` + `METRICS_REPORTER_IMPL=CustomMetricsReporter` 构造 catalog；对 `newFastAppend`、`createTransaction().newFastAppend()`、`createOrReplaceTransaction().newFastAppend()`、`replaceTransaction().newFastAppend()` 四条 commit 路径分别断言 `COMMIT_COUNTER` 自增到 1 并重置；对 `newScan().planFiles()` 断言 `SCAN_COUNTER` 自增到 1。
- 新增静态内部类 `CustomMetricsReporter implements MetricsReporter`，按 `MetricsReport` 实际类型（`ScanReport`/`CommitReport`）分别累加 `SCAN_COUNTER`/`COMMIT_COUNTER`。

### `core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryCatalog.java`

**修改目的**：实现 `initCatalog` 抽象方法。

**工作逻辑**：新增 `initCatalog(String catalogName, Map<String,String> additionalProperties)`，构造 `InMemoryCatalog` 实例并 `initialize` 后返回；`before()` 改为调用 `initCatalog("in-memory-catalog", ImmutableMap.of())`。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

**修改目的**：把原有 `initCatalog` 提升为 `@Override`，删除本类独立的自定义 reporter 测试（已上移到 `CatalogTests`）。

**工作逻辑**：
- 将 `private JdbcCatalog initCatalog(...)` 改为 `@Override protected JdbcCatalog initCatalog(String catalogName, Map<String,String> additionalProperties)`，参数名改为 `additionalProperties`，逻辑不变。
- 删除本类 `testCatalogWithCustomMetricsReporter` 测试与 `CustomMetricsReporter` 内部类，以及相关 import（`AtomicInteger`、`FileFormat`、`FileScanTask`、`CloseableIterable`、`MetricsReport`、`MetricsReporter`）。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalogWithV1Schema.java`

**修改目的**：补齐 `initCatalog` 抽象方法实现。

**工作逻辑**：新增 `@Override protected JdbcCatalog initCatalog(String catalogName, Map<String,String> additionalProperties)`，把原 `setupCatalog()` 中的初始化逻辑搬到该方法，并接受额外属性；`setupCatalog()` 改为 `this.catalog = initCatalog("testCatalog", ImmutableMap.of());`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：补齐 `initCatalog` 抽象方法实现，删除本类独立的自定义 reporter 测试。

**工作逻辑**：
- 把 `createCatalog()` 中创建 `RESTCatalog` 的逻辑抽到 `@Override protected RESTCatalog initCatalog(String catalogName, Map<String,String> additionalProperties)`，接受额外属性并合并到初始化属性中；`createCatalog()` 仅完成 backend catalog、HTTP server 启动，然后调用 `this.restCatalog = initCatalog("prod", ImmutableMap.of());`。
- 删除本类 `testCatalogWithCustomMetricsReporter` 测试与 `CustomMetricsReporter` 内部类，以及相关 import。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java`

**修改目的**：补齐 `initCatalog` 抽象方法实现。

**工作逻辑**：新增 `@Override protected HiveCatalog initCatalog(String catalogName, Map<String,String> additionalProperties)`，通过 `CatalogUtil.loadCatalog` 加载 `HiveCatalog`，合并 `CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS` 默认属性和 `additionalProperties`；`before()` 改为 `catalog = initCatalog("hive", ImmutableMap.of());`。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java`

**修改目的**：把 `initNessieCatalog` 改为 `initCatalog` 并支持额外属性。

**工作逻辑**：将原 `private NessieCatalog initNessieCatalog(String ref)` 改为 `@Override protected NessieCatalog initCatalog(String catalogName, Map<String,String> additionalProperties)`，固定 `ref=main`，把 `options` 与 `additionalProperties` 合并后调用 `CatalogUtil.buildIcebergCatalog`；`before()` 改为 `catalog = initCatalog("nessie", ImmutableMap.of());`。

### `open-api/src/test/java/org/apache/iceberg/rest/RESTCompatibilityKitCatalogTests.java`

**修改目的**：补齐 `initCatalog` 抽象方法实现。

**工作逻辑**：新增 `@Override protected RESTCatalog initCatalog(String catalogName, Map<String,String> additionalProperties)`，委托给 `RCKUtils.initCatalogClient(additionalProperties)`。

## 小结

- **成效**：修复了通过 `createTransaction`/`createOrReplaceTransaction`/`replaceTransaction` 事务路径提交时自定义 metrics reporter 不被调用的缺陷；同时补齐 `InMemoryCatalog` 的 `properties()` 实现，使该 catalog 也能正确加载 reporter。测试上提到 `CatalogTests` 公共基类后，所有 catalog 实现（InMemory、Jdbc、JdbcV1、REST、Hive、Nessie、REST CTK）统一接受该回归测试覆盖。
- **影响范围**：核心改动集中在 `BaseMetastoreCatalog`（3 处调用）和 `Transactions`（新增 1 个重载）、`InMemoryCatalog`（新增 `properties()`）。测试侧 7 个测试类同步实现 `initCatalog`。共 11 个文件、约 198 行新增、143 行删除。
- **回迁到 1.4.x 的注意事项**：这是一个 bug 修复，影响所有通过 `Transaction` 接口创建/替换表并依赖自定义 `metrics-reporter-impl` 上报 commit metrics 的用户，**建议回迁**。回迁时需要：① 修改 `BaseMetastoreCatalog` 三处事务调用传入 `metricsReporter()`；② 在 `Transactions` 中新增 `createOrReplaceTableTransaction(..., MetricsReporter)` 重载（如果 1.4.x 已有其他两个重载的话）；③ 在 `InMemoryCatalog` 中补 `properties()` 实现与 `catalogProperties` 字段。如果 1.4.x 上的 `BaseTransaction` 已有接受 `MetricsReporter` 的构造器，则无需额外改动；否则需要同时回迁 `BaseTransaction` 的相关构造器。回迁后建议把 `CatalogTests.testCatalogWithCustomMetricsReporter` 一并迁入，以避免后续回归。注意各 catalog 测试子类的 `initCatalog` 抽象方法是破坏性变更（新增 abstract 方法），需要同步修改所有继承 `CatalogTests` 的子类，否则编译失败。
