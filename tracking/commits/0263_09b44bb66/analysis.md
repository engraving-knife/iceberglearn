# 提交 0263：Hive: Introduce HiveMetastoreExtension for Hive tests (#9282)

## 提交信息

- **序号**：0263 / 4088
- **哈希**：09b44bb66f4b56bb093a070dec9fcedea98d67ab
- **短哈希**：09b44bb66
- **日期**：2023-12-12 15:09:14 +0100
- **作者**：Naveen Kumar
- **提交说明**：Hive: Introduce HiveMetastoreExtension for Hive tests (#9282)
- **PR/Issue**：#9282

## 总体目的

在 `iceberg-hive-metastore` 测试模块中，原本所有需要 Hive Metastore 的测试都继承自一个抽象基类 `HiveMetastoreTest`，该基类用 `@BeforeAll`/`@AfterAll` 静态方法在测试类级别启动/停止一个全局共享的 `TestHiveMetastore`、`HiveMetaStoreClient` 和 `HiveCatalog`，并把这些实例放在 `protected static` 字段中。这种设计有几个明显问题：

1. **共享全局状态**：所有子类共享同一个 `metastore`、`metastoreClient`、`hiveConf`、`catalog` 静态字段。一旦某个测试类需要覆盖 HiveConf（例如 `TestHiveCommitLocks` 需要 `HIVE_TXN_TIMEOUT=1s`），就必须调用 `HiveMetastoreTest.startMetastore(Map)` 这个静态方法重启 metastore，会破坏并行/同 JVM 多测试类运行时的状态隔离。
2. **继承耦合**：测试类只能通过继承来获得 metastore 生命周期管理，无法用组合的方式注入，且 `DB_NAME` 也是全局常量，不同测试类无法使用不同数据库名以避免冲突。
3. **生命周期不灵活**：`@BeforeAll`/`@AfterAll` 静态方法无法与 JUnit 5 的 `@RegisterExtension` 组合使用，难以做参数化或自定义扩展。

本提交引入 JUnit 5 扩展 `HiveMetastoreExtension`（实现 `BeforeAllCallback` + `AfterAllCallback`），把 metastore 启停逻辑封装为可注册的扩展。每个测试类通过 `@RegisterExtension` 持有自己的扩展实例（可自定义 `databaseName` 和 `hiveConfOverride`），从而实现测试类级别的 metastore 隔离。同时把原来继承 `HiveMetastoreTest` 的所有测试类改为组合式使用扩展，删除 `HiveMetastoreTest` 基类。这把 Iceberg Hive 测试基础设施从"继承 + 全局静态"演进到"扩展 + 实例隔离"，更符合 JUnit 5 推荐实践，也为后续引入并行测试、参数化 metastore 配置打下基础。

## 如何达成设计目的

整体设计思路是"用 JUnit 5 Extension 替代抽象基类"。新增的 `HiveMetastoreExtension` 把原 `HiveMetastoreTest.startMetastore/stopMetastore` 的逻辑搬到 `beforeAll`/`afterAll` 回调中，并通过构造参数暴露 `databaseName` 与 `hiveConfOverride`，让每个测试类独立配置。原来 `HiveMetastoreTest` 暴露的 `metastoreClient`、`hiveConf`、`metastore`、`catalog` 四个静态字段，被替换为扩展上的实例方法 `metastoreClient()`、`hiveConf()`、`metastore()`；`catalog` 不再由基类统一创建，而是各测试类自己在 `@BeforeAll` 或 `@BeforeEach` 中通过 `CatalogUtil.loadCatalog(...)` 装配（这样不同测试可以传不同的 catalog 属性）。`HiveTableBaseTest` 仍然作为公共基类存在，但它不再继承 `HiveMetastoreTest`，而是通过 `@RegisterExtension` 持有扩展并把 `catalog` 初始化放到 `@BeforeAll initCatalog()`。`TestHiveCommitLocks` 不再继承 `HiveTableBaseTest`，因为它需要自定义 HiveConf 与 spy 客户端，所以独立声明扩展并自己管理表生命周期。

## 修改详情

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveMetastoreExtension.java`（新文件）

**修改目的**：把 Hive Metastore 的启停与 `HiveMetaStoreClient`/`HiveCatalog` 配置封装为 JUnit 5 扩展，支持按测试类独立配置与隔离。

**工作逻辑**：

- 实现 `BeforeAllCallback` + `AfterAllCallback`，对应原 `HiveMetastoreTest.startMetastore` / `stopMetastore` 的语义，作用域是测试类级别（`beforeAll` 在所有测试方法前调用一次）。
- 构造方法接收 `String databaseName` 和 `Map<String, String> hiveConfOverride`，替代原来 `startMetastore(Map)` 静态方法。
- `beforeAll`：新建 `TestHiveMetastore`，用 `new HiveConf(TestHiveMetastore.class)` 作为基础配置；遍历 `hiveConfOverride` 调用 `hiveConfWithOverrides.set(k, v)` 注入覆盖项；`metastore.start(hiveConfWithOverrides)` 启动内嵌 metastore；`new HiveMetaStoreClient(hiveConfWithOverrides)` 创建客户端；通过 `metastore.getDatabasePath(databaseName)` 拿到数据库路径后调用 `metastoreClient.createDatabase(new Database(databaseName, "description", dbPath, Maps.newHashMap()))` 创建测试数据库。
- `afterAll`：分别 close `metastoreClient`、stop `metastore`，并把二者置为 `null` 释放引用，避免跨测试类泄漏。
- 暴露三个 getter：`metastoreClient()`、`hiveConf()`（委托给 `metastore.hiveConf()`）、`metastore()`，让测试代码以组合方式拿到所需依赖，替代原来的 `protected static` 字段。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveMetastoreTest.java`（删除）

**修改目的**：移除旧的抽象基类，让测试不再依赖继承与全局静态字段。

**工作逻辑**：原 85 行的 `HiveMetastoreTest` 类被整体删除，其职责拆分：
- metastore 启停 → `HiveMetastoreExtension.beforeAll/afterAll`
- `catalog` 创建 → 各测试类自己的 `initCatalog()`
- `DB_NAME` 常量 → 各测试类内部自定义
- `EVICTION_INTERVAL` → 各测试类按需使用 `TimeUnit.SECONDS.toMillis(10)`

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveTableBaseTest.java`

**修改目的**：作为多个 Hive 表测试的公共基类，改用扩展注入 metastore 与 catalog。

**工作逻辑**：

- 不再 `extends HiveMetastoreTest`，改为独立类。
- 引入 `@RegisterExtension protected static final HiveMetastoreExtension HIVE_METASTORE_EXTENSION = new HiveMetastoreExtension(DB_NAME, Collections.emptyMap())`。`DB_NAME` 由继承自基类的 `"hivedb"` 改为本类内部常量。
- 新增 `protected static HiveCatalog catalog` 字段和 `@BeforeAll initCatalog()`：通过 `CatalogUtil.loadCatalog(HiveCatalog.class.getName(), ICEBERG_CATALOG_TYPE_HIVE, ImmutableMap.of(CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS, "10000"), HIVE_METASTORE_EXTENSION.hiveConf())` 创建 `HiveCatalog`。这样 catalog 与 metastore 扩展的生命周期一致。
- `dropTestTable()` 中 `tableLocation.getFileSystem(hiveConf)` 改为 `getFileSystem(HIVE_METASTORE_EXTENSION.hiveConf())`。
- `getTableBasePath(String)` 中 `metastore.getDatabasePath(DB_NAME)` 改为 `HIVE_METASTORE_EXTENSION.metastore().getDatabasePath(DB_NAME)`。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveCreateReplaceTableTest.java`

**修改目的**：脱离 `HiveMetastoreTest` 继承，独立管理 metastore 与 catalog。

**工作逻辑**：

- 不再 `extends HiveMetastoreTest`，类内自定义 `DB_NAME = "hivedb"`。
- `@RegisterExtension private static final HiveMetastoreExtension HIVE_METASTORE_EXTENSION = new HiveMetastoreExtension(DB_NAME, Collections.emptyMap())`。
- 新增 `private static HiveCatalog catalog` 和 `@BeforeAll initCatalog()`，与 `HiveTableBaseTest` 一致地通过 `CatalogUtil.loadCatalog(...)` 装配 catalog。
- `createTableLocation()` 去掉了 `throws IOException`（不再需要）。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveTableTest.java`

**修改目的**：把对继承自基类的 `metastoreClient` 字段的引用改为通过 `HIVE_METASTORE_EXTENSION.metastoreClient()` 访问。

**工作逻辑**：多处把 `metastoreClient.getTable(...)`、`metastoreClient.alter_table(...)`、`metastoreClient.createTable(...)`、`metastoreClient.dropTable(...)`、`metastoreClient.dropDatabase(...)` 改写为 `HIVE_METASTORE_EXTENSION.metastoreClient().xxx(...)`，因为 `metastoreClient` 不再是基类静态字段。涉及 `testRenameTable`、`testHiveTableRefresh`、`testFailure`、`testListTables`、`testDropTable`、`testRegisterTable`、`testRegisterExistingTable`、`testHiveEngineEnabledAndDisabled` 等多个用例，行为不变，只是访问入口迁移。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestCachedClientPool.java`

**修改目的**：脱离 `HiveMetastoreTest` 继承，独立管理 metastore。

**工作逻辑**：

- 不再 `extends HiveMetastoreTest`，新增 `EVICTION_INTERVAL` 和 `DB_NAME` 常量。
- `@RegisterExtension private static final HiveMetastoreExtension HIVE_METASTORE_EXTENSION = new HiveMetastoreExtension(DB_NAME, Collections.emptyMap())`。
- `testClientPoolCleaner()` 中改用 `HIVE_METASTORE_EXTENSION.hiveConf()` 构造 `CachedClientPool`，并把 `CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS` 直接传入 catalog 属性 map（之前是从基类继承的全局配置）。
- `testClientPoolKey()` 中新增局部变量 `HiveConf hiveConf = HIVE_METASTORE_EXTENSION.hiveConf()`，便于后续 `Key` 计算使用，逻辑不变。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java`

**修改目的**：脱离 `HiveMetastoreTest` 继承，按测试方法粒度管理 catalog，并解决局部 `catalog` 变量与原基类静态字段冲突。

**工作逻辑**：

- 不再 `extends HiveMetastoreTest`。
- `@RegisterExtension private static final HiveMetastoreExtension HIVE_METASTORE_EXTENSION = new HiveMetastoreExtension(DB_NAME, Collections.emptyMap())`。
- 新增 `private HiveCatalog catalog` 字段和 `@BeforeEach before()`：在每个测试方法前通过 `CatalogUtil.loadCatalog(...)` 创建 catalog。这里特意用 `@BeforeEach` 而非 `@BeforeAll`，是为了让每个测试方法获得独立的 catalog 实例，便于不同用例覆盖不同配置（如 `testWarehouse()`、`testWarehouseWithSlash()` 等）。这也意味着原基类的全局 `catalog` 字段被替换为实例字段。
- 把原代码中若干局部变量 `HiveCatalog catalog = new HiveCatalog()` 重命名为 `hiveCatalog`，避免与新增的实例字段 `catalog` 同名遮蔽。例如 `testInitializeCatalogNullConfig`、`testCatalogToString`、`testSetConf`、`testWarehouseWithSlash`。
- 全部 `metastoreClient.xxx(...)` 改为 `HIVE_METASTORE_EXTENSION.metastoreClient().xxx(...)`，`hiveConf` 改为 `HIVE_METASTORE_EXTENSION.hiveConf()`。涉及 `testCreateTableOwner`、`testCreateNamespace`、`testCreateNamespaceWithLocation`、`testNamespaceOwner`、`testSetNamespaceOwner`、`defaultUri`、`hmsTableParameters`、`testTablePropsOnExistingDB` 等用例。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCommitLocks.java`

**修改目的**：脱离 `HiveTableBaseTest` 继承，独立管理 metastore（带 `HIVE_TXN_TIMEOUT=1s` 覆盖）与测试表生命周期。

**工作逻辑**：

- 不再 `extends HiveTableBaseTest`，类内自定义 `DB_NAME`、`TABLE_NAME`、`schema`、`partitionSpec`、`TABLE_IDENTIFIER`（与 `HiveTableBaseTest` 保持一致）。
- `@RegisterExtension private static final HiveMetastoreExtension HIVE_METASTORE_EXTENSION = new HiveMetastoreExtension(DB_NAME, ImmutableMap.of(HiveConf.ConfVars.HIVE_TXN_TIMEOUT.varname, "1s"))`，把原来 `HiveMetastoreTest.startMetastore(ImmutableMap.of(HIVE_TXN_TIMEOUT, "1s"))` 的覆盖逻辑改为扩展构造参数。
- 新增 `private static HiveCatalog catalog` 和 `private Path tableLocation`。
- `@BeforeAll initCatalog()` 替代原 `startMetastore()`：通过 `CatalogUtil.loadCatalog(...)` 创建 catalog，配置 `CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS`。同时把原来在 `startMetastore` 中的 spy 设置（`overriddenHiveConf`、`spyClientPool`、`spyCachedClientPool`）保留在此方法中。`hiveConf` 引用全部改为 `HIVE_METASTORE_EXTENSION.hiveConf()`，spy 的 `HiveMetaStoreClient` 也用此 conf 构造。
- `@BeforeEach before()` 中新增 `this.tableLocation = new Path(catalog.createTable(TABLE_IDENTIFIER, schema, partitionSpec).location())`，把原本依赖 `HiveTableBaseTest.createTestTable()` 的建表逻辑迁到本类。后续 `catalog.loadTable(TABLE_IDENTIFIER)` 不变。
- 新增 `@AfterEach dropTestTable()`：删表数据 + `catalog.dropTable(TABLE_IDENTIFIER, false)`，与 `HiveTableBaseTest.dropTestTable()` 对齐，保证每个测试方法后清理。
- 原来的 `@AfterAll cleanup()` 仍保留，用于清理 spy 资源。

## 小结

本提交通过引入 JUnit 5 扩展 `HiveMetastoreExtension` 取代抽象基类 `HiveMetastoreTest`，把 Iceberg Hive 测试基础设施从"继承 + 全局静态字段"重构为"扩展 + 实例隔离"，使每个测试类可以独立配置 metastore 数据库名与 HiveConf 覆盖项，消除了全局共享状态和继承耦合，提升了测试隔离性与可组合性，为后续并行测试与多配置测试奠定基础。
