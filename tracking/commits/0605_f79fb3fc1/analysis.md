# 提交 0605：Core: Migrate tests to JUnit5

## 提交信息

- **序号**：0605 / 4088
- **哈希**：f79fb3fc188a623dfb989bd3e47ade59a7cbf74e
- **短哈希**：f79fb3fc1
- **日期**：2024-03-18（Mon Mar 18 17:00:42 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Core: Migrate tests to JUnit5 (#9964)
- **PR/Issue**：#9964
- **改动统计**：9 个文件，+479 行 / -560 行（净减少 81 行）

## 总体目的

本提交把 `iceberg-core` 模块中 9 个与 Manifest（清单文件）相关的测试类从 JUnit4 迁移到 JUnit5，是 Iceberg 项目整体向 JUnit5 渐进迁移的一部分。JUnit4 已停止维护（最后一个发布是 2017 年的 4.13.2），而 JUnit5 自 2017 年发布以来已成为 Java 生态测试框架的事实标准，提供更现代的扩展模型、更清晰的 API 分层（Platform / Jupiter / Vintage）、更好的模块化与对 Java 8+ 特性的原生支持。

Iceberg 项目对 JUnit5 的迁移采用渐进策略：
- JUnit5 依赖（`junit.jupiter`、`junit.jupiter.engine`）与 JUnit Vintage Engine（`junit.vintage.engine`，让 JUnit4 测试能在 JUnit5 Platform 上运行）已在 `build.gradle` 中声明。
- 各模块的 `test { useJUnitPlatform() }` 已配置，使 JUnit4 与 JUnit5 测试能在同一构建中并存。
- 前序提交 `e16bfcffc`（PR #9424，"Core: Add JUnit5 version of TableTestBase"）已引入 JUnit5 版本的测试基类 `TestBase` 以及配套的自定义参数化扩展 `ParameterizedTestExtension`、`Parameters`、`Parameter`（位于 `api/src/test/java/org/apache/iceberg/`，借鉴自 Apache Flink）。
- 后续按测试包/主题逐步迁移，本提交聚焦于 Manifest 系列测试。

本次迁移的 9 个测试类围绕 Iceberg 表的清单文件（Manifest File）与清单列表（Manifest List）的读写、缓存、加密、清理、版本兼容等核心功能。这些测试覆盖了 Iceberg 表格式规范的关键部分（v1/v2 格式版本、序列号、删除文件、统计信息、加密、对象存储路径等），是保障 Iceberg 核心正确性的重要测试资产。

## 如何达成设计目的

迁移严格遵循"机械式重构"原则——不改变测试覆盖的语义，只替换测试框架与断言库。具体迁移路径如下：

### 1. 测试基类替换

对于继承自 `TableTestBase`（JUnit4）的参数化测试类，改为继承 `TestBase`（JUnit5）。`TestBase` 是 `e16bfcffc` 引入的 JUnit5 版本，提供相同的表创建、Schema、Spec、文件常量、`@BeforeEach`/`@AfterEach` 生命周期等能力，但用 JUnit5 注解替换了 JUnit4 注解。

### 2. 参数化机制替换

JUnit4 的 `@RunWith(Parameterized.class)` + 构造器注入参数的写法，替换为 JUnit5 的 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` 静态方法 + `@Parameter` 字段注入。这是 Iceberg 自定义的扩展，等价于 JUnit5 内置的 `@ParameterizedTest` 但语义更贴近 JUnit4 的旧写法，降低迁移成本。

### 3. 测试方法注解替换

- 参数化测试方法：`@Test`（JUnit4 `org.junit.Test`）→ `@TestTemplate`（JUnit5 `org.junit.jupiter.api.TestTemplate`），因为 `@TestTemplate` 是 JUnit5 中表示"由扩展提供多次调用上下文"的注解，配合 `ParameterizedTestExtension` 才能让每个参数组合生成一次测试调用。
- 独立测试方法（不继承 `TestBase`、不参数化）：`@Test`（JUnit4 `org.junit.Test`）→ `@Test`（JUnit5 `org.junit.jupiter.api.Test`），注解名相同但包不同。

### 4. 临时目录机制替换

JUnit4 的 `@Rule public TemporaryFolder temp = new TemporaryFolder();` + `temp.newFile("name.avro")` 替换为 JUnit5 的 `@TempDir private Path temp;` + `File.createTempFile("name", ".avro", temp.toFile())`。`@TempDir` 直接注入 `java.nio.file.Path`，没有 `newFile()` 之类的辅助方法，需要调用 Java NIO 的 `File.createTempFile` 来创建临时文件。

### 5. 断言库替换

JUnit4 的 `org.junit.Assert.*` 全部替换为 AssertJ 的 fluent 断言（`org.assertj.core.api.Assertions.assertThat`），并将 `Assertions.assertThat` / `Assertions.assertThatThrownBy` / `Assumptions.assumeThat` 改为静态导入。AssertJ 提供更可读的链式断言（如 `assertThat(list).hasSize(n)`、`assertThat(list).isEmpty()`、`assertThat(actual).as("msg").isEqualTo(expected)`），并且错误信息更友好。

### 6. Assume 替换

JUnit4 的 `Assume.assumeTrue("msg", condition)` 替换为 AssertJ 的 `assumeThat(...).isGreaterThan(1)` 等，更精确地表达"假设某个值满足某条件"。

### 7. 类型自动拆箱

JUnit4 的 `Assert.assertEquals` 对 `Long`/`Integer` 与 `long`/`int` 比较需要手动拆箱（如 `(long) manifest.addedRowsCount()`、`(int) manifest.addedFilesCount()`）；AssertJ 的 `isEqualTo` 自动处理装箱类型，无需手动拆箱，让断言更简洁。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestFormatVersions.java`

**修改目的**：把格式版本（v1/v2）相关测试迁移到 JUnit5。

**工作逻辑**：

- **基类替换**：`extends TableTestBase` → `extends TestBase`；删除构造器 `public TestFormatVersions() { super(1); }`。
- **参数化方法**：新增 `@Parameters(name = "formatVersion = {0}") protected static List<Object> parameters() { return Arrays.asList(1); }`。注意此类只测试 v1，所以重写 `parameters()` 返回单元素列表（覆盖 `TestBase` 默认的 `Arrays.asList(1, 2)`）。
- **测试方法注解**：4 个 `@Test` → `@TestTemplate`（因为继承 `TestBase` 走参数化路径）。
- **断言**：`Assert.assertEquals("Should default to v1", 1, table.ops().current().formatVersion())` → `assertThat(table.ops().current().formatVersion()).isEqualTo(1)`；`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。注意原 JUnit4 写法带描述字符串（如 "Should default to v1"），迁移后这些描述被丢弃——因为 AssertJ 风格更倾向让断言本身可读，而非靠描述字符串补充语义。

### `core/src/test/java/org/apache/iceberg/TestLocationProvider.java`

**修改目的**：把 `LocationProvider`（数据文件路径生成策略）测试迁移到 JUnit5。

**工作逻辑**：

- **运行器替换**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；`extends TableTestBase` → `extends TestBase`。
- **参数化方法**：JUnit4 `@Parameterized.Parameters public static Object[][] parameters() { return new Object[][] { new Object[] {1}, new Object[] {2} }; }` → JUnit5 `@Parameters(name = "formatVersion = {0}") protected static List<Object> parameters() { return Arrays.asList(1, 2); }`。注意 `Object[][]` 改为 `List<Object>`，每个元素是一个参数（不再是数组），因为 Iceberg 的扩展假设每个参数集只有一个参数。
- **构造器**：删除 `public TestLocationProvider(int formatVersion) { super(formatVersion); }`，参数通过 `TestBase` 的 `@Parameter protected int formatVersion;` 字段注入。
- **测试方法注解**：所有 `@Test` → `@TestTemplate`。
- **断言**：`Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`（保留描述字符串）；`Assert.assertTrue("msg", actual)` → `assertThat(actual).as("msg").isTrue()`；`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。

### `core/src/test/java/org/apache/iceberg/TestManifestCaching.java`

**修改目的**：把清单文件缓存测试迁移到 JUnit5。这是一个独立测试类（不继承 `TableTestBase`/`TestBase`）。

**工作逻辑**：

- **临时目录**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`，并新增 `import java.nio.file.Files` 和 `import java.nio.file.Path`。
- **测试方法注解**：`@Test`（JUnit4 `org.junit.Test`）→ `@Test`（JUnit5 `org.junit.jupiter.api.Test`），注解名相同但包不同——因为此类不参数化，用 `@Test` 即可。
- **断言**：
  - `Assert.assertEquals("msg", expected, Iterables.size(scan1.planTasks()))` → `assertThat(scan1.planTasks()).hasSize(expected)`——利用 AssertJ 的 `hasSize` 直接对 `Iterable` 断言，无需 `Iterables.size()` 包装。
  - `Assert.assertEquals(0, cache.estimatedCacheSize())` → `assertThat(cache.estimatedCacheSize()).isEqualTo(0)`。
  - `Assert.assertNotSame(cache1, cache2)` → `assertThat(cache2).isNotSameAs(cache1)`。
  - `Assert.assertSame(cache2, cache3)` → `assertThat(cache3).isSameAs(cache2)`。
- **导入清理**：删除 `org.apache.iceberg.relocated.com.google.common.collect.Iterables`（因 `hasSize` 取代了 `Iterables.size()`）。

### `core/src/test/java/org/apache/iceberg/TestManifestCleanup.java`

**修改目的**：把清单文件清理（删除过期 manifest）测试迁移到 JUnit5。

**工作逻辑**：

- **运行器与基类替换**：`@RunWith(Parameterized.class)` + `extends TableTestBase` → `@ExtendWith(ParameterizedTestExtension.class)` + `extends TestBase`；构造器删除。
- **参数化方法**：`@Parameterized.Parameters(name = "formatVersion = {0}") public static Object[] parameters() { return new Object[] {1, 2}; }` → `@Parameters(name = "formatVersion = {0}") protected static List<Object> parameters() { return Arrays.asList(1, 2); }`。
- **测试方法注解**：`@Test` → `@TestTemplate`。
- **断言**：
  - `Assert.assertEquals("msg", 0, listManifestFiles().size())` → `assertThat(listManifestFiles()).isEmpty()`——利用 AssertJ 集合专属断言，更语义化。
  - `Assert.assertEquals("msg", 1, list.size())` → `assertThat(list).as("msg").hasSize(1)`。
  - `Assert.assertEquals("msg", s2.allManifests(table.io()), s3.allManifests(table.io()))` → `assertThat(s3.allManifests(table.io())).isEqualTo(s2.allManifests(table.io()))`。

### `core/src/test/java/org/apache/iceberg/TestManifestEncryption.java`

**修改目的**：把清单文件加密测试迁移到 JUnit5。这是独立测试类。

**工作逻辑**：

- **临时目录**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`，新增 `import java.io.File` 和 `import java.nio.file.Path`（用于 `temp.toFile()`）。
- **测试方法注解**：`@Test`（JUnit4）→ `@Test`（JUnit5）。
- **断言**：批量替换 `Assert.assertEquals("label", expected, actual)` → `assertThat(actual).isEqualTo(expected)`，迁移过程中**丢弃了所有描述字符串**（如 `"Status"`、`"Snapshot ID"`、`"Path"` 等），让断言更紧凑。对于 `Assert.assertNull(dataFile.equalityFieldIds())` → `assertThat(dataFile.equalityFieldIds()).isNull()`。对于装箱类型比较：`Assert.assertEquals("Record count", METRICS.recordCount(), (Long) dataFile.recordCount())` → `assertThat(dataFile.recordCount()).isEqualTo(METRICS.recordCount())`——去掉手动 `(Long)` 拆箱。
- **断言方法**：`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。

### `core/src/test/java/org/apache/iceberg/TestManifestListVersions.java`

**修改目的**：把清单列表（Manifest List，即 snapshot 的 manifest 集合）的 v1/v2 版本兼容读写测试迁移到 JUnit5。这是独立测试类，改动量最大（+/-185 行）。

**工作逻辑**：

- **临时目录**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`，新增 `import java.nio.file.Path`。
- **测试方法注解**：`@Test`（JUnit4）→ `@Test`（JUnit5）。
- **断言**：大量 `Assert.assertEquals("label", expected, actual)` → `assertThat(actual).isEqualTo(expected)`，**丢弃描述字符串**。
- **特殊处理**：
  - `Assert.assertEquals("Path", PATH, generic.get("manifest_path").toString())` → `assertThat(generic.get("manifest_path")).asString().isEqualTo(PATH)`——利用 AssertJ 的 `asString()` 进行类型转换后再断言，避免显式 `.toString()`。
  - `File manifestListFile = temp.newFile("manifest-list.avro"); Assert.assertTrue(manifestListFile.delete());` → `File manifestListFile = File.createTempFile("manifest-list", ".avro", temp.toFile()); assertThat(manifestListFile.delete()).isTrue();`——`@TempDir` 注入的是 `Path`，需要用 `File.createTempFile(prefix, suffix, dir)` 创建临时文件，并把 `assertTrue` 改为 `assertThat(...).isTrue()`。
- **装箱类型**：`(long) manifest.snapshotId()`、`(int) manifest.addedFilesCount()` 等手动拆箱全部去掉。

### `core/src/test/java/org/apache/iceberg/TestManifestReaderStats.java`

**修改目的**：把清单读取器统计信息测试迁移到 JUnit5。这是参数化测试类。

**工作逻辑**：

- **运行器与基类替换**：`@RunWith(Parameterized.class)` + `extends TableTestBase` → `@ExtendWith(ParameterizedTestExtension.class)` + `extends TestBase`；构造器删除。
- **参数化方法**：`@Parameterized.Parameters(name = "formatVersion = {0}") public static Object[] parameters() { return new Object[] {1, 2}; }` → `@Parameters(name = "formatVersion = {0}") protected static List<Object> parameters() { return Arrays.asList(1, 2); }`。
- **测试方法注解**：所有 `@Test` → `@TestTemplate`。
- **断言**：`Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`；`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。

### `core/src/test/java/org/apache/iceberg/TestManifestWriter.java`

**修改目的**：把清单写入器测试迁移到 JUnit5。这是参数化测试类。

**工作逻辑**：

- **运行器与基类替换**：`@RunWith(Parameterized.class)` + `extends TableTestBase` → `@ExtendWith(ParameterizedTestExtension.class)` + `extends TestBase`；构造器删除。
- **参数化方法**：标准替换。
- **测试方法注解**：所有 `@Test` → `@TestTemplate`。
- **Assume 替换**：`Assume.assumeTrue("msg", formatVersion > 1)` → `assumeThat(formatVersion).isGreaterThan(1)`（来自 `static org.assertj.core.api.Assumptions.assumeThat`），更精确地表达"formatVersion 大于 1"。
- **临时目录**：`temp.newFile("manifest.avro")` → `File.createTempFile("manifest", ".avro", temp.toFile())`，并 `Assert.assertTrue(manifestListFile.delete())` → `assertThat(manifestListFile.delete()).isTrue()`。
- **断言**：
  - `Assert.assertTrue("Added files should be present", manifest.hasAddedFiles())` → `assertThat(manifest.hasAddedFiles()).isTrue()`（丢弃描述）。
  - `Assert.assertEquals("msg", expected, (int) manifest.addedFilesCount())` → `assertThat(manifest.addedFilesCount()).isEqualTo(expected)`（去拆箱）。
  - `Assert.assertEquals("msg", 1, partitions.size())` → `assertThat(partitions).hasSize(1)`。
  - `Assert.assertFalse("contains_null should be false", partitionFieldSummary.containsNull())` → `assertThat(partitionFieldSummary.containsNull()).isFalse()`。
  - `Assertions.assertThat(writer.toManifestFiles()).isEmpty()` → `assertThat(writer.toManifestFiles()).isEmpty()`。
  - `Assumptions.assumeThat(formatVersion).isGreaterThan(1)` → `assumeThat(formatVersion).isGreaterThan(1)`（静态导入）。

### `core/src/test/java/org/apache/iceberg/TestManifestWriterVersions.java`

**修改目的**：把清单写入器的 v1/v2 版本兼容测试迁移到 JUnit5。这是独立测试类。

**工作逻辑**：

- **临时目录**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`，新增 `import java.io.File` 和 `import java.nio.file.Path`。
- **测试方法注解**：`@Test`（JUnit4）→ `@Test`（JUnit5）。
- **断言**：`Assert.assertEquals("Content", ManifestContent.DATA, manifest.content())` → `assertThat(manifest.content()).isEqualTo(ManifestContent.DATA)`；`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。装箱类型 `(Long) SNAPSHOT_ID` 等手动拆箱去掉。

### `core/src/test/java/org/apache/iceberg/TestManifestReaderStats.java`（补充）

已在上面分析，与其它参数化测试类同模式。

## 小结

本提交是 Iceberg 项目向 JUnit5 渐进迁移的一步，聚焦于 `iceberg-core` 模块中 9 个 Manifest 系列测试类。改动总计 +479/-560 行（净减少 81 行），主要源于：丢弃 JUnit4 `assertEquals` 的描述字符串、AssertJ 集合专属断言（`isEmpty`/`hasSize`）替代 `assertEquals(0, size())`、去掉手动装箱/拆箱、删除构造器与 `@RunWith` 等模板代码。

迁移遵循严格的机械式重构原则，不改变测试覆盖语义。所有迁移都依赖前序提交 `e16bfcffc`（PR #9424）引入的 `TestBase`、`ParameterizedTestExtension`、`Parameters`、`Parameter` 基础设施，以及 `build.gradle` 中早已配置好的 `useJUnitPlatform()` 与 JUnit5/Vintage 引擎依赖。

- **影响范围**：仅 `iceberg-core` 模块的 9 个测试类，无生产代码改动，无 API 变化。
- **回迁到 1.4.x 的注意事项**：
  - **强依赖前序提交**：必须先回迁 `e16bfcffc`（PR #9424，引入 `TestBase` 与 `ParameterizedTestExtension`）。若 1.4.x 上没有这些基础设施，本提交的 9 个测试类无法编译（找不到 `TestBase`、`ParameterizedTestExtension`、`Parameters` 符号）。
  - **检查 build.gradle**：1.4.x 的 `iceberg-core` 项目需已配置 `useJUnitPlatform()`，并声明 `junit.jupiter`、`junit.jupiter.engine`、`junit.vintage.engine` 依赖。若 1.4.x 仍只配 JUnit4，需先升级构建配置。
  - **冲突风险**：测试类是机械式重构，若 1.4.x 上这些测试文件有其他改动（bug 修复、新增测试方法），需要逐文件合并。建议按文件 cherry-pick 后单独跑 `./gradlew :iceberg-core:test --tests "org.apache.iceberg.TestManifest*"` 验证。
  - **测试等价性验证**：迁移前后测试数量应一致（参数化测试每个 formatVersion 算一个测试实例）。可用 `./gradlew :iceberg-core:test --tests "org.apache.iceberg.TestManifestWriter"` 对比迁移前后的测试方法数与通过率。
