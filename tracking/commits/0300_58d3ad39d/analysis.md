# 提交 0300：Spark 3:5 Migrate tests to JUnit5 in source directory (#9342)

## 提交信息

- **序号**：0300 / 4088
- **哈希**：58d3ad39d5978ec9a2e14fea668c2832edc44e32
- **短哈希**：58d3ad39d
- **日期**：2023-12-22 17:08:40 +0100
- **作者**：Chinmay Bhat
- **提交说明**：Spark 3:5 Migrate tests to JUnit5 in source directory (#9342)
- **PR/Issue**：#9342

## 总体目的

本提交是继 #9341（data 目录迁移）之后，Iceberg Spark 3.5 模块 JUnit 4 → JUnit 5 系列迁移的下一批次，专门迁移 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/` 目录及其相关测试基类（共 24 个文件，833 行新增、756 行删除）。该目录包含 Spark DataSource V2 集成的核心测试：读写器（`TestBaseReader`、`TestChangelogReader`）、数据源选项（`TestDataSourceOptions`）、各类扫描（`TestAvroScan`、`TestParquetScan`、`TestReadProjection`）、流式读取（`TestStructuredStreaming`、`TestStreamingOffset`）、元数据表（`TestMetadataTableReadableMetrics`）、catalog 测试基类（`CatalogTestBase`）等，是 Spark 集成测试体系中规模最大、最核心的测试集合之一。

与 data 目录迁移相比，本批次面临额外的复杂性：source 目录大量测试继承自 `SparkTestBase`/`SparkTestBaseWithCatalog`/`SparkCatalogTestBase` 这套 JUnit 4 风格的基类体系，迁移需要将这套基类整体替换为 JUnit 5 风格的 `TestBase`/`TestBaseWithCatalog`/`CatalogTestBase`。其中 `CatalogTestBase` 还涉及参数化测试的改造——从 JUnit 4 的 `Stream<Arguments>` + `@ParameterizedTest` 模式迁移到 Iceberg 自定义的 `ParameterizedTestExtension` 扩展 + `@Parameters` 注解的 `Object[][]` 模式，并将测试方法从 `@Test` 改为 `@TestTemplate` 以支持参数化重复执行。

迁移动机与 #9341 一致：统一到 JUnit 5 现代框架、统一 AssertJ 静态导入断言风格、临时目录改用 `@TempDir Path`、利用 JUnit 5 扩展机制和参数化测试能力，为后续测试基础设施现代化奠定基础。

## 如何达成设计目的

整体设计思路与 #9341 一致——保持测试逻辑不变，仅替换测试基础设施 API。但由于 source 目录涉及基类体系和参数化测试，迁移还额外包含：将父类从 `SparkTestBase`/`SparkTestBaseWithCatalog`/`SparkCatalogTestBase` 切换到 `TestBase`/`TestBaseWithCatalog`/`CatalogTestBase`；将 `CatalogTestBase` 的参数提供方式从 `Stream<Arguments>` 改为 `@Parameters Object[][]` 并通过 `@ExtendWith(ParameterizedTestExtension.class)` 注册扩展；将原本 `SparkCatalogTestBase` 子类的测试方法注解从 `@Test` 改为 `@TestTemplate`。其余文件的迁移规则（注解替换、`TemporaryFolder` → `@TempDir Path`、断言静态导入化）与 #9341 完全一致。

## 修改详情

本次迁移涉及 24 个文件。下面先说明迁移模式，再对关键文件单独展开。

### 迁移模式总览

**1. 注解替换**（与 #9341 一致）：`org.junit.Test` → `org.junit.jupiter.api.Test`；`@Ignore` → `@Disabled`；`@Before`/`@After` → `@BeforeEach`/`@AfterEach`；`@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`；新增 `@TestTemplate` 用于参数化测试方法。

**2. 临时目录替换**（与 #9341 一致）：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private/protected Path temp;`；`temp.newFolder()` → `temp.toFile()`；`temp.newFolder("iceberg-table").toString()` → `temp.resolve("iceberg-table").toFile().toString()`；`temp.newFolder(desc)` → `temp.resolve(desc).toFile()`。

**3. 断言替换**（与 #9341 一致）：移除 `org.junit.Assert`、`org.assertj.core.api.Assertions` import，新增 `assertThat`/`assertThatThrownBy` 静态导入；`Assert.assertEquals("m", e, a)` → `assertThat(a).as("m").isEqualTo(e)`；`Assert.assertTrue/assertFalse/assertNotNull/assertNull` → `assertThat(...).as(...).isTrue()/isFalse()/isNotNull()/isNull()`；`Assert.assertArrayEquals` → `isEqualTo`；`Assertions.assertThatThrownBy` → `assertThatThrownBy`；`Assert.assertNotSame` → `isNotSameAs`；迭代器 `hasNext` 断言用 `assertThat(iter).hasNext()`/`isExhausted()`。

**4. 基类体系切换**（本批次特有）
- `extends SparkTestBase` → `extends TestBase`
- `extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`
- `extends SparkCatalogTestBase` → `extends CatalogTestBase`
- import 由 `org.apache.iceberg.spark.SparkTestBase`/`SparkTestBaseWithCatalog`/`SparkCatalogTestBase` 改为 `org.apache.iceberg.spark.TestBase`/`TestBaseWithCatalog`/`CatalogTestBase`

**5. 参数化测试改造**（本批次特有，见 `CatalogTestBase`）

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/CatalogTestBase.java`

**修改目的**：将参数化 catalog 测试基类从 JUnit 4 参数化模式迁移到 JUnit 5 扩展模式。

**工作逻辑**：这是本批次最关键的改造。原 `CatalogTestBase` 继承 `TestBaseWithCatalog`，通过静态方法 `parameters()` 返回 `Stream<Arguments>`（包含 HIVE/HADOOP/SPARK 三种 catalog 配置），供 JUnit 4 参数化运行器使用。迁移后：
- 类上新增 `@ExtendWith(ParameterizedTestExtension.class)`，注册 Iceberg 自定义的参数化测试扩展（注意这是 Iceberg 自己的 `org.apache.iceberg.ParameterizedTestExtension`，而非 JUnit 5 内置的 `ParameterizedTestExtension`）。
- `parameters()` 方法从 `public static Stream<Arguments> parameters()` 改为 `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}") public static Object[][] parameters()`，返回类型由 `Stream<Arguments>` 改为 `Object[][]`，并用 `@Parameters` 注解标注（Iceberg 自定义注解 `org.apache.iceberg.Parameters`），注解的 `name` 属性定义了参数化测试的显示名称模板。
- 方法体由 `Stream.of(Arguments.of(...), ...)` 改为 `new Object[][] { {...}, {...}, {...} }`，三种 catalog 配置各自作为一个 Object 数组。
- 新增 `@TempDir protected Path temp;` 字段（原基类体系通过 `SparkTestBase` 提供临时目录，新基类体系改为由 `CatalogTestBase` 自身持有 `@TempDir`）。
- 新增 import `java.nio.file.Path`、`org.apache.iceberg.ParameterizedTestExtension`、`org.apache.iceberg.Parameters`、`org.junit.jupiter.api.extension.ExtendWith`、`org.junit.jupiter.api.io.TempDir`，移除 `java.util.stream.Stream`、`org.junit.jupiter.params.provider.Arguments`。

这一改造意味着所有继承 `CatalogTestBase`（原 `SparkCatalogTestBase`）的子类测试方法需从 `@Test` 改为 `@TestTemplate`，以便 `ParameterizedTestExtension` 能针对每组参数重复执行。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestBaseReader.java`

**修改目的**：迁移文件扫描任务读取器测试到 JUnit 5。

**工作逻辑**：典型迁移。`@Rule TemporaryFolder` → `@TempDir private Path temp`；`temp.newFolder(desc)` → `temp.resolve(desc).toFile()`。大量 `Assert` 断言迁移，例如：
- `Assert.assertNotNull("Reader should return non-null value", reader.get())` → `assertThat(reader.get()).as("...").isNotNull()`
- `Assert.assertEquals("Reader returned incorrect number of records", totalTasks * recordPerTask, countRecords)` → `assertThat(totalTasks * recordPerTask).as("...").isEqualTo(countRecords)`
- `Assert.assertTrue("All iterators should be closed after read exhausion", reader.isIteratorClosed(t))` → `assertThat(reader.isIteratorClosed(t)).as("...").isTrue()`
- `Assert.assertEquals(2, tasks.size())` → `assertThat(tasks).hasSize(2)`（使用 AssertJ 集合尺寸断言）
- `Assert.assertTrue(dataFolder.mkdirs())` → `assertThat(dataFolder.mkdirs()).as("mkdirs should succeed").isTrue()`
- `Assert.assertTrue(reader.next())` / `Assert.assertFalse(reader.next())` → `assertThat(reader.next()).isTrue()` / `isFalse()`

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataSourceOptions.java`

**修改目的**：迁移数据源选项测试到 JUnit 5，并切换基类。

**工作逻辑**：基类从 `SparkTestBaseWithCatalog` 切换到 `TestBaseWithCatalog`。生命周期注解 `@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`（用于 SparkSession 启动/停止）。`@Rule TemporaryFolder` → `@TempDir private Path temp`。`temp.newFolder("iceberg-table").toString()` → `temp.resolve("iceberg-table").toFile().toString()`。断言迁移，例如：
- `Assert.assertEquals(FileFormat.PARQUET, fileFormat)` → `assertThat(fileFormat).isEqualTo(FileFormat.PARQUET)`
- `Assert.assertEquals("Should have written 1 file", 1, files.size())` → `assertThat(files).as("...").hasSize(1)`
- `Assert.assertEquals("Spark partitions should match", 2, resultDf.javaRDD().getNumPartitions())` → `assertThat(resultDf.javaRDD().getNumPartitions()).as("...").isEqualTo(2)`
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)` 静态导入
- `Assert.assertEquals("Records should match", expectedRecords, resultRecords)` → `assertThat(resultRecords).as("...").isEqualTo(expectedRecords)`

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`

**修改目的**：迁移 Iceberg Source 表测试抽象基类到 JUnit 5，并切换基类。

**工作逻辑**：基类从 `SparkTestBase` 切换到 `TestBase`。`@After` → `@AfterEach`（`removeTable` 方法）。`@Rule TemporaryFolder` → `@TempDir protected Path temp`。`temp.newFolder()` → `temp.toFile()`（用于创建 parquet_table 的 LOCATION）。大量 `Assert.assertEquals` 迁移到 `assertThat(...).as(...).hasSize(...)`/`isEqualTo(...)`，例如：
- `Assert.assertEquals("Should only contain one manifest", 1, snapshot.allManifests(table.io()).size())` → `assertThat(snapshot.allManifests(table.io())).as("...").hasSize(1)`
- `Assert.assertEquals("Entries table should have one row", 1, expected.size())` → `assertThat(expected).as("...").hasSize(1)`
- `Assert.assertTrue("Stage table should have some snapshots", table.snapshots().iterator().hasNext())` → `assertThat(table.snapshots().iterator()).as("...").hasNext()`
- `Assert.assertNull("Stage table should have null currentSnapshot", table.currentSnapshot())` → `assertThat(table.currentSnapshot()).as("...").isNull()`
- `Assertions.assertThatThrownBy` → `assertThatThrownBy`

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkTable.java`

**修改目的**：迁移 SparkTable 测试到 JUnit 5，并切换基类到 `CatalogTestBase`，测试方法改用 `@TestTemplate`。

**工作逻辑**：基类从 `SparkCatalogTestBase` 切换到 `CatalogTestBase`。`@Before`/`@After` → `@BeforeEach`/`@AfterEach`。关键点：`@Test` → `@TestTemplate`（因为父类 `CatalogTestBase` 现在使用 `ParameterizedTestExtension`，子类测试方法需用 `@TestTemplate` 才能被参数化重复执行）。断言迁移：`Assert.assertNotSame("References must be different", table1, table2)` → `assertThat(table1).as("...").isNotSameAs(table2)`；`Assert.assertEquals("Tables must be equivalent", table1, table2)` → `assertThat(table1).as("...").isEqualTo(table2)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPathIdentifier.java`

**修改目的**：迁移 PathIdentifier 测试到 JUnit 5，并切换基类。

**工作逻辑**：基类 `SparkTestBase` → `TestBase`。`@Rule TemporaryFolder` → `@TempDir private Path temp`。`@Before`/`@After` → `@BeforeEach`/`@AfterEach`。`temp.newFolder()` → `temp.toFile()`。断言迁移：`Assert.assertEquals(table.table().location(), tableLocation.getAbsolutePath())` → `assertThat(tableLocation.getAbsolutePath()).isEqualTo(table.table().location())`；`Assertions.assertThat(table.table()).isInstanceOf(BaseTable.class)` → `assertThat(table.table()).isInstanceOf(BaseTable.class)`（去掉 `Assertions.` 前缀）；`Assert.assertTrue(sparkCatalog.dropTable(identifier))` → `assertThat(sparkCatalog.dropTable(identifier)).isTrue()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestInternalRowWrapper.java`

**修改目的**：迁移 InternalRowWrapper 测试到 JUnit 5。

**工作逻辑**：`@Ignore` → `@Disabled`（禁用 `testTimestampWithoutZone` 和 `testTime`，因 Spark 不支持）。断言迁移：`Assert.assertTrue("Should have more records", actual.hasNext())` → `assertThat(actual).as("...").hasNext()`；`Assert.assertFalse("Shouldn't have more record", actual.hasNext())` → `assertThat(actual).as("...").isExhausted()`。注意该类继承 `RecordWrapperTest`（见下）。

### `data/src/test/java/org/apache/iceberg/RecordWrapperTest.java`

**修改目的**：将 `RecordWrapperTest` 抽象基类的 `@Test` import 切换到 JUnit 5（仅 import 替换）。

**工作逻辑**：该文件位于 `data` 模块（非 spark 模块），是 `TestInternalRowWrapper` 等的父类。本次仅做最小化迁移：将 `import org.junit.Test;` 改为 `import org.junit.jupiter.api.Test;`，保留 `import org.junit.Assert;`（未迁移断言部分，因为该文件改动量被控制在最小）。这是为了让子类 `TestInternalRowWrapper` 在 JUnit 5 环境下能正确继承并执行父类的 `@Test` 方法（若父类仍用 JUnit 4 的 `@Test`，JUnit 5 引擎不会识别）。该文件仅 1 行变更，体现了迁移的谨慎——只迁移必要的部分以避免扩大影响面。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestStreamingOffset.java`

**修改目的**：迁移 StreamingOffset 序列化测试到 JUnit 5。

**工作逻辑**：小文件迁移。`Assert.assertArrayEquals("StreamingOffsets should match", expected, Arrays.stream(expected).map(...).toArray())` → `assertThat(Arrays.stream(expected).map(...).toArray()).as("...").isEqualTo(expected)`（AssertJ 用 `isEqualTo` 处理数组，替代 `assertArrayEquals`）；`Assert.assertEquals("Json should match", expectedJson, actualJson)` → `assertThat(actualJson).isEqualTo(expectedJson)`（此处省略了 `.as()`，因消息冗余）。

### 其余测试类

包括 `TestChangelogReader`、`TestDataFrameWriterV2`、`TestForwardCompatibility`、`TestIcebergSourceHadoopTables`、`TestIcebergSourceHiveTables`、`TestIcebergSpark`、`TestMetadataTableReadableMetrics`、`TestReadProjection`、`TestSparkAggregates`、`TestSparkCatalogCacheExpiration`、`TestSparkDataFile`、`TestSparkReadMetrics`、`TestSparkStagedScan`、`TestStructuredStreaming`、`TestWriteMetricsConfig`、`TestAvroScan`、`TestDataFrameWrites`、`TestParquetScan`。

**修改目的**：将 source 目录下剩余测试类统一迁移到 JUnit 5。

**工作逻辑**：均遵循前述统一迁移模式——注解替换、`TemporaryFolder` → `@TempDir Path`、断言静态导入化、必要处切换基类（`SparkTestBase` → `TestBase` 等）。其中 `TestReadProjection`（361 行变更）和 `TestIcebergSourceTablesBase`（273 行变更）改动量最大，主要因断言密度高、临时目录/文件操作密集；`TestSparkReadMetrics`（108 行）、`TestWriteMetricsConfig`（100 行）、`TestDataSourceOptions`（92 行）、`TestIcebergSpark`（132 行）也因断言量大而有较多改动。`TestSparkCatalogCacheExpiration`、`TestForwardCompatibility` 等还涉及 `Assume.assumeTrue` → `assumeThat` 的迁移。

## 小结

本次提交是 Iceberg Spark 3.5 模块 JUnit 4 → JUnit 5 系列迁移的 source 目录批次，涉及 24 个文件、净增 77 行，是迄今为止规模最大的迁移批次。相比 #9341 的 data 目录，本批次额外完成了基类体系切换（`SparkTestBase`/`SparkTestBaseWithCatalog`/`SparkCatalogTestBase` → `TestBase`/`TestBaseWithCatalog`/`CatalogTestBase`）和参数化测试改造（`Stream<Arguments>` → `@Parameters Object[][]` + `ParameterizedTestExtension` 扩展 + `@TestTemplate`），技术复杂度更高。迁移保持测试逻辑不变，仅替换测试基础设施，为后续全面采用 JUnit 5 扩展能力和参数化测试奠定坚实基础，是 Spark 集成测试现代化的重要里程碑。
