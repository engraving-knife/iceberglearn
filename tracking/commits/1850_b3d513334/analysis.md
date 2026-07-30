# 提交 1850：Spark: Migrate Spark 3.4 test base to JUnit5 (#12501)

## 提交信息

- **序号**：1850 / 4088
- **哈希**：b3d5133343ff6b15b7c0201e662b545533d5eba7
- **短哈希**：b3d513334
- **日期**：2025-03-14 09:09:52 +0100
- **作者**：Tom Tanaka
- **提交说明**：Spark: Migrate Spark 3.4 test base to JUnit5 (#12501)
- **PR/Issue**：#12501

## 总体目的

Iceberg 的 Spark 3.4 测试基础类此前仍基于 JUnit 4（`org.junit.*`），使用 `@Before`/`@After`/`@Test`、`Assert.assertEquals`、`@Rule public TemporaryFolder temp` 等老式 API。而 Spark 3.5 模块早已完成 JUnit 5 迁移（使用 `@BeforeEach`/`@AfterEach`/`@TestTemplate`、AssertJ 的 `assertThat`、`@TempDir` 等），两个分支的测试基础设施不一致，导致：

1. 测试代码风格不统一，维护成本高；
2. 跨版本共享测试代码（如复制 3.5 的测试到 3.4）需要额外做 JUnit 4↔5 适配；
3. JUnit 5 的参数化扩展（`ParameterizedTestExtension`、`@TestTemplate`）在 3.4 中不可用，限制了测试组织方式。

本提交将 Spark 3.4 的测试基类与若干测试用例从 JUnit 4 迁移到 JUnit 5，使其与 Spark 3.5 对齐，统一测试基础设施。同时把原来 3.4 独有的 `SparkTestBase`/`SparkTestBaseWithCatalog`/`SparkCatalogTestBase` 体系替换为 3.5 风格的 `TestBase`/`TestBaseWithCatalog`/`CatalogTestBase` 三层结构。

## 如何达成设计目的

整体思路是把 3.5 已有的 JUnit 5 测试基类（`TestBase`、`TestBaseWithCatalog`、`CatalogTestBase`）整体复制到 3.4 模块，再把 3.4 下仍使用旧基类的测试类切换到新基类，并同步把 JUnit 4 注解替换为 JUnit 5 注解、把 `Assert.*` 替换为 AssertJ 的 `assertThat`。同时顺手对 3.5 的少量测试做了同样的 AssertJ 风格统一（如 `hasSize(1)` 代替 `isEqualTo(1)`、`containsEntry` 代替 `get(...).isEqualTo(...)`），让两个版本完全一致。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/CatalogTestBase.java` (新增, 59 lines)

**修改目的**：提供 3.4 模块下参数化测试的顶级基类，对齐 3.5 的同名类。

**工作逻辑**：继承 `TestBaseWithCatalog`，标注 `@ExtendWith(ParameterizedTestExtension.class)`，通过 `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}")` 暴露 4 组 catalog 参数（HIVE、HADOOP、SPARK、REST），其中 REST 组会把 `RESTServerExtension` 起的本地 REST 端口 URI 注入到配置中。子类继承后即可获得跨 4 种 catalog 的参数化测试能力。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestBase.java` (新增, 287 lines)

**修改目的**：替换原 `SparkTestBase`，作为 3.4 模块所有 Spark 测试的根基类，使用 JUnit 5 生命周期。

**工作逻辑**：
- `@BeforeAll startMetastoreAndSpark()`：启动内嵌 Hive metastore、构造 `SparkSession`（local[2]、dynamic partition overwrite、Hive 支持）并创建 `HiveCatalog`，建立 `default` 命名空间。
- `@AfterAll stopMetastoreAndSpark()`：逆序关闭 catalog/metastore/spark。
- 提供 `sql(...)`、`scalarSql(...)`、`append(...)`、`withSQLConf(...)`、`withDefaultTimeZone(...)`、`withUnavailableFiles(...)`、`executeAndKeepPlan(...)` 等测试辅助方法。`scalarSql` 改用 AssertJ `assertThat(rows).hasSize(1)`。
- `withSQLConf` 仍保留对静态配置的拒绝逻辑（遇 `SQLConf.isStaticConfigKey` 抛异常）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java` (新增, 195 lines)

**修改目的**：替换原 `SparkTestBaseWithCatalog`，引入基于 `@RegisterExtension RESTServerExtension` 的 REST catalog 与参数化 catalog 配置。

**工作逻辑**：
- 通过 `@RegisterExtension private static final RESTServerExtension REST_SERVER_EXTENSION` 启动本地 REST 服务器（`FREE_PORT` + `CLIENT_POOL_SIZE=1`，注释解释 sqlite 内存库连接隔离问题）。
- `@Parameters` 默认提供 HADOOP 一组参数；`@Parameter` 注入 `catalogName`/`implementation`/`catalogConfig`。
- `@BeforeEach before()`：根据 `catalogConfig` 调用 `configureValidationCatalog()` 选择 `HadoopCatalog`/`RESTCatalog`/`HiveCatalog`/`InMemoryCatalog` 作为 `validationCatalog`，把 Spark session 配置注入 catalog，并创建 `default` 命名空间。
- `@TempDir protected java.nio.file.Path temp`：用 JUnit 5 的 `@TempDir` 替换 `@Rule TemporaryFolder`。
- 提供 `tableName(...)`、`commitTarget()`、`selectTarget()`、`cachingCatalogEnabled()`、`configurePlanningMode(...)` 等辅助方法。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestFunctionCatalog.java` (修改, 69 lines diff)

**修改目的**：把 `TestFunctionCatalog` 从 `SparkTestBaseWithCatalog` 切到 `TestBaseWithCatalog`，迁移到 JUnit 5。

**工作逻辑**：
- 类头加 `@ExtendWith(ParameterizedTestExtension.class)`，继承改为 `TestBaseWithCatalog`。
- 删除原构造函数中 `castToFunctionCatalog(catalogName)` 的初始化（因为 JUnit 5 参数注入时机不同），改在 `@BeforeEach createDefaultNamespace()` 中先 `super.before()` 再初始化 `asFunctionCatalog`。
- `@Before/@After/@Test` → `@BeforeEach/@AfterEach/@TestTemplate`（参数化测试用 `@TestTemplate`）。
- `Assert.assertArrayEquals(...)`/`Assert.assertEquals(...)` 全部替换为 `assertThat(...).isEqualTo(...)`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestPathIdentifier.java` (修改, 30 lines diff)

**修改目的**：迁移 `TestPathIdentifier` 到新基类与 JUnit 5。

**工作逻辑**：基类从 `SparkTestBase` 改为 `TestBase`；`@Rule public TemporaryFolder temp = new TemporaryFolder();` 改为 `@TempDir private Path temp;`；`temp.newFolder()` 改为 `temp.toFile()`；`@Before/@After/@Test` → `@BeforeEach/@AfterEach/@Test`（非参数化用 `@Test`）；`Assert.*` 替换为 `assertThat`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestAlterTable.java` (修改, 155 lines diff)

**修改目的**：迁移 `TestAlterTable` 到 `CatalogTestBase` 与 JUnit 5。

**工作逻辑**：基类从 `SparkCatalogTestBase` 改为 `CatalogTestBase`；删除接收 `(catalogName, implementation, config)` 的构造函数（JUnit 5 参数注入取代）；`@Before/@After/@Test` → `@BeforeEach/@AfterEach/@TestTemplate`；大量 `Assert.assertEquals("Schema should match expected", expected, actual)` 替换为 `assertThat(actual).as("Schema should match expected").isEqualTo(expected)`，断言对象与期望值的顺序也对齐到 AssertJ 习惯（actual 在前）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBase.java` (修改, 4 lines diff)

**修改目的**：把 3.5 的 `scalarSql` 风格统一为 AssertJ 推荐写法，与 3.4 新基类保持一致。

**工作逻辑**：`assertThat(rows.size()).isEqualTo(1)` → `assertThat(rows).hasSize(1)`；`assertThat(row.length).isEqualTo(1)` → `assertThat(row).hasSize(1)`。语义等价但更地道。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPathIdentifier.java` (修改, 1 line diff)

**修改目的**：移除多余的 `tableLocation.delete()` 调用。

**工作逻辑**：`@TempDir` 会在测试结束后自动清理临时目录，手动 `delete()` 既有重复之嫌也可能在 Windows 等环境下失败，删除后由 JUnit 5 统一清理。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestAlterTable.java` (修改, 8 lines diff)

**修改目的**：把 3.5 的 `testSetTableProperties` 断言改为更精确的 Map 断言。

**工作逻辑**：`assertThat(props.get("prop")).isEqualTo("value")` → `assertThat(props).containsEntry("prop", "value")`；`assertThat(props.get("prop")).isNull()` → `assertThat(props).doesNotContainKey("prop")`。这样断言更聚焦于"键是否存在"而非"取出值再比较"，避免 null 歧义。

## 小结

- **成效**：Spark 3.4 测试基础设施统一到 JUnit 5，与 3.5 完全对齐；3.4 新增了 `TestBase`/`TestBaseWithCatalog`/`CatalogTestBase` 三层基类，旧的 `SparkTestBase`/`SparkTestBaseWithCatalog`/`SparkCatalogTestBase` 体系被取代；3 处测试用例完成迁移；3.5 的少量断言被顺手统一。
- **影响范围**：仅测试代码，9 个文件、+667/-141 行，无生产代码改动。
- **回迁到 1.4.x 的注意事项**：纯测试基础设施迁移，回迁相对安全但工作量大。需要确认 1.4.x 分支的 Spark 3.4 测试是否仍在使用 JUnit 4 的旧基类；若回迁，需同步引入 `ParameterizedTestExtension`（位于 iceberg-core 的测试支持类）以及 `RESTServerExtension`；建议优先回迁以减少后续 cherry-pick 测试改动时的冲突。建议回迁。
