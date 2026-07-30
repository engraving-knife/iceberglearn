# 提交 0334：Spark 3.5: Migrate tests to JUnit5 (#9417)

## 提交信息

- **序号**：0334
- **哈希**：460282433756620310fd346e519b18b74a745305
- **短哈希**：460282433
- **日期**：2024-01-05 17:18:20 +0100
- **作者**：Chinmay Bhat
- **提交说明**：Spark 3.5: Migrate tests to JUnit5 (#9417)
- **PR/Issue**：#9417

## 总体目的

本提交是 Iceberg Spark 3.5 模块 JUnit 4 → JUnit 5 系列迁移的延续批次，覆盖 `spark/v3.5/spark/src/test/java/` 下 `org/apache/iceberg/` 与 `org/apache/iceberg/spark/` 两个目录共 25 个文件（685 行新增、689 行删除，净减 4 行）。本次迁移的文件分布：8 个位于 `org/apache/iceberg/` 顶层（`TaskCheckHelper`、`ValidationHelpers` 两个测试辅助类，以及 `TestDataFileSerialization`、`TestFileIOSerialization`、`TestHadoopMetricsContextSerialization`、`TestManifestFileSerialization`、`TestScanTaskSerialization`、`TestTableSerialization` 等序列化测试类）；17 个位于 `org/apache/iceberg/spark/`（包括 `TestBase`、`TestBaseWithCatalog` 两个新的 JUnit 5 基类，以及 `TestChangelogIterator`、`TestFileRewriteCoordinator`、`TestFunctionCatalog`、`TestSpark3Util`、`TestSparkCachedTableCatalog`、`TestSparkCatalogOperations`、`TestSparkCompressionUtil`、`TestSparkDistributionAndOrderingUtil`、`TestSparkFilters`、`TestSparkSchemaUtil`、`TestSparkSessionCatalog`、`TestSparkTableUtil`、`TestSparkV2Filters`、`TestSparkValueConverter`、`TestSparkWriteConf` 等 Spark 集成工具/功能测试类）。这批文件在迁移前仍依赖 JUnit 4 的 `@RunWith(Parameterized.class)`、`@Rule`/`@ClassRule` 体系与 `org.junit.Assert` 断言，是 Spark 3.5 模块最后一批未迁移的核心测试。

迁移动机与 #9341/#9342/#9366 等前序批次一致：统一到 JUnit 5 现代框架；用 Iceberg 自定义 `ParameterizedTestExtension` + `@Parameter` + `@Parameters` 替换 JUnit 4 `@RunWith(Parameterized.class)` + 构造器注入；用 `@TempDir Path` 替换 `@Rule TemporaryFolder`；统一 AssertJ 静态导入断言风格（`assertThat` / `assertThatThrownBy`）；将基类体系从 `SparkTestBase`/`SparkTestBaseWithCatalog`/`SparkCatalogTestBase` 切换到 `TestBase`/`TestBaseWithCatalog`/`CatalogTestBase`（这些 JUnit 5 基类在 #9342 中已建好）；将参数化测试方法的 `@Test` 改为 `@TestTemplate` 以便扩展对每组参数重复执行。

本批次相比前序有几处特别值得关注的复杂改造：(1) `TestTableSerialization` 涉及参数化测试改造（`isObjectStoreEnabled` 参数从构造器注入改为 `@Parameter` 字段注入）；(2) `TestSparkCachedTableCatalog` 原本通过 `super(SparkCatalogConfig.HIVE)` 构造器调用固定单个 catalog，迁移后改为通过 `@Parameters` 提供 `Object[][]` 形式的单组参数（HIVE），让 `TestBaseWithCatalog` 的参数化机制能识别；(3) `TestFunctionCatalog` 把原本在无参构造器中的初始化逻辑（`castToFunctionCatalog(catalogName)`）迁到 `@BeforeEach before()` 方法中并显式调用 `super.before()`，因为新基类 `TestBaseWithCatalog` 的 `before()` 负责 catalog 上下文初始化，子类必须先调用父类 `before()` 才能拿到 `catalogName`；(4) 多个测试类（`TestSparkWriteConf`、`TestFunctionCatalog` 等）新增 `super.before()` 调用以衔接基类生命周期。

## 如何达成设计目的

整体设计思路与前序 JUnit 5 迁移批次一致——保持测试逻辑不变，仅替换测试基础设施 API。具体改造分七类：(1) **import 替换**：`org.junit.Test` → `org.junit.jupiter.api.Test`、`org.junit.Before`/`After` → `BeforeEach`/`AfterEach`、`org.junit.BeforeClass`/`AfterClass` → `BeforeAll`/`AfterAll`、`org.junit.Rule`/`ClassRule`/`runner.RunWith`/`runners.Parameterized`/`rules.TemporaryFolder` → `junit.jupiter.api.extension.ExtendWith`/`io.TempDir` 等；移除 `org.junit.Assert`、`org.assertj.core.api.Assertions`，新增 `static import org.assertj.core.api.Assertions.assertThat`/`assertThatThrownBy`。(2) **临时目录替换**：`@Rule public TemporaryFolder temp = new TemporaryFolder()` → `@TempDir private Path temp`；`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`；`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`；`temp.getRoot()` → `temp.toFile()`。(3) **基类切换**：`extends SparkTestBase` → `extends TestBase`、`extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`、`extends SparkCatalogTestBase` → `extends CatalogTestBase`。(4) **参数化测试改造**：类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；`@Parameterized.Parameters` → `@Parameters`（`org.apache.iceberg.Parameters`）；构造器参数 → `@Parameter` 字段；参数方法返回类型从 `Object[]` 升级为 `List<String>` / `Object[][]` / 集合类型；测试方法 `@Test` → `@TestTemplate`。(5) **断言替换**：`Assert.assertEquals("m", e, a)` → `assertThat(a).as("m").isEqualTo(e)`；`Assert.assertTrue`/`assertFalse`/`assertNotNull`/`assertNull` → `assertThat(...).isTrue()`/`isFalse()`/`isNotNull()`/`isNull()`；`Assert.assertArrayEquals` → `isEqualTo`；`Assertions.assertThat(x).as(...).isInstanceOf(...)` → 静态导入 `assertThat(x).as(...).isInstanceOf(...)`；`Assertions.assertThatThrownBy` → `assertThatThrownBy`；`org.junit.Assert.assertThrows(msg, Class, supplier)` → `assertThatThrownBy(supplier).isInstanceOf(Class).hasMessage(msg)`；`assertThat(c1).hasSameSizeAs(c2)` / `hasSize(n)` / `containsEntry(k, v)` / `isNotEmpty()` 等 AssertJ 集合断言被广泛采用以替代 `Assert.assertEquals(c1.size(), c2.size())` 等冗长写法。(6) **生命周期改造**：`@Before`/`@After` → `@BeforeEach`/`@AfterEach`；`@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`；子类 `@BeforeEach` 方法显式调用 `super.before()` 以衔接新基类 `TestBaseWithCatalog.before()` 中的 catalog 上下文初始化。(7) **构造器链清理**：原继承 `SparkCatalogTestBase` 的子类有 `(String catalogName, String implementation, Map<String,String> config)` 构造器，迁移到 `CatalogTestBase` 后该构造器整体删除（基类改为字段注入）；原继承 `SparkTestBaseWithCatalog` 的子类有 `super(SparkCatalogConfig.HIVE)` 这种固定单 catalog 的构造器调用，迁移后改为通过 `@Parameters` 提供单组 `Object[][]` 参数让基类参数化机制识别。

## 修改详情

本次迁移涉及 25 个文件。下面先说明迁移模式，再对关键文件单独展开。

### 迁移模式总览

**1. 注解替换**：`org.junit.Test` → `org.junit.jupiter.api.Test`；`@Before`/`@After` → `@BeforeEach`/`@AfterEach`；`@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`；新增 `@TestTemplate` 用于参数化测试方法。

**2. 临时目录替换**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`；`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`；`temp.newFile("input.m0.avro")` → `File.createTempFile("input.m0", ".avro", temp.toFile())`（注意前缀/后缀拆分）；`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`；`temp.getRoot()` → `temp.toFile()`。

**3. 基类切换**：`SparkTestBase` → `TestBase`、`SparkTestBaseWithCatalog` → `TestBaseWithCatalog`、`SparkCatalogTestBase` → `CatalogTestBase`。

**4. 参数化测试改造**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；`@Parameterized.Parameters` → `@Parameters`；构造器参数 → `@Parameter` 字段；`@Test` → `@TestTemplate`。

**5. 断言替换**：`Assert.*` → `assertThat(...)`（AssertJ 静态导入）；`Assertions.assertThat(x)` → `assertThat(x)`；`assertThrows` → `assertThatThrownBy`；广泛使用 `hasSize` / `hasSameSizeAs` / `containsEntry` / `isNotEmpty` / `isInstanceOf` 等 AssertJ 流式断言。

**6. 子类构造器清理**：移除 `(String catalogName, String implementation, Map<String,String> config)` 等参数化构造器，以及 `super(SparkCatalogConfig.HIVE)` 等单 catalog 固定调用。

**7. 子类 `@BeforeEach` 显式调用 `super.before()`**：当子类重写 `before()` 时必须先调用 `super.before()`，让基类 `TestBaseWithCatalog.before()` 完成 catalog 上下文初始化（catalogName 字段赋值等），子类才能安全引用 `catalogName`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBase.java`

**修改目的**：将 JUnit 5 基类 `TestBase` 的断言从 `Assertions.assertThat` 全限定写法改为静态导入 `assertThat`。

**工作逻辑**：新增 `import static org.assertj.core.api.Assertions.assertThat;`，移除 `import org.assertj.core.api.Assertions;`。`scalarSql` 方法中两处断言改写：`Assertions.assertThat(rows.size()).as("Scalar SQL should return one row").isEqualTo(1)` → `assertThat(rows.size()).as("Scalar SQL should return one row").isEqualTo(1)`；`Assertions.assertThat(row.length).as("Scalar SQL should return one value").isEqualTo(1)` → `assertThat(row.length).as(...).isEqualTo(1)`。该类本身已是 JUnit 5 风格（用 `@BeforeAll`/`@AfterAll`），本次仅做断言风格统一。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java`

**修改目的**：将 JUnit 5 基类 `TestBaseWithCatalog` 的断言统一为 AssertJ 静态导入风格。

**工作逻辑**：新增 `import static org.assertj.core.api.Assertions.assertThat;`，移除 `import org.assertj.core.api.Assertions;`。`@BeforeAll createWarehouse()` 中 `Assertions.assertThat(warehouse.delete()).isTrue()` → `assertThat(warehouse.delete()).isTrue()`；`@AfterAll dropWarehouse()` 中 `Assertions.assertThat(fs.delete(warehousePath, true)).as("Failed to delete " + warehousePath).isTrue()` → 单行紧凑形式 `assertThat(fs.delete(warehousePath, true)).as("Failed to delete " + warehousePath).isTrue()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/TaskCheckHelper.java`

**修改目的**：将测试辅助类 `TaskCheckHelper` 的断言迁移到 AssertJ 静态导入风格。这是本批次中改动量第二大的辅助类（99 行变更）。

**工作逻辑**：新增 `import static org.assertj.core.api.Assertions.assertThat;`，移除 `import org.junit.Assert;`。`assertEquals(FileScanTask expected, FileScanTask actual)` 方法中所有 `Assert.assertEquals("msg", expected.x(), actual.x())` 改为 `assertThat(actual.x()).as("msg").isEqualTo(expected.x())`——参数顺序按 AssertJ "actual first" 风格颠倒，例如 `Assert.assertEquals("PartitionSpec doesn't match", expected.spec(), actual.spec())` → `assertThat(actual.spec()).as("PartitionSpec doesn't match").isEqualTo(expected.spec())`。集合尺寸断言 `Assert.assertEquals("The number of file scan tasks should match", expectedTasks.size(), actualTasks.size())` 改为 `assertThat(actualTasks).as("The number of file scan tasks should match").hasSameSizeAs(expectedTasks)`，更可读。`assertEquals(DataFile expected, DataFile actual)` 方法中 12 处 `Assert.assertEquals` 同样逐条改写，覆盖 path/format/partition/recordCount/fileSizeInBytes/valueCounts/nullValueCounts/lowerBounds/upperBounds/keyMetadata/splitOffsets 等字段。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/TestDataFileSerialization.java`

**修改目的**：将 DataFile 序列化测试迁移到 JUnit 5，临时目录改用 `@TempDir Path`。

**工作逻辑**：`import org.junit.Rule;`/`Test;`/`rules.TemporaryFolder;` 与 `org.junit.Assert`、`org.assertj.core.api.Assertions` 全部移除，新增 `import org.junit.jupiter.api.Test;`、`import org.junit.jupiter.api.io.TempDir;`、`import static org.assertj.core.api.Assertions.assertThat;`、`import java.nio.file.Path;`。`@Rule public TemporaryFolder temp = new TemporaryFolder()` → `@TempDir private Path temp`。`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`，并紧跟 `Assert.assertTrue(data.delete())` → `assertThat(data.delete()).isTrue()`。`temp.getRoot()` → `temp.toFile()`。`Assertions.assertThat(obj).as("Should be a DataFile").isInstanceOf(DataFile.class)` → 静态导入 `assertThat(obj).as("Should be a DataFile").isInstanceOf(DataFile.class)`（去掉 `Assertions.` 前缀）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/TestFileIOSerialization.java`

**修改目的**：将 FileIO 序列化测试迁移到 JUnit 5。

**工作逻辑**：`@Rule public TemporaryFolder temp` → `@TempDir private Path temp`；`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`（新增 `import java.nio.file.Files;`、`import java.nio.file.Path;`）；`@Before` → `@BeforeEach`；`Assert.assertTrue(tableLocation.delete())` → `assertThat(tableLocation.delete()).isTrue()`；`Assert.assertEquals("Conf pairs must match", toMap(expectedConf), toMap(actualConf))` → `assertThat(toMap(actualConf)).as("Conf pairs must match").isEqualTo(toMap(expectedConf))`；同样处理 `actualConf.get("k1")`/`actualConf.get("k2")` 两处值断言。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/TestHadoopMetricsContextSerialization.java`

**修改目的**：单行 import 替换。

**工作逻辑**：仅 `import org.junit.Test;` → `import org.junit.jupiter.api.Test;`，无其他改动。这是本批次最小的文件改动。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/TestManifestFileSerialization.java`

**修改目的**：将 ManifestFile 序列化测试迁移到 JUnit 5（108 行变更，本批次改动量第三大）。

**工作逻辑**：`@Rule public TemporaryFolder temp` → `@TempDir private Path temp`；`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`；`temp.newFile("input.m0.avro")` → `File.createTempFile("input.m0", ".avro", temp.toFile())`（注意 JUnit 4 `newFile(name)` 接受完整文件名，而 `File.createTempFile(prefix, suffix, dir)` 需要拆分为前缀+后缀，并在中间追加随机串）；`Assert.assertTrue(manifestFile.delete())` → `assertThat(manifestFile.delete()).isTrue()`。`checkManifestFile` 方法中 14 处 `Assert.assertEquals("msg", expected.x(), actual.x())` 全部改写为 `assertThat(actual.x()).as("msg").isEqualTo(expected.x())`，覆盖 path/length/partitionSpecId/snapshotId/hasAddedFiles/addedFilesCount/addedRowsCount/hasExistingFiles/existingFilesCount/existingRowsCount/hasDeletedFiles/deletedFilesCount/deletedRowsCount 等字段。`PartitionFieldSummary` 比较 4 处 `Assert.assertEquals` 改写为 containsNull/containsNaN/lowerBound/upperBound 字段断言。`Assertions.assertThat(obj).as("Should be a ManifestFile").isInstanceOf(ManifestFile.class)` → 静态导入 `assertThat(obj).as("Should be a ManifestFile").isInstanceOf(ManifestFile.class)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/TestScanTaskSerialization.java`

**修改目的**：将扫描任务序列化测试迁移到 JUnit 5，基类从 `SparkTestBase` 切换到 `TestBase`。

**工作逻辑**：基类 `extends SparkTestBase` → `extends TestBase`；`@Rule public TemporaryFolder temp` → `@TempDir private Path temp`；`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`；`@Before` → `@BeforeEach`；`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`；`Assert.assertTrue(data.delete())` → `assertThat(data.delete()).isTrue()`；`Assert.assertTrue("Task group can't be empty", !taskGroup.tasks().isEmpty())` → `assertThat(taskGroup.tasks()).as("Task group can't be empty").isNotEmpty()`（AssertJ 集合非空断言更可读）。`Assertions.assertThat(obj).as("...").isInstanceOf(BaseCombinedScanTask.class)` → 静态导入 `assertThat(obj).as("...").isInstanceOf(BaseCombinedScanTask.class)`（去掉 `Assertions.` 前缀，并合并多行链式调用为单行）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/TestTableSerialization.java`

**修改目的**：将 Table 序列化测试从 JUnit 4 `@RunWith(Parameterized.class)` 模式迁移到 JUnit 5 `@ExtendWith(ParameterizedTestExtension.class)` 模式，并切换临时目录机制。本批次中唯一涉及参数化测试改造的文件（除继承 `CatalogTestBase` 的子类外）。

**工作逻辑**：类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`（新增 import `org.apache.iceberg.ParameterizedTestExtension`、`org.apache.iceberg.Parameters`、`org.apache.iceberg.Parameter`、`org.junit.jupiter.api.extension.ExtendWith`）。构造器 `public TestTableSerialization(String isObjectStoreEnabled) { this.isObjectStoreEnabled = isObjectStoreEnabled; }` 整体删除；字段 `private final String isObjectStoreEnabled` → `@Parameter private String isObjectStoreEnabled`。参数方法 `@Parameterized.Parameters(name = "isObjectStoreEnabled = {0}") public static Object[] parameters() { return new Object[] {"true", "false"}; }` → `@Parameters(name = "isObjectStoreEnabled = {0}") public static List<String> parameters() { return Arrays.asList("true", "false"); }`（返回类型从 `Object[]` 升级为 `List<String>`，新增 `import java.util.Arrays;`、`import java.util.List;`）。`@Rule public TemporaryFolder temp` → `@TempDir private Path temp`；`@Before` → `@BeforeEach`；`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`；`Assert.assertTrue(tableLocation.delete())` → `assertThat(tableLocation.delete()).isTrue()`。5 处测试方法 `@Test` → `@TestTemplate`（`testCloseSerializableTableKryoSerialization`、`testCloseSerializableTableJavaSerialization`、`testSerializableTableKryoSerialization`、`testSerializableMetadataTableKryoSerialization`、`testSerializableTransactionTableKryoSerialization`），以便 `ParameterizedTestExtension` 在每组 `isObjectStoreEnabled` 参数下重复执行。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/ValidationHelpers.java`

**修改目的**：测试辅助类的断言风格统一。

**工作逻辑**：`import org.assertj.core.api.Assertions;` → `import static org.assertj.core.api.Assertions.assertThat;`；`Assertions.assertThat(actual).as(errorMessage).hasSameElementsAs(expected)` → `assertThat(actual).as(errorMessage).hasSameElementsAs(expected)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestChangelogIterator.java`

**修改目的**：将 ChangelogIterator 测试迁移到 JUnit 5，并用 `assertThatThrownBy` 替换 `assertThrows`。

**工作逻辑**：`import static org.junit.Assert.assertThrows;` → `import static org.assertj.core.api.Assertions.assertThat;` + `import static org.assertj.core.api.Assertions.assertThatThrownBy;`；`import org.junit.Assert;`/`Test;` → `import org.junit.jupiter.api.Test;`。`Assert.assertEquals(24, permutations.size())` → `assertThat(permutations).hasSize(24)`（集合尺寸断言）。关键的 `assertThrows("Cannot compute updates because there are multiple rows with the same identifier fields([id, name]). Please make sure the rows are unique.", IllegalStateException.class, () -> Lists.newArrayList(iterator))` 改写为 `assertThatThrownBy(() -> Lists.newArrayList(iterator)).isInstanceOf(IllegalStateException.class).hasMessage("Cannot compute updates because there are multiple rows with the same identifier fields([id,name]). Please make sure the rows are unique.")`——这里 AssertJ 把消息从 `assertThrows` 的"消息前缀"语义改为 `hasMessage` 的"完整相等"语义，注意消息原文中 `[id, name]` 在新版改为 `[id,name]`（去掉空格），与异常实际抛出时拼接的字符串保持一致。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestFileRewriteCoordinator.java`

**修改目的**：将文件重写协调器测试迁移到 JUnit 5，基类从 `SparkCatalogTestBase` 切换到 `CatalogTestBase`，删除子类构造器链。

**工作逻辑**：基类 `extends SparkCatalogTestBase` → `extends CatalogTestBase`；删除子类构造器 `public TestFileRewriteCoordinator(String catalogName, String implementation, Map<String, String> config) { super(...); }`；`import java.util.Map;`、`org.junit.After;`、`Assert;`、`Test;`、`org.junit.runner.RunWith;`、`org.junit.runners.Parameterized;`、`Iterables` 等不再需要的 import 移除，新增 `import org.junit.jupiter.api.AfterEach;`、`import org.junit.jupiter.api.TestTemplate;`、`import static org.assertj.core.api.Assertions.assertThat;`。`@After` → `@AfterEach`；3 处 `@Test` → `@TestTemplate`（`testBinPackRewrite`、`testSortRewrite`、`testCommitMultipleRewrites`）。`Assert.assertEquals("Should produce 4 snapshots", 4, Iterables.size(table.snapshots()))` → `assertThat(table.snapshots()).as("Should produce 4 snapshots").hasSize(4)`（不再需要 `Iterables.size` 中转，AssertJ `hasSize` 直接对 `Iterable` 工作）；`Assert.assertEquals("Deleted files count must match", "4", summary.get("deleted-data-files"))` → `assertThat(summary.get("deleted-data-files")).as("Deleted files count must match").isEqualTo("4")`；`Assert.assertEquals("Row count must match", 4000L, rowCount)` → `assertThat(rowCount).as("Row count must match").isEqualTo(4000L)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestFunctionCatalog.java`

**修改目的**：将 FunctionCatalog 测试迁移到 JUnit 5，基类从 `SparkTestBaseWithCatalog` 切换到 `TestBaseWithCatalog`，并把构造器初始化逻辑迁到 `@BeforeEach` 方法。

**工作逻辑**：基类 `extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`。字段 `private final FunctionCatalog asFunctionCatalog` → `private FunctionCatalog asFunctionCatalog`（去掉 `final`，因改为 `@BeforeEach` 中赋值）。原无参构造器 `public TestFunctionCatalog() { this.asFunctionCatalog = castToFunctionCatalog(catalogName); }` 拆分并合并到 `@BeforeEach before()` 方法：`@BeforeEach public void before() { super.before(); this.asFunctionCatalog = castToFunctionCatalog(catalogName); sql("CREATE NAMESPACE IF NOT EXISTS %s", catalogName + ".default"); }`——关键点：必须显式调用 `super.before()`，因为新基类 `TestBaseWithCatalog.before()` 负责 catalog 上下文初始化（包括 `catalogName` 字段赋值），子类才能在 `castToFunctionCatalog(catalogName)` 中安全引用。`@Before`/`@After` → `@BeforeEach`/`@AfterEach`；`@Test` → `@TestTemplate`（3 处：`testListFunctionsViaCatalog`、`testLoadFunctions`、`testCallingFunctionInSQLEndToEnd`）。`Assertions.assertThat(...)` → 静态导入 `assertThat(...)`；`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`；`Assert.assertArrayEquals("msg", new Identifier[0], asFunctionCatalog.listFunctions(DEFAULT_NAMESPACE))` → `assertThat(asFunctionCatalog.listFunctions(DEFAULT_NAMESPACE)).as("msg").isEqualTo(new Identifier[0])`；`Assert.assertEquals("msg", buildVersion, scalarSql(...))` → `assertThat(scalarSql(...)).as("msg").isEqualTo(buildVersion)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSpark3Util.java`

**修改目的**：将 Spark3Util 测试迁移到 JUnit 5，基类从 `SparkTestBase` 切换到 `TestBase`。

**工作逻辑**：基类 `extends SparkTestBase` → `extends TestBase`。`import org.assertj.core.api.Assertions;`/`org.junit.Assert;`/`Test;` 移除，新增 `import org.junit.jupiter.api.Test;` + `import static org.assertj.core.api.Assertions.assertThat;`。`testDescribeSortOrder` 方法中 9 处 `Assert.assertEquals("Sort order isn't correct.", "expected", Spark3Util.describe(...))` 改为 `assertThat(Spark3Util.describe(...)).as("Sort order isn't correct.").isEqualTo("expected")`，每个断言独占多行格式，可读性更好。`testDescribeSchema` 中 `Assert.assertEquals` → `assertThat(Spark3Util.describe(schema)).as("Schema description isn't correct.").isEqualTo("struct<...>")`。`testLoadIcebergTable` 中 `Assert.assertTrue(table.name().equals(tableFullName))` → `assertThat(table.name()).isEqualTo(tableFullName)`（用 `isEqualTo` 替代 `assertTrue + equals`，更地道）。`testLoadIcebergCatalog` 中 `Assert.assertTrue("Should retrieve underlying catalog class", catalog instanceof CachingCatalog)` → `assertThat(catalog).as("Should retrieve underlying catalog class").isInstanceOf(CachingCatalog.class)`。`testDescribeExpression` 中 9 处 `Assertions.assertThat(Spark3Util.describe(expr)).isEqualTo("...")` → 静态导入 `assertThat(Spark3Util.describe(expr)).isEqualTo("...")`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkCachedTableCatalog.java`

**修改目的**：将 SparkCachedTableCatalog 测试迁移到 JUnit 5，基类从 `SparkTestBaseWithCatalog` 切换到 `TestBaseWithCatalog`，并把原 `super(SparkCatalogConfig.HIVE)` 单 catalog 调用改为 `@Parameters` 提供单组参数。

**工作逻辑**：基类 `extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`。原构造器 `public TestSparkCachedTableCatalog() { super(SparkCatalogConfig.HIVE); }` 删除，改为通过 `@Parameters` 注解的静态方法提供单组 `Object[][]` 参数：`@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}") protected static Object[][] parameters() { return new Object[][] { { SparkCatalogConfig.HIVE.catalogName(), SparkCatalogConfig.HIVE.implementation(), SparkCatalogConfig.HIVE.properties() } }; }`——这样 `TestBaseWithCatalog` 的 `ParameterizedTestExtension` 能识别这组参数并在测试执行前注入到基类字段（catalogName/implementation/config），让子类的 `tableName`、`tableIdent` 等基类提供的字段可用。`@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`（用于注册/取消 `spark.sql.catalog.testcache` 配置）；`@Test` → `@TestTemplate`（`testTimeTravel`）。新增 `import org.apache.iceberg.Parameters;`、`import org.junit.jupiter.api.AfterAll;`、`BeforeAll;`、`TestTemplate;`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkCatalogOperations.java`

**修改目的**：将 SparkCatalogOperations 测试迁移到 JUnit 5，基类从 `SparkCatalogTestBase` 切换到 `CatalogTestBase`。

**工作逻辑**：基类 `extends SparkCatalogTestBase` → `extends CatalogTestBase`；删除子类构造器 `(String catalogName, String implementation, Map<String,String> config)`；`@Before`/`@After` → `@BeforeEach`/`@AfterEach`；`@Test` → `@TestTemplate`（`testAlterTable`、`testInvalidateTable`）。`Assert.assertNotNull("Should return updated table", table)` → `assertThat(table).as("Should return updated table").isNotNull()`；`Assert.assertEquals("msg", expectedField, table.schema().fields()[2])` → `assertThat(table.schema().fields()[2]).as("msg").isEqualTo(expectedField)`；原两步断言 `Assert.assertTrue("...", table.properties().containsKey(propsKey))` + `Assert.assertEquals("...", propsValue, table.properties().get(propsKey))` 合并为单个 `assertThat(table.properties()).as("Adding a property to a table should return the updated table with the new property with the new correct value").containsEntry(propsKey, propsValue)`（AssertJ `containsEntry` 一行替代两行，更紧凑）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkCompressionUtil.java`

**修改目的**：单类迁移到 JUnit 5。

**工作逻辑**：`import org.junit.Before;`/`Test;` → `import org.junit.jupiter.api.BeforeEach;`/`Test;`；`@Before` → `@BeforeEach`。无断言变更。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkDistributionAndOrderingUtil.java`

**修改目的**：将 Spark 写入分布与排序工具测试迁移到 JUnit 5，基类从 `SparkTestBaseWithCatalog` 切换到 `TestBaseWithCatalog`。本批次改动量最大的文件（222 行变更）。

**工作逻辑**：基类 `extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`；`@After` → `@AfterEach`；约 40 处 `@Test` → `@TestTemplate`（覆盖 `testDefaultWriteUnpartitionedUnsortedTable`、`testHashWriteUnpartitionedUnsortedTable`、`testRangeWriteUnpartitionedUnsortedTable`、`testDefaultWriteUnpartitionedSortedTable` 等 write/delete/copyOnWrite/positionDelta 各种分布与排序组合的测试方法）。因测试方法数量多，注解替换是本文件的主要变更来源。`import org.junit.After;`/`Assert;`/`Test;` 移除，新增 `import org.junit.jupiter.api.AfterEach;`/`TestTemplate;` + `import static org.assertj.core.api.Assertions.assertThat;`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkFilters.java`

**修改目的**：将 Spark V1 过滤器转换测试迁移到 JUnit 5，统一断言风格。

**工作逻辑**：`import org.junit.Assert;`/`Test;` 移除，新增 `import org.junit.jupiter.api.Test;` + `import static org.assertj.core.api.Assertions.assertThat;`。`testConvertCollationSupport` 等方法中所有 `Assert.assertEquals("IsNull must match", expectedIsNull.toString(), actualIsNull.toString())` 改为 `assertThat(actualIsNull.toString()).as("IsNull must match").isEqualTo(expectedIsNull.toString())`，覆盖 IsNull/IsNotNull/LessThan/LessThanOrEqual/GreaterThan/GreaterThanOrEqual/EqualTo/EqualNullSafe/In 等 9 种过滤器；`testConvertTimestamp`/`testConvertInstant`/`testConvertLocalDate` 中 `Assert.assertEquals` 改为对应 `assertThat(...).as(...).isEqualTo(...)`；`Assert.assertNull("Expression should not be converted", converted)` → `assertThat(converted).as("Expression should not be converted").isNull()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkSchemaUtil.java`

**修改目的**：将 SparkSchemaUtil 测试迁移到 JUnit 5。

**工作逻辑**：`import org.junit.Assert;`/`Test;` 移除，新增 `import org.junit.jupiter.api.Test;` + `import static org.assertj.core.api.Assertions.assertThat;`。`Assert.assertEquals("estimateSize returns Long max value", Long.MAX_VALUE, SparkSchemaUtil.estimateSize(null, Long.MAX_VALUE))` → `assertThat(SparkSchemaUtil.estimateSize(null, Long.MAX_VALUE)).as("estimateSize returns Long max value").isEqualTo(Long.MAX_VALUE)`；`Assert.assertTrue("metadata columns should have __metadata_col in attribute metadata", MetadataAttribute.unapply(attrRef).isDefined())` → `assertThat(MetadataAttribute.unapply(attrRef).isDefined()).as("...").isTrue()`；`Assert.assertFalse("non metadata columns should not have __metadata_col in attribute metadata", ...)` → `assertThat(MetadataAttribute.unapply(attrRef).isDefined()).as("...").isFalse()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkSessionCatalog.java`

**修改目的**：将 SparkSessionCatalog 测试迁移到 JUnit 5（66 行变更）。

**工作逻辑**：`import org.junit.Assert;`/`Test;` 移除，新增 `import org.junit.jupiter.api.Test;` + `import static org.assertj.core.api.Assertions.assertThat;` + `import static org.assertj.core.api.Assertions.assertThatThrownBy;`。所有 `Assert.*` 与 `Assertions.assertThat*` 替换为静态导入风格，`assertThrows` 替换为 `assertThatThrownBy`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkTableUtil.java`

**修改目的**：将 SparkTableUtil 测试迁移到 JUnit 5。

**工作逻辑**：`import org.assertj.core.api.Assertions;`/`org.junit.Assert;`/`Test;` 移除，新增 `import org.junit.jupiter.api.Test;` + `import static org.assertj.core.api.Assertions.assertThat;`。`Assertions.assertThat(sparkPartition).isEqualTo(deserialized)` → 静态导入 `assertThat(sparkPartition).isEqualTo(deserialized)`；`Assert.assertEquals(MetricsModes.Full.get().toString(), deserialized.columnMode("col1").toString())` → `assertThat(deserialized.columnMode("col1").toString()).isEqualTo(MetricsModes.Full.get().toString())`——注意此处原 JUnit 4 写法没有消息参数（仅 expected、actual），AssertJ 改写后也省略 `.as(...)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkV2Filters.java`

**修改目的**：将 Spark V2 过滤器转换测试迁移到 JUnit 5（150 行变更，本批次改动量第二大）。

**工作逻辑**：`import org.assertj.core.api.Assertions;`/`org.junit.Assert;`/`Test;` 移除，新增 `import org.junit.jupiter.api.Test;` + `import static org.assertj.core.api.Assertions.assertThat;` + `import static org.assertj.core.api.Assertions.assertThatThrownBy;`。`testConvertCollationSupport` 方法中对 IsNull/IsNotNull/LessThan/LessThanOrEqual/GreaterThan/GreaterThanOrEqual 等多种 Predicate 的双向（attrAndValue、valueAndAttr）转换断言，所有 `Assert.assertEquals("msg", expected.toString(), actual.toString())` 改为 `assertThat(actual.toString()).as("msg").isEqualTo(expected.toString())`，约 20+ 处。`testConvertTimestamp`/`testConvertLocalDate`/`testConvertDate` 等方法的 `Assert.assertEquals` 同样改写。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkValueConverter.java`

**修改目的**：将 SparkValueConverter 测试迁移到 JUnit 5。

**工作逻辑**：`import org.junit.Assert;`/`Test;` 移除，新增 `import org.junit.jupiter.api.Test;` + `import static org.assertj.core.api.Assertions.assertThat;`。`Assert.assertEquals("Round-trip conversion should produce original value", record, SparkValueConverter.convert(schema, sparkRow))` → `assertThat(SparkValueConverter.convert(schema, sparkRow)).as("Round-trip conversion should produce original value").isEqualTo(record)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java`

**修改目的**：将 SparkWriteConf 测试迁移到 JUnit 5，基类从 `SparkTestBaseWithCatalog` 切换到 `TestBaseWithCatalog`，并显式调用 `super.before()`。

**工作逻辑**：基类 `extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`；`@Before` → `@BeforeEach`，方法体首行新增 `super.before();`（关键——基类 `TestBaseWithCatalog.before()` 负责 catalog 上下文初始化，子类必须先调用才能让 `tableName`/`tableIdent` 等字段就绪）；`@After` → `@AfterEach`；约 16 处 `@Test` → `@TestTemplate`。`import org.junit.After;`/`Assert;`/`Before;`/`Test;` 移除，新增 `import org.junit.jupiter.api.AfterEach;`/`BeforeEach;`/`TestTemplate;`。`checkMode` 方法中 7 处 `Assert.assertEquals(expectedMode, writeConf.distributionMode())` / `Assert.assertEquals(expectedMode, writeConf.copyOnWriteDistributionMode(DELETE))` 等改为 `assertThat(writeConf.distributionMode()).isEqualTo(expectedMode)` 等——注意此处原断言无消息参数，AssertJ 改写后也省略 `.as(...)`。`Assert.assertEquals(expectedProperties.size(), writeConf.writeProperties().size())` → `assertThat(writeConf.writeProperties()).hasSameSizeAs(expectedProperties)`；`Assert.assertEquals(entry.getValue(), expectedProperties.get(entry.getKey()))` → `assertThat(expectedProperties).containsEntry(entry.getKey(), entry.getValue())`（AssertJ `containsEntry` 替代 `get` + `assertEquals`，语义更清晰）。

## 小结

本次提交是 Iceberg Spark 3.5 模块 JUnit 4 → JUnit 5 系列迁移的延续批次，涉及 25 个文件、净减 4 行。覆盖 `org/apache/iceberg/` 顶层的序列化测试与测试辅助类（8 个）以及 `org/apache/iceberg/spark/` 下 Spark 集成工具/功能测试（17 个）。核心改造与前序批次一致：注解替换、临时目录改用 `@TempDir Path`、基类体系切换（`SparkTestBase`/`SparkTestBaseWithCatalog`/`SparkCatalogTestBase` → `TestBase`/`TestBaseWithCatalog`/`CatalogTestBase`）、参数化测试改造（`@RunWith(Parameterized.class)` + 构造器注入 → `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameter` 字段注入）、断言统一为 AssertJ 静态导入风格。本批次特别值得关注的复杂改造包括：`TestTableSerialization` 的参数化测试整体重构、`TestSparkCachedTableCatalog` 由 `super(SparkCatalogConfig.HIVE)` 调用改为 `@Parameters` 提供单组参数、`TestFunctionCatalog`/`TestSparkWriteConf` 子类 `@BeforeEach` 显式调用 `super.before()` 衔接新基类生命周期。本批次的完成让 Spark 3.5 模块测试基础设施基本完成 JUnit 5 现代化，为后续利用 JUnit 5 扩展能力与参数化测试提供坚实基础。
