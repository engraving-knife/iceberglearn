# 提交 0361：Core, Spark: Migrate tests that depend on ScanTestBase to JUnit5 (#9416)

## 提交信息

- **序号**：0361
- **哈希**：ea30d363e077763524c2f943ff1b1c5fe65eb295
- **短哈希**：ea30d363e
- **日期**：2024-01-16 09:49:43 +0100
- **作者**：Chinmay Bhat
- **提交说明**：Core, Spark: Migrate tests that depend on ScanTestBase to JUnit5 (#9416)
- **PR/Issue**：#9416

## 总体目的

本提交是 Iceberg 测试基础设施向 JUnit 5 迁移工作的一部分，专门处理"以 `ScanTestBase` 为根的扫描测试继承树"。`ScanTestBase` 是 Iceberg core 模块中所有扫描（TableScan / IncrementalAppendScan / IncrementalChangelogScan / BatchScan 等）相关测试的抽象基类，它本身继承 `TableTestBase`/`TestBase`，并通过 JUnit 4 的 `@RunWith(Parameterized.class)` + 构造器注入的方式对 `formatVersion`（1 或 2）做参数化测试。其下挂了一棵相当深的继承树：`DataTableScanTestBase`、`ScanPlanningAndReportingTestBase`、`FilterFilesTestBase`、`DeleteFileIndexTestBase` 等抽象中间层，再被 `TestLocalDataTableScan`、`TestBaseIncrementalAppendScan`、`TestBaseIncrementalChangelogScan`、`TestLocalFilterFiles` 以及 Spark 模块的 `SparkDistributedDataScanTestBase` 及其 6 个具体子类（Deletes / FilterFiles / JavaSerialization / KryoSerialization / Reporting，覆盖 Spark 3.4 与 3.5 两个版本）所继承。整棵树此前还停留在 JUnit 4 API 上，与社区已经迁移到 JUnit 5 的 `TestBase`/`TableTestBase` 主干不一致，导致这些测试无法享受 JUnit 5 的扩展模型（如 `@ExtendWith`、`@TestTemplate`、参数解析器），也无法与已迁移的测试共用生命周期回调。

迁移的核心是引入 Iceberg 自定义的 `ParameterizedTestExtension`（JUnit 5 的 `ParameterizedExtension` 变体）替代 JUnit 4 的 `@RunWith(Parameterized.class)`。该扩展配合 `@Parameters` 静态工厂方法与 `@Parameter(index = n)` 字段注入，保留了原来"一次定义参数集合、为每组参数运行一次测试"的参数化能力，但改用 JUnit 5 的 `@TestTemplate`（而非 `@Test`）标注测试方法——`@TestTemplate` 是 JUnit 5 中专为"同一测试方法可被多次调用"设计的注解，是参数化测试、重复测试等扩展的统一入口。同时把 `@Before` 换成 `@BeforeEach`，把 `temp.newFolder()`（JUnit 4 `TemporaryFolder`）换成 `Files.createTempDirectory(temp, "junit").toFile()`（因为新的 `TestBase` 使用 JUnit 5 的 `@TempDir`，其类型是 `java.nio.file.Path` 而非 JUnit 4 的 `File`）。

更深层的目的在于统一断言风格。Iceberg 社区已经选定 AssertJ 流式断言（`assertThat(...).isEqualTo(...)`、`assertThatThrownBy(...).isInstanceOf(...).hasMessage(...)`）作为标准，因为它相比 JUnit 4 的 `Assert.assertEquals(message, expected, actual)` 在可读性、链式断言、失败信息丰富度上都更优。本提交把所有被迁移文件中的 `org.junit.Assert.*` 调用、`org.junit.Assume.assumeTrue` 调用、以及 `org.assertj.core.api.Assertions.assertThatThrownBy`（静态方法调用形式）统一改为静态导入的 `assertThat` / `assertThatThrownBy` / `assumeThat`，让测试代码风格与已迁移模块完全一致。这是一次"机械但量大"的清理：23 个文件、626 行新增、667 行删除，跨越 core / data / spark-v3.4 / spark-v3.5 四个模块。

## 如何达成设计目的

迁移遵循一套固定的机械变换规则：(1) 类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；(2) 父类 `extends TableTestBase` → `extends TestBase`（`TableTestBase` 是 JUnit 4 时代的基类，`TestBase` 是已迁移到 JUnit 5 的等价基类，二者共享同样的 `table`/`SCHEMA`/`FILE_A` 等测试夹具）；(3) 参数化工厂方法 `@Parameterized.Parameters` → `@Parameters`，返回类型从 `Object[]`/`Object[][]` 改为 `List<Object>`（每个元素仍是 `Object[]` 表示一组参数），这样既兼容 `ParameterizedTestExtension` 的契约，又能用 `Arrays.asList(...)` 写法更简洁；(4) 构造器注入 `public Foo(int formatVersion, ...)` + `super(formatVersion)` → 删除构造器，改用 `@Parameter(index = 0) private int formatVersion;` 等字段注入，子类无需再写 `super(formatVersion)`；(5) `@Test` → `@TestTemplate`，`@Before` → `@BeforeEach`；(6) 断言全改 AssertJ 静态导入。对于无参构造的基类（如 `ScanPlanningAndReportingTestBase` 原本固定 `super(2)`），改为提供 `@Parameters` 返回 `Arrays.asList(2)`，让参数化框架仍能驱动 `@TestTemplate` 执行。Spark 模块的 `SparkDistributedDataScanTestBase` 参数更复杂（formatVersion × dataMode × deleteMode 共 8 组），同样从构造器注入迁移到 `@Parameter(index = 1)`/`@Parameter(index = 2)` 字段注入，并把 `@Before configurePlanningModes()` 改为 `@BeforeEach`。

## 修改详情

### `core/src/test/java/org/apache/iceberg/ScanTestBase.java`

**修改目的**：把扫描测试的抽象基类从 JUnit 4 迁移到 JUnit 5，并统一断言风格。这是整棵继承树的根，迁移它才能让所有子类顺理成章地走 JUnit 5 执行路径。

**工作逻辑**：类注解从 `@RunWith(Parameterized.class)` 改为 `@ExtendWith(ParameterizedTestExtension.class)`；删除原 `@Parameterized.Parameters` 工厂方法与 `public ScanTestBase(int formatVersion) { super(formatVersion); }` 构造器——因为父类 `TestBase` 已通过 `@Parameter(0)` 字段注入 `formatVersion`，子类无需再转发；父类从 `TableTestBase` 改为 `TestBase`。所有 `@Test` 改为 `@TestTemplate`。断言层面：`assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`；`Assert.assertTrue("msg", cond)` → `assertThat(x).as("msg").isNotEmpty()/isGreaterThan(0)`；`Assert.assertNotEquals("msg", a, b)` → `assertThat(b).as("msg").isNotEqualTo(a)`；`Assume.assumeTrue(formatVersion == 2)` → `assumeThat(formatVersion).isEqualTo(2)`（AssertJ 的 `Assumptions.assumeThat` 在断言失败时跳过测试，语义等价于 JUnit 4 `Assume.assumeTrue`）；`Assertions.assertThatThrownBy(...)`（全限定静态调用）→ 静态导入 `assertThatThrownBy(...)`。临时目录从 `temp.newFolder()`（JUnit 4 `TemporaryFolder` 返回 `File`）改为 `Files.createTempDirectory(temp, "junit").toFile()`——因为新 `TestBase` 的 `temp` 是 JUnit 5 `@TempDir` 注入的 `java.nio.file.Path`，需要用 NIO API 创建子目录再转 `File`。

### `core/src/test/java/org/apache/iceberg/DataTableScanTestBase.java`

**修改目的**：迁移 `ScanTestBase` 下的第一层抽象子类，覆盖数据表扫描（含分支/标签/快照时间旅行等场景）。

**工作逻辑**：`@ExtendWith(ParameterizedTestExtension.class)` 加到类上；删除 `public DataTableScanTestBase(int formatVersion) { super(formatVersion); }` 构造器；`@Test` → `@TestTemplate`；`Assume.assumeTrue(formatVersion == 2)` → `assumeThat(formatVersion).isEqualTo(2)`；所有 `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)` 或 `hasSize(n)`；`Assertions.assertThatThrownBy` → 静态导入 `assertThatThrownBy`；`Assertions.assertThat(x).hasSize(n)` → `assertThat(x).hasSize(n)`。

### `core/src/test/java/org/apache/iceberg/ScanPlanningAndReportingTestBase.java`

**修改目的**：迁移扫描计划与指标上报（ScanMetrics / ScanReport）测试基类。该类原为固定 formatVersion=2 的非参数化测试（无参构造 `super(2)`），迁移后改为参数化框架但参数集合只含单元素 2。

**工作逻辑**：父类 `TableTestBase` → `TestBase`；新增 `@ExtendWith(ParameterizedTestExtension.class)`；新增 `@Parameters(name = "formatVersion = {0}") public static List<Object> parameters() { return Arrays.asList(2); }`——保留单元素参数列表，让 `@TestTemplate` 仍被驱动一次；删除原无参构造器 `ScanPlanningAndReportingTestBase() { super(2); }`；`@Test` → `@TestTemplate`。这样统一了"参数化测试"的形态，即便只有一个参数组合。

### `core/src/test/java/org/apache/iceberg/FilterFilesTestBase.java`

**修改目的**：迁移文件过滤测试基类。

**工作逻辑**：与 `ScanTestBase` 同构的迁移——`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；删除构造器；`@Test` → `@TestTemplate`；断言改 AssertJ 静态导入。

### `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java`

**修改目的**：迁移删除文件索引测试基类（401 行变更，是本提交最大的单文件改动）。

**工作逻辑**：同样的迁移规则——`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；删除构造器与 `@Parameterized.Parameters`；`@Test` → `@TestTemplate`；大量 `Assert.assertEquals`/`Assert.assertTrue`/`Assert.assertFalse` → AssertJ `assertThat(...).isEqualTo(...)`/`isTrue()`/`isFalse()`；`Assume.assumeTrue` → `assumeThat`。由于该文件原本断言密集（删除文件索引涉及 manifest、partition、equality delete、position delete 等多维断言），迁移工作量最大。

### `core/src/test/java/org/apache/iceberg/TestBase.java`

**修改目的**：微调已迁移到 JUnit 5 的 `TestBase` 中 `parameters()` 方法的返回类型，让子类的 `@Parameters` 工厂方法签名一致。

**工作逻辑**：`protected static List<Object[]> parameters()` → `protected static List<Object> parameters()`，返回值从 `Arrays.asList(new Object[] {1}, new Object[] {2})` 改为 `Arrays.asList(1, 2)`。这是因为 `ParameterizedTestExtension` 期望 `@Parameters` 方法返回 `List<Object>`（每个元素代表一组参数；单参数时直接是标量，多参数时是 `Object[]`）。把基类签名统一为 `List<Object>` 后，子类 override 时类型才能对齐。

### `core/src/test/java/org/apache/iceberg/TestBaseIncrementalAppendScan.java`

**修改目的**：迁移增量追加扫描测试。

**工作逻辑**：`@ExtendWith(ParameterizedTestExtension.class)`；删除构造器；`@Test` → `@TestTemplate`；`Assert.assertEquals(3, Iterables.size(scan.planFiles()))` → `assertThat(scan.planFiles()).hasSize(3)`；`Assertions.assertThatThrownBy` → `assertThatThrownBy`；`Assertions.assertThat(x).hasSize(n)` → `assertThat(x).hasSize(n)`。

### `core/src/test/java/org/apache/iceberg/TestBaseIncrementalChangelogScan.java`

**修改目的**：迁移增量变更日志扫描测试。

**工作逻辑**：与同构迁移规则一致——`@ExtendWith`、删构造器、`@TestTemplate`、AssertJ 静态导入。

### `core/src/test/java/org/apache/iceberg/TestLocalDataTableScan.java`

**修改目的**：迁移本地 TableScan 测试（`DataTableScanTestBase` 的具体子类）。

**工作逻辑**：仅删除 `public TestLocalDataTableScan(int formatVersion) { super(formatVersion); }` 构造器——因为父类已不再需要构造器转发 `formatVersion`，该字段由 `TestBase` 的 `@Parameter(0)` 注入。这是迁移后许多叶子测试类最常见的"瘦身"模式。

### `core/src/test/java/org/apache/iceberg/TestLocalFilterFiles.java`

**修改目的**：迁移本地文件过滤测试（`FilterFilesTestBase` 的具体子类）。

**工作逻辑**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；`@Parameterized.Parameters` 工厂方法改用 `@Parameters` 注解、返回 `List<Object>`（`Arrays.asList(1, 2)`）；删除构造器。这是叶子类既要在自身定义参数又要在父类继承体系下工作的典型示例。

### `data/src/test/java/org/apache/iceberg/io/TestGenericSortedPosDeleteWriter.java`

**修改目的**：迁移一个间接依赖 `ScanTestBase` 体系的 data 模块测试。

**工作逻辑**：小改动——主要是 `@Test` → `@TestTemplate`（若该测试在参数化上下文中运行）以及断言风格统一为 AssertJ 静态导入。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/SparkDistributedDataScanTestBase.java`

**修改目的**：迁移 Spark 3.4 模块的分布式扫描测试基类。该类参数化维度更复杂（formatVersion × dataPlanningMode × deletePlanningMode，共 8 组）。

**工作逻辑**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；`@Parameters(name = "formatVersion = {0}, dataMode = {1}, deleteMode = {2}")` 工厂方法返回类型从 `Object[]`（内含 `Object[][]`）改为 `List<Object>`（内含 8 个 `Object[]`）；删除原构造器 `public SparkDistributedDataScanTestBase(int formatVersion, PlanningMode dataPlanningMode, PlanningMode deletePlanningMode) { super(formatVersion); this.dataMode = ...; this.deleteMode = ...; }`，改用 `@Parameter(index = 1) private PlanningMode dataMode;` 与 `@Parameter(index = 2) private PlanningMode deleteMode;` 字段注入（`index = 0` 的 `formatVersion` 由父类 `TestBase` 的 `@Parameter(0)` 注入）；`@Before` → `@BeforeEach`。`@Parameter(index = n)` 的索引对应 `@Parameters` 返回的 `Object[]` 中的位置，这是 `ParameterizedTestExtension` 与 JUnit 4 构造器注入的关键差异——从位置参数变为命名字段。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/TestSparkDistributedDataScanDeletes.java`

**修改目的**：迁移 Spark 3.4 分布式扫描删除文件测试（`SparkDistributedDataScanTestBase` 的子类）。

**工作逻辑**：删构造器；`@Test` → `@TestTemplate`；断言改 AssertJ 静态导入。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/TestSparkDistributedDataScanFilterFiles.java`

**修改目的**：迁移 Spark 3.4 分布式扫描文件过滤测试。

**工作逻辑**：同构迁移——删构造器、`@TestTemplate`、AssertJ 静态导入。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/TestSparkDistributedDataScanJavaSerialization.java`

**修改目的**：迁移 Spark 3.4 分布式扫描 Java 序列化测试（验证扫描对象可被 Java 原生序列化）。

**工作逻辑**：同构迁移——删构造器、`@TestTemplate`、断言改 AssertJ。13 行变更主要是注解与断言替换。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/TestSparkDistributedDataScanKryoSerialization.java`

**修改目的**：迁移 Spark 3.4 分布式扫描 Kryo 序列化测试（验证扫描对象可被 Spark Kryo 序列化）。

**工作逻辑**：与 Java 序列化测试同构。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/TestSparkDistributedDataScanReporting.java`

**修改目的**：迁移 Spark 3.4 分布式扫描指标上报测试。

**工作逻辑**：同构迁移。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/SparkDistributedDataScanTestBase.java`

**修改目的**：迁移 Spark 3.5 模块的分布式扫描测试基类（与 3.4 镜像）。

**工作逻辑**：与 v3.4 版本完全同构的迁移——`@ExtendWith(ParameterizedTestExtension.class)`、`@Parameters` 返回 `List<Object>`、`@Parameter(index = 1/2)` 字段注入、`@BeforeEach`、AssertJ 静态导入。Iceberg 为 Spark 3.4 与 3.5 维护两份平行源码，因此同样的迁移需在两处各做一次。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/TestSparkDistributedDataScanDeletes.java`、`TestSparkDistributedDataScanFilterFiles.java`、`TestSparkDistributedDataScanJavaSerialization.java`、`TestSparkDistributedDataScanKryoSerialization.java`、`TestSparkDistributedDataScanReporting.java`

**修改目的**：迁移 Spark 3.5 模块的 5 个分布式扫描测试子类（与 3.4 镜像）。

**工作逻辑**：均与 v3.4 对应文件同构——删构造器、`@Test` → `@TestTemplate`、断言改 AssertJ 静态导入。

## 小结

本次提交把 Iceberg 中"以 `ScanTestBase` 为根"的整棵扫描测试继承树（core 模块的 6 个基类/子类、data 模块 1 个测试、Spark 3.4 与 3.5 各 6 个测试类，共 23 个文件）从 JUnit 4 迁移到 JUnit 5。核心变换是 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`、构造器注入 → `@Parameter(index)` 字段注入、`@Test` → `@TestTemplate`、`@Before` → `@BeforeEach`、`TableTestBase` → `TestBase`，并把所有断言统一为 AssertJ 静态导入风格（`assertThat`/`assertThatThrownBy`/`assumeThat`）。`TestBase.parameters()` 返回类型从 `List<Object[]>` 改为 `List<Object>`，让基类与 `ParameterizedTestExtension` 契约对齐。临时目录 API 也从 JUnit 4 `TemporaryFolder.newFolder()` 适配为 JUnit 5 `@TempDir`（`Path`）下的 `Files.createTempDirectory`。这次迁移让扫描测试与社区已迁移到 JUnit 5 的主干统一，为后续使用 JUnit 5 扩展模型（如自定义 `Extension`、参数解析器）扫清障碍，是一次量大但机械的测试基础设施现代化工作。
