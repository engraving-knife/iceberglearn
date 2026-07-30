# 提交 2100：Spark 3.4: Migrate source and spark tests to JUnit5 (#12998)

## 提交信息

- **序号**：2100 / 4088
- **哈希**：ada3d12b4e02f8c9579c708fffb4711c52bc434c
- **短哈希**：ada3d12b
- **日期**：2025-05-08 07:31:29 +0200
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Spark 3.4: Migrate source and spark tests to JUnit5 (#12998)
- **PR/Issue**：#12998

## 总体目的

Iceberg 的测试基础设施正在从 JUnit 4 迁移到 JUnit 5（Jupiter）。本提交针对 Spark 3.4 模块下 `spark`（非 extensions）子项目中的测试类，把 JUnit 4 API 替换为 JUnit 5 API，并把断言从 JUnit `Assert.*` / AssertJ 旧风格统一到 AssertJ `assertThat(...)` 风格。这是系列迁移工作的一部分（与 #13007、#13015 等配套），目的是让 Spark 3.4 测试套件与核心模块统一在 JUnit 5 上，便于使用 JUnit 5 的扩展模型（`@ExtendWith`）、参数化测试（`@TestTemplate` + `ParameterizedTestExtension`）、生命周期注解等现代特性，并最终移除对 JUnit 4 的依赖。

本次迁移的测试类覆盖 `spark` 包下的 source 与 catalog 测试，包括 `TestFileRewriteCoordinator`、`TestSparkCachedTableCatalog`、`TestSparkCatalogOperations`、`TestSparkWriteConf`、`TestMigrateTableAction`、`TestDataSourceOptions`、`TestIcebergSourceHadoopTables`、`TestIcebergSourceHiveTables`、`TestIcebergSourceTablesBase`、`TestMetadataTableReadableMetrics`、`TestMetadataTablesWithPartitionEvolution`。同时涉及 `spark-extensions` 子项目下对应测试的小幅调整（参数化扩展适配）。

## 如何达成设计目的

通过机械但有规律的替换完成迁移：
1. **导入替换**：`org.junit.Before/After/Test/Rule` → `org.junit.jupiter.api.BeforeEach/AfterEach/Test`、`org.junit.rules.TemporaryFolder` → `org.junit.jupiter.api.io.TempDir`、`org.junit.Assert` → AssertJ `org.assertj.core.api.Assertions.assertThat`。
2. **注解替换**：`@Before` → `@BeforeEach`、`@After` → `@AfterEach`、`@Test` 保持但来自 jupiter；参数化测试用 `@TestTemplate` + `@ExtendWith(ParameterizedTestExtension.class)`。
3. **临时目录**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir protected Path temp;`。
4. **基类替换**：`extends SparkTestBase` → `extends TestBase`；`extends SparkCatalogTestBase` → `extends CatalogTestBase`；`extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`（这些新基类是先前迁移中引入的 JUnit 5 版本）。
5. **断言替换**：`Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`；`Assert.assertTrue(...)` → `assertThat(...).isTrue()`；集合大小用 `hasSize(int)`；单元素用 `singleElement().satisfies(...)` 等。
6. **生命周期**：若新基类有 `before()`，子类 `@BeforeEach` 方法中调用 `super.before()` 以保持原有初始化顺序。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestFileRewriteCoordinator.java` (修改, +20/-15 lines)

**修改目的**：迁移到 JUnit 5 + AssertJ。

**工作逻辑**：`extends SparkCatalogTestBase` → `extends CatalogTestBase`；加 `@ExtendWith(ParameterizedTestExtension.class)`；`@Test` → `@TestTemplate`；`@After` → `@AfterEach`；`Assert.*` → `assertThat(...)`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkCachedTableCatalog.java` (修改, +12/-9 lines)

**修改目的**：同上迁移。

**工作逻辑**：导入与注解替换，断言改 AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkCatalogOperations.java` (修改, +20/-16 lines)

**修改目的**：同上迁移。

**工作逻辑**：`extends SparkCatalogTestBase` → `extends CatalogTestBase` + `@ExtendWith(ParameterizedTestExtension.class)`；`@Test` → `@TestTemplate`；`@After` → `@AfterEach`；断言改 AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java` (修改, +25/-22 lines)

**修改目的**：迁移并改用参数化扩展。

**工作逻辑**：`extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog` + `@ExtendWith(ParameterizedTestExtension.class)`；`@Before` → `@BeforeEach` 并调用 `super.before()`；`@After` → `@AfterEach`；`@Test` → `@TestTemplate`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestMigrateTableAction.java` (修改, +12/-10 lines)

**修改目的**：迁移到 JUnit 5。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestDataSourceOptions.java` (修改, +35/-31 lines)

**修改目的**：迁移到 JUnit 5 + AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceHadoopTables.java` (修改, +6/-3 lines)

**修改目的**：基类迁移（`SparkTestBase` → `TestBase`）与小适配。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceHiveTables.java` (修改, +4/-2 lines)

**修改目的**：同上。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java` (修改, +95/-78 lines)

**修改目的**：抽象基类迁移到 JUnit 5。

**工作逻辑**：`extends SparkTestBase` → `extends TestBase`；`@Rule TemporaryFolder temp` → `@TempDir Path temp`；`@After` → `@AfterEach`；大量 `Assert.assertEquals` → `assertThat(...).isEqualTo/hasSize/singleElement.satisfies` 等。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestMetadataTableReadableMetrics.java` (修改, +18/-13 lines)

**修改目的**：迁移到 JUnit 5 + AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestMetadataTablesWithPartitionEvolution.java` (修改, +35/-26 lines)

**修改目的**：迁移到 JUnit 5 + AssertJ，基类替换。

### `spark-extensions` 子项目下的对应测试（`TestFileRewriteCoordinator`、`TestSparkCachedTableCatalog`、`TestSparkCatalogOperations`、`TestSparkWriteConf`、`TestDataSourceOptions`、`TestIcebergSourceHadoopTables`、`TestIcebergSourceTablesBase`、`TestMetadataTableReadableMetrics`，各修改几行到几十行）

**修改目的**：与 spark 子项目测试同步，适配新基类与参数化扩展。

**工作逻辑**：主要是 `@ExtendWith(ParameterizedTestExtension.class)` 注册、`@TestTemplate`、`super.before()` 调用等小调整，保持 extensions 测试与 spark 测试基类一致。

## 总结

本次提交把 Spark 3.4 `spark` 子项目下的 11 个测试类（及其在 `spark-extensions` 下的对应）从 JUnit 4 迁移到 JUnit 5：替换导入与注解（`@Before/After`→`@BeforeEach/AfterEach`、`@Test`→`@TestTemplate` for 参数化）、临时目录改 `@TempDir`、基类改 `TestBase/CatalogTestBase/TestBaseWithCatalog`、断言统一到 AssertJ `assertThat`。这是 Spark 3.4 测试套件 JUnit 5 化系列迁移的一环，为最终移除 JUnit 4 依赖、统一测试基础设施铺路。
