# 提交 0313：Spark 3.5: Migrate tests to JUnit5 in actions directory (#9367)

## 提交信息

- **序号**：0313
- **哈希**：a8f468d6ddd0e031d41f7d07ae710511f96cbf36
- **短哈希**：a8f468d6d
- **日期**：2023-12-29 10:52:25 +0100
- **作者**：Chinmay Bhat
- **提交说明**：Spark 3.5: Migrate tests to JUnit5 in actions directory (#9367)
- **PR/Issue**：#9367

## 总体目的

本提交是 Iceberg Spark 3.5 模块 JUnit 4 → JUnit 5 系列迁移的 actions 目录批次，迁移 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/` 目录下 8 个测试类（共 1030 行新增、997 行删除，净增 33 行）。该目录涵盖 Spark actions 的核心测试：`CreateActions`（migrateTable/addFiles 表迁移）、`DeleteReachableFilesAction`（删除可达文件）、`ExpireSnapshotsAction`（快照过期）、`RemoveOrphanFilesAction`/`RemoveOrphanFilesAction3`（孤儿文件清理）、`RewriteDataFilesAction`（数据文件重写）、`RewriteManifestsAction`（manifest 重写）、`RewritePositionDeleteFilesAction`（position delete 重写）。这些测试是 Spark 集成测试体系中行为最复杂、断言密度最高的部分之一。

与 source 目录批次（#9342）相比，actions 目录的迁移复杂性体现在三方面参数化基类并存：(1) 部分测试原继承 `SparkCatalogTestBase`（参数化 catalog 维度），需切换到 `CatalogTestBase` 并将方法从 `@Test` 改为 `@TestTemplate`；(2) 部分测试原以 `@RunWith(Parameterized.class) extends SparkTestBase` 自带参数化（如 `TestRewriteManifestsAction` 的 snapshotIdInheritanceEnabled/useCaching/formatVersion 维度），需改为 `@ExtendWith(ParameterizedTestExtension.class) extends TestBase`，参数提供方法从 `@Parameterized.Parameters` 改为 Iceberg 自定义 `@Parameters`，方法注解 `@Test` 改为 `@TestTemplate`；(3) 部分测试是非参数化的简单继承 `SparkTestBase`，仅需切换到 `TestBase` 并完成注解/断言替换，方法仍用 `@Test`。迁移动机与之前批次一致：统一到 JUnit 5 现代框架、采用 AssertJ 静态导入断言、`@TempDir Path` 临时目录、利用 JUnit 5 扩展机制。

## 如何达成设计目的

整体设计思路与 #9342、#9364 一致——保持测试逻辑不变，仅替换测试基础设施 API。但由于 actions 目录三类基类并存，迁移按文件类型采用不同模式：catalog 参数化测试（`TestCreateActions`）切换到 `CatalogTestBase` 并扩展 `@Parameters` 维度；自带参数化测试（`TestRewriteManifestsAction`、`TestRewriteDataFilesAction`、`TestRewritePositionDeleteFilesAction`、`TestExpireSnapshotsAction`）改为 `@ExtendWith(ParameterizedTestExtension.class) extends TestBase`，`@Parameter` 字段注入参数；非参数化测试（`TestDeleteReachableFilesAction`、`TestRemoveOrphanFilesAction`）仅切换基类到 `TestBase` 并保留 `@Test`；子类继承场景（`TestRemoveOrphanFilesAction3 extends TestRemoveOrphanFilesAction`）跟随父类迁移但保留自身 `@Test`。所有文件统一完成断言静态导入化、`TemporaryFolder` → `@TempDir`、`Assume` → `assumeThat`、`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`（注意 actions 目录较多用 `Files.createTempDirectory` 而非 `temp.toFile()`，因这些测试需要新建子目录而非复用临时根）。

## 修改详情

本次迁移涉及 8 个文件。下面先说明迁移模式总览，再对几个代表性文件单独展开。

### 迁移模式总览

**1. 注解替换**（与系列前序一致）：`org.junit.Test` → `org.junit.jupiter.api.Test`（非参数化）或 `org.junit.jupiter.api.TestTemplate`（参数化）；`@Before`/`@After` → `@BeforeEach`/`@AfterEach`；`@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`；`@Ignore` → `@Disabled`。

**2. 临时目录替换**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private/protected Path temp;`；`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`（actions 目录普遍用此形式，因需在临时目录下新建子目录作为表 location）；`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`。

**3. 断言替换**：移除 `org.junit.Assert`、`org.assertj.core.api.Assertions` import，新增 `assertThat`、`assertThatThrownBy`、`assumeThat` 静态导入；`Assert.assertEquals/assertTrue/assertFalse/assertNotNull/assertNull` → `assertThat(...).as(...).isEqualTo/isTrue/isFalse/isNotNull/isNull()`；`Assertions.assertThatThrownBy` → 静态导入 `assertThatThrownBy`；`Assert.assertTrue(... anyMatch ...)` → `assertThat(stream).as(...).anyMatch(...)`；`Assume.assumeTrue/assumeFalse` → `assumeThat(...).as(...).isEqualTo/isFalse/isTrue()`。

**4. 基类与参数化机制切换**（本批次的核心差异点，分三类）：
- **Catalog 参数化测试**（`TestCreateActions`）：`extends SparkCatalogTestBase` → `extends CatalogTestBase`；`@Parameterized.Parameters` → `@Parameters`（Iceberg 自定义注解）；方法返回 `Object[][]`；删除构造器，新增 `@Parameter(index = N)` 字段注入；测试方法 `@Test` → `@TestTemplate`。
- **自带参数化测试**（`TestRewriteManifestsAction` 等）：`@RunWith(Parameterized.class) extends SparkTestBase` → `@ExtendWith(ParameterizedTestExtension.class) extends TestBase`；`@Parameterized.Parameters` → `@Parameters`；删除构造器，`@Parameter` 字段注入；测试方法 `@Test` → `@TestTemplate`。
- **非参数化测试**（`TestDeleteReachableFilesAction`、`TestRemoveOrphanFilesAction`）：`extends SparkTestBase` → `extends TestBase`；测试方法保持 `@Test`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java`

**修改目的**：将 CreateActions（migrateTable）测试迁移到 `CatalogTestBase`，并扩展参数化维度新增 `type` 参数。

**工作逻辑**：基类 `SparkCatalogTestBase` → `CatalogTestBase`。原 `@Parameterized.Parameters(name = "Catalog Name {0} - Options {2}")` 改为 `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, type = {3}")`，并在四组 catalog 配置数组末尾追加第 4 元素 `"hive"` 或 `"hadoop"`（用于后续 assume 判断 catalog 类型）。删除构造器 `public TestCreateActions(String catalogName, String implementation, Map<String, String> config)`，改为 `@Parameter(index = 3) private String type;` 字段注入（前 3 个参数由父类 `CatalogTestBase` 注入）。`catalog` 字段从 `final` 改为普通字段，在 `@BeforeEach before()` 中通过 `super.before()` 后从 `spark.sessionState().catalogManager().catalog(catalogName)` 获取（原构造器中获取，但新机制下 catalogName 在 `before` 前才注入）。`@Before`/`@After` → `@BeforeEach`/`@AfterEach`；`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`。所有测试方法 `@Test` → `@TestTemplate`。`Assume.assumeTrue("Cannot migrate to a hadoop based catalog", !type.equals("hadoop"))` → `assumeThat(type).as("...").isNotEqualTo("hadoop")`；`Assume.assumeTrue("Can only migrate from Spark Session Catalog", catalog.name().equals("spark_catalog"))` → `assumeThat(catalog.name()).as("...").isEqualTo("spark_catalog")`。断言迁移示例：`Assert.assertNull(beforeSchema.findField(newCol1))` → `assertThat(beforeSchema.findField(newCol1)).isNull()`；`Assert.assertFalse(results1.isEmpty())` → `assertThat(results1).isNotEmpty()`；`Assert.assertTrue(Arrays.asList(schema.fieldNames()).contains(newCol2))` → `assertThat(schema.fieldNames()).contains(newCol2)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：将 RewriteManifestsAction 测试从 `@RunWith(Parameterized.class) extends SparkTestBase` 模式迁移到 JUnit 5 扩展模式。

**工作逻辑**：类注解由 `@RunWith(Parameterized.class)` 改为 `@ExtendWith(ParameterizedTestExtension.class)`，基类 `SparkTestBase` → `TestBase`。`@Parameters(name = "snapshotIdInheritanceEnabled = {0}, useCaching = {1}, formatVersion = {2}")` 改为 `@Parameters(name = "snapshotIdInheritanceEnabled = {0}, useCaching = {1}, shouldStageManifests = {2}, formatVersion = {3}")`（参数化维度有调整）。删除构造器，参数改为 `@Parameter` 字段注入。`@Rule TemporaryFolder` → `@TempDir Path`。`@Before` → `@BeforeEach`。测试方法 `@Test` → `@TestTemplate`。`Assertions.assertThatThrownBy` → 静态导入 `assertThatThrownBy`。新增 `org.apache.iceberg.{Parameter,ParameterizedTestExtension,Parameters}` import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestDeleteReachableFilesAction.java`

**修改目的**：将非参数化的 DeleteReachableFilesAction 测试迁移到 `TestBase`。

**工作逻辑**：基类 `SparkTestBase` → `TestBase`，类无 `@RunWith`/`@ExtendWith` 注解（非参数化）。`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`。`@Before` → `@BeforeEach`。测试方法保持 `@Test`（非 `@TestTemplate`，因不参与参数化）。断言迁移：`Assert.assertEquals` → `assertThat(...).isEqualTo(...)`；`Assertions.assertThatThrownBy` → `assertThatThrownBy` 静态导入；移除 `org.assertj.core.api.Assertions` 与 `org.junit.{Assert,Before,Rule,Test}`、`org.junit.rules.TemporaryFolder` import，新增 `org.junit.jupiter.api.{BeforeEach,Test}`、`org.junit.jupiter.api.io.TempDir`、`java.nio.file.Path` import。移除未使用的 `Iterables` import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction3.java`

**修改目的**：作为 `TestRemoveOrphanFilesAction` 的子类，跟随父类完成 JUnit 5 化，但因非参数化而保留 `@Test`。

**工作逻辑**：该类 `extends TestRemoveOrphanFilesAction`（继承父类迁移后的基类链）。`@After` → `@AfterEach`。测试方法保持 `@Test`（不改为 `@TestTemplate`，因父类 `TestRemoveOrphanFilesAction` 非参数化）。断言迁移：`Assert.assertTrue("trash file should be removed", StreamSupport.stream(results.orphanFileLocations().spliterator(), false).anyMatch(file -> file.contains(...)))` → `assertThat(StreamSupport.stream(results.orphanFileLocations().spliterator(), false)).as("trash file should be removed").anyMatch(file -> file.contains(...))`（5 处重复模式统一迁移）。

### 其余测试类

包括 `TestExpireSnapshotsAction`（454 行变更，最大）、`TestRemoveOrphanFilesAction`（200 行）、`TestRewriteDataFilesAction`（295 行）、`TestRewritePositionDeleteFilesAction`（310 行）。

**修改目的**：将 actions 目录下剩余测试类统一迁移到 JUnit 5。

**工作逻辑**：`TestExpireSnapshotsAction`、`TestRewriteDataFilesAction`、`TestRewritePositionDeleteFilesAction` 均为自带参数化测试，遵循 `TestRewriteManifestsAction` 同样模式（`@RunWith(Parameterized.class) extends SparkTestBase` → `@ExtendWith(ParameterizedTestExtension.class) extends TestBase`，`@Parameterized.Parameters` → `@Parameters`，构造器参数 → `@Parameter` 字段，`@Test` → `@TestTemplate`）。`TestRemoveOrphanFilesAction` 为非参数化测试，遵循 `TestDeleteReachableFilesAction` 模式（`SparkTestBase` → `TestBase`，保留 `@Test`）。这些文件改动量大主要因断言密度极高（涉及大量文件计数、快照 ID、manifest 验证等断言）与临时目录/文件操作密集。

## 小结

本次提交是 Iceberg Spark 3.5 模块 JUnit 4 → JUnit 5 系列迁移的 actions 目录批次，涉及 8 个文件、净增 33 行。本批次的复杂之处在于 actions 目录三类基类/参数化模式并存：catalog 参数化（→ `CatalogTestBase` + `@TestTemplate`）、自带参数化（→ `@ExtendWith(ParameterizedTestExtension.class) extends TestBase` + `@TestTemplate`）、非参数化（→ `TestBase` + `@Test`）。迁移保持所有测试逻辑不变，仅替换测试基础设施，为后续全面采用 JUnit 5 扩展能力奠定基础。`TestCreateActions` 还额外扩展了参数化维度新增 `type` 参数以更精细地控制 catalog 类型 assume，体现了迁移过程中对测试场景的同步增强。
