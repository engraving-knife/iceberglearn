# 提交分析：Spark 3.4: Migrate tests in spark.data including refactoring Spark 3.5 tests

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2117 |
| 短哈希 | `5d2230ead` |
| 完整哈希 | `5d2230ead79da64a8c871a02eb1304a94aaece5c` |
| 作者 | Tom Tanaka |
| 邮箱 | 43331405+tomtongue@users.noreply.github.com |
| 日期 | 2025-05-13 21:42:11 2025 +0900 |
| 提交信息 | Spark 3.4: Migrate tests in spark.data including refactoring Spark 3.5 tests (#13038) |

## 总体目的

本提交将 Spark 3.4 模块中 `spark.data` 包下的测试类从 JUnit 4 迁移到 JUnit 5。这是继 2108（#13015）和 2109（#13031）之后的第三批 JUnit 5 迁移工作，专门处理 `spark.data` 包中的测试。同时同步重构了 Spark 3.5 模块中对应的测试类，统一断言风格。

## 设计目的的实现方式

1. **JUnit 4 到 JUnit 5 注解迁移**：
   - `@Test`（`org.junit.Test`）→ `@Test`（`org.junit.jupiter.api.Test`）或 `@TestTemplate`（参数化测试）
   - `@Before` → `@BeforeEach`
   - `@Rule public TemporaryFolder temp` → `@TempDir private Path temp`

2. **参数化测试迁移**：
   - `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
   - 构造函数注入 → `@Parameter` 字段注入
   - `@Parameterized.Parameters` → `@Parameters`

3. **断言库统一**：
   - `org.junit.Assert.*` → AssertJ `assertThat`
   - 合并多个断言链为一行：`assertThat(x).isInstanceOf(...).isEqualTo(...)`
   - 使用 `.asString()` 替代 `.toString()` 进行字符串比较

4. **临时文件 API 适配**：`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`

5. **新增测试**：在 `TestSparkParquetWriter` 中添加 `testFpp` 测试方法，验证 Parquet Bloom Filter 的 FPP（误报率）配置。

## 修改详情

### Spark 3.4 模块修改（12 个文件）

#### 1. `TestHelpers.java`

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`

**修改内容**：
- 移除 `org.junit.Assert` 导入
- 所有 `Assert.assertEquals`、`Assert.assertNotNull`、`Assert.assertArrayEquals` 替换为 AssertJ `assertThat`
- 合并断言链：如 `assertThat(actual).as("...").isInstanceOf(String.class); assertThat(actual).as("...").isEqualTo(...)` 合并为 `assertThat(actual).isInstanceOf(String.class).isEqualTo(...)`
- 使用 `.asString()` 替代 `.toString()` 进行类型转换后比较

**目的**：统一断言风格为 AssertJ，使断言更简洁可读。合并断言链减少了重复代码。

#### 2. `GenericsHelpers.java`

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/GenericsHelpers.java`

**修改内容**：同 TestHelpers，JUnit Assert → AssertJ 断言。

#### 3. `TestOrcWrite.java`

**修改内容**：注解迁移、断言库替换。

#### 4. `TestParquetAvroReader.java`

**修改内容**：`@Rule TemporaryFolder` → `@TempDir`，`temp.newFile()` → `File.createTempFile()`，断言迁移。

#### 5. `TestParquetAvroWriter.java`

**修改内容**：同上。

#### 6. `TestSparkAvroEnums.java`

**修改内容**：注解迁移、临时文件 API 适配。

#### 7. `TestSparkDateTimes.java`

**修改内容**：注解迁移、断言库替换。

#### 8. `TestSparkOrcReadMetadataColumns.java`

**修改内容**：
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
- `@Parameterized.Parameters` → `@Parameters`
- 构造函数注入 → `@Parameter` 字段注入
- 返回类型从 `Object[]` 改为 `Collection<Boolean>`
- `@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`
- `@Rule TemporaryFolder` → `@TempDir`

#### 9. `TestSparkParquetReadMetadataColumns.java`

**修改内容**：参数化测试迁移、注解迁移、临时文件 API 适配、断言库替换。

#### 10. `TestSparkParquetWriter.java`

**修改内容**：
- `@Rule TemporaryFolder` → `@TempDir`
- `@Test` → JUnit 5 `@Test`
- 断言迁移
- **新增 `testFpp` 测试方法**：通过反射访问 `ParquetWriter` 的私有 `props` 字段，验证 Bloom Filter 的 FPP 配置是否正确设置。测试使用 `PARQUET_BLOOM_FILTER_COLUMN_ENABLED_PREFIX` 和 `PARQUET_BLOOM_FILTER_COLUMN_FPP_PREFIX` 配置写入器，然后检查 `ParquetProperties.getBloomFilterFPP()` 返回的值是否为 0.05。
- **新增 `SCHEMA` 常量**：用于 `testFpp` 测试

#### 11. `TestParquetDictionaryFallbackToPlainEncodingVectorizedReads.java`

**修改内容**：注解迁移。

#### 12. `TestParquetVectorizedReads.java`

**修改内容**：注解迁移、临时文件 API 适配。

### Spark 3.5 模块修改（5 个文件）

#### 1. `GenericsHelpers.java`

**修改内容**：同步 Spark 3.4 的 AssertJ 断言改进（合并断言链、使用 `.asString()`）。

#### 2. `TestHelpers.java`

**修改内容**：同上，同步断言改进。

#### 3. `TestOrcWrite.java`

**修改内容**：同步断言改进。

#### 4. `TestSparkDateTimes.java`

**修改内容**：同步断言改进。

#### 5. `TestSparkParquetWriter.java`

**修改内容**：同步断言改进。

## 总结

本提交是 Spark 3.4 JUnit 4 到 JUnit 5 迁移的第三批工作，处理 `spark.data` 包中的测试类。核心变更包括：

1. **注解迁移**：`@Test` → JUnit 5 `@Test`/`@TestTemplate`，`@Before` → `@BeforeEach`，`@Rule TemporaryFolder` → `@TempDir`
2. **参数化测试迁移**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，构造函数注入 → `@Parameter` 字段注入
3. **断言统一**：JUnit Assert → AssertJ，合并断言链，使用 `.asString()` 进行类型转换
4. **临时文件 API 适配**：`temp.newFile()` → `File.createTempFile()`
5. **新增测试**：`testFpp` 验证 Parquet Bloom Filter FPP 配置
6. **Spark 3.5 同步**：5 个文件同步断言改进

共修改 17 个文件，新增 304 行，删除 280 行，净增 24 行。
