# 提交分析：Spark 3.4: Migrate SparkTestBase related tests to JUnit5

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2109 |
| 短哈希 | `951ba965a` |
| 完整哈希 | `951ba965a7cab8f5bc7270faac7b4cf444e2d14e` |
| 作者 | Tom Tanaka |
| 邮箱 | 43331405+tomtongue@users.noreply.github.com |
| 日期 | 2025-05-12 19:33:14 2025 +0900 |
| 提交信息 | Spark 3.4: Migrate SparkTestBase related tests to JUnit5 (#13031) |

## 总体目的

本提交将 Spark 3.4 模块中所有直接继承自 `SparkTestBase`（不带 Catalog 配置的测试基类）的测试类从 JUnit 4 迁移到 JUnit 5。这是继提交 2108（#13015）迁移 `SparkTestBaseWithCatalog` 子类之后的后续工作。本提交删除了旧的 `SparkTestBase` 类（287 行），将所有继承它的测试类切换到前序提交 `b3d513334` 中创建的 JUnit 5 基类 `TestBase`。同时对 Spark 3.5 模块中对应的测试类进行了同步修改。

## 设计目的的实现方式

1. **删除旧基类**：直接删除 `SparkTestBase.java`（287 行），其功能已由 JUnit 5 版本的 `TestBase` 完全替代。

2. **批量替换基类和注解**：
   - 继承基类：`extends SparkTestBase` → `extends TestBase`
   - `@Test` → `@Test`（JUnit 5 的 `org.junit.jupiter.api.Test`）或 `@TestTemplate`（参数化测试）
   - `@Before` → `@BeforeEach`，`@After` → `@AfterEach`
   - `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
   - `@Rule public TemporaryFolder temp` → `@TempDir private Path temp`

3. **参数化测试重构**：`TestIdentityPartitionData` 的参数化测试做了较大重构：
   - 构造函数注入参数改为 `@Parameter` 字段注入
   - 参数从简单的 `String format, boolean vectorized, PlanningMode planningMode` 改为 `FileFormat format, boolean vectorized, Map<String, String> properties`，将属性映射的构建从构造函数移到 `@Parameters` 方法中

4. **临时文件 API 适配**：JUnit 4 的 `temp.newFile()` / `temp.newFolder()` 替换为 `File.createTempFile("junit", null, temp.toFile())` / `Files.createTempDirectory(temp, "...").toFile()`

5. **同步修改 Spark 3.5 测试**：将 `hasSize(0)` 改为 `isEmpty()` 等小改进。

## 修改详情

### 1. 删除 `SparkTestBase.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkTestBase.java`

**修改内容**：整个文件被删除（287 行）。

**目的**：该类是 JUnit 4 风格的测试基类，提供了 Spark 会话初始化、Hive 配置、SQL 执行辅助方法等功能。其功能已由前序提交中创建的 `TestBase`（JUnit 5 风格）完全替代，因此可以安全删除。这是 JUnit 4 到 JUnit 5 迁移的最后一步——删除旧基类。

### 2. 修改 `TestChangelogReader.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestChangelogReader.java`

**修改内容**：
- 基类从 `SparkTestBase` 改为 `TestBase`
- `@Rule public TemporaryFolder temp = new TemporaryFolder()` → `@TempDir private Path temp`
- `@Before` → `@BeforeEach`，`@After` → `@AfterEach`
- `Assert.assertEquals("Should have no rows", 0, rows.size())` → `assertThat(rows).as("Should have no rows").isEmpty()`
- `temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`

**目的**：将 Changelog 读取器测试迁移到 JUnit 5。该测试不需要参数化，因此 `@Test` 注解保持为 JUnit 5 的 `@Test`（而非 `@TestTemplate`）。临时文件 API 从 JUnit 4 的 `TemporaryFolder` 规则迁移到 JUnit 5 的 `@TempDir` 注解。

### 3. 修改 `TestIdentityPartitionData.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIdentityPartitionData.java`

**修改内容**：
- 基类从 `SparkTestBase` 改为 `TestBase`
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
- 构造函数参数注入改为 `@Parameter(index = N)` 字段注入
- 参数类型变更：`String format` → `FileFormat format`，`PlanningMode planningMode` → `Map<String, String> properties`（将 PlanningMode 转换为属性映射的工作移到了 `@Parameters` 方法中）
- `@Rule public TemporaryFolder temp` → `@TempDir private Path temp`
- `temp.newFolder("logs")` → `Files.createTempDirectory(temp, "logs").toFile()`
- `@Test` → `@TestTemplate`（因为是参数化测试）
- `Assert.assertEquals` → AssertJ `assertThat`
- `format.equals("parquet")` → `format.equals(FileFormat.PARQUET)`

**目的**：这是最复杂的迁移文件。参数化测试从 JUnit 4 的构造函数注入模式迁移到 JUnit 5 的 `@Parameter` 字段注入模式。同时重构了参数设计：原来传入 `PlanningMode` 枚举在构造函数中构建属性映射，现在直接在 `@Parameters` 方法中构建完整的属性映射，使测试参数更加自包含和清晰。

### 4. 修改 `TestSparkMetadataColumns.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`

**修改内容**：基类替换、注解迁移、断言库替换。

**目的**：标准 JUnit 4 到 JUnit 5 迁移。

### 5. 修改 `TestTimestampWithoutZone.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestTimestampWithoutZone.java`

**修改内容**：基类替换、注解迁移、断言库替换、临时文件 API 适配。

**目的**：标准 JUnit 4 到 JUnit 5 迁移。

### 6. 修改 `TestChangelogReader.java`（Spark 3.5）

**文件**：`spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestChangelogReader.java`

**修改内容**：`assertThat(rows).as("Should have no rows").hasSize(0)` → `assertThat(rows).as("Should have no rows").isEmpty()`

**目的**：将 `hasSize(0)` 替换为更语义化的 `isEmpty()`，与 Spark 3.4 版本的修改保持一致。

### 7. 修改 `TestIdentityPartitionData.java`（Spark 3.5）

**文件**：`spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIdentityPartitionData.java`

**修改内容**：同步 Spark 3.4 中的参数化测试重构（`FileFormat` 枚举、属性映射参数化等）。

**目的**：确保 Spark 3.5 与 Spark 3.4 测试的一致性。

### 8. 修改 `TestTimestampWithoutZone.java`（Spark 3.5）

**文件**：`spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestTimestampWithoutZone.java`

**修改内容**：同步 Spark 3.4 中的修改。

**目的**：确保 Spark 3.5 与 Spark 3.4 测试的一致性。

## 总结

本提交是 Spark 3.4 模块 JUnit 4 到 JUnit 5 迁移工作的收尾部分，处理的是直接继承 `SparkTestBase`（而非 `SparkTestBaseWithCatalog`）的测试类。核心变更包括：

1. **删除旧基类** `SparkTestBase`（287 行），所有测试类改用 `TestBase`
2. **注解迁移**：`@Test` → `@Test`/`@TestTemplate`、`@Before/@After` → `@BeforeEach/@AfterEach`
3. **参数化测试重构**：`TestIdentityPartitionData` 从构造函数注入改为 `@Parameter` 字段注入，并重构了参数设计
4. **临时文件 API 适配**：`TemporaryFolder` 规则 → `@TempDir` 注解，`temp.newFile()/newFolder()` → `File.createTempFile()` / `Files.createTempDirectory()`
5. **断言改进**：JUnit 4 Assert → AssertJ，`hasSize(0)` → `isEmpty()`
6. **跨版本同步**：同步修改 Spark 3.5 对应测试文件

共修改 8 个文件，删除 435 行，新增 197 行，净减 238 行（主要来自删除旧基类）。
