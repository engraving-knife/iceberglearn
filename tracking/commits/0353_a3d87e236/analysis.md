# 提交 0353：Spark 3.5: Migrate remaining tests in source directory to JUnit5 (#9380)

## 提交信息

- **序号**：0353
- **哈希**：a3d87e2368e56a33d7284f83c7ae64ea462c66d8
- **短哈希**：a3d87e236
- **日期**：2024-01-13 15:11:10 +0100
- **作者**：Chinmay Bhat
- **提交说明**：Spark 3.5: Migrate remaining tests in source directory to JUnit5 (#9380)
- **PR/Issue**：#9380

## 总体目的

本提交是 Iceberg Spark 3.5 集成模块（`spark/v3.5/spark`）从 JUnit 4 向 JUnit 5（Jupiter）迁移工程的"收尾"工作，把 `source/` 目录下剩余 19 个测试类一次性迁移到 JUnit 5。Iceberg 社区此前已经分批迁移了 `spark/v3.5/spark-extensions` 等目录的测试类，并创建了 JUnit 5 版本的新基类 `CatalogTestBase`（对应旧 `SparkCatalogTestBase`）与 `TestBaseWithCatalog`（对应旧 `SparkTestBaseWithCatalog`），但 `source/` 目录下仍有 19 个测试类继承旧基类。本提交完成最后一批迁移，让 Spark 3.5 测试基础设施 JUnit 5 化基本完成。

Spark 3.5 的迁移模式与 Flink 1.18（commit 0352）高度相似，但有几个 Spark 特有的差异：(1) **基类替换有两种**——继承 `SparkCatalogTestBase` 的测试类（参数化 catalog 测试）改为继承 `CatalogTestBase`，继承 `SparkTestBaseWithCatalog` 的测试类（固定 catalog 测试）改为继承 `TestBaseWithCatalog`；前者通过 `@Parameters` 方法枚举多组 catalog 配置（如 `SparkCatalogConfig.SPARK`、`SparkCatalogConfig.HADOOP` 等），后者在 `@Parameters` 中固定使用某一组 catalog 配置（如 `SparkCatalogConfig.HADOOP`）。(2) **基类参数索引不同**——Spark 的 `CatalogTestBase` 用 `@Parameter(index=0)` 注入 `catalogName`、`@Parameter(index=1)` 注入 `implementation`、`@Parameter(index=2)` 注入 `config`（三段式 Spark catalog 标识），子类额外参数从 `index=3` 起递增；Flink 的 `CatalogTestBase` 只有 `index=0/1`（catalogName、baseNamespace），子类从 `index=2` 起。(3) **`@ExtendWith` 显式声明**——Spark 迁移中每个子类都显式 `@ExtendWith(ParameterizedTestExtension.class)`，即使基类已有该注解；Flink 迁移中子类不重复声明（依赖基类继承）。这可能与 JUnit 5 `@ExtendWith` 在某些情况下的继承行为有关，Spark 侧选择显式声明更稳妥。(4) **`@Parameters` 必须显式枚举 catalog 三元组**——旧 `SparkCatalogTestBase` 通过基类机制自动把 catalog 配置（catalogName/implementation/config）注入子类，子类 `@Parameterized.Parameters` 只需声明自己的额外参数（如 `format`、`properties`）；新 `CatalogTestBase` 不再有这种"自动合并"机制，子类 `@Parameters` 必须把 catalog 三元组与自己的额外参数一起枚举。这意味着 `@Parameters` 方法的 `Object[][]` 每一行必须以 `SparkCatalogConfig.SPARK.catalogName(), SparkCatalogConfig.SPARK.implementation(), SparkCatalogConfig.SPARK.properties()` 三元组开头，再追加自己的额外参数。这是迁移中工作量最大的一类改动。

迁移遵循统一的"7 步替换"模式（与 Flink 0352 类似但适配 Spark）：(1) import 替换——`org.junit.*` → `org.junit.jupiter.api.*`（`Before`/`After`/`BeforeClass`/`AfterClass`/`Test` → `BeforeEach`/`AfterEach`/`BeforeAll`/`AfterAll`/`TestTemplate`）+ AssertJ `assertThat`/`assertThatThrownBy` + Iceberg `Parameter`/`Parameters`/`ParameterizedTestExtension` + `org.junit.jupiter.api.extension.ExtendWith` + `org.junit.jupiter.api.io.TempDir`；旧基类 import `SparkCatalogTestBase`/`SparkTestBaseWithCatalog` → 新基类 `CatalogTestBase`/`TestBaseWithCatalog`。(2) 类签名加 `@ExtendWith(ParameterizedTestExtension.class)`，去掉 `@RunWith(Parameterized.class)`，父类替换。(3) 删除构造函数（不再需要 `super(catalogName, implementation, config)` 转发），子类额外参数改为 `@Parameter(index=N)` 字段注入（从 `index=3` 起）。(4) `@Parameterized.Parameters` → `@Parameters`，`Object[]` → `Object[][]`（统一为二维数组），方法体必须把 catalog 三元组与额外参数一起枚举。(5) `@Before`/`@After`/`@BeforeClass`/`@AfterClass` → `@BeforeEach`/`@AfterEach`/`@BeforeAll`/`@AfterAll`，`@Test` → `@TestTemplate`。(6) `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private java.nio.file.Path temp;`（或 `File`），原 `temp.newFile()` 调用改为 `File.createTempFile("junit", null, temp.toFile())`，`temp.newFolder("iceberg-table")` 改为 `temp.resolve("iceberg-table").toFile()`。(7) 断言替换：`Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`，`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`（静态导入），`Assert.assertTrue("msg", cond)` → `assertThat(cond).as("msg").isTrue()`，集合大小断言 `Assert.assertEquals("msg", N, coll.size())` → `assertThat(coll).as("msg").hasSize(N)`，`Iterables.size(iterable)` 直接 `assertThat(iterable).hasSize(N)`。

配套地，部分测试类还做了语义等价的小重构：例如 `TestPositionDeletesTable` 中 `assertEquals("msg", expected, actual)` 改为 `assertThat(actual).as("msg").usingRecursiveComparison().isEqualTo(expected)`——因为 `expected` 与 `actual` 是 `StructLikeSet` 等复杂对象，需要递归比较而非引用相等；`TestSnapshotSelection` 把原本基于 `PlanningMode` 枚举的参数化改为基于 `Map<String, String> properties` 参数化（`@Parameters` 直接枚举 `ImmutableMap.of(DATA_PLANNING_MODE, LOCAL.modeName(), DELETE_PLANNING_MODE, LOCAL.modeName())` 与 `DISTRIBUTED` 两组），消除了构造函数中的转换逻辑。

## 如何达成设计目的

实现路径遵循"7 步替换"机械模式（详见上文），针对每个测试类逐文件应用。关键技术点：(1) **`@Parameters` 重构**——这是工作量最大的部分。原 `SparkCatalogTestBase` 子类的 `@Parameterized.Parameters` 只枚举自己的额外参数（如 `{"parquet", props1}, {"orc", props2}`），catalog 三元组由基类自动注入；新 `CatalogTestBase` 子类的 `@Parameters` 必须完整枚举 catalog 三元组 + 额外参数，如 `{SPARK.catalogName(), SPARK.implementation(), SPARK.properties(), PARQUET, props1}, {SPARK.catalogName(), SPARK.implementation(), SPARK.properties(), ORC, props2}`——每行前 3 个元素固定为 catalog 三元组，从第 4 个起是子类自己的额外参数。`@Parameters` 的 `name` 字符串也相应更新，从 `"format = {0}, properties = {1}"` 改为 `"catalogName = {0}, implementation = {1}, config = {2}, format = {3}, properties = {4}"`（占位符索引必须与参数数组的实际位置对应）。(2) **`@Parameter` 字段注入**——子类额外参数用 `@Parameter(index=3) private FileFormat format;`、`@Parameter(index=4) private Map<String, String> properties;` 等字段注入，`index` 从 3 起（0/1/2 在基类已定义为 catalogName/implementation/config）。(3) **`@TempDir` 适配**——JUnit 4 `TemporaryFolder.newFile()` 返回 `File`，JUnit 5 `@TempDir Path` 没有 `newFile()` 方法，需用 `File.createTempFile("junit", null, temp.toFile())` 或 `temp.resolve("name").toFile()` 等价替代。(4) **`@AfterParam` → `@AfterEach`**——部分测试类（如 `TestCompressionSettings`）原本用 JUnit 4 的 `@Parameterized.AfterParam` 在每组参数化测试结束后清理（如 `DROP TABLE IF EXISTS`），JUnit 5 没有 `@AfterParam` 等价物，改为 `@AfterEach`（每个测试方法后清理），语义略宽（每个方法都清理而非每组参数清理），但对 `DROP TABLE IF EXISTS` 这种幂等操作不影响正确性。(5) **断言现代化**——除机械替换 `Assert.assertEquals` → `assertThat(...).isEqualTo(...)` 外，部分场景使用 AssertJ 的更语义化断言：`hasSize(N)` 替代 `isEqualTo(N)` 用于集合大小、`hasSizeGreaterThan(1)` 替代 `Iterables.size(...) > 1` + `assertTrue`、`usingRecursiveComparison().isEqualTo(expected)` 替代 `assertEquals` 用于复杂对象递归比较、`assertThatThrownBy` 静态导入替代 `Assertions.assertThatThrownBy` 限定调用。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestCompressionSettings.java`（106 行变更）

**修改目的**：把压缩配置测试类（5 组参数化：parquet×2 + orc×2 + avro×1）从 JUnit 4 迁移到 JUnit 5，验证不同 FileFormat 与压缩属性的写入正确性。

**工作逻辑**：(1) 新增 import `static org.apache.iceberg.FileFormat.AVRO/ORC`（原代码用字符串 `"parquet"`/`"orc"`，迁移后直接用 `PARQUET`/`ORC`/`AVRO` 枚举常量，更类型安全）；新增 `import org.apache.iceberg.Parameter`/`ParameterizedTestExtension`/`Parameters`、`import org.apache.iceberg.spark.CatalogTestBase`、`import org.junit.jupiter.api.AfterAll`/`AfterEach`/`BeforeAll`/`TestTemplate`/`extension.ExtendWith`/`io.TempDir`、`import static org.assertj.core.api.Assertions.assertThat`。(2) 类签名 `@RunWith(Parameterized.class) public class TestCompressionSettings extends SparkCatalogTestBase` → `@ExtendWith(ParameterizedTestExtension.class) public class TestCompressionSettings extends CatalogTestBase`。(3) 删除构造函数 `public TestCompressionSettings(String format, ImmutableMap properties) { super(SPARK.catalogName(), SPARK.implementation(), SPARK.properties()); this.format = FileFormat.fromString(format); this.properties = properties; }`，改为 `@Parameter(index = 3) private FileFormat format;` 与 `@Parameter(index = 4) private Map<String, String> properties;`——注意类型从 `String` + `FileFormat.fromString(...)` 解析升级为直接 `FileFormat` 枚举注入，避免运行时解析失败风险。(4) `@Parameterized.Parameters(name = "format = {0}, properties = {1}")` → `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, format = {3}, properties = {4}")`，方法体每行前 3 个元素补上 `SparkCatalogConfig.SPARK.catalogName()/implementation()/properties()` 三元组，参数值从字符串 `"parquet"` 改为枚举 `PARQUET`。(5) `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private java.nio.file.Path temp;`。(6) `@BeforeClass startSpark()` → `@BeforeAll startSpark()`，`@AfterClass stopSpark()` → `@AfterAll stopSpark()`。(7) `@Parameterized.AfterParam clearSourceCache()` → `@AfterEach afterEach()`——语义从"每组参数后执行一次"变为"每个测试方法后执行一次"，对 `DROP TABLE IF EXISTS` 幂等操作无影响。(8) `@Test` → `@TestTemplate`。(9) `Assertions.assertThat(getCompressionType(inputFile)).isEqualToIgnoringCase(...)` → `assertThat(getCompressionType(inputFile)).isEqualToIgnoringCase(...)`（静态导入替代限定调用）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestFilteredScan.java`（135 行变更）

**修改目的**：把过滤扫描测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。该类继承 `SparkCatalogTestBase` → `CatalogTestBase`，参数化方式从 `@RunWith(Parameterized.class)` + 构造函数改为 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameter` 字段注入。`@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`（管理 Spark session 生命周期），`@Before`/`@After` → `@BeforeEach`/`@AfterEach`。`@Test` → `@TestTemplate`。`Assert.assertEquals`/`Assert.assertTrue` → AssertJ `assertThat(...).isEqualTo(...)`/`.isTrue()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIdentityPartitionData.java`（117 行变更）

**修改目的**：把 identity 分区数据测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类 `SparkCatalogTestBase` → `CatalogTestBase`，删除构造函数，`@Parameter(index=3)` 注入 `FileFormat format`。`@Parameters` 方法枚举 catalog 三元组 + format。`@Rule TemporaryFolder` → `@TempDir Path`。生命周期与测试注解按模式替换。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestMetadataTablesWithPartitionEvolution.java`（95 行变更）

**修改目的**：把分区演化的元数据表测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类 `SparkCatalogTestBase` → `CatalogTestBase`，`@Parameter` 字段注入。`@Before`/`@After`/`@Test` → `@BeforeEach`/`@AfterEach`/`@TestTemplate`。`@Rule TemporaryFolder` → `@TempDir Path`。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPartitionPruning.java`（99 行变更）

**修改目的**：把分区裁剪测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类 `SparkCatalogTestBase` → `CatalogTestBase`。`@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`。`@Test` → `@TestTemplate`。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPartitionValues.java`（117 行变更）

**修改目的**：把分区值测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类 `SparkCatalogTestBase` → `CatalogTestBase`，`@Parameter` 字段注入。`@Rule TemporaryFolder` → `@TempDir Path`。生命周期与测试注解按模式替换。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesTable.java`（297 行变更，最大）

**修改目的**：把 position deletes 表测试类（最复杂的测试，覆盖 position delete 文件读写、分区、split、扫描等场景）迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"，关键点：(1) 类签名 `@RunWith(Parameterized.class) public class TestPositionDeletesTable extends SparkCatalogTestBase` → `@ExtendWith(ParameterizedTestExtension.class) public class TestPositionDeletesTable extends CatalogTestBase`。(2) 删除构造函数，`private final FileFormat format` → `@Parameter(index = 3) private FileFormat format;`。(3) `@Parameterized.Parameters(name = "formatVersion = {0}, catalogName = {1}, implementation = {2}, config = {3}, fileFormat = {4}")` → `@Parameters(name = "catalogName = {1}, implementation = {2}, config = {3}, fileFormat = {4}")`——注意 `name` 字符串移除了 `formatVersion = {0}` 占位符（因为 `formatVersion` 已不在参数数组中，或已通过其他方式管理），索引从 1 开始标注 catalog 三元组。(4) `@Rule public TemporaryFolder temp = new TemporaryFolder();` → 删除该字段，改用基类 `@TempDir` 提供的临时目录。所有 `temp.newFile()` 调用改为 `File.createTempFile("junit", null, temp.toFile())`——如 `FileHelpers.writeDeleteFile(tab, Files.localOutput(temp.newFile()), ...)` → `FileHelpers.writeDeleteFile(tab, Files.localOutput(File.createTempFile("junit", null, temp.toFile())), ...)`，需要 `import java.io.File`。(5) `@Test` → `@TestTemplate`。(6) 断言替换：`Assert.assertEquals("Position Delete table should contain expected rows", expected, actual)` → `assertThat(actual).as("Position Delete table should contain expected rows").isEqualTo(expected)`；`Assert.assertTrue("Position delete scan should produce more than one split", Iterables.size(deleteTable.newBatchScan().planTasks()) > 1)` → `assertThat(deleteTable.newBatchScan().planTasks()).as("Position delete scan should produce more than one split").hasSizeGreaterThan(1)`（AssertJ 集合专属断言，比 `Iterables.size(...) > 1` + `assertTrue` 更语义化）；`Assert.assertEquals("Position delete scan should produce one split", 1, Iterables.size(deleteTable.newBatchScan().planTasks()))` → `assertThat(deleteTable.newBatchScan().planTasks()).as("Position delete scan should produce one split").hasSize(1)`。复杂对象比较：`assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").usingRecursiveComparison().isEqualTo(expected)`——因为 `StructLikeSet` 的 `equals` 实现可能不完整，需要 AssertJ 递归比较字段值确保语义正确。(7) 导入清理：移除 `org.apache.iceberg.relocated.com.google.common.collect.Iterables`（被 AssertJ `hasSize`/`hasSizeGreaterThan` 替代）、`org.junit.Assert`/`Rule`/`Test`/`rules.TemporaryFolder`/`runner.RunWith`/`runners.Parameterized`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestReadProjection.java`（42 行变更）

**修改目的**：把读取投影抽象基类迁移到 JUnit 5。

**工作逻辑**：(1) 新增 `import java.nio.file.Path`、`import org.apache.iceberg.FileFormat`、`import org.apache.iceberg.Parameter`/`ParameterizedTestExtension`、`import org.junit.jupiter.api.TestTemplate`/`extension.ExtendWith`/`io.TempDir`。(2) 类签名加 `@ExtendWith(ParameterizedTestExtension.class)`。(3) 删除构造函数 `TestReadProjection(String format) { this.format = format; }`，`final String format` → `@Parameter(index = 0) protected FileFormat format;`——注意类型从 `String` 升级为 `FileFormat` 枚举（更类型安全），且字段从 package-private `final` 改为 `protected`（让子类能访问，因为 `format` 在 `writeAndRead` 等方法中使用）。(4) `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir protected Path temp;`——同样提升为 `protected` 让子类可用。(5) `@Test` → `@TestTemplate`（约 7 处测试方法）。该类是抽象基类（有抽象方法 `writeAndRead`），子类（如 `TestSparkReadProjection`）需相应迁移。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestRequiredDistributionAndOrdering.java`（37 行变更）

**修改目的**：把分布与排序要求测试类迁移到 JUnit 5。

**工作逻辑**：(1) 新增 `import static org.assertj.core.api.Assertions.assertThatThrownBy`，删除 `import java.util.Map`（不再在构造函数签名中使用）、`import org.assertj.core.api.Assertions`、`import org.junit.After`/`Test`。(2) 类签名 `public class TestRequiredDistributionAndOrdering extends SparkCatalogTestBase` → `public class TestRequiredDistributionAndOrdering extends CatalogTestBase`——注意该类没有 `@ExtendWith(ParameterizedTestExtension.class)`，因为它继承自 `CatalogTestBase`（已 `@ExtendWith`），且自身没有额外 `@Parameter` 字段（只用 catalog 三元组）。(3) 删除构造函数 `public TestRequiredDistributionAndOrdering(String catalogName, String implementation, Map<String, String> config) { super(catalogName, implementation, config); }`——catalog 三元组由基类的 `@Parameter(index=0/1/2)` 注入。(4) `@After dropTestTable()` → `@AfterEach dropTestTable()`。(5) `@Test` → `@TestTemplate`（约 9 处）。(6) `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`（静态导入替代限定调用）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestRuntimeFiltering.java`（78 行变更）

**修改目的**：把运行时过滤测试类（基于 `PlanningMode` 参数化）迁移到 JUnit 5，同时把基类从 `SparkTestBaseWithCatalog` 改为 `TestBaseWithCatalog`。

**工作逻辑**：(1) 新增 `import static org.assertj.core.api.Assertions.assertThat`、`import org.apache.iceberg.Parameter`/`ParameterizedTestExtension`/`Parameters`、`import org.apache.iceberg.spark.SparkCatalogConfig`、`import org.apache.iceberg.spark.TestBaseWithCatalog`、`import org.junit.jupiter.api.AfterEach`/`TestTemplate`/`extension.ExtendWith`，删除 `import org.apache.iceberg.spark.SparkTestBaseWithCatalog`、`import org.junit.After`/`Assert`/`Test`/`runner.RunWith`/`runners.Parameterized`。(2) 类签名 `@RunWith(Parameterized.class) public class TestRuntimeFiltering extends SparkTestBaseWithCatalog` → `@ExtendWith(ParameterizedTestExtension.class) public class TestRuntimeFiltering extends TestBaseWithCatalog`。(3) `@Parameterized.Parameters(name = "planningMode = {0}") public static Object[] parameters() { return new Object[] {LOCAL, DISTRIBUTED}; }` → `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, planningMode = {3}") public static Object[][] parameters() { return new Object[][] { {HADOOP.catalogName(), HADOOP.implementation(), HADOOP.properties(), LOCAL}, {HADOOP.catalogName(), HADOOP.implementation(), HADOOP.properties(), DISTRIBUTED} }; }`——`Object[]` 改为 `Object[][]`，每行补上 catalog 三元组。(4) 删除构造函数 `public TestRuntimeFiltering(PlanningMode planningMode) { this.planningMode = planningMode; }`，改为 `@Parameter(index = 3) private PlanningMode planningMode;`。(5) `@After` → `@AfterEach`，`@Test` → `@TestTemplate`（约 9 处）。(6) 断言：`Assert.assertEquals(errorMessage, expectedFilterCount, actualFilterCount)` → `assertThat(actualFilterCount).as(errorMessage).isEqualTo(expectedFilterCount)`；`Assert.assertEquals("Deleted unexpected number of files", expectedDeletedFileCount, deletedFileLocations.size())` → `assertThat(deletedFileLocations).as("Deleted unexpected number of files").hasSize(expectedDeletedFileCount)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSnapshotSelection.java`（178 行变更）

**修改目的**：把快照选择测试类（基于 `PlanningMode` 参数化）迁移到 JUnit 5，并把参数化方式从枚举改为 properties map。

**工作逻辑**：(1) 新增 `import static org.assertj.core.api.Assertions.assertThat`/`assertThatThrownBy`、`import java.nio.file.Path`、`import org.apache.iceberg.Parameter`/`ParameterizedTestExtension`/`Parameters`、`import org.junit.jupiter.api.AfterAll`/`BeforeAll`/`TestTemplate`/`extension.ExtendWith`/`io.TempDir`，删除 `import org.apache.iceberg.PlanningMode`、`import org.apache.iceberg.relocated.com.google.common.collect.Iterables`、`import org.assertj.core.api.Assertions`、`import org.junit.AfterClass`/`Assert`/`BeforeClass`/`Rule`/`Test`/`rules.TemporaryFolder`/`runner.RunWith`/`runners.Parameterized`。(2) 类签名加 `@ExtendWith(ParameterizedTestExtension.class)`——该类不继承任何基类（独立管理 Spark session）。(3) **参数化方式重构**：原 `@Parameterized.Parameters(name = "planningMode = {0}") public static Object[] parameters() { return new Object[] {LOCAL, DISTRIBUTED}; }` + 构造函数 `public TestSnapshotSelection(PlanningMode planningMode) { this.properties = ImmutableMap.of(DATA_PLANNING_MODE, planningMode.modeName(), DELETE_PLANNING_MODE, planningMode.modeName()); }`——把 `PlanningMode` 枚举在构造函数中转为 properties map；新 `@Parameters(name = "properties = {0}") public static Object[][] parameters() { return new Object[][] { {ImmutableMap.of(DATA_PLANNING_MODE, LOCAL.modeName(), DELETE_PLANNING_MODE, LOCAL.modeName())}, {ImmutableMap.of(DATA_PLANNING_MODE, DISTRIBUTED.modeName(), DELETE_PLANNING_MODE, DISTRIBUTED.modeName())} }; }` + `@Parameter(index = 0) private Map<String, String> properties;`——直接把 properties map 作为参数，消除构造函数中的转换逻辑，更直接。(4) `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`。(5) `temp.newFolder("iceberg-table").toString()` → `temp.resolve("iceberg-table").toFile().toString()`——JUnit 5 `@TempDir Path` 用 `resolve` 创建子目录。(6) `@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`。(7) `@Test` → `@TestTemplate`。(8) 断言：`Assert.assertEquals("Expected 2 snapshots", 2, Iterables.size(table.snapshots()))` → `assertThat(table.snapshots()).as("Expected 2 snapshots").hasSize(2)`；`Assert.assertEquals("Current snapshot rows should match", expectedRecords, currentSnapshotRecords)` → `assertThat(currentSnapshotRecords).as("Current snapshot rows should match").isEqualTo(expectedRecords)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkCatalogHadoopOverrides.java`（63 行变更）

**修改目的**：把 Spark catalog Hadoop 配置覆盖测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类 `SparkCatalogTestBase` → `CatalogTestBase`，删除构造函数。新增 `import static org.assertj.core.api.Assertions.assertThat`，删除 `import java.util.Map`、`import org.junit.Assert`/`Test`/`runner.RunWith`/`runners.Parameterized`。`@Test` → `@TestTemplate`。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataWrite.java`（207 行变更）

**修改目的**：把 Spark 数据写入测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类 `SparkCatalogTestBase` → `CatalogTestBase`，`@Parameter(index=3)` 注入 `FileFormat format`。`@Parameters` 枚举 catalog 三元组 + format。`@Rule TemporaryFolder` → `@TempDir Path`。`@Before`/`@After`/`@Test` → `@BeforeEach`/`@AfterEach`/`@TestTemplate`。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`（82 行变更）

**修改目的**：把 Spark 元数据列测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类 `SparkCatalogTestBase` → `CatalogTestBase`。`@Before`/`@After`/`@Test` → `@BeforeEach`/`@AfterEach`/`@TestTemplate`。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReadProjection.java`（47 行变更）

**修改目的**：把 Spark 读取投影测试类（继承抽象基类 `TestReadProjection`）迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。该类继承 `TestReadProjection`（也同步迁移），自身参数化方式从构造函数改为 `@Parameter`。`@Test` → `@TestTemplate`。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderWithBloomFilter.java`（68 行变更）

**修改目的**：把 BloomFilter 读取测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类替换，`@Parameter` 字段注入。`@Before`/`@After`/`@Test` → `@BeforeEach`/`@AfterEach`/`@TestTemplate`。断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`（206 行变更）

**修改目的**：把 Spark scan 测试类（基于 `format` 参数化，3 组：parquet/avro/orc）迁移到 JUnit 5，同时把基类从 `SparkTestBaseWithCatalog` 改为 `TestBaseWithCatalog`。

**工作逻辑**：(1) 新增 `import static org.assertj.core.api.Assertions.assertThat`、`import org.apache.iceberg.Parameter`/`ParameterizedTestExtension`/`Parameters`、`import org.apache.iceberg.spark.SparkCatalogConfig`、`import org.apache.iceberg.spark.TestBaseWithCatalog`，删除 `import org.apache.iceberg.spark.SparkTestBaseWithCatalog`、`import org.assertj.core.api.Assertions`、`import org.junit.After`/`Assert`/`Before`/`Test`/`runner.RunWith`/`runners.Parameterized`。(2) 类签名 `@RunWith(Parameterized.class) public class TestSparkScan extends SparkTestBaseWithCatalog` → `@ExtendWith(ParameterizedTestExtension.class) public class TestSparkScan extends TestBaseWithCatalog`。(3) `@Parameterized.Parameters(name = "format = {0}") public static Object[] parameters() { return new Object[] {"parquet", "avro", "orc"}; }` → `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, format = {3}") public static Object[][] parameters() { return new Object[][] { {HADOOP.catalogName(), HADOOP.implementation(), HADOOP.properties(), "parquet"}, {HADOOP.catalogName(), HADOOP.implementation(), HADOOP.properties(), "avro"}, {HADOOP.catalogName(), HADOOP.implementation(), HADOOP.properties(), "orc"} }; }`——`Object[]` 改为 `Object[][]`，每行补上 HADOOP catalog 三元组。(4) 删除构造函数 `public TestSparkScan(String format) { this.format = format; }`，改为 `@Parameter(index = 3) private String format;`。(5) `@Before` → `@BeforeEach`，`@After` → `@AfterEach`，`@Test` → `@TestTemplate`。(6) 断言改 AssertJ `assertThat(...)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java`（165 行变更）

**修改目的**：把结构化流式读取测试类（3 组参数化）迁移到 JUnit 5。

**工作逻辑**：(1) 新增 `import static org.assertj.core.api.Assertions.assertThat`、`import org.apache.iceberg.ParameterizedTestExtension`、`import org.junit.jupiter.api.AfterEach`/`BeforeAll`/`BeforeEach`/`TestTemplate`/`extension.ExtendWith`，删除 `import org.assertj.core.api.Assertions`、`import org.junit.After`/`Assert`/`Before`/`BeforeClass`/`Test`/`runner.RunWith`/`runners.Parameterized`。(2) 类签名 `@RunWith(Parameterized.class) public final class TestStructuredStreamingRead3 extends SparkCatalogTestBase` → `@ExtendWith(ParameterizedTestExtension.class) public final class TestStructuredStreamingRead3 extends CatalogTestBase`——保留 `final` 修饰符。(3) 删除构造函数 `public TestStructuredStreamingRead3(String catalogName, String implementation, Map<String, String> config) { super(catalogName, implementation, config); }`——该类无额外参数，catalog 三元组由基类 `@Parameter(index=0/1/2)` 注入。(4) `@BeforeClass setupSpark()` → `@BeforeAll setupSpark()`，`@Before setupTable()` → `@BeforeEach setupTable()`，`@After` → `@AfterEach`，`@Test` → `@TestTemplate`。(5) 断言改 AssertJ。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestTimestampWithoutZone.java`（62 行变更）

**修改目的**：把无时区 timestamp 测试类迁移到 JUnit 5。

**工作逻辑**：执行"7 步替换"。基类 `SparkCatalogTestBase` → `CatalogTestBase`。`@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`。`@Test` → `@TestTemplate`。断言改 AssertJ。

## 小结

本次提交完成 Iceberg Spark 3.5 集成模块 `source/` 目录下 19 个测试类从 JUnit 4 向 JUnit 5 的迁移，是 Spark 3.5 测试基础设施 JUnit 5 化的收尾工作。迁移遵循统一的"7 步替换"模式：(1) import 替换（`org.junit.*` → `org.junit.jupiter.api.*` + AssertJ + Iceberg `Parameter`/`Parameters`/`ParameterizedTestExtension` + `ExtendWith` + `TempDir`，旧基类 `SparkCatalogTestBase`/`SparkTestBaseWithCatalog` → 新基类 `CatalogTestBase`/`TestBaseWithCatalog`）；(2) 类签名加 `@ExtendWith(ParameterizedTestExtension.class)`，去 `@RunWith(Parameterized.class)`，父类替换；(3) 删除构造函数，子类额外参数改为 `@Parameter(index=N)` 字段注入（从 `index=3` 起，0/1/2 是 catalog 三元组）；(4) `@Parameterized.Parameters` → `@Parameters`，`Object[]` → `Object[][]`，方法体必须把 catalog 三元组（`SparkCatalogConfig.SPARK.catalogName()/implementation()/properties()` 或 `HADOOP` 等）与额外参数一起枚举（这是工作量最大的改动，因为旧 `SparkCatalogTestBase` 自动注入 catalog 三元组，新 `CatalogTestBase` 不再自动合并）；(5) `@Before`/`@After`/`@BeforeClass`/`@AfterClass` → `@BeforeEach`/`@AfterEach`/`@BeforeAll`/`@AfterAll`，`@Test` → `@TestTemplate`；(6) `@Rule TemporaryFolder` → `@TempDir Path`，`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`，`temp.newFolder("x")` → `temp.resolve("x").toFile()`；(7) `Assert.assertEquals`/`assertTrue` → AssertJ `assertThat(...).isEqualTo(...)`/`.isTrue()`，`Assertions.assertThatThrownBy` → `assertThatThrownBy`（静态导入），集合大小用 `hasSize(N)`/`hasSizeGreaterThan(N)` 替代 `Iterables.size(...)` + `assertEquals`/`assertTrue`，复杂对象用 `usingRecursiveComparison().isEqualTo(...)`。Spark 3.5 迁移与 Flink 1.18 迁移（commit 0352）的关键差异：Spark 用 3 段 catalog 三元组（catalogName/implementation/config），Flink 用 2 段（catalogName/baseNamespace）；Spark 子类显式 `@ExtendWith`，Flink 依赖基类继承；Spark `@Parameters` 必须完整枚举 catalog 三元组，Flink 同样需要但三元组更短。本次迁移涉及 19 个测试类、约 1183 行新增 / 1018 行删除，是 Spark 3.5 模块测试基础设施现代化的关键一步。
