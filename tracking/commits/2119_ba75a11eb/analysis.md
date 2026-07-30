# 提交分析：Spark 3.4: Migrate tests in spark.source including refactoring Spark 3.5 tests

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2119 |
| 短哈希 | `ba75a11eb` |
| 完整哈希 | `ba75a11ebec01e6661544d5c85df5448bb676a69` |
| 作者 | Tom Tanaka |
| 邮箱 | 43331405+tomtongue@users.noreply.github.com |
| 日期 | 2025-05-14 02:03:15 2025 +0900 |
| 提交信息 | Spark 3.4: Migrate tests in spark.source including refactoring Spark 3.5 tests (#13040) |

## 总体目的

本提交将 Spark 3.4 模块中 `spark.source` 包及其相关测试类从 JUnit 4 迁移到 JUnit 5。这是继 2108（#13015）、2109（#13031）和 2117（#13038）之后的第四批 JUnit 5 迁移工作，处理 `spark.source` 包和 `spark.actions` 中的测试。同时同步重构了 Spark 3.5 模块中对应的测试类。

## 设计目的的实现方式

1. **JUnit 4 到 JUnit 5 注解迁移**：
   - `@Test`（`org.junit.Test`）→ `@Test`（`org.junit.jupiter.api.Test`）或 `@TestTemplate`（参数化测试）
   - `@Before` → `@BeforeEach`，`@After` → `@AfterEach`
   - `@BeforeClass` → `@BeforeAll`，`@AfterClass` → `@AfterAll`
   - `@Rule public TemporaryFolder temp` → `@TempDir private Path temp`

2. **参数化测试迁移**：
   - `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
   - 构造函数注入 → `@Parameter` 字段注入

3. **断言库统一**：`org.junit.Assert.*` → AssertJ `assertThat`

4. **断言改进**：使用 AssertJ 的流式 API 改进断言，如 `assertThat(results).singleElement().satisfies(...)` 替代多次 `Assert.assertEquals`

## 修改详情

### Spark 3.4 模块修改（14 个文件）

#### 1. `TestExpireSnapshotsAction.java`
**修改内容**：注解迁移、断言库替换。

#### 2. `TestBaseReader.java`
**修改内容**：注解迁移、断言库替换、临时文件 API 适配。

#### 3. `TestFilteredScan.java`
**修改内容**：注解迁移（`@Test` → `@TestTemplate`）、参数化测试迁移、断言库替换、临时文件 API 适配。

#### 4. `TestForwardCompatibility.java`
**修改内容**：注解迁移、断言库替换。

#### 5. `TestIcebergSpark.java`
**修改内容**：
- `@BeforeClass` → `@BeforeAll`，`@AfterClass` → `@AfterAll`
- 断言改进：使用 `assertThat(results).singleElement().satisfies(...)` 替代多次 `Assert.assertEquals`
- `Assert.assertThrows` → `assertThatThrownBy`

#### 6. `TestInternalRowWrapper.java`
**修改内容**：注解迁移、断言库替换。

#### 7. `TestPartitionPruning.java`
**修改内容**：参数化测试迁移、注解迁移、断言库替换。

#### 8. `TestPartitionValues.java`
**修改内容**：参数化测试迁移、注解迁移、断言库替换、临时文件 API 适配。

#### 9. `TestSparkAggregates.java`
**修改内容**：注解迁移、断言库替换。

#### 10. `TestSparkDataFile.java`
**修改内容**：注解迁移、断言库替换。

#### 11. `TestSparkReaderWithBloomFilter.java`
**修改内容**：注解迁移、断言库替换。

#### 12. `TestStreamingOffset.java`
**修改内容**：
- `@Test` → JUnit 5 `@Test`
- `Assert.assertArrayEquals` → `assertThat(...).isEqualTo(...)`
- `Assert.assertEquals` → `assertThat(...).isEqualTo(...)`

#### 13. `TestStructuredStreaming.java`
**修改内容**：注解迁移、断言库替换。

#### 14. `TestWriteMetricsConfig.java`
**修改内容**：参数化测试迁移、注解迁移、断言库替换。

### Spark 3.5 模块修改（9 个文件）

对以下文件同步 Spark 3.4 的修改：
- `TestFilteredScan.java`
- `TestForwardCompatibility.java`
- `TestIcebergSpark.java`
- `TestPartitionPruning.java`
- `TestPartitionValues.java`
- `TestSparkAggregates.java`
- `TestSparkDataFile.java`
- `TestStructuredStreaming.java`
- `TestWriteMetricsConfig.java`

**修改内容**：同步断言改进（使用 `singleElement().satisfies()` 等 AssertJ 流式 API）、注解一致性。

## 总结

本提交是 Spark 3.4 JUnit 4 到 JUnit 5 迁移的第四批工作，处理 `spark.source` 包中的测试类。核心变更包括：

1. **注解迁移**：`@Test` → JUnit 5 `@Test`/`@TestTemplate`，`@Before/@After` → `@BeforeEach/@AfterEach`，`@BeforeClass/@AfterClass` → `@BeforeAll/@AfterAll`
2. **参数化测试迁移**：多个测试类从 JUnit 4 参数化运行器迁移到 JUnit 5 扩展
3. **断言统一和改进**：JUnit Assert → AssertJ，使用 `singleElement().satisfies()` 等流式 API
4. **临时文件 API 适配**：`TemporaryFolder` → `@TempDir`
5. **Spark 3.5 同步**：9 个文件同步断言改进

共修改 23 个文件，新增 679 行，删除 619 行，净增 60 行。
