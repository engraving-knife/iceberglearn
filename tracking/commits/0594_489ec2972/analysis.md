# 提交 0594：将 Core 测试迁移到 JUnit5

## 提交信息

- **序号**：0594 / 4088
- **哈希**：489ec2972e352a701745f0a15bdfaa722ad07f3a
- **短哈希**：489ec2972
- **日期**：2024-03-15
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Core: Migrate tests to JUnit5 (#9927)
- **PR/Issue**：#9927

## 总体目的

本提交是 Iceberg 项目从 JUnit4 迁移到 JUnit5 的持续工作的一部分，目标是把 `core` 模块下 6 个仍使用 JUnit4 的测试类迁移到 JUnit5。具体涉及：

- `TableMetadataParserCodecTest`
- `TableMetadataParserTest`
- `TestMetadataTableFilters`
- `TestMetadataUpdateParser`
- `TestTableMetadata`
- `TestTableMetadataSerialization`

迁移的动机：
1. **JUnit4 已停止演进**：JUnit5 是 JUnit 生态的现代标准，提供更丰富的扩展模型、更灵活的参数化测试、更好的模块化。
2. **统一测试栈**：Iceberg 此前已陆续迁移部分测试（PR #9161 引入了自定义 `ParameterizedTestExtension`，PR #9424 引入了 JUnit5 版本的 `TestBase`），本提交继续推进剩余 JUnit4 测试的迁移，保持一致性。
3. **提升断言可读性**：从 JUnit4 的 `Assert.assertEquals(message, expected, actual)` 迁移到 AssertJ 的流式断言 `assertThat(actual).as(message).isEqualTo(expected)`，断言更表达力更强、失败信息更友好。
4. **减少样板代码**：JUnit5 + AssertJ 的组合通常更精简，本提交净减少 226 行代码（750 删除 / 524 新增）。

## 如何达成设计目的

整体迁移遵循 Iceberg 已建立的 JUnit5 迁移模式（由 PR #9161、#9424 确立），核心设计思路是**复用自定义测试基础设施**而非裸用 JUnit5 原生 API，并通过 AssertJ 流式断言统一断言风格。具体实现路径：

1. **注解替换**：将 JUnit4 注解逐一对映到 JUnit5：
   - `@org.junit.Test` → `@org.junit.jupiter.api.Test`
   - `@org.junit.Before` → `@org.junit.jupiter.api.BeforeEach`
   - `@org.junit.After` → `@org.junit.jupiter.api.AfterEach`
   - `@org.junit.Rule TemporaryFolder` → `@org.junit.jupiter.api.io.TempDir Path`

2. **参数化测试迁移**：JUnit5 原生的 `@ParameterizedTest` 只支持方法级参数化，不支持 JUnit4 `@RunWith(Parameterized.class)` 的类级参数化。Iceberg 在 PR #9161 中自定义了 `ParameterizedTestExtension` + `@Parameters` + `@Parameter` 注解来填补这一空缺。本提交将参数化测试从 JUnit4 模式迁移到这套自定义扩展：
   - `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
   - `@Parameterized.Parameters` → `@Parameters`（自定义注解）
   - 构造函数注入参数 → `@Parameter` 字段注入
   - `@Test` → `@TestTemplate`（参数化测试需要按参数集多次执行，`@TestTemplate` 是 JUnit5 的正确注解）
   - `Object[]` / `Object[][]` 返回 → `List<Object>` / `List<Object[]>` 返回

3. **测试基类替换**：`extends TableTestBase`（JUnit4 基类）→ `extends TestBase`（PR #9424 引入的 JUnit5 等价基类）。`TestBase` 提供与 `TableTestBase` 相同的表测试基础设施（`table`、`ops`、FILE_A/B/C/D 等），但使用 JUnit5 的生命周期注解。

4. **断言迁移**：全面切换到 AssertJ 静态导入：
   - `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
   - `Assert.assertEquals(message, expected, actual)` → `assertThat(actual).as(message).isEqualTo(expected)`（许多场景下 message 被省略以简化）
   - `Assert.assertNull(value)` → `assertThat(value).isNull()`
   - `Assert.assertEquals(expected, Iterables.size(tasks))` → `assertThat(tasks).hasSize(expected)`（顺带移除 `Iterables.size` 与 `StreamSupport` 的 Guava 工具调用）
   - `Assertions.assertThatThrownBy(...)`（AssertJ 类调用）→ `assertThatThrownBy(...)`（静态导入）
   - `Assume.assumeTrue(formatVersion == 1)` → `assumeThat(formatVersion).isEqualTo(1)`（AssertJ 的 `Assumptions.assumeThat`）

5. **临时目录迁移**：`@Rule public TemporaryFolder temp = new TemporaryFolder()` → `@TempDir private Path temp`。JUnit5 的 `@TempDir` 直接注入 `java.nio.file.Path`，比 JUnit4 的 `TemporaryFolder`（基于 `java.io.File`）更现代。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TableMetadataParserCodecTest.java`

**修改目的**：将最简单的非参数化测试类迁移到 JUnit5。

**工作逻辑**：
- 导入由 `org.junit.Assert`、`org.assertj.core.api.Assertions`、`org.junit.Test` 改为静态导入 `assertThat`、`assertThatThrownBy` 与 `org.junit.jupiter.api.Test`。
- `Assert.assertEquals(Codec.GZIP, Codec.fromName("gzip"))` 改为 `assertThat(Codec.fromName("gzip")).isEqualTo(Codec.GZIP)`（注意 actual 与 expected 的位置调换：JUnit4 是 `(expected, actual)`，AssertJ 是 `assertThat(actual).isEqualTo(expected)`）。
- `Assertions.assertThatThrownBy(...)` 改为静态导入的 `assertThatThrownBy(...)`。

### `core/src/test/java/org/apache/iceberg/TableMetadataParserTest.java`

**修改目的**：将参数化测试迁移到 JUnit5 自定义扩展。

**工作逻辑**：
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- `@Parameterized.Parameters` 静态方法返回 `Object[]` → `@Parameters` 静态方法返回 `List<Object>`（用 `Arrays.asList("none", "gzip")`）。
- 构造函数注入 `String codecName` → `@Parameter` 字段注入 `private String codecName`。
- `@Test` → `@TestTemplate`（因为参数化测试需多次执行）。注意方法名由 `testCompressionProperty` 改为 `testGzipCompressionProperty`（更明确）。
- `@After` → `@AfterEach`。
- `Assert.assertEquals(codec == Codec.GZIP, isCompressed(fileName))` → `assertThat(isCompressed(fileName)).isEqualTo(codec == Codec.GZIP)`。
- `verifyMetadata` 内的 4 个 `Assert.assertEquals` 改为 `assertThat(...).isEqualTo(...)`。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableFilters.java`

**修改目的**：将多参数参数化测试迁移到 JUnit5，并切换到 JUnit5 测试基类。

**工作逻辑**：
- 基类 `TableTestBase` → `TestBase`。
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- 参数化方法 `@Parameterized.Parameters` 返回 `Object[][]`（二维数组，每行 `{type, formatVersion}`）→ `@Parameters` 返回 `List<Object>`，每元素为 `Object[]{formatVersion, type}`。**注意参数顺序调整**：原顺序是 `(type, formatVersion)`，新顺序是 `(formatVersion, type)`，配合 `@Parameter(index = 1)` 指定 `type` 字段取第二个参数。这种调整使 `formatVersion` 作为第一个参数，与 `@Parameters(name = "formatVersion = {0}, table_type = {1}")` 的命名一致，便于在测试报告中快速识别。
- 构造函数 `TestMetadataTableFilters(MetadataTableType type, int formatVersion)` → `@Parameter(index = 1) private MetadataTableType type` 字段注入（`formatVersion` 由基类 `TestBase` 通过其自身的 `@Parameter` 注入）。
- `@Before` → `@BeforeEach`。
- `@Test` → `@TestTemplate`（共 8 个测试方法）。
- `Assume.assumeTrue(formatVersion == 1)` → `assumeThat(formatVersion).isEqualTo(1)`（3 处）。
- `Assert.assertEquals(expectedScanTaskCount(4), Iterables.size(tasks))` → `assertThat(tasks).hasSize(expectedScanTaskCount(4))`，顺带移除 `Iterables` 与 `StreamSupport` 的 Guava 导入。

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`

**修改目的**：将使用 `TemporaryFolder` 的非参数化测试迁移到 JUnit5。

**工作逻辑**：
- `@Rule public TemporaryFolder temp = new TemporaryFolder()` → `@TempDir private Path temp`。
- 导入 `java.nio.file.Path`，移除 `java.util.Objects`、`java.util.UUID`（清理未使用的导入）。
- `org.assertj.core.api.Assertions` 类引用 → 静态导入 `assertThat`、`assertThatThrownBy`、`fail`。
- `org.junit.Assert`、`org.junit.Rule`、`org.junit.Test`、`org.junit.rules.TemporaryFolder` → `org.junit.jupiter.api.Test`、`org.junit.jupiter.api.io.TempDir`。
- `Assert.assertEquals(message, expected, actual)` → `assertThat(actual).as(message).isEqualTo(expected)`（多处）。
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`（多处）。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：将最大的测试类（原 ~571 行改动）迁移到 JUnit5，统一断言风格。

**工作逻辑**：
- 该文件原本已部分使用 JUnit5（`@Test` 已是 `org.junit.jupiter.api.Test`），但仍混用 JUnit4 的 `Assert.*` 与 AssertJ 的 `Assertions.*` 类调用。本提交完成清理：
  - 移除 `org.assertj.core.api.Assertions` 与 `org.junit.Assert` 导入。
  - 新增静态导入 `assertThatThrownBy`、`entry`。
  - `Assert.assertEquals(message, expected, actual)` → `assertThat(actual).isEqualTo(expected)`（大量 message 被省略以简化，因为 AssertJ 的失败信息本身已足够清晰）。
  - `Assert.assertNull(message, value)` → `assertThat(value).isNull()`。
  - `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`（约 10 处）。
  - 部分断言升级为更富表达力的 AssertJ 链式调用，例如 `assertThat(...).containsExactly(1, 2)`、`assertThat(...).containsExactly(entry("key2", "val2"))`、`assertThat(...).hasSize(2).containsExactlyElementsOf(...)`。

### `core/src/test/java/org/apache/iceberg/TestTableMetadataSerialization.java`

**修改目的**：将参数化测试迁移到 JUnit5 自定义扩展并切换基类。

**工作逻辑**：
- 基类 `TableTestBase` → `TestBase`。
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- `@Parameterized.Parameters` 返回 `Object[]` → `@Parameters` 返回 `List<Object>`（`Arrays.asList(1, 2)`）。
- 构造函数 `TestTableMetadataSerialization(int formatVersion)` → 基类 `TestBase` 的 `@Parameter` 字段注入 `formatVersion`。
- `@Test` → `@TestTemplate`。
- `Assert.assertEquals(message, expected, actual)` → `assertThat(actual).isEqualTo(expected)`（约 10 处，message 全部省略）。
- 一处 `assertThat(Lists.transform(result.snapshots(), Snapshot::snapshotId)).isEqualTo(Lists.transform(meta.snapshots(), Snapshot::snapshotId))` 将 actual 放到 `assertThat()` 中（符合 AssertJ 习惯：`assertThat(actual)` 而非 `assertThat(expected)`）。

## 小结

本提交是 Iceberg JUnit4 → JUnit5 迁移工作的批量推进，共修改 6 个测试文件，524 行新增、750 行删除（净减 226 行）。

**成效**：
- 6 个测试类全部迁移到 JUnit5，统一了测试栈。
- 断言全面切换到 AssertJ 流式风格，可读性与失败诊断信息更优。
- 参数化测试统一使用 Iceberg 自定义 `ParameterizedTestExtension`，支持类级参数化（JUnit5 原生不支持）。
- 净减少 226 行代码，体现了 AssertJ + JUnit5 的简洁性。
- 测试基类统一为 `TestBase`（JUnit5 版本），为后续彻底移除 `TableTestBase` 铺路。

**影响范围**：
- 仅影响 `core` 模块的测试代码，不影响生产代码与公共 API。
- 依赖 Iceberg 自定义测试基础设施：`ParameterizedTestExtension`、`@Parameters`、`@Parameter`（PR #9161 引入）、`TestBase`（PR #9424 引入）。
- 依赖 AssertJ 的 `Assumptions.assumeThat`（用于替代 JUnit4 的 `Assume.assumeTrue`）。

**回迁到 1.4.x 的注意事项**：
- 本提交为纯测试代码迁移，不影响生产功能，回迁优先级较低。
- 回迁前必须确认 1.4.x 是否已具备前置依赖：
  - `ParameterizedTestExtension`、`@Parameters`、`@Parameter` 注解（来自 PR #9161）。
  - `TestBase` JUnit5 基类（来自 PR #9424）。
  - `build.gradle` 中 JUnit5 与 AssertJ 的依赖配置。
- 若 1.4.x 缺少这些前置基础设施，需要先回迁 PR #9161 与 #9424，否则编译会失败。
- 若不打算回迁前置基础设施，则本提交不应回迁，保持 1.4.x 测试使用 JUnit4 即可（功能等价）。
- 注意 `TestMetadataTableFilters` 中参数顺序的调整（`formatVersion` 提前到第一位），若 1.4.x 中有其他测试依赖于该类的参数顺序，需同步调整。
