# 提交 0341：Core: Add JUnit5 version of TableTestBase (#9424)

## 提交信息

- **序号**：0341
- **哈希**：e16bfcffc9c1d57bbe331aa9d545919711a9dd6f
- **短哈希**：e16bfcffc
- **日期**：2024-01-08 21:14:06 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add JUnit5 version of TableTestBase (#9424)
- **PR/Issue**：#9424

## 总体目的

本提交是 Iceberg 测试基础设施从 JUnit 4 迁移到 JUnit 5 工作的关键起点：在 `core` 模块的测试源码中新增一个 JUnit 5 版本的基类 `TestBase`，作为旧 `TableTestBase` 的并行替代品，并配套迁移三个测试类作为首批消费者，同时通过 `build.gradle` 中新增 `useJUnitPlatform()` 让 `iceberg-data` 子项目的测试在 JUnit Platform（JUnit 5 的执行引擎）上运行。这一变更并非"删旧建新"——旧的 `TableTestBase` 仍保留，目的是让两个测试基类在过渡期共存，已迁移的测试走新基类，尚未迁移的测试仍走旧基类，避免一次性大爆炸式迁移带来的合并风险与回归风险。后续 PR 可按模块、按文件逐步把继承自 `TableTestBase` 的测试改造成继承 `TestBase`，最终在全部完成后移除 `TableTestBase`。

从迁移模式角度看，新基类把 JUnit 4 的"构造器注入 + `@RunWith(Parameterized.class)` + `@Parameterized.Parameters`"模型彻底替换为 JUnit 5 的"字段注入 + 自定义 `@ExtendWith` + 自定义 `@Parameters`/`@Parameter` 注解"模型。Iceberg 并没有直接使用 JUnit 5 自带的 `@ParameterizedTest` + `@MethodSource`/`@CsvSource` 风格，而是选择在 `api` 模块中实现自定义的 `ParameterizedTestExtension`、`Parameters`、`Parameter` 三件套（这些类位于 `api/src/test/java/org/apache/iceberg/`），用 JUnit 5 的扩展机制 (`Extension`/`TestTemplate`+`InvocationContext`) 重建了一套与旧 `@Parameterized` 风格几乎一一对应的 API。这一设计是迁移效率的关键：让大量依赖构造器参数化风格的老测试只需做"机械替换"——把 `@RunWith(Parameterized.class)` 改成 `@ExtendWith(ParameterizedTestExtension.class)`、把 `@Parameterized.Parameters` 改成 `@Parameters`、把构造器参数改成 `@Parameter` 字段、把 `@Test` 改成 `@TestTemplate`——而不必逐个测试改写参数供给方式。

第二个设计要点是断言库的同步迁移：从 JUnit 4 的 `org.junit.Assert.assertXxx(expected, actual)` + 失败消息作为第一参数的风格，全面切换到 AssertJ 的 `assertThat(actual).as("description").isEqualTo(expected)` 流式断言风格。AssertJ 的可读性、链式断言能力（如 `containsEntry`、`hasSize`、`isExhausted`）以及与 JUnit 5 的天然契合度都更好。本提交的所有迁移测试都演示了这一断言切换的范式，为后续迁移提供了"参考样板"。

附带地，本提交也修正了一些小问题，例如 `TestGenericSortedPosDeleteWriter` 原先手写的 try-catch + `Assert.assertTrue("...", caughtError)` 模式被改写为 `assertThatThrownBy(...).isInstanceOf(Exception.class)`，更精确地断言被抛异常的类型；注释说明"不再校验底层错误消息因为 JDK 版本可能影响消息内容"，体现了断言不应依赖不稳定细节的良好实践。

## 如何达成设计目的

实现路径分四步：(1) **新增 `TestBase.java`**——把旧 `TableTestBase.java` 的 753 行内容整体复制为新的 754 行 `TestBase.java`，但把所有 JUnit 4 的注解、导入与构造器替换为 JUnit 5 等价物，逻辑主体（schema、partition spec、`FILE_A`/`FILE_B`/`FILE_C`/`FILE_D` 等测试数据文件、`writeManifest`/`validateSnapshot`/`validateManifest` 等工具方法、`TableAssertions` 内部类）原样保留。(2) **`build.gradle` 启用 JUnit Platform**——在 `iceberg-data` 子项目的 `test` 块中加 `useJUnitPlatform()`，让 Gradle 用 JUnit 5 引擎跑该模块的测试（注意只对 `iceberg-data` 启用，因为该模块的 `TestGenericSortedPosDeleteWriter` 是被迁移测试之一；其他模块的 `useJUnitPlatform()` 会在后续迁移时再加）。(3) **迁移三个测试类**——`TestCreateSnapshotEvent`、`TestManifestReader`、`TestGenericSortedPosDeleteWriter`，把 `extends TableTestBase` 改为 `extends TestBase`，并按上述迁移模式逐项替换注解与断言。(4) **保留旧 `TableTestBase`** 不动，让其他未迁移测试继续工作。

## 修改详情

### `build.gradle`

**修改目的**：在 `:iceberg-data` 子项目的 `test` 配置块中新增 `useJUnitPlatform()`，启用 JUnit 5 测试引擎。

**工作逻辑**：Gradle 默认会用 JUnit 4 的 Runner 跑测试；`useJUnitPlatform()` 告诉 Gradle 把测试委托给 JUnit Platform（JUnit 5 的执行引擎），后者通过 `junit-vintage-engine` 兼容跑 JUnit 4 测试、通过 `jupiter-engine` 跑 JUnit 5 测试。这行配置是实现"同一项目里 JUnit 4 与 JUnit 5 测试共存"的关键：本提交迁移的测试用 JUnit 5 写法，未迁移的测试用 JUnit 4 写法，两者都能在 `useJUnitPlatform()` 下运行。注意此改动只针对 `:iceberg-data`，因为本提交迁移的三个测试之一 `TestGenericSortedPosDeleteWriter` 在 `data/` 模块下；`:core` 模块目前仍跑 JUnit 4 风格（其 `useJUnitPlatform()` 会在更后续的提交中加上）。

### `core/src/test/java/org/apache/iceberg/TestBase.java`（新文件，754 行）

**修改目的**：提供 JUnit 5 版本的测试基类，作为旧 `TableTestBase` 的并行替代品。

**工作逻辑**：内容主体与旧 `TableTestBase.java` 几乎逐行对应，主要差异集中在生命周期注解、临时目录、参数化机制三处：

- **类级注解**：`public class TableTestBase` → `@ExtendWith(ParameterizedTestExtension.class) public class TestBase`。`ParameterizedTestExtension` 是 Iceberg 自定义的 JUnit 5 `Extension`（位于 `api/src/test/java/org/apache/iceberg/ParameterizedTestExtension.java`），实现了 `TestTemplateInvocationContextProvider` 接口，把每个标了 `@TestTemplate` 的方法按 `@Parameters` 提供的参数集展开为多次调用。
- **临时目录**：旧 `@Rule public TemporaryFolder temp = new TemporaryFolder();` → 新 `@TempDir protected Path temp;` + `@TempDir protected File tableDir = null;`。`@TempDir` 是 JUnit 5 内置的 `Extension`，自动在测试前创建临时目录、测试后清理；它支持 `Path` 与 `File` 两种类型，本基类用 `Path temp` 做工作目录、用 `File tableDir` 作为 Iceberg 表目录（兼容 `TestTables.create(File, ...)` 等需要 `File` 的旧 API）。
- **参数化字段与构造器**：旧 `protected final int formatVersion; public TableTestBase(int formatVersion) { this.formatVersion = formatVersion; this.V1Assert = new TableAssertions(1, formatVersion); this.V2Assert = new TableAssertions(2, formatVersion); }` → 新 `@Parameters(name = "formatVersion = {0}") protected static List<Object[]> parameters() { return Arrays.asList(new Object[] {1}, new Object[] {2}); } @Parameter protected int formatVersion;`，并把 `V1Assert`/`V2Assert` 的初始化从构造器移到 `@BeforeEach setupTable()`（因为 JUnit 5 字段注入发生在构造之后、`@BeforeEach` 之前，而 `V1Assert` 依赖 `formatVersion`，所以要么在 `@BeforeEach` 中初始化，要么用 `TestInfo` 在构造器中拿到参数——这里选择前者更简单）。`@Parameters` 与 `@Parameter` 都是 Iceberg 自定义注解（位于 `api/src/test/java/org/apache/iceberg/`），通过 `ParameterizedTestExtension` 解析：`@Parameters` 标记的静态方法返回 `List<Object[]>` 作为参数集；`@Parameter(index = N)` 标记的字段按 index 从参数数组中取值注入。这与 JUnit 4 `@Parameterized.Parameter` 风格一致，便于机械迁移。
- **生命周期方法**：`@Before public void setupTable()` → `@BeforeEach public void setupTable() throws Exception`，删除了旧代码中的 `this.tableDir = temp.newFolder(); tableDir.delete();`（因为新版用 `@TempDir File tableDir` 直接由 JUnit 5 创建并保证空目录，不再需要 `temp.newFolder()`+`delete()` 的旧 `TemporaryFolder` 风格）。`@After public void cleanupTables()` → `@AfterEach public void cleanupTables()`。
- **断言库**：把 `import org.junit.Assert;` 替换为 `import static org.assertj.core.api.Assertions.assertThat;`，方法体内的 `Assert.assertEquals(...)`/`Assert.assertTrue(...)` 替换为 `assertThat(...).isEqualTo(...)`/`assertThat(...).isTrue()`。具体替换发生在 `validateSnapshot`/`validateManifest`/`validateDeleteManifest`/`validateManifestEntries` 等工具方法内，例如 `Assert.assertEquals("Should create 1 new manifest and reuse old manifests", 1, newManifests.size())` → `assertThat(newManifests).as("Should create 1 new manifest and reuse old manifests").hasSize(1)`。
- **其余主体**：`SCHEMA`、`SPEC`、`FILE_A`/`FILE_A2`/`FILE_A_DELETES`/`FILE_A2_DELETES`/`FILE_B`/`FILE_B_DELETES`/`FILE_C`/`FILE_C2_DELETES`/`FILE_D`/`FILE_D2_DELETES`/`FILE_WITH_STATS` 等静态测试数据常量、`FILE_IO`、`BUCKETS_NUMBER` 等常量原样保留；`writeManifest`/`writeDeleteManifest`/`writeManifestWithName`/`manifestEntry`/`validateSnapshot`/`validateTableFiles`/`validateBranchFiles`/`validateBranchDeleteFiles`/`validateManifest`/`validateDeleteManifest`/`validateManifestEntries`/`paths`/`newDataFile`/`newDeleteFile`/`newEqualityDeleteFile`/`positionDelete`/`withUnavailableLocations` 等工具方法签名与实现原样保留；`TableAssertions` 内部类（用 `enabled` 标志控制是否在当前 `formatVersion` 下生效，提供 `assertEquals` 重载）原样保留；`Action` 函数式接口原样保留。

注意 `TableAssertions` 的 `enabled` 字段在构造时确定（基于 `validForVersion == formatVersion`），运行时可通过 `enable()`/`disable()` 切换——这是为了支持某些测试在特定步骤临时关闭 V1/V2 断言。新基类把 `V1Assert`/`V2Assert` 改为非 final 字段并在 `@BeforeEach` 中初始化，因为字段注入的 `formatVersion` 在构造器阶段尚未赋值。

### `core/src/test/java/org/apache/iceberg/TestCreateSnapshotEvent.java`

**修改目的**：作为首批迁移示例，演示 `TableTestBase` → `TestBase` 的迁移范式。

**工作逻辑**：迁移模式对应如下：

- import：`org.junit.Assert` → `static org.assertj.core.api.Assertions.assertThat`；`org.junit.Test` → `org.junit.jupiter.api.TestTemplate`；`org.junit.Before` → `org.junit.jupiter.api.BeforeEach`；`org.junit.runner.RunWith` + `org.junit.runners.Parameterized` → 删除（不再需要 `@RunWith`）。
- 类声明：`@RunWith(Parameterized.class) public class TestCreateSnapshotEvent extends TableTestBase` → `public class TestCreateSnapshotEvent extends TestBase`（不再需要类级 `@RunWith`，因为父类 `TestBase` 已经标了 `@ExtendWith(ParameterizedTestExtension.class)`）。
- 参数化方法：`@Parameterized.Parameters(name = "formatVersion = {0}") public static Object[] parameters() { return new Object[] {1, 2}; }` → 删除（参数化方法已移到父类 `TestBase`，子类不再需要重复声明）。
- 构造器：`public TestCreateSnapshotEvent(int formatVersion) { super(formatVersion); Listeners.register(...); }` → 删除构造器，把 `Listeners.register(new MyListener(), CreateSnapshotEvent.class)` 移到新增的 `@BeforeEach public void initListener()` 方法。这是迁移的常见动作：JUnit 5 字段注入不支持构造器参数，所以原构造器中的初始化逻辑要么移到 `@BeforeEach`，要么改用字段注入。
- 测试方法：`@Test public void testAppendCommitEvent()` → `@TestTemplate public void testAppendCommitEvent()`。`@TestTemplate` 表示该方法是一个"测试模板"，由扩展（这里是 `ParameterizedTestExtension`）按参数集展开为多次调用；普通 `@Test` 在 JUnit 5 中只能调用一次，参数化测试必须用 `@TestTemplate`。
- 断言：`Assert.assertEquals("Added records in the table should be 1", "1", currentEvent.summary().get("added-records"))` 等多行 `Assert.assertEquals` 被合并为 `assertThat(currentEvent.summary()).containsEntry("added-records", "1").containsEntry("added-data-files", "1").containsEntry("total-records", "1").containsEntry("total-data-files", "1")`。这体现了 AssertJ 链式断言的优越性：多个对同一 Map 的断言可链接在一起，可读性远高于逐条 `assertEquals`；`Assert.assertNotNull(currentEvent)` → `assertThat(currentEvent).isNotNull()`；`Assert.assertEquals("Table should start empty", 0, listManifestFiles().size())` → `assertThat(listManifestFiles()).as("Table should start empty").isEmpty()`。

### `core/src/test/java/org/apache/iceberg/TestManifestReader.java`

**修改目的**：把 `TestManifestReader` 从 `TableTestBase` 迁移到 `TestBase`，并演示 `Assume` → AssertJ `assumeThat` 的迁移模式。

**工作逻辑**：迁移模式与 `TestCreateSnapshotEvent` 基本一致（去掉 `@RunWith`、删除构造器与 `@Parameterized.Parameters`、`@Test` → `@TestTemplate`、`Assert` → `assertThat`），另有几处特殊点：

- 新增 import `static org.assertj.core.api.Assumptions.assumeThat;`——这是 AssertJ 的假设 API，对应 JUnit 4 的 `org.junit.Assume.assumeTrue`，在不符合前置条件时跳过测试（而非失败）。本提交中虽未实际使用 `assumeThat` 调用（只是为了未来可能的 `assumeTrue(...)` 替换预留 import），但展示了迁移路径。
- 删除 `import org.junit.Assert; import org.junit.Assume; import org.junit.Test; import org.junit.runner.RunWith; import org.junit.runners.Parameterized;`，新增 `import org.junit.jupiter.api.TestTemplate;`。
- `Assert.assertEquals(123L, (long) entry.snapshotId())` → `assertThat(entry.snapshotId()).isEqualTo(123L)`——注意 JUnit 4 风格需要把 `Long` 拆箱为 `long` 才能避免 `assertEquals(Object, Object)` 的歧义，而 AssertJ 风格直接 `isEqualTo(Long)` 即可，类型推断更友好。
- `Assert.assertEquals("Position should match", (Long) expectedPos, file.pos())` → `assertThat(file.pos()).as("Position should match").isEqualTo(expectedPos)`——失败消息从第一参数移到 `.as(...)`，更符合流式风格。
- 大量 `Assert.assertEquals(1000, fields.get(0).fieldId())` 类断言被替换为 `assertThat(fields.get(0).fieldId()).isEqualTo(1000)`。

### `data/src/test/java/org/apache/iceberg/io/TestGenericSortedPosDeleteWriter.java`

**修改目的**：演示多参数化的迁移模式（同时按 `formatVersion` 与 `FileFormat` 参数化），以及 try-catch 断言改写为 `assertThatThrownBy` 的模式。

**工作逻辑**：

- import：新增 `import static org.assertj.core.api.Assertions.assertThat; import static org.assertj.core.api.Assertions.assertThatThrownBy; import java.util.Arrays; import java.util.List; import org.apache.iceberg.Parameter; import org.apache.iceberg.Parameters;`，删除 `import org.junit.Assert; import org.junit.Before; import org.junit.Test; import org.junit.runner.RunWith; import org.junit.runners.Parameterized;`。父类从 `TableTestBase` 改为 `TestBase`（注意 `TestBase` 在 `core` 模块，所以 `data` 模块需要跨模块依赖 `core` 的测试源码，这是 Iceberg 测试基础设施共享的常规模式）。
- 类声明：`@RunWith(Parameterized.class) public class TestGenericSortedPosDeleteWriter extends TableTestBase` → `public class TestGenericSortedPosDeleteWriter extends TestBase`。
- 参数化字段：旧 `private final FileFormat format;`（在构造器中赋值） → 新 `@Parameter(index = 1) private FileFormat format;`（由 `ParameterizedTestExtension` 注入）。`@Parameter(index = 1)` 表示这是参数数组中的第二个元素（index 0 是父类的 `formatVersion`）。这种"父类与子类各自负责一组参数"的设计是 `ParameterizedTestExtension` 的特色——通过 `index` 在合并后的参数数组中定位。
- 参数集方法：旧 `@Parameterized.Parameters(name = "FileFormat={0}") public static Object[] parameters() { return new Object[][] {new Object[] {"avro"}, new Object[] {"orc"}, new Object[] {"parquet"}}; }` → 新 `@Parameters(name = "formatVersion = {0}, fileFormat = {1}") public static List<Object[]> parameters() { return Arrays.asList(new Object[] {FORMAT_V2, FileFormat.AVRO}, new Object[] {FORMAT_V2, FileFormat.ORC}, new Object[] {FORMAT_V2, FileFormat.PARQUET}); }`。注意三处变化：(1) 返回类型 `Object[]` → `List<Object[]>`（更现代的集合风格）；(2) 参数从 `String` "avro"/"orc"/"parquet" 改为 `FileFormat` 枚举值（避免运行时 `FileFormat.fromString` 解析）；(3) 每个参数数组从单元素 `[format]` 变为双元素 `[formatVersion, format]`，因为现在父类的 `formatVersion` 也要通过 `@Parameters` 提供——子类 `@Parameters` 覆盖父类 `@Parameters`，必须返回包含全部参数的数组。
- 构造器：旧 `public TestGenericSortedPosDeleteWriter(String fileFormat) { super(FORMAT_V2); this.format = FileFormat.fromString(fileFormat); }` → 删除。`FORMAT_V2 = 2` 现在通过 `@Parameters` 的第一个元素提供，`format` 通过 `@Parameter(index = 1)` 注入。
- `@Before setupTable()` → `@BeforeEach setupTable() throws Exception`，并删除 `this.tableDir = temp.newFolder(); Assert.assertTrue(tableDir.delete());`（同 `TestBase` 的 `setupTable`，新版用 `@TempDir File tableDir` 由 JUnit 5 创建空目录，不再需要 `temp.newFolder()`+`delete()`）。
- 测试方法：`@Test` → `@TestTemplate`。
- 断言：`Assert.assertEquals(1, deleteFiles.size())` → `assertThat(deleteFiles).hasSize(1)`；`Assert.assertEquals(expectedDeletes, readRecordsAsList(...))` → `assertThat(readRecordsAsList(...)).isEqualTo(expectedDeletes)`；`Assert.assertEquals("Should have the expected records", expectedRowSet(expectedData), actualRowSet("*"))` → `assertThat(actualRowSet("*")).isEqualTo(expectedRowSet(expectedData))`。
- 异常断言改写：旧 `testSortedPosDeleteWithSchemaAndNullRow` 中手写的
  ```
  SortedPosDeleteWriter<Record> writer = new SortedPosDeleteWriter<>(...);
  boolean caughtError = false;
  try { writer.delete(dataFile.path(), 0L); } catch (Exception e) { caughtError = true; }
  Assert.assertTrue("Should fail because the appender are required non-null rows to write", caughtError);
  ```
  → 新
  ```
  // no check on the underlying error msg as it might be missing based on the JDK version
  assertThatThrownBy(() -> new SortedPosDeleteWriter<>(appenderFactory, fileFactory, format, null, 1).delete(dataFile.path(), 0L))
      .isInstanceOf(Exception.class);
  ```
  这体现了 AssertJ `assertThatThrownBy` 的优越性：(1) 不再需要手写 try-catch + 标志变量；(2) 直接断言异常类型（`isInstanceOf(Exception.class)`），可进一步链式断言消息；(3) 注释说明"不再校验底层错误消息因为 JDK 版本可能影响消息内容"，体现了断言不应依赖不稳定字符串的良好实践。

## 小结

本提交是 Iceberg 项目从 JUnit 4 向 JUnit 5 迁移的"基础设施 + 首批样板"PR，确立了整个迁移工作的模板与范式：新基类 `TestBase` 与旧 `TableTestBase` 并存，迁移模式为 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`、`@Parameterized.Parameters` → `@Parameters`、构造器参数 → `@Parameter(index = N)` 字段注入、`@Before/@After` → `@BeforeEach/@AfterEach`、`@Rule TemporaryFolder` → `@TempDir`、`@Test` → `@TestTemplate`、`Assert.assertXxx` → `assertThat(...).xxx()`、`Assume.assumeTrue` → `assumeThat(...)`、try-catch 异常断言 → `assertThatThrownBy(...).isInstanceOf(...)`。三个被迁移测试（`TestCreateSnapshotEvent`、`TestManifestReader`、`TestGenericSortedPosDeleteWriter`）覆盖了单参数化、跨模块依赖、多参数化、异常断言改写等典型场景，为后续 PR 大规模迁移提供了可直接套用的样板。`build.gradle` 仅对 `:iceberg-data` 子项目启用 `useJUnitPlatform()`，体现了"渐进迁移、不破坏其他模块"的工程纪律——其他模块的 `useJUnitPlatform()` 会在该模块被迁移时才同步加入。
