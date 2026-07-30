# 提交 0500：MR: Migrate parameterized tests to JUni5 (#9711)

## 提交信息

- **序号**：0500 / 4088
- **哈希**：50fb4004d299737fcbae6526e1743dd197c9242d
- **短哈希**：50fb4004d
- **日期**：2024-02-13 19:20:19 +0800
- **作者**：DongDongLee
- **提交说明**：MR: Migrate parameterized tests to JUni5 (#9711)
- **PR/Issue**：#9711

## 总体目的

这个提交将 Iceberg 的 MR（MapReduce/Hive）模块下的全部参数化测试从 JUnit 4 迁移到 JUnit 5（Jupiter），这是 Iceberg 项目整体测试框架现代化工作的一部分。JUnit 5 相比 JUnit 4 在架构设计、扩展机制、断言表达力等方面有显著改进，迁移后可以获得更现代的测试编写体验和更强的扩展能力。

本次迁移的核心挑战在于参数化测试（parameterized tests）的处理。在 JUnit 4 中，参数化测试通过 `@RunWith(Parameterized.class)` 运行器配合 `@Parameter` 字段注入和 `@Parameters` 静态方法实现，这是一种基于运行器的整体式方案。而 JUnit 5 采用了完全不同的扩展模型（Extension Model），通过 `@ExtendWith` 声明扩展，用 `@TestTemplate` 替代 `@Test` 来标识需要在多种参数组合下重复执行的测试方法。

值得注意的是，本次迁移并未直接使用 JUnit 5 自带的 `@ParameterizedTest`，而是使用了 Iceberg 自定义的 `ParameterizedTestExtension`（来自 `org.apache.iceberg` 包）。这个自定义扩展封装了参数化测试的执行逻辑，提供 `@Parameter`（字段注入，使用 `index` 属性指定参数位置）和 `@Parameters`（标记提供参数的静态方法）注解。这种自定义方案的好处是在 JUnit 5 的扩展模型下保留了类似 JUnit 4 参数化测试的字段注入风格，使迁移过程中的代码改动更机械、更可控，同时也便于 Iceberg 在各模块间统一参数化测试的编写范式。

除了测试框架本身的迁移，本次提交还同步将断言库从 JUnit 4 的 `org.junit.Assert` 和 JUnit 4 的 `Assume` 全面切换到 AssertJ 的流式断言 API（`assertThat`、`assertThatThrownBy`、`assumeThat`）。AssertJ 提供了更丰富的断言方法和更友好的错误信息，是 Java 测试生态中广泛采用的现代断言库。临时目录管理也从 JUnit 4 的 `@Rule TemporaryFolder`（基于 `java.io.File`）迁移到 JUnit 5 的 `@TempDir`（基于 `java.nio.file.Path`），后者使用 NIO.2 API，在路径操作上更现代、更类型安全。

## 如何达成设计目的

整体迁移遵循一套系统化的机械替换模式，覆盖 8 个测试文件。对每个文件，迁移步骤包括：(1) 将类级注解从 `@RunWith(Parameterized.class)` 替换为 `@ExtendWith(ParameterizedTestExtension.class)`；(2) 将所有测试方法的 `@Test` 替换为 `@TestTemplate`，因为参数化测试在 JUnit 5 中通过 `@TestTemplate` 标识；(3) 将生命周期注解从 JUnit 4 的 `@Before`/`@After`/`@BeforeClass`/`@AfterClass` 替换为 JUnit 5 的 `@BeforeEach`/`@AfterEach`/`@BeforeAll`/`@AfterAll`；(4) 将参数字段的注解从 JUnit 4 的 `@Parameter(N)`（public 字段）替换为 Iceberg 自定义的 `@Parameter(index = N)`（private 字段），提升封装性；(5) 将 `@Rule TemporaryFolder temp` 替换为 `@TempDir Path temp`，并将相关 API 调用从 `temp.newFolder(...)` / `temp.getRoot()` 迁移到 `temp.resolve(...)` / `temp.toAbsolutePath()` 等 NIO.2 API；(6) 将 `org.junit.Assert.*` 断言替换为 AssertJ 的 `assertThat`；(7) 将 `org.junit.Assume.*` 假设替换为 AssertJ 的 `Assumptions.assumeThat`；(8) 将 JUnit 4 的 `@Rule Timeout` 替换为 JUnit 5 的 `@Timeout`（类级注解）。此外，工具类 `HiveIcebergStorageHandlerTestUtils` 和 `TestTables` 中接收 `TemporaryFolder` 参数的方法签名也同步改为接收 `Path`，以适配调用方的变更。

## 修改详情

### `mr/src/test/java/org/apache/iceberg/mr/TestIcebergInputFormats.java`

**修改目的**：将 InputFormat 测试类从 JUnit 4 参数化测试迁移到 JUnit 5，并切换到 AssertJ 断言。

**工作逻辑**：

该文件是参数化测试中改动模式最丰富的一个。关键变更包括：

- 类注解从 `@RunWith(Parameterized.class)` 改为 `@ExtendWith(ParameterizedTestExtension.class)`。
- 临时目录从 `@Rule public TemporaryFolder temp = new TemporaryFolder();` 改为 `@TempDir private Path temp;`，并新增 `import java.nio.file.Path` 和 `java.nio.file.Paths`。
- 参数注入方式从构造函数注入改为字段注入：原代码通过构造函数 `TestIcebergInputFormats(TestInputFormat.Factory<Record> testInputFormat, String fileFormat)` 接收参数并赋值给 `final` 字段；迁移后删除构造函数，改用 `@Parameter(index = 0)` 和 `@Parameter(index = 1)` 注解标记 private 字段。同时将 `fileFormat` 字段类型从 `String` 改为 `FileFormat`，相应地在 `@Parameters` 方法中将参数数组中的字符串通过 `FileFormat.fromString(fileFormat)` 转为枚举值，避免在字段中存储字符串再转换。
- `@Before` 改为 `@BeforeEach`，其中临时目录创建逻辑从 `temp.newFolder(testInputFormat.name(), fileFormat.name())` 配合 `Assert.assertTrue(location.delete())` 改为 `temp.resolve(Paths.get(testInputFormat.name(), fileFormat.name())).toFile()` 配合 `assertThat(location).doesNotExist()`。这里有个语义微调：原代码先创建文件夹再删除（`newFolder` 会创建目录，然后 `delete()` 删掉），新代码用 `resolve` 仅构造路径不创建目录，然后用 `doesNotExist()` 断言路径不存在，行为更清晰。
- 所有 `@Test` 改为 `@TestTemplate`。
- 所有 `Assert.assertEquals` / `Assert.assertArrayEquals` 改为 AssertJ 的 `assertThat(...).isEqualTo(...)` / `assertThat(...).hasSameSizeAs(...)` / `assertThat(...).containsExactly(...)`。例如 `Assert.assertArrayEquals(new String[] {"*"}, split.getLocations())` 改为 `assertThat(split.getLocations()).containsExactly("*")`。
- `Assertions.assertThatThrownBy`（AssertJ 全限定调用）改为静态导入的 `assertThatThrownBy`。
- 自定义目录 `temp.newFolder("hadoop_catalog").getAbsolutePath()` 改为 `temp.resolve("hadoop_catalog").toAbsolutePath().toString()`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/HiveIcebergStorageHandlerTestUtils.java`

**修改目的**：将工具类中接收 `TemporaryFolder` 的方法签名改为接收 `Path`，适配调用方的 JUnit 5 迁移。

**工作逻辑**：

- 移除 `import org.junit.rules.TemporaryFolder`，新增 `import java.nio.file.Path`。
- `testTables(...)` 方法的 `TemporaryFolder temp` 参数改为 `Path temp`，两个重载版本同步修改。
- `init(...)` 方法的 `TemporaryFolder temp` 参数改为 `Path temp`，其中获取根路径的方式从 `temp.getRoot().getAbsolutePath()` 改为 `temp.toAbsolutePath().toString()`。`TemporaryFolder.getRoot()` 返回临时根目录的 `File` 对象，而 `Path.toAbsolutePath()` 直接返回绝对路径字符串，两者语义等价。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerLocalScan.java`

**修改目的**：将 Hive 本地扫描测试类从 JUnit 4 参数化测试迁移到 JUnit 5，并切换到 AssertJ 断言。

**工作逻辑**：

- 标准的框架迁移：`@RunWith(Parameterized.class)` -> `@ExtendWith(ParameterizedTestExtension.class)`，`@Parameter(N)` public 字段 -> `@Parameter(index = N)` private 字段，`@Rule TemporaryFolder` -> `@TempDir Path`，`@BeforeClass`/`@AfterClass`/`@Before`/`@After` -> `@BeforeAll`/`@AfterAll`/`@BeforeEach`/`@AfterEach`，`@Test` -> `@TestTemplate`。
- 断言迁移的典型模式：将多行 `Assert.assertEquals(N, rows.size())` + 多个 `Assert.assertArrayEquals(expected, rows.get(i))` 合并为单个 `assertThat(rows).containsExactly(expected1, expected2, ...)`。例如 `testScanTable` 中原本 4 行断言（1 个 size 检查 + 3 个数组比较）合并为 1 个 `containsExactly` 链式调用，代码更简洁且错误信息更友好。
- `Assert.assertEquals(0, rows.size())` 改为 `assertThat(rows).isEmpty()`。
- 单值比较 `Assert.assertEquals(expected, queryResult.get(0)[0])` 改为 `assertThat(queryResult.get(0)[0]).isEqualTo(expected)`。
- 结构体多字段比较从多个 `Assert.assertEquals` 合并为 `assertThat(queryResult.get(0)).containsExactly(...)`。
- 文件末尾的 schema/spec 验证从 `Assert.assertEquals` 改为 `assertThat(...).isEqualTo(...)`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerNoScan.java`

**修改目的**：将 Hive 非扫描测试类从 JUnit 4 参数化测试迁移到 JUnit 5，并切换到 AssertJ 断言。

**工作逻辑**：

- 标准框架迁移，模式与 LocalScan 一致。新增 `assertThatThrownBy` 和 `assumeThat` 的静态导入。
- `@Parameter(0) public TestTables.TestTableType testTableType` 改为 `@Parameter private TestTables.TestTableType testTableType`（注意此处 `@Parameter` 未指定 `index`，默认为 0）。
- `@Rule TemporaryFolder` 改为 `@TempDir java.nio.file.Path temp`（此处使用了全限定类名而非导入）。
- `Assume.assumeTrue("msg", condition)` 改为 `assumeThat(testTableType).as("msg").isEqualTo(...)`，`Assume.assumeFalse` 改为 `assumeThat(...).as("msg").isNotEqualTo(...)`。AssertJ 的假设 API 将断言对象作为主体，表达更自然。
- `Assertions.assertThatThrownBy`（全限定）改为静态导入的 `assertThatThrownBy`。
- 复杂的 HMS 属性验证从多个独立的 `Assert.assertEquals` / `Assert.assertNull` / `Assert.assertNotNull` / `Assert.assertTrue` / `Assert.assertFalse` 合并为单个 `assertThat(hmsParams).hasSize(N).containsEntry(k, v).doesNotContainKey(k)...` 链式调用。例如 `testIcebergAndHmsTableProperties` 中原本十多行断言合并为一个流畅的 AssertJ 链，可读性大幅提升。
- `Assert.assertEquals(expectedSchema.asStruct(), icebergTable.schema().asStruct())` 改为 `assertThat(icebergTable.schema().asStruct()).isEqualTo(expectedSchema.asStruct())`，注意断言主体的顺序调整为"实际值在前"。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerTimezone.java`

**修改目的**：将时区相关测试类从 JUnit 4 参数化测试迁移到 JUnit 5，并切换到 AssertJ 断言。

**工作逻辑**：

- 标准框架迁移。`@Parameter(0) public String timezoneString` 改为 `@Parameter private String timezoneString`。
- 断言迁移模式：`Assert.assertEquals(1, result.size())` + `Assert.assertEquals("2020-01-21", result.get(0)[0])` 分别改为 `assertThat(result).hasSize(1)` 和 `assertThat(result.get(0)[0]).isEqualTo("2020-01-21")`。
- `Assert.assertEquals(0, result.size())` 改为 `assertThat(result).isEmpty()`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerWithEngine.java`

**修改目的**：将带执行引擎的 Hive 测试类从 JUnit 4 参数化测试迁移到 JUnit 5，并切换到 AssertJ 断言。

**工作逻辑**：

- 标准框架迁移。4 个参数字段（`fileFormat`、`executionEngine`、`testTableType`、`isVectorized`）从 `@Parameter(N) public` 改为 `@Parameter(index = N) private`。
- 超时机制迁移：`@Rule public Timeout timeout = new Timeout(200_000, TimeUnit.MILLISECONDS)` 改为类级注解 `@Timeout(value = 200_000, unit = TimeUnit.MILLISECONDS)`。JUnit 5 的 `@Timeout` 可以直接作为类级注解，对该类所有测试方法生效，比 JUnit 4 的 `@Rule` 方式更简洁。
- `Assume.assumeTrue("msg", condition)` 改为 `assumeThat(condition).as("msg").isTrue()`，多处 `assumeTrue` / `assumeThat(executionEngine).as("msg").isEqualTo("mr")` 模式用于条件性跳过测试（如 Tez 写入未实现时跳过相关测试）。
- 断言迁移：`Assert.assertArrayEquals` + `Assert.assertEquals` 组合改为 `assertThat(rows).containsExactly(...)`；`Assert.assertEquals(20000, result.size())` 改为 `assertThat(result).hasSize(20000)`；`Assert.assertNull(results.get(0)[0])` 改为 `assertThat(results.get(0)[0]).isNull()`；`Assert.assertTrue(stats.startsWith(...))` 改为 `assertThat(stats).startsWith(...)`；`Assert.assertFalse("msg", elements.isEmpty())` 改为 `assertThat(elements).as("msg").isNotEmpty()`。
- `assumeTrue(isVectorized && FileFormat.ORC.equals(fileFormat))` 改为 `assumeThat(isVectorized && FileFormat.ORC.equals(fileFormat)).isTrue()`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerWithMultipleCatalogs.java`

**修改目的**：将多 Catalog 测试类从 JUnit 4 参数化测试迁移到 JUnit 5，并切换到 AssertJ 断言。

**工作逻辑**：

- 标准框架迁移。7 个参数字段（`fileFormat1`、`fileFormat2`、`executionEngine`、`testTableType1`、`table1CatalogName`、`testTableType2`、`table2CatalogName`）从 `@Parameterized.Parameter(N) public` 改为 `@Parameter(index = N) private`。注意此文件原本使用的是 `@Parameterized.Parameter`（带前缀的全限定形式），迁移后统一为 Iceberg 自定义的 `@Parameter`。
- `@Parameterized.Parameters(...)` 改为 `@Parameters(...)`。
- `Assert.assertEquals(HiveIcebergStorageHandlerTestUtils.CUSTOMER_RECORDS.size(), rows.size())` 改为 `assertThat(rows).hasSameSizeAs(HiveIcebergStorageHandlerTestUtils.CUSTOMER_RECORDS)`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestTables.java`

**修改目的**：将测试辅助类的临时目录类型从 `TemporaryFolder` 改为 `Path`，并迁移相关断言，适配 JUnit 5。

**工作逻辑**：

- 移除 `import org.junit.rules.TemporaryFolder` 和 `import org.junit.Assert`，移除不再使用的 `import org.apache.iceberg.relocated.com.google.common.collect.ObjectArrays` 和 `import java.io.UncheckedIOException`，新增 `import java.nio.file.Path`、`java.nio.file.Paths` 和 AssertJ 的 `assertThat` 静态导入。
- 基类 `TestTables` 的 `temp` 字段类型从 `TemporaryFolder` 改为 `Path`，两个构造函数参数同步修改。
- 各子类（`CustomCatalogTestTables`、`HadoopCatalogTestTables`、`HadoopTestTables`、`HiveTestTables`）的构造函数参数中 `TemporaryFolder temp` 全部改为 `Path temp`。
- `TestTableType` 枚举的 `instance` 方法签名中 `TemporaryFolder` 改为 `Path`。
- 目录创建逻辑迁移：`temp.newFolder("custom", "warehouse").toString()` 改为 `temp.resolve(Paths.get("custom", "warehouse"))`；`temp.newFolder("hadoop", "warehouse").toString()` 改为 `temp.resolve(Paths.get("hadoop", "warehouse"))`。
- `HadoopTestTables.identifier` 方法重构：原代码用 `temp.newFolder(ObjectArrays.concat(namespace.levels(), identifier.name()))` 创建目录并用 try-catch 包裹 `IOException`（转为 `UncheckedIOException`），再用 `Assert.assertTrue(location.delete())` 删除目录。新代码改用 `temp.resolve(Joiner.on(File.separator).join(...) + File.separator + identifier.name()).toFile()` 构造路径（不创建目录），然后用 `assertThat(location).doesNotExist()` 断言路径不存在。这消除了 `IOException` 处理和 `ObjectArrays` 依赖，逻辑更简洁。
- `locationForCreateTableSQL` 和 `loadTable` 中 `temp.getRoot().getPath()` 改为直接 `temp`（`Path` 的 `toString` 会返回路径字符串，在字符串拼接中自动调用）。

## 小结

本提交是 MR 模块测试基础设施的一次系统性现代化迁移，将 8 个测试文件从 JUnit 4 全面迁移到 JUnit 5。迁移涵盖了测试框架注解（`@RunWith` -> `@ExtendWith`、`@Test` -> `@TestTemplate`、生命周期注解）、参数化测试机制（使用 Iceberg 自定义的 `ParameterizedTestExtension`）、断言库（JUnit Assert -> AssertJ assertThat）、假设机制（JUnit Assume -> AssertJ assumeThat）、超时机制（`@Rule Timeout` -> `@Timeout`）以及临时目录管理（`@Rule TemporaryFolder` -> `@TempDir Path`，从 `java.io.File` API 迁移到 `java.nio.file.Path` API）。迁移后代码风格更现代、断言表达力更强、错误信息更友好，字段封装性也得到改善（参数字段从 public 改为 private）。整个迁移遵循机械化的替换模式，不改变测试的业务逻辑和覆盖范围，属于纯基础设施层面的改进。
