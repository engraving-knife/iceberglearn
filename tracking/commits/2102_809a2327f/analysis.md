# 提交 2102：Spark 3.4: Migrate SparkCatalogTestBase related tests to JUnit5 (#13007)

## 提交信息

- **序号**：2102 / 4088
- **哈希**：809a2327f15dd0f5f9a20f43b3dbb1632bb00828
- **短哈希**：809a2327
- **日期**：2025-05-08 11:16:45 +0200
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Spark 3.4: Migrate SparkCatalogTestBase related tests to JUnit5 (#13007)
- **PR/Issue**：#13007

## 总体目的

继续 Spark 3.4 测试套件的 JUnit 4 → JUnit 5 迁移（与 #12998、#13015 配套）。本提交聚焦于继承自 `SparkCatalogTestBase` 的一批 source 测试类，并把已被 JUnit 5 版本 `CatalogTestBase` 取代的旧抽象基类 `SparkCatalogTestBase` 删除。

被迁移的测试类包括 `TestCompressionSettings`、`TestRequiredDistributionAndOrdering`、`TestRuntimeFiltering`、`TestSparkCatalogHadoopOverrides`、`TestSparkStagedScan`、`TestSparkTable`、`TestStructuredStreamingRead3`。这些类原本通过 `@RunWith(Parameterized.class)` + `@Parameterized.Parameters` 实现参数化（按 catalog 类型 HIVE/HADOOP/SPARK/REST，部分还按文件格式/属性再参数化），迁移后改用 Iceberg 自定义的 `ParameterizedTestExtension` + `@Parameter` / `@Parameters` + `@TestTemplate` 模型，与核心模块的 JUnit 5 参数化风格一致。`spark-extensions` 子项目下的同名测试也做了同步小调整。

## 如何达成设计目的

1. **删除旧基类 `SparkCatalogTestBase`**：该类是 JUnit 4 的 `@RunWith(Parameterized.class)` 参数化基类，提供 HIVE/HADOOP/SPARK/REST 四种 catalog 参数，已被先前迁移引入的 JUnit 5 版本 `CatalogTestBase`（使用 `ParameterizedTestExtension`）取代，故整体删除。
2. **测试类迁移**：
   - `extends SparkCatalogTestBase` → `extends CatalogTestBase`。
   - `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
   - `@Parameterized.Parameters(name=...)` → `@Parameters(name=...)`（Iceberg 自定义注解）。
   - 构造函数注入参数 → `@Parameter(index=N)` 字段注入（参数索引从 3 开始，因为 0-2 是 catalog 的 name/impl/config）。
   - `@Rule TemporaryFolder temp` → `@TempDir java.nio.file.Path temp`。
   - `@Before/@After/@BeforeClass/@AfterClass` → `@BeforeEach/@AfterEach/@BeforeAll/@AfterAll`。
   - `@Test` → `@TestTemplate`（参数化）或保持 `@Test`（非参数化方法）。
   - `Assert.*` → AssertJ `assertThat(...)`。
3. **`spark-extensions` 子项目同步**：同名测试类各加几行（主要是 `@ExtendWith`、`super` 调用等小适配），保持两个子项目的测试基类一致。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogTestBase.java` (删除, -72 lines)

**修改目的**：移除已被 `CatalogTestBase` 取代的 JUnit 4 参数化基类。

**工作逻辑**：原类用 `@RunWith(Parameterized.class)` + `@Parameterized.Parameters` 提供 HIVE/HADOOP/SPARK/REST 四组 catalog 参数，并持有 `@Rule TemporaryFolder`。删除后，所有子类改用 `CatalogTestBase`（JUnit 5 + `ParameterizedTestExtension`）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestCompressionSettings.java` (修改, +60/-30 lines)

**修改目的**：迁移到 JUnit 5 参数化模型，并扩展参数维度。

**工作逻辑**：
- `extends SparkCatalogTestBase` → `extends CatalogTestBase` + `@ExtendWith(ParameterizedTestExtension.class)`。
- 原构造函数注入 `FileFormat format` + `Map properties` 改为 `@Parameter(index=3)` 与 `@Parameter(index=4)` 字段注入（0-2 留给 catalog）。
- `@Parameterized.Parameters(name = "format = {0}, properties = {1}")` → `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, format = {3}, properties = {4}")`，参数矩阵与 `CatalogTestBase` 的 catalog 参数做笛卡尔积。
- `@Rule TemporaryFolder temp` → `@TempDir java.nio.file.Path temp`。
- `@Before/@After/@BeforeClass/@AfterClass` → `@BeforeEach/@AfterEach/@BeforeAll/@AfterAll`；`@Test` → `@TestTemplate`。
- 断言改 AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestRequiredDistributionAndOrdering.java` (修改, +13/-10 lines)

**修改目的**：迁移到 JUnit 5 + `CatalogTestBase`。

**工作逻辑**：基类替换、`@ExtendWith(ParameterizedTestExtension.class)`、`@Test`→`@TestTemplate`、`@After`→`@AfterEach`、断言改 AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestRuntimeFiltering.java` (修改, +28/-22 lines)

**修改目的**：同上迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkCatalogHadoopOverrides.java` (修改, +24/-21 lines)

**修改目的**：同上迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkStagedScan.java` (修改, +16/-10 lines)

**修改目的**：同上迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkTable.java` (修改, +11/-7 lines)

**修改目的**：同上迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (修改, +48/-45 lines)

**修改目的**：同上迁移。

### `spark-extensions` 子项目下 `TestRequiredDistributionAndOrdering`、`TestSparkCatalogHadoopOverrides`、`TestSparkStagedScan`、`TestSparkTable`（各 +3 lines）

**修改目的**：与 spark 子项目同步适配新基类。

**工作逻辑**：每个加 3 行，主要是 `@ExtendWith(ParameterizedTestExtension.class)` 注册、`super` 调用等小调整。

## 总结

本次提交把 Spark 3.4 下继承 `SparkCatalogTestBase` 的 7 个 source 测试类迁移到 JUnit 5：改用 `CatalogTestBase` + `ParameterizedTestExtension` + `@Parameter`/`@Parameters`/`@TestTemplate` 参数化模型，临时目录改 `@TempDir`，断言改 AssertJ；并删除已被取代的 JUnit 4 基类 `SparkCatalogTestBase`。这是 Spark 3.4 测试 JUnit 5 化系列迁移的又一环，与 #12998、#13015 共同推进。
