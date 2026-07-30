# 提交 0359：Spark, Flink: Migrate DeleteReadTests and its subclasses to JUnit5 (#9382)

## 提交信息

- **序号**：0359
- **哈希**：bc7e56c2e5e9c5a3b97e599ab518c77d65014db9
- **短哈希**：bc7e56c2e
- **日期**：2024-01-15 14:51:03 +0100
- **作者**：Chinmay Bhat
- **提交说明**：Spark, Flink: Migrate DeleteReadTests and its subclasses to JUnit5 (#9382)
- **PR/Issue**：#9382

## 总体目的

本提交是 Iceberg 项目"测试框架从 JUnit4 迁移到 JUnit5"系列工作的一个有机组成部分，专门处理 `DeleteReadTests` 抽象测试基类及其在 Spark（3.3/3.4/3.5）、Flink（1.16/1.17/1.18）、MR（MapReduce InputFormat）、Generic Data 等多模块下的全部子类。`DeleteReadTests` 是 Iceberg 跨引擎读取删改（equality delete / position delete / mixed delete）行为的核心测试基类，定义了 `testEqualityDeletes`、`testPositionDeletes`、`testMixedPositionAndEqualityDeletes`、`testMultipleEqualityDeleteSchemas`、`testEqualityDeleteByNull` 等十余个测试模板方法，子类只需实现 `createTable`、`rowSet`、`dropTable` 等抽象方法即可复用全部测试用例。这一基类的 JUnit4 → JUnit5 迁移会自动带动所有子类一起进入 JUnit5 体系，是迁移工作的"高杠杆点"。

迁移涉及的关键技术替换包括：(1) **测试运行器**：JUnit4 的 `@RunWith(Parameterized.class)` + 构造函数注入参数，替换为 JUnit5 的 `@ExtendWith(ParameterizedTestExtension.class)`（Iceberg 自有的参数化扩展，封装了 JUnit5 `ParameterizedTestExtension` 的常用模式）+ `@Parameter` 字段注入 + `@Parameters` 静态方法提供参数；(2) **测试方法注解**：`@Test`（org.junit.Test）→ `@TestTemplate`（org.junit.jupiter.api.TestTemplate），用 `@TestTemplate` 而非 `@Test` 是因为参数化测试在 JUnit5 中需要"按参数动态生成测试实例"的模板语义，`@Test` 只能对应单次执行；(3) **生命周期回调**：`@Before`/`@After`/`@BeforeClass`/`@AfterClass` → `@BeforeEach`/`@AfterEach`/`@BeforeAll`/`@AfterAll`；(4) **临时目录**：JUnit4 的 `@Rule public TemporaryFolder temp = new TemporaryFolder()`（`temp.newFile()`/`temp.newFolder()`）→ JUnit5 的 `@TempDir Path temp`（用 `File.createTempFile("junit", null, temp.toFile())` 与 `Files.createTempDirectory(temp, "junit")` 替代 `temp.newFile()`/`temp.newFolder()`）；(5) **断言**：JUnit4 的 `Assert.assertEquals(msg, expected, actual)` → AssertJ 的 `assertThat(actual).as(msg).isEqualTo(expected)`，`Assert.assertTrue(msg, cond)` → `assertThat(cond).as(msg).isTrue()`，`Assert.assertEquals(msg, 0, size)` → `assertThat(coll).as(msg).hasSize(0)`；(6) **假设**：JUnit4 的 `Assume.assumeTrue(format.equals("parquet"))` → AssertJ 的 `assumeThat(format).isEqualTo("parquet")`（来自 `org.assertj.core.api.Assumptions.assumeThat`，与 JUnit5 `@TestTemplate` 兼容）。这些替换不仅是机械注解改名，还涉及临时目录 API 的语义差异——JUnit4 的 `TemporaryFolder.newFile()` 直接返回 `File`，而 JUnit5 的 `@TempDir Path` 没有等价的 `newFile()`，需用 JDK 原生 `File.createTempFile` / `Files.createTempDirectory` 在 `@TempDir` 指定的目录下创建临时文件/目录，这也是本提交大量 `temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())` 替换的原因。

此外，本提交还顺手清理了 Flink 子类（`TestFlinkReaderDeletesBase`、`TestFlinkInputFormatReaderDeletes`、`TestIcebergSourceReaderDeletes`）中重复的参数化逻辑——原本每个 Flink 子类都有自己的 `@Parameterized.Parameters`、构造函数、`FileFormat format` 字段，迁移后这些统一上移到基类 `DeleteReadTests`（新增 `@Parameter protected FileFormat format` 字段 + `@Parameters` 静态方法），子类删除重复的参数化代码，消除冗余。Flink 子类原本的 `MiniClusterWithClientResource`（JUnit4 `@ClassRule`）也被替换为 JUnit5 的 `MiniClusterExtension`（`@RegisterExtension`），通过 Iceberg 自有的 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()` 工厂方法创建。MR 模块的 `TestHelper` 则新增了一个接受 `Path temp` 的构造函数（与旧的 `TemporaryFolder tmp` 构造函数并存，旧的标 `@Deprecated`），以适配 `@TempDir Path` 注入的新测试风格。

## 如何达成设计目的

实现路径以基类 `DeleteReadTests` 为核心，向下传播到所有子类。基类侧的关键改动是引入参数化字段与 `@TestTemplate`：原本 `DeleteReadTests` 是普通抽象类（不带参数化），子类各自实现 `@RunWith(Parameterized.class)` + 构造函数注入 `FileFormat`；迁移后 `DeleteReadTests` 自身成为参数化基类，新增 `@Parameter protected FileFormat format` 字段、`@Parameters(name = "fileFormat = {0}")` 静态方法返回 `PARQUET/AVRO/ORC` 三种格式，并把所有 `@Test` 改为 `@TestTemplate`（因为参数化模板方法需要按参数重复执行）。同时 `@Rule public TemporaryFolder temp` 改为 `@TempDir protected Path temp`，所有 `temp.newFile()` 改为 `File.createTempFile("junit", null, temp.toFile())`——注意保留前缀 `junit` 是为了在调试时能识别这些是测试创建的临时文件。`Assert.assertEquals(...)` 全部改为 AssertJ `assertThat(...).as(...).isEqualTo(...)` 链式断言。

子类侧的改动遵循统一模式：(a) 删除构造函数与 `@Parameterized.Parameters`（参数化逻辑已上移到基类）；(b) 类上加 `@ExtendWith(ParameterizedTestExtension.class)`；(c) 多参数子类（如 Spark 的 `format` + `vectorized` 两参数、Spark 3.4/3.5 的 `format` + `vectorized` + `planningMode` 三参数、MR 的 `inputFormat` + `fileFormat` 两参数）保留自己的 `@Parameter` 字段与 `@Parameters` 静态方法（覆盖基类的单参数版本），通过 `@Parameter(index = 1)` / `@Parameter(index = 2)` 指定多参数下标；(d) `@BeforeClass`/`@AfterClass` 改为 `@BeforeAll`/`@AfterAll`，方法必须改为 `static`（JUnit5 要求）；(e) `@Before`/`@After` 改为 `@BeforeEach`/`@AfterEach`；(f) 临时目录相关 API 跟随基类改动；(g) Flink 的 `MiniClusterWithClientResource`（`@ClassRule`）改为 `MiniClusterExtension`（`@RegisterExtension`）。`TestHelper`（MR 模块）特殊处理：新增接受 `Path temp` 的构造函数，旧的 `TemporaryFolder tmp` 构造函数标 `@Deprecated` 保留兼容，`appender()` 方法按 `tmp != null` 分支选择调用哪个 `GenericAppenderHelper` 构造函数。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/DeleteReadTests.java`

**修改目的**：把跨引擎删除读取测试的抽象基类从 JUnit4 迁移到 JUnit5，并把参数化逻辑（`FileFormat` 三选一）从各子类上移到基类，作为本次迁移的核心。

**工作逻辑**：(1) **import 替换**：删除 `org.junit.After`/`Assert`/`Before`/`Rule`/`Test`/`rules.TemporaryFolder`，新增 `org.junit.jupiter.api.AfterEach`/`BeforeEach`/`TestTemplate`/`io.TempDir`、`org.assertj.core.api.Assertions.assertThat`、`org.apache.iceberg.Parameter`/`Parameters`、`java.io.File`/`java.nio.file.Path`、`org.apache.iceberg.FileFormat`。(2) **临时目录字段**：`@Rule public TemporaryFolder temp = new TemporaryFolder()` → `@TempDir protected Path temp`（注意可见性从 `public` 改为 `protected`，供子类访问）。(3) **参数化字段与方法（核心新增）**：新增 `@Parameter protected FileFormat format;` 字段与 `@Parameters(name = "fileFormat = {0}") public static Object[][] parameters()` 返回 `{{PARQUET}, {AVRO}, {ORC}}`——把原本分散在各子类的参数化逻辑统一上移到基类。(4) **生命周期**：`@Before` → `@BeforeEach`，`@After` → `@AfterEach`。(5) **临时文件创建**：所有 `temp.newFile()` 改为 `File.createTempFile("junit", null, temp.toFile())`——因为 JUnit5 的 `@TempDir Path` 没有 `newFile()` 方法，需用 JDK 原生 API 在 `temp` 目录下创建临时文件。(6) **断言**：`Assert.assertEquals("Table should contain expected number of deletes", expectedDeletes, actualDeletes)` → `assertThat(actualDeletes).as("Table should contain expected number of deletes").isEqualTo(expectedDeletes)`——注意 AssertJ 的参数顺序是 `actual` 在前、`expected` 在后，与 JUnit4 的 `assertEquals(expected, actual)` 相反。(7) **测试方法注解**：所有 `@Test` → `@TestTemplate`（参数化模板方法）。

### `data/src/test/java/org/apache/iceberg/data/TestGenericReaderDeletes.java`

**修改目的**：让 Generic Data 模块的删除读取测试子类跟随基类迁移到 JUnit5。

**工作逻辑**：类上加 `@ExtendWith(ParameterizedTestExtension.class)`，import 删除 `org.junit.Assert`，新增 `org.assertj.core.api.Assertions.assertThat`、`java.nio.file.Files`、`org.apache.iceberg.ParameterizedTestExtension`。`createTable` 实现中 `temp.newFolder()` + `Assert.assertTrue(tableDir.delete())` 改为 `Files.createTempDirectory(temp, "junit").toFile()` + `assertThat(tableDir.delete()).isTrue()`——用 `Files.createTempDirectory` 在 `@TempDir` 目录下创建子目录，避免 `TemporaryFolder.newFolder()` 的 API。该子类无自定义参数化（沿用基类的 `FileFormat` 单参数），故不再需要自己的 `@Parameter` / `@Parameters`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkReaderDeletesBase.java`（及 v1.17、v1.18 同名文件）

**修改目的**：把 Flink 三个版本（1.16/1.17/1.18）的删除读取测试抽象子类从 JUnit4 `@RunWith(Parameterized.class)` 迁移到 JUnit5 `@ExtendWith(ParameterizedTestExtension.class)`，并删除冗余的参数化代码与 `TemporaryFolder`。

**工作逻辑**：(1) 类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。(2) 删除 `@ClassRule public static final TemporaryFolder TEMP_FOLDER = new TemporaryFolder();`（不再需要）。(3) 删除 `protected final FileFormat format;` 字段、`@Parameterized.Parameters` 静态方法、`TestFlinkReaderDeletesBase(FileFormat fileFormat)` 构造函数——参数化逻辑已上移到基类 `DeleteReadTests`。(4) `@BeforeClass` → `@BeforeAll`、`@AfterClass` → `@AfterAll`（`startMetastore`/`stopMetastore` 方法本来就是 static，符合 JUnit5 要求）。(5) import 清理：删除 `org.junit.AfterClass`/`BeforeClass`/`ClassRule`/`rules.TemporaryFolder`/`runner.RunWith`/`runners.Parameterized` 与 `org.apache.iceberg.FileFormat`，新增 `org.apache.iceberg.ParameterizedTestExtension`、`org.junit.jupiter.api.AfterAll`/`BeforeAll`/`extension.ExtendWith`。三个 Flink 版本的改动完全一致（v1.16/v1.17/v1.18），是平行迁移。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkInputFormatReaderDeletes.java`（及 v1.17、v1.18 同名文件）

**修改目的**：删除 Flink InputFormat 子类中冗余的构造函数（参数化逻辑已上移到基类）。

**工作逻辑**：删除 `public TestFlinkInputFormatReaderDeletes(FileFormat inputFormat) { super(inputFormat); }` 构造函数与 `import org.apache.iceberg.FileFormat;`。子类不再需要构造函数注入参数，因为基类已通过 `@Parameter` 字段注入。三个 Flink 版本改动一致（v1.18 还顺带删除了一个多余空行）。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceReaderDeletes.java`（及 v1.17、v1.18 同名文件）

**修改目的**：把 Flink IcebergSource 子类的 Flink MiniCluster 资源从 JUnit4 `@ClassRule MiniClusterWithClientResource` 迁移到 JUnit5 `@RegisterExtension MiniClusterExtension`，并删除冗余构造函数。

**工作逻辑**：(1) 删除 `@ClassRule public static final TemporaryFolder TMP_FOLDER = new TemporaryFolder();`。(2) `@ClassRule public static final MiniClusterWithClientResource MINI_CLUSTER = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()...build())` 替换为 `@RegisterExtension private static final MiniClusterExtension MINI_CLUSTER = MiniFlinkClusterExtension.createWithClassloaderCheckDisabled();`——`MiniClusterWithClientResource` 是 Flink 提供的 JUnit4 资源，`MiniClusterExtension` 是 Flink 提供的 JUnit5 等价物，Iceberg 通过自有的 `MiniFlinkClusterExtension` 工厂方法封装创建逻辑（禁用 classloader 检查以适配 Iceberg 测试环境）。(3) 删除 `public TestIcebergSourceReaderDeletes(FileFormat inputFormat) { super(inputFormat); }` 构造函数。(4) import 清理：删除 `MiniClusterResourceConfiguration`、`MiniClusterWithClientResource`、`FileFormat`、`org.junit.ClassRule`/`rules.TemporaryFolder`，新增 `MiniClusterExtension`、`MiniFlinkClusterExtension`、`org.junit.jupiter.api.extension.RegisterExtension`。三个 Flink 版本改动一致。

### `mr/src/test/java/org/apache/iceberg/mr/TestHelper.java`

**修改目的**：为 `TestHelper` 新增接受 `java.nio.file.Path temp` 的构造函数，适配 JUnit5 `@TempDir Path` 注入风格，同时保留旧的 `TemporaryFolder tmp` 构造函数（标 `@Deprecated`）以兼容尚未迁移的调用方。

**工作逻辑**：(1) 新增 `private final Path temp;` 字段与 `import java.nio.file.Path;`。(2) 旧构造函数 `TestHelper(..., TemporaryFolder tmp)` 加 `@Deprecated` 注解，函数体中 `this.temp = null; this.tmp = tmp;`。(3) 新增构造函数 `TestHelper(..., Path temp)`，函数体中 `this.temp = temp; this.tmp = null;`——两个构造函数通过 `null` 标记互斥，让 `appender()` 方法能判断用哪个。(4) `appender()` 方法改为分支：`if (null != tmp) { return new GenericAppenderHelper(table, fileFormat, tmp, conf); } return new GenericAppenderHelper(table, fileFormat, temp, conf);`——`GenericAppenderHelper` 同样有两个重载构造函数，分别接受 `TemporaryFolder` 与 `Path`。这种"双构造函数 + 分支选择"模式让 `TestHelper` 在 JUnit4 与 JUnit5 测试代码中都可用，是迁移过渡期的常见做法。

### `mr/src/test/java/org/apache/iceberg/mr/TestInputFormatReaderDeletes.java`

**修改目的**：把 MR InputFormat 删除读取测试从 JUnit4 参数化迁移到 JUnit5，使用 Iceberg 自有的 `@Parameter`/`@Parameters`/`ParameterizedTestExtension`。

**工作逻辑**：(1) 类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。(2) 参数字段从构造函数注入改为字段注入：`private final String inputFormat; private final FileFormat fileFormat;` → `@Parameter private String inputFormat; @Parameter(index = 1) private FileFormat fileFormat;`——这里子类有两个参数（`inputFormat` 与 `fileFormat`），覆盖基类的单参数 `FileFormat format`，故保留自己的 `@Parameters` 静态方法返回 `{{"IcebergInputFormat", PARQUET}, ...}` 6 种组合。(3) `@Parameterized.Parameters` → `@Parameters`（Iceberg 自有注解，name 模板从 `"inputFormat = {0}, fileFormat={1}"` 微调为 `"inputFormat = {0}, fileFormat = {1}"`——加空格规范化）。(4) `@Before` → `@BeforeEach`。(5) 删除构造函数 `TestInputFormatReaderDeletes(String, FileFormat)`。(6) `createTable` 中 `temp.newFolder(inputFormat, fileFormat.name())` + `Assert.assertTrue(location.delete())` 改为 `temp.resolve(inputFormat).resolve(fileFormat.name()).toFile()` + `assertThat(location.mkdirs()).isTrue()`——用 `Path.resolve` 拼接子路径、`mkdirs()` 创建目录（注意：原来是先 `newFolder` 创建再 `delete()` 让 `createTable` 内部重建，新写法是直接 `mkdirs()` 创建，语义略有不同但效果等价）。(7) `helper = new TestHelper(conf, tables, location.toString(), schema, spec, fileFormat, temp)` 使用新构造函数（接受 `Path`）。(8) import 替换：JUnit4 注解 → JUnit5 + Iceberg 参数化注解 + AssertJ。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`

**修改目的**：把 Spark 3.3 删除读取测试从 JUnit4 参数化迁移到 JUnit5，覆盖 `format` + `vectorized` 两参数。

**工作逻辑**：(1) 类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。(2) 参数字段从构造函数注入改为字段注入：`private final String format; private final boolean vectorized;` → `@Parameter private String format; @Parameter(index = 1) private boolean vectorized;`——子类有两参数（`format` 与 `vectorized`），覆盖基类的单参数 `FileFormat format`，故保留自己的 `@Parameters` 静态方法返回 `{{"parquet", false}, {"parquet", true}, {"orc", false}, {"orc", true}, {"avro", false}}` 5 种组合。(3) `@BeforeClass` → `@BeforeAll`、`@AfterClass` → `@AfterAll`、`@After` → `@AfterEach`（`startMetastoreAndSpark`/`stopMetastoreAndSpark` 本就是 static）。(4) 所有 `@Test` → `@TestTemplate`。(5) 所有 `temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`。(6) 断言：`Assert.assertEquals("Table should contain no rows", 0, actual.size())` → `assertThat(actual).as("Table should contain no rows").hasSize(0)`，`Assert.assertEquals("should include 4 deleted row", 4, actualRowSet.size())` → `assertThat(actualRowSet).as("should include 4 deleted row").hasSize(4)`，`Assert.assertEquals("deleted row should be matched", expectedRowSet, actualRowSet)` → `assertThat(actualRowSet).as("deleted row should be matched").isEqualTo(expectedRowSet)`，`Assert.assertTrue("Delete should succeed", testFile.delete())` → `assertThat(testFile.delete()).as("Delete should succeed").isTrue()`，`Assert.assertEquals(193, rowSet(...).size())` → `assertThat(rowSet(...)).hasSize(193)`。(7) 假设：`Assume.assumeTrue(format.equals("parquet"))` → `assumeThat(format).isEqualTo("parquet")`（来自 `org.assertj.core.api.Assumptions.assumeThat`，与 JUnit5 `@TestTemplate` 兼容）。(8) import 替换：删除 `org.junit.After`/`AfterClass`/`Assert`/`Assume`/`BeforeClass`/`Test`/`runner.RunWith`/`runners.Parameterized`，新增 `org.junit.jupiter.api.AfterAll`/`AfterEach`/`BeforeAll`/`TestTemplate`/`extension.ExtendWith`、`org.assertj.core.api.Assertions.assertThat`、`org.assertj.core.api.Assumptions.assumeThat`、`org.apache.iceberg.Parameter`/`ParameterizedTestExtension`/`Parameters`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`（及 v3.5 同名文件）

**修改目的**：把 Spark 3.4 与 3.5 删除读取测试从 JUnit4 参数化迁移到 JUnit5，覆盖 `format` + `vectorized` + `planningMode` 三参数（比 3.3 多一个 `PlanningMode` 参数）。

**工作逻辑**：与 Spark 3.3 改动模式完全一致，差异仅在参数数量：子类有 `@Parameter private String format;`、`@Parameter(index = 1) private boolean vectorized;`、`@Parameter(index = 2) private PlanningMode planningMode;` 三个字段，`@Parameters(name = "format = {0}, vectorized = {1}, planningMode = {2}")` 静态方法返回包含 `PlanningMode.DISTRIBUTED`/`PlanningMode.LOCAL` 的更多组合。其余注解替换、临时文件 API 替换、断言替换、假设替换、import 替换与 Spark 3.3 完全相同。Spark 3.4 与 3.5 的 diff 完全一致（同样的行号、同样的改动），是平行迁移。值得注意的是，`@Parameter(index = 2)` 这种显式 index 在多参数场景下是必要的，因为基类 `DeleteReadTests` 已有一个无 index 的 `@Parameter protected FileFormat format;`（默认 index=0），子类要覆盖基类的参数化就必须重新声明自己的 `@Parameter` 字段并显式指定 index，否则 JUnit5 参数注入会冲突。这一设计让基类与子类的参数化能各自独立工作，互不干扰。

## 小结

本次提交是 Iceberg 测试框架 JUnit4 → JUnit5 迁移系列的关键一步，集中处理 `DeleteReadTests` 抽象基类及其在 4 个模块（data/mr/flink/spark）、3 个 Flink 版本（1.16/1.17/1.18）、3 个 Spark 版本（3.3/3.4/3.5）下的全部子类，共 16 个文件、424 增 397 删。核心设计是把参数化逻辑（`FileFormat` 三选一）从各子类上移到基类 `DeleteReadTests`，子类只需声明自己的多参数 `@Parameter` 字段与 `@Parameters` 方法（覆盖基类的单参数版本），大幅消除重复代码。技术替换涵盖六大维度：测试运行器（`@RunWith` → `@ExtendWith`）、测试方法注解（`@Test` → `@TestTemplate`，因参数化模板需动态生成测试实例）、生命周期回调（`@Before`/`@After`/`@BeforeClass`/`@AfterClass` → `@BeforeEach`/`@AfterEach`/`@BeforeAll`/`@AfterAll`）、临时目录（`@Rule TemporaryFolder` → `@TempDir Path`，配套 `temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`）、断言（JUnit4 `Assert.assertEquals` → AssertJ `assertThat(...).as(...).isEqualTo(...)`）、假设（`Assume.assumeTrue` → AssertJ `assumeThat(...).isEqualTo(...)`）。Flink 子类的 `MiniClusterWithClientResource`（`@ClassRule`）也迁移到 `MiniClusterExtension`（`@RegisterExtension`）。`TestHelper` 通过双构造函数（旧的 `TemporaryFolder` 标 `@Deprecated`、新的 `Path`）实现迁移过渡。本提交让 Iceberg 删除读取测试体系全面进入 JUnit5，与项目整体迁移节奏一致，为后续利用 JUnit5 的高级特性（如 `@Nested`、参数化扩展、动态测试）奠定基础。
