# 提交 0364：Flink 1.18: Create JUnit5 version of TestFlinkScan (#9480)

## 提交信息

- **序号**：0364
- **哈希**：581e03713c2b397c2416899fdf1e433540aca3d3
- **短哈希**：581e03713
- **日期**：2024-01-16 11:47:12 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Flink 1.18: Create JUnit5 version of TestFlinkScan (#9480)
- **PR/Issue**：#9480

## 总体目的

本提交把 Iceberg Flink 1.18 集成模块中"以 `TestFlinkScan` 为根"的扫描测试继承树从 JUnit 4 迁移到 JUnit 5。`TestFlinkScan` 是 Flink 端 Iceberg 数据源扫描测试的抽象基类，通过 `@RunWith(Parameterized.class)` 对文件格式（avro/parquet/orc）做参数化测试；其下挂 `TestFlinkSource`（抽象层）、再下挂 `TestFlinkInputFormat`（InputFormat API）、`TestIcebergSourceBounded`（FLIP-27 Source API）、以及 `TestFlinkScanSql`/`TestIcebergSourceBoundedSql`（SQL 入口）等具体子类。整棵树此前基于 JUnit 4 的 `@ClassRule`/`@Rule` 生命周期管理 Flink MiniCluster 与 HadoopCatalog 测试夹具，基于 `@RunWith(Parameterized.class)` + 构造器注入做参数化，与社区已迁移到 JUnit 5 的 Spark 端（#9416 等）和 core 端测试基础设施不一致。本提交让 Flink 1.18 测试与社区主干对齐，统一到 JUnit 5 的 `@ExtendWith` 扩展模型、`@TestTemplate` 参数化入口、`@RegisterExtension` 字段式扩展注册、`@TempDir` 临时目录注入等现代测试 API。

迁移的核心难点在于 Flink 测试夹具的 JUnit 5 适配。Flink 1.18 测试此前依赖两个 JUnit 4 `TestRule`：(1) `MiniClusterWithClientResource`（来自 `org.apache.flink.test.util`）——启动一个嵌入式 Flink MiniCluster 供测试提交作业；(2) `HadoopCatalogResource`（Iceberg 自有）——在临时目录上创建一个 HadoopCatalog 实例并提供 `catalog()`/`tableLoader()`/`warehouse()` 给测试。JUnit 5 不再支持 `@ClassRule`/`@Rule`，改用 `@RegisterExtension`（字段式注册 `Extension`）与 `@ExtendWith`（类级注册 `Extension`）。本提交把这两个夹具替换为对应的 JUnit 5 `Extension`：`MiniClusterExtension`（来自 `org.apache.flink.test.junit5`，Flink 官方提供的 JUnit 5 适配）通过 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()` 创建；`HadoopCatalogExtension`（Iceberg 自有的 JUnit 5 `Extension`，对应 `HadoopCatalogResource`）通过 `@RegisterExtension` 注册。临时目录从 JUnit 4 `TemporaryFolder`（返回 `File`）改为 JUnit 5 `@TempDir`（注入 `java.nio.file.Path`），相应地把所有 `TEMPORARY_FOLDER` 引用改为 `temporaryDirectory`，并把 `GenericAppenderHelper` 构造器参数从 `File` 适配为 `Path`。

更深层的目的在于统一断言风格与简化参数化。Iceberg 社区已选定 AssertJ 流式断言作为标准，本提交把 `TestFlinkScan` 及其子类中所有 `org.junit.Assert.assertEquals`/`assertTrue`、`org.junit.Assume.assumeTrue`、`org.assertj.core.api.Assertions.assertThatThrownBy`（静态方法调用形式）统一改为静态导入的 `assertThat`/`assertThatThrownBy`/`assumeThat`。参数化方面，原 `@Parameterized.Parameters public static Object[] parameters() { return new Object[] {"avro", "parquet", "orc"}; }` + 构造器 `TestFlinkScan(String fileFormat) { this.fileFormat = FileFormat.fromString(fileFormat); }` 改为 `@Parameters public static Collection<FileFormat> fileFormat() { return Arrays.asList(FileFormat.AVRO, FileFormat.PARQUET, FileFormat.ORC); }` + `@Parameter protected FileFormat fileFormat` 字段注入——直接用 `FileFormat` 枚举集合，省去字符串到枚举的转换，类型更安全。`TestHelpers`（385 行变更，最大的单文件改动）作为 Flink 测试通用断言工具类，其中大量 `Assert.assertEquals("msg", expected, actual)`/`Assert.assertTrue("msg", cond)` 被替换为 AssertJ `assertThat(actual).as("msg").isEqualTo(expected)`/`assertThat(x).isNotNull()`，让断言失败信息更丰富、链式可读。

## 如何达成设计目的

迁移遵循一套固定的机械变换规则，但需要先准备好 JUnit 5 版本的测试夹具（`HadoopCatalogExtension`、`MiniFlinkClusterExtension`，这些 Extension 类假定在更早的提交中已引入，本提交只负责消费）。变换规则：(1) 类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`（Iceberg 自有的 JUnit 5 参数化扩展，配合 `@Parameters` + `@Parameter`）；(2) 删除所有构造器（`TestFlinkScan(String)`、`TestFlinkSource(String)`、`TestFlinkInputFormat(String)`、`TestIcebergSourceBounded(String)`、`TestFlinkScanSql(String)`、`TestIcebergSourceBoundedSql(String)`）及其 `super(fileFormat)` 调用——改用 `@Parameter protected FileFormat fileFormat` 字段注入，子类无需再转发；(3) `@Parameterized.Parameters` → `@Parameters`，返回类型 `Object[]` → `Collection<FileFormat>`，直接用枚举值；(4) `@Test` → `@TestTemplate`，`@Before` → `@BeforeEach`；(5) `@ClassRule public static final MiniClusterWithClientResource MINI_CLUSTER_RESOURCE = MiniClusterResource.createWithClassloaderCheckDisabled()` → `@RegisterExtension protected static MiniClusterExtension miniClusterResource = MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()`；(6) `@ClassRule public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder()` → `@TempDir protected Path temporaryDirectory`；(7) `@Rule public final HadoopCatalogResource catalogResource = new HadoopCatalogResource(TEMPORARY_FOLDER, ...)` → `@RegisterExtension protected static final HadoopCatalogExtension catalogExtension = new HadoopCatalogExtension(TestFixtures.DATABASE, TestFixtures.TABLE)`（注意 `HadoopCatalogExtension` 不再需要 `TEMPORARY_FOLDER` 参数，因为它自身管理临时目录或通过 JUnit 5 扩展机制获取）；(8) 所有 `catalogResource` 引用 → `catalogExtension`，`TEMPORARY_FOLDER` → `temporaryDirectory`；(9) 断言全改 AssertJ 静态导入；(10) `org.junit.Assume.assumeTrue` → `org.assertj.core.api.Assumptions.assumeThat`。子类（`TestFlinkSource`/`TestFlinkScanSql`/`TestIcebergSourceBounded`/`TestIcebergSourceBoundedSql`/`TestFlinkInputFormat`）只需删构造器、改 `@Test`→`@TestTemplate`、`@Before`→`@BeforeEach`、`catalogResource`→`catalogExtension`、`TEMPORARY_FOLDER`→`temporaryDirectory`、断言改 AssertJ，无需重复定义参数化与夹具（继承自 `TestFlinkScan`）。`TestHelpers` 工具类的断言迁移是机械但量大的工作，涉及按 `Type` 分支断言各种 Iceberg/Flink 类型（boolean/int/long/float/double/string/date/time/timestamp/decimal/binary/struct/list/map 等）。

## 修改详情

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkScan.java`

**修改目的**：把 Flink 扫描测试的抽象基类从 JUnit 4 迁移到 JUnit 5，迁移参数化机制、测试夹具（MiniCluster + HadoopCatalog + 临时目录）、断言风格。这是整棵继承树的根，迁移它才能让所有子类走 JUnit 5 执行路径。

**工作逻辑**：
- **import 变更**：移除 `MiniClusterWithClientResource`、`HadoopCatalogResource`、`MiniClusterResource`、`org.junit.ClassRule`、`org.junit.Rule`、`org.junit.Test`、`org.junit.rules.TemporaryFolder`、`org.junit.runner.RunWith`、`org.junit.runners.Parameterized`、`org.assertj.core.api.Assertions`、`org.junit.Assert`；新增 `MiniClusterExtension`（来自 `org.apache.flink.test.junit5`）、`HadoopCatalogExtension`、`MiniFlinkClusterExtension`、`Parameter`、`ParameterizedTestExtension`、`Parameters`（Iceberg 自有）、`org.junit.jupiter.api.TestTemplate`、`org.junit.jupiter.api.extension.ExtendWith`、`org.junit.jupiter.api.extension.RegisterExtension`、`org.junit.jupiter.api.io.TempDir`、`java.nio.file.Path`、`java.util.Collection`、AssertJ 静态导入 `assertThat`/`assertThatThrownBy`。
- **类注解**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- **MiniCluster 夹具**：`@ClassRule public static final MiniClusterWithClientResource MINI_CLUSTER_RESOURCE = MiniClusterResource.createWithClassloaderCheckDisabled()` → `@RegisterExtension protected static MiniClusterExtension miniClusterResource = MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()`。`@ClassRule`（JUnit 4 类级规则）对应 `@RegisterExtension`（JUnit 5 字段式扩展注册）；`static final` 保留表示类级共享；可见性从 `public` 改为 `protected` 让子类可访问。`MiniClusterWithClientResource`（Flink JUnit4 夹具）替换为 `MiniClusterExtension`（Flink JUnit5 夹具），通过 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()` 工厂方法创建（禁用 classloader 检查，与原 `MiniClusterResource.createWithClassloaderCheckDisabled` 语义一致）。
- **临时目录夹具**：`@ClassRule public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder()` → `@TempDir protected Path temporaryDirectory`。JUnit 4 `TemporaryFolder` 是 `TestRule`，需手动 `new`；JUnit 5 `@TempDir` 由 JUnit 框架自动注入 `Path` 字段。类型从 `File`（`TEMPORARY_FOLDER`）变为 `Path`（`temporaryDirectory`），所有下游 `GenericAppenderHelper(table, fileFormat, TEMPORARY_FOLDER)` 调用改为 `GenericAppenderHelper(table, fileFormat, temporaryDirectory)`——`GenericAppenderHelper` 构造器已支持 `Path` 参数（假定在更早提交中适配）。
- **HadoopCatalog 夹具**：`@Rule public final HadoopCatalogResource catalogResource = new HadoopCatalogResource(TEMPORARY_FOLDER, TestFixtures.DATABASE, TestFixtures.TABLE)` → `@RegisterExtension protected static final HadoopCatalogExtension catalogExtension = new HadoopCatalogExtension(TestFixtures.DATABASE, TestFixtures.TABLE)`。`@Rule`（JUnit 4 实例级规则）对应 `@RegisterExtension`（JUnit 5）；`final` 实例字段改为 `static final`（`HadoopCatalogExtension` 设计为类级共享，且不再需要 `TEMPORARY_FOLDER` 参数——它内部管理目录）。`catalogResource` 改名为 `catalogExtension` 以反映其 JUnit 5 Extension 本质。
- **参数化**：`@Parameterized.Parameters(name = "format={0}") public static Object[] parameters() { return new Object[] {"avro", "parquet", "orc"}; }` → `@Parameters(name = "format={0}") public static Collection<FileFormat> fileFormat() { return Arrays.asList(FileFormat.AVRO, FileFormat.PARQUET, FileFormat.ORC); }`。返回类型从 `Object[]`（字符串数组）改为 `Collection<FileFormat>`（枚举集合），方法名从 `parameters` 改为 `fileFormat`（更具语义），直接用 `FileFormat.AVRO/PARQUET/ORC` 枚举值而非字符串，类型更安全。
- **构造器→字段注入**：`protected final FileFormat fileFormat; TestFlinkScan(String fileFormat) { this.fileFormat = FileFormat.fromString(fileFormat); }` → `@Parameter protected FileFormat fileFormat`。删除构造器，`@Parameter` 注解让 `ParameterizedTestExtension` 自动按参数位置注入字段，无需 `FileFormat.fromString` 转换。
- **`@Test` → `@TestTemplate`**：所有测试方法（`testUnpartitionedTable`/`testPartitionedTable`/`testProjection`/`testIdentityPartitionProjections`/`testSnapshotReads`/`testTagReads`/`testBranchReads`/`testIncrementalReadViaTag`/`testIncrementalRead`/`testFilterExpPartition`/`testFilterExp`/`testFilterExpCaseInsensitive`/`testPartitionTypes`/`testCustomizedFlinkDataTypes`）的 `@Test` 改为 `@TestTemplate`。`@TestTemplate` 是 JUnit 5 中专为"同一测试方法可被多次调用"设计的注解，是参数化测试扩展的统一入口。
- **`catalogResource` → `catalogExtension`**：所有 `catalogResource.catalog()`/`catalogResource.tableLoader()` 调用改为 `catalogExtension.catalog()`/`catalogExtension.tableLoader()`。`tableLoader()` 辅助方法内部 `return catalogResource.tableLoader()` 改为 `return catalogExtension.tableLoader()`。
- **断言改 AssertJ**：`Assert.assertEquals("Projected field " + name + " should match", inputRecord.getField(name), actualRecord.getField(i))` → `assertThat(inputRecord.getField(name)).as("Projected field " + name + " should match").isEqualTo(actualRecord.getField(i))`；`Assertions.assertThatThrownBy(...)` → 静态导入 `assertThatThrownBy(...)`。
- **`TEMPORARY_FOLDER` → `temporaryDirectory`**：所有 `new GenericAppenderHelper(table, fileFormat, TEMPORARY_FOLDER)` 改为 `new GenericAppenderHelper(table, fileFormat, temporaryDirectory)`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSource.java`

**修改目的**：迁移 `TestFlinkScan` 下的抽象中间层（Flink Source API 抽象）。

**工作逻辑**：删除构造器 `TestFlinkSource(String fileFormat) { super(fileFormat); }`（父类已无构造器）；`catalogResource.catalog()` → `catalogExtension.catalog()`。该类本身无 `@Test` 方法（都是 `protected abstract`/`protected` 模板方法供子类实现），故无需改 `@TestTemplate`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkScanSql.java`

**修改目的**：迁移 SQL 入口的 Flink 扫描测试。

**工作逻辑**：`import org.junit.Before` → `import org.junit.jupiter.api.BeforeEach`；删除构造器 `public TestFlinkScanSql(String fileFormat) { super(fileFormat); }`；`@Before public void before()` → `@BeforeEach public void before()`；`catalogResource.warehouse()` → `catalogExtension.warehouse()`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBounded.java`

**修改目的**：迁移 FLIP-27 Source API 的有界扫描测试。

**工作逻辑**：移除 `import org.junit.runner.RunWith`、`import org.junit.runners.Parameterized`；移除 `@RunWith(Parameterized.class)` 类注解（父类 `TestFlinkScan` 已有 `@ExtendWith(ParameterizedTestExtension.class)`，子类继承）；删除构造器 `public TestIcebergSourceBounded(String fileFormat) { super(fileFormat); }`；`catalogResource.catalog()` → `catalogExtension.catalog()`。该类继承参数化能力，无需重复声明。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedSql.java`

**修改目的**：迁移 FLIP-27 Source API 的 SQL 入口测试。

**工作逻辑**：`import org.junit.Before` → `import org.junit.jupiter.api.BeforeEach`；删除构造器；`@Before` → `@BeforeEach`；`catalogResource.warehouse()` → `catalogExtension.warehouse()`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkInputFormat.java`

**修改目的**：迁移 `FlinkInputFormat` API 测试（`TestFlinkSource` 的子类）。

**工作逻辑**：新增 import `static org.assertj.core.api.Assumptions.assumeThat`；移除 `import org.junit.Assume`、`import org.junit.Test`；新增 `import org.junit.jupiter.api.TestTemplate`；删除构造器 `public TestFlinkInputFormat(String fileFormat) { super(fileFormat); }`；`@Test` → `@TestTemplate`；`Assume.assumeTrue(...)` → `assumeThat(...)...`；`catalogResource.catalog()` → `catalogExtension.catalog()`；`TEMPORARY_FOLDER` → `temporaryDirectory`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java`

**修改目的**：迁移 Flink 测试通用断言工具类到 AssertJ 风格。385 行变更，是本提交最大的单文件改动。

**工作逻辑**：新增 `import static org.assertj.core.api.Assertions.assertThat`；移除 `import org.junit.Assert`（保留 `import org.assertj.core.api.Assertions` 因为部分复杂断言仍用全限定形式，但逐步迁移）。核心变换是把 `TestHelpers` 中所有静态断言方法（`assertRecords`/`assertRowData`/`assertRow`/`assertRecord`/`assertArray`/`assertGenericData`/`assertFieldValues` 等）里的 `Assert.assertEquals("msg", expected, actual)`/`Assert.assertTrue("msg", cond)`/`Assertions.assertThat(x).as("msg").isInstanceOf(...)` 替换为静态导入的 `assertThat(actual).as("msg").isEqualTo(expected)`/`assertThat(x).isNotNull()`/`assertThat(x).as("msg").isInstanceOf(...)`。例如 `Assert.assertEquals("boolean value should be equal", expected, actual)` → `assertThat(actual).as("boolean value should be equal").isEqualTo(expected)`；`Assert.assertTrue("expected and actual should be both null or not null", expected != null && actual != null)` → `assertThat(expected).isNotNull(); assertThat(actual).isNotNull();`（拆分为两条断言，更清晰）；`Assertions.assertThat(expected).as("Should expect a CharSequence").isInstanceOf(CharSequence.class)` → `assertThat(expected).as("Should expect a CharSequence").isInstanceOf(CharSequence.class)`（仅去掉 `Assertions.` 前缀）。该文件按 Iceberg `Type` 分支断言各种类型（boolean/integer/long/float/double/string/date/time/timestamp/decimal/binary/struct/list/map/uuid/fixed），迁移工作量集中在把这些分支里的 `Assert.assertEquals` 统一改写。`assertRows` 等方法中 `Assertions.assertThat(results).containsExactlyInAnyOrderElementsOf(expected)` 改为 `assertThat(results).containsExactlyInAnyOrderElementsOf(expected)`。

## 小结

本次提交把 Iceberg Flink 1.18 集成模块中"以 `TestFlinkScan` 为根"的扫描测试继承树（6 个测试类）从 JUnit 4 迁移到 JUnit 5。核心变换是 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`、构造器注入 → `@Parameter` 字段注入（参数类型从 `Object[]` 字符串升级为 `Collection<FileFormat>` 枚举，类型更安全）、`@Test` → `@TestTemplate`、`@Before` → `@BeforeEach`。测试夹具全面 JUnit 5 化：`@ClassRule`/`@Rule` → `@RegisterExtension`，`MiniClusterWithClientResource`（Flink JUnit4）→ `MiniClusterExtension`（Flink JUnit5，经 `MiniFlinkClusterExtension` 工厂创建），`HadoopCatalogResource` → `HadoopCatalogExtension`，`TemporaryFolder`（`File`）→ `@TempDir`（`Path`），相应地把 `catalogResource`→`catalogExtension`、`TEMPORARY_FOLDER`→`temporaryDirectory`。断言统一为 AssertJ 静态导入风格，`TestHelpers` 工具类作为最大改动点（385 行），把所有 `Assert.assertEquals`/`assertTrue` 与 `Assertions.assertThat` 全限定调用替换为 `assertThat(...).as("msg").isEqualTo(...)` 链式断言。这次迁移让 Flink 1.18 测试与社区已迁移到 JUnit 5 的 Spark/core 主干对齐，统一了测试基础设施，是一次量大但机械的现代化工作，为后续 Flink 测试使用 JUnit 5 扩展模型（如自定义 `Extension`、参数解析器）扫清障碍。
