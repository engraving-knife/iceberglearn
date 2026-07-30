# 提交 0644：Core, Data: Migrate tests to JUnit5 (#10039)

## 提交信息

- **序号**：0644 / 4088
- **哈希**：4eef2fe8263f11e8e448a11c4e07acf2cbecda7f
- **短哈希**：4eef2fe82
- **日期**：2024-03-28（Thu Mar 28 23:21:31 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Core, Data: Migrate tests to JUnit5 (#10039)
- **PR/Issue**：#10039

## 总体目的

本提交将 Iceberg 的 `core` 与 `data` 模块中 14 个测试类（及测试辅助类）从 JUnit 4 迁移到 JUnit 5（Jupiter），并统一使用 AssertJ 流式断言。

背景动机：
- Iceberg 仓库长期混用 JUnit 4 与 JUnit 5，社区正在持续推进向 JUnit 5 统一的迁移。JUnit 5 模块化更好（Jupiter API / 引擎分离）、扩展模型更现代（`@ExtendWith` 取代 `@RunWith`）、生命周期注解更清晰（`@BeforeEach`/`@AfterEach`），且对 Java 8+ 更友好。
- JUnit 4 的参数化测试（`@RunWith(Parameterized.class)` + 构造器注入）在 JUnit 5 中没有完全等价的标准方案（标准 `@ParameterizedTest` 是方法级而非类级）。为此 Iceberg 引入了自定义的 `ParameterizedTestExtension`（移植自 Flink），以提供与 JUnit 4 类似的类级参数化能力。
- 同时将断言从 JUnit 的 `Assert.assertEquals/assertFalse` 与 AssertJ 的 `Assertions.assertThat` 混用，统一为 AssertJ 的静态导入 `assertThat` / `assertThatThrownBy`，提升可读性与一致性。
- 将测试基类从 `TableTestBase`（JUnit 4）切换到 `TestBase`（JUnit 5），配合整体迁移。

## 如何达成设计目的

整体迁移遵循一套统一的机械式改写规则，逐文件套用：

1. **注解替换**：
   - `org.junit.Test` → `org.junit.jupiter.api.Test`（普通测试）
   - 普通测试方法在参数化类中改为 `org.junit.jupiter.api.TestTemplate`（参数化测试方法必须用 `@TestTemplate`，由 `ParameterizedTestExtension` 驱动）
   - `@Before` → `@BeforeEach`，`@After` → `@AfterEach`

2. **参数化测试改写**（最核心）：
   - JUnit 4：`@RunWith(Parameterized.class)` + `@Parameterized.Parameters(name=...)` 静态方法返回 `Object[]` + 构造器接收参数。
   - JUnit 5（Iceberg 自定义方案）：
     - 类注解 `@ExtendWith(ParameterizedTestExtension.class)`
     - `@Parameters(name=...)` 标注的 `protected static List<Object> parameters()` 方法返回参数列表（来自 `org.apache.iceberg.Parameters`）
     - `@Parameter` 标注的字段接收注入值（来自 `org.apache.iceberg.Parameter`），取代构造器参数
     - 所有测试方法用 `@TestTemplate`
   - `ParameterizedTestExtension`（位于 `api/src/test/java/org/apache/iceberg/`，移植自 Flink）实现 `TestTemplateInvocationContextProvider`，在每次调用时通过反射查找 `@Parameters` 方法获取参数集，并将每个参数值注入到对应 `@Parameter` 字段。

3. **基类切换**：`extends TableTestBase` → `extends TestBase`（`TestBase` 是 JUnit 5 版本的表测试基类，已存在于仓库中）。

4. **临时目录**：
   - `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir public Path temp;`（JUnit 5 的 `org.junit.jupiter.api.io.TempDir`）
   - `temp.newFile()` → `File.createTempFile("test", null, temp.toFile())`
   - `temp.newFolder("orc")` → `java.nio.file.Files.createTempDirectory(temp, "orc").toFile()`

5. **断言统一为 AssertJ**：
   - `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
   - `Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)` 或直接去掉冗余消息
   - `Assert.assertFalse(msg, cond)` → `assertThat(...).doesNotContainKey(...)` 等语义化断言
   - `Assume.assumeTrue(...)` → `assumeThat(...)`（AssertJ 的 `org.assertj.core.api.Assumptions.assumeThat`）
   - `Assertions.assertThat(...)`（静态调用）→ 静态导入 `assertThat(...)`
   - `Assertions.assertThatThrownBy(...)` → 静态导入 `assertThatThrownBy(...)`
   - `Iterables.size(...)` + `assertEquals` → `assertThat(...).hasSize(n)` / `.isEmpty()`
   - `ImmutableList.of(...)` 相等比较 → `.containsExactly(...)`

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestWapWorkflow.java`

**修改目的**：将 WAP（Write-Audit-Publish）工作流测试迁移到 JUnit 5 参数化方案。

**工作逻辑**：
- 类注解由 `@RunWith(Parameterized.class)` 改为 `@ExtendWith(ParameterizedTestExtension.class)`，基类由 `TableTestBase` 改为 `TestBase`。
- 删除构造器 `public TestWapWorkflow(int formatVersion)`，改为 `@Parameter private int formatVersion;` 字段注入；`@Parameterized.Parameters` 的 `Object[] parameters()` 改为 `@Parameters` 的 `List<Object> parameters()` 返回 `Arrays.asList(1, 2)`。
- `@Before` → `@BeforeEach`，所有 `@Test` → `@TestTemplate`。
- 大量 `Assert.assertEquals(msg, expected, actual)` 改写为 `assertThat(actual).isEqualTo(expected)`（去掉冗余消息或用 `.as(msg)` 保留）；`Assertions.assertThatThrownBy` → `assertThatThrownBy`。例如 `Assert.assertEquals("Metadata should have both snapshots", 2, base.snapshots().size())` → `assertThat(base.snapshots()).hasSize(2)`。
- 移除对 `Iterables` 的依赖，改用 AssertJ 的 `hasSize`。该文件删除行数最多（554 行变更，大幅瘦身），主要是断言精简。

### `core/src/test/java/org/apache/iceberg/TestMetrics.java`

**修改目的**：将 Metrics 抽象测试基类迁移到 JUnit 5 参数化方案。

**工作逻辑**：
- 去掉构造器 `protected TestMetrics(int formatVersion)`，改为 `@Parameter private int formatVersion;` + `@Parameters` 静态方法返回 `Arrays.asList(1, 2)`。
- `@Rule public TemporaryFolder temp` → `@TempDir public Path temp`。
- `@After` → `@AfterEach`。
- 所有 `@Test` → `@TestTemplate`（因为这是参数化基类，子类通过 `@ExtendWith(ParameterizedTestExtension.class)` 驱动）。
- `Assert.assertEquals(2L, (long) metrics.recordCount())` → `assertThat(metrics.recordCount()).isEqualTo(2L)`。
- `Assume.assumeTrue(...)` → `assumeThat(...)`（导入 `org.assertj.core.api.Assumptions.assumeThat`）。
- 新增 `java.nio.file.Files`/`Path`/`Arrays` 导入，移除 `java.util.Objects`、`org.junit.*` 旧导入。
- 此基类被 `TestParquetMetrics`、`TestOrcMetrics`（data 模块）继承，迁移后子类需相应改为 `@ExtendWith(ParameterizedTestExtension.class)`。

### `core/src/test/java/org/apache/iceberg/FilterFilesTestBase.java`

**修改目的**：将过滤文件测试基类中的 JUnit 4 断言迁移到 AssertJ。

**工作逻辑**：
- `assertEquals(0, Iterables.size(emptyScan.planFiles()))` → `assertThat(emptyScan.planFiles()).isEmpty()`。
- `assertEquals(1, Iterables.size(nonEmptyScan.planFiles()))` → `assertThat(nonEmptyScan.planFiles()).hasSize(1)`。
- 移除 `org.junit.Assert.assertEquals` 与 `Iterables` 导入，改为 `org.assertj.core.api.Assertions.assertThat` 静态导入。该类本身是抽象基类，注解层面改动较少。

### `core/src/test/java/org/apache/iceberg/actions/TestCommitService.java`

**修改目的**：将 CommitService 测试迁移到 JUnit 5 参数化方案。

**工作逻辑**：
- 基类 `TableTestBase` → `TestBase`，类注解加 `@ExtendWith(ParameterizedTestExtension.class)`。
- 原构造器 `public TestCommitService() { super(1); }` 改为 `@Parameters` 方法返回 `Arrays.asList(1)`（单参数，保持原行为）+ `@Parameter` 字段。
- `@Test` → `@TestTemplate`。
- `Assertions.assertThat(actual).isEqualTo(expected)` → `assertThat(actual).isEqualTo(expected)`。
- `Assertions.assertThatThrownBy(commitService::close)` → `assertThatThrownBy(commitService::close)`。
- `Assertions.assertThat(commitService.results()).isEqualTo(ImmutableList.of(0,1,2,3,4,5,6,7))` → `assertThat(commitService.results()).containsExactly(0,1,2,3,4,5,6,7)`（语义更精确）。
- 移除 `ImmutableList`/`ImmutableSet` 导入（不再需要），保留 `Sets`。

### `core/src/test/java/org/apache/iceberg/actions/TestSizeBasedRewriter.java`

**修改目的**：将 SizeBasedRewriter 测试迁移到 JUnit 5 参数化方案。

**工作逻辑**：
- 同样的基类切换 `TableTestBase` → `TestBase` + `@ExtendWith(ParameterizedTestExtension.class)`，构造器参数改为 `@Parameter` + `@Parameters`，`@Test` → `@TestTemplate`，断言统一为 AssertJ 静态导入。

### `core/src/test/java/org/apache/iceberg/avro/AvroTestHelpers.java`

**修改目的**：将 Avro 测试辅助类中的 AssertJ 调用从静态类引用改为静态导入，统一风格。

**工作逻辑**：
- `Assertions.assertThat(actual).as("...").hasSameSizeAs(expected)` → `assertThat(actual).as("...").hasSameSizeAs(expected)`。
- 多处 `Assertions.assertThat(...)` → `assertThat(...)`，并合并部分多行链式调用为单行（如 primitive/struct/list/map 分支的断言）。
- 移除 `org.assertj.core.api.Assertions` 导入，改为 `static org.assertj.core.api.Assertions.assertThat` 静态导入。

### `core/src/test/java/org/apache/iceberg/avro/TestNameMappingWithAvroSchema.java`

**修改目的**：将 NameMapping 与 Avro Schema 测试迁移到 JUnit 5。

**工作逻辑**：
- `@Test`（junit）→ `@Test`（jupiter）；`Assert.assertEquals` → `assertThat(...).isEqualTo(...)`。

### `core/src/test/java/org/apache/iceberg/encryption/TestGcmStreams.java`

**修改目的**：将 AES-GCM 加密流测试迁移到 JUnit 5。

**工作逻辑**：
- `@Rule public TemporaryFolder temp` → `@TempDir private Path temp`。
- `temp.newFile()` → `File.createTempFile("test", null, temp.toFile())`。
- `@Test`（junit）→ `@Test`（jupiter）。
- `Assert.assertEquals("File size", 0, decryptedFile.getLength())` → `assertThat(decryptedFile.getLength()).isEqualTo(0)`（消息用 `.as(...)` 保留或省略）。
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。

### `core/src/test/java/org/apache/iceberg/encryption/TestStandardKeyMetadataParser.java`

**修改目的**：将标准密钥元数据解析测试迁移到 JUnit 5。

**工作逻辑**：
- 注解与断言同模式迁移：`@Test` junit → jupiter，`Assert.assertEquals` → `assertThat(...).isEqualTo(...)`。

### `core/src/test/java/org/apache/iceberg/io/TestOutputFileFactory.java`

**修改目的**：将 OutputFileFactory 测试迁移到 JUnit 5。

**工作逻辑**：
- `@Rule public TemporaryFolder temp` → `@TempDir Path temp`；`temp.newFolder(...)` 改用 `Files.createTempDirectory`。
- `@Test` junit → jupiter；`Assert.*` → AssertJ `assertThat(...)`。

### `core/src/test/java/org/apache/iceberg/mapping/TestMappingUpdates.java`

**修改目的**：将 NameMapping 更新测试迁移到 JUnit 5。

**工作逻辑**：
- `@Test` junit → jupiter；`Assert.assertEquals/assertTrue/assertFalse` → AssertJ 链式断言；`Assertions.assertThatThrownBy` → `assertThatThrownBy`。

### `core/src/test/java/org/apache/iceberg/mapping/TestNameMapping.java`

**修改目的**：将 NameMapping 测试迁移到 JUnit 5。

**工作逻辑**：
- `@Test`（junit）→ `@Test`（jupiter）。
- `Assert.assertEquals(expected, mapping.asMappedFields())` → `assertThat(mapping.asMappedFields()).isEqualTo(expected)`。
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。

### `data/src/test/java/org/apache/iceberg/orc/TestOrcMetrics.java`

**修改目的**：将 ORC Metrics 测试（继承 `TestMetrics`）迁移到 JUnit 5 参数化方案。

**工作逻辑**：
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- 删除 `@Parameterized.Parameters` 的 `Object[] parameters()` 与构造器 `public TestOrcMetrics(int formatVersion)`（参数注入由父类 `TestMetrics` 的 `@Parameter` 字段接管）。
- `temp.newFolder("orc")` → `java.nio.file.Files.createTempDirectory(temp, "orc").toFile()`。
- `Assert.assertFalse("...", metrics.lowerBounds().containsKey(fieldId))` → `assertThat(metrics.lowerBounds()).doesNotContainKey(fieldId)`（语义化断言，去掉冗余消息）。

### `data/src/test/java/org/apache/iceberg/parquet/TestParquetMetrics.java`

**修改目的**：将 Parquet Metrics 测试（继承 `TestMetrics`）迁移到 JUnit 5 参数化方案。

**工作逻辑**：
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- 删除 `@Parameterized.Parameters` 方法与构造器（参数注入由父类接管）。
- 其余断言/导入按统一模式调整。

## 小结

本提交是 Iceberg 测试框架统一化（JUnit 4 → JUnit 5）迁移工作的组成部分，覆盖 core 模块 12 个文件与 data 模块 2 个文件，净减少约 192 行代码（554 增 / 746 删），主要源于断言精简和构造器参数改字段注入。

成效：
- 这些测试类统一到 JUnit 5（Jupiter）生命周期与扩展模型，消除 JUnit 4 `@RunWith` 的局限。
- 采用 Iceberg 自定义 `ParameterizedTestExtension`（移植自 Flink）实现类级参数化，保留了 JUnit 4 时代 `@RunWith(Parameterized.class)` 的使用习惯（`@Parameters` + `@Parameter` 字段注入），降低迁移成本。
- 断言全面统一为 AssertJ 流式风格，可读性更好。
- 基类 `TableTestBase` → `TestBase` 的切换配合仓库整体的 JUnit 5 基类迁移。

影响范围：
- 仅影响 `core` 与 `data` 模块的测试代码及测试辅助类，不改动生产代码。
- 由于 `TestMetrics` 是抽象基类，其子类（`TestParquetMetrics`、`TestOrcMetrics`）必须同步迁移，本提交已一并处理。

回迁到 1.4.x 注意事项：
- 前置依赖：1.4.x 必须已存在 `api/src/test/java/org/apache/iceberg/{ParameterizedTestExtension,Parameters,Parameter}.java` 这套自定义参数化框架，以及 JUnit 5 版本的 `TestBase`。若 1.4.x 仍是纯 JUnit 4，需先回迁这些基础设施提交，否则本提交无法编译。
- 需确认 1.4.x 的 `build.gradle` 已引入 JUnit Jupiter 与 AssertJ 依赖，且 JUnit 5 vintage 引擎配置允许新旧测试共存（迁移期间常见做法）。
- 逐文件回迁时可独立进行，但 `TestMetrics` 必须与其两个子类 `TestParquetMetrics`/`TestOrcMetrics` 同时回迁，否则子类会因父类构造器签名变化而编译失败。
- `@TempDir` 注入的 `Path` 与原 `TemporaryFolder` 的 `newFile()/newFolder()` API 不同，回迁时需逐一确认临时文件创建调用点是否已正确改写（如 `File.createTempFile`、`Files.createTempDirectory`）。
