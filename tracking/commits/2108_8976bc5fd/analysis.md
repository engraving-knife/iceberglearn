# 提交分析：Spark 3.4: Migrate SparkTestBaseWithCatalog related tests to JUnit5

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2108 |
| 短哈希 | `8976bc5fd` |
| 完整哈希 | `8976bc5fd9b39f4d079e6e5d66749dee8d4c423f` |
| 作者 | Tom Tanaka |
| 邮箱 | 43331405+tomtongue@users.noreply.github.com |
| 日期 | 2025-05-12 17:15:26 +0900 |
| 提交信息 | Spark 3.4: Migrate SparkTestBaseWithCatalog related tests to JUnit5 (#13015) |

## 总体目的

本提交将 Spark 3.4 模块中所有继承自 `SparkTestBaseWithCatalog` 的测试类从 JUnit 4 迁移到 JUnit 5。这是 Iceberg 项目逐步将测试框架从 JUnit 4 迁移到 JUnit 5 的延续工作。此前在提交 `b3d513334`（#12501）中已经创建了 JUnit 5 版本的新基类 `TestBaseWithCatalog`，本提交则将剩余的、仍然依赖旧基类 `SparkTestBaseWithCatalog` 的测试类全部切换到新基类，并删除旧基类。同时对 Spark 3.5 模块中对应的测试类也进行了同步修改，确保两个版本之间的一致性。

## 设计目的的实现方式

1. **删除旧基类**：直接删除 `SparkTestBaseWithCatalog.java`（182 行），因为新的 JUnit 5 基类 `TestBaseWithCatalog` 已在前序提交中创建并提供等价功能。

2. **批量替换注解和导入**：对所有继承 `SparkTestBaseWithCatalog` 的测试类执行以下系统性替换：
   - 继承基类：`extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`
   - 测试注解：`@Test`（`org.junit.Test`）→ `@TestTemplate`（`org.junit.jupiter.api.TestTemplate`）
   - 生命周期注解：`@Before` → `@BeforeEach`，`@After` → `@AfterEach`
   - 参数化运行器：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
   - 断言库：`org.junit.Assert.assertEquals` 等替换为 AssertJ 的 `assertThat`
   - 临时文件夹规则：`@Rule public TemporaryFolder temp` → 使用 JUnit 5 的 `@TempDir` 机制（通过 `temp.toFile()` 访问）

3. **同步修改 Spark 3.5 测试**：对 Spark 3.5 模块中对应的测试类进行同步修改，添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解，并将不安全的 map 直接取值断言改为使用 `hasEntrySatisfying` 进行更安全的断言。

## 修改详情

### 1. 删除 `SparkTestBaseWithCatalog.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkTestBaseWithCatalog.java`

**修改内容**：整个文件被删除（182 行）。

**目的**：该类是 JUnit 4 风格的测试基类，使用了 `@ClassRule`、`@Rule`、`@BeforeClass`、`@AfterClass` 等 JUnit 4 注解。其功能已由前序提交 `b3d513334` 中创建的 `TestBaseWithCatalog`（JUnit 5 风格）完全替代，因此可以安全删除。

### 2. 修改 `TestSparkDistributionAndOrderingUtil.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkDistributionAndOrderingUtil.java`

**修改内容**：
- 基类从 `SparkTestBaseWithCatalog` 改为 `TestBaseWithCatalog`
- 添加 `@ExtendWith(ParameterizedTestExtension.class)` 类注解
- 导入从 JUnit 4（`org.junit.After`、`org.junit.Assert`、`org.junit.Test`）替换为 JUnit 5（`org.junit.jupiter.api.AfterEach`、`org.junit.jupiter.api.TestTemplate`）
- 所有 `@Test` 替换为 `@TestTemplate`
- `@After` 替换为 `@AfterEach`
- 断言从 `Assert.assertEquals` 改为 AssertJ `assertThat`

**目的**：将该测试类从 JUnit 4 参数化测试迁移为 JUnit 5 参数化扩展模式，使用 `@TestTemplate` 配合 `ParameterizedTestExtension` 实现参数化测试。

### 3. 修改 `TestSparkExecutorCache.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkExecutorCache.java`

**修改内容**：
- 基类从 `SparkTestBaseWithCatalog` 改为 `TestBaseWithCatalog`
- `@RunWith(Parameterized.class)` 替换为 `@ExtendWith(ParameterizedTestExtension.class)`
- 删除了构造函数 `public TestSparkExecutorCache(String catalogName, String implementation, Map<String, String> config)`，因为 JUnit 5 的参数注入不再需要构造函数
- `@Parameters` 方法的可见性从 `public` 改为 `protected`
- `@Before` → `@BeforeEach`，`@After` → `@AfterEach`，`@Test` → `@TestTemplate`
- `temp.newFile("...")` 替换为 `new File(temp.toFile(), "...")`，适配 JUnit 5 的 `@TempDir` API

**目的**：迁移到 JUnit 5 参数化扩展机制。JUnit 5 不再需要通过构造函数注入参数，而是通过 `ParameterizedTestExtension` 自动处理参数注入。`temp.newFile()` 是 JUnit 4 `TemporaryFolder` 的 API，替换为 JUnit 5 的 `temp.toFile()` 方式。

### 4. 修改 `TestDataFrameWriterV2.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2.java`

**修改内容**：基类替换、注解迁移（`@Test` → `@TestTemplate`、`@After` → `@AfterEach`）、断言库替换。

**目的**：标准 JUnit 4 到 JUnit 5 迁移。

### 5. 修改 `TestDataFrameWriterV2Coercion.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2Coercion.java`

**修改内容**：基类替换、注解迁移、断言库替换。

**目的**：标准 JUnit 4 到 JUnit 5 迁移。

### 6. 修改 `TestSparkCatalogCacheExpiration.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkCatalogCacheExpiration.java`

**修改内容**：基类替换、注解迁移、断言库替换。

**目的**：标准 JUnit 4 到 JUnit 5 迁移。

### 7. 修改 `TestSparkPlanningUtil.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkPlanningUtil.java`

**修改内容**：基类替换、注解迁移、断言库替换。

**目的**：标准 JUnit 4 到 JUnit 5 迁移。

### 8. 修改 `TestSparkReadMetrics.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReadMetrics.java`

**修改内容**：
- 基类替换、注解迁移
- 添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解
- 断言方式改写：将 `assertThat(metricsMap.get("key").value())` 形式改为 `assertThat(metricsMap).hasEntrySatisfying("key", sqlMetric -> assertThat(sqlMetric.value())...)` 形式

**目的**：迁移到 JUnit 5 的同时改进断言安全性。原来的写法在 map 中不存在对应 key 时会抛出 NPE，新写法使用 `hasEntrySatisfying` 在 key 不存在时会给出更清晰的断言失败信息。

### 9. 修改 `TestSparkScan.java`（Spark 3.4）

**文件**：`spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`

**修改内容**：基类替换、注解迁移、断言库替换、参数化测试迁移。

**目的**：标准 JUnit 4 到 JUnit 5 迁移。

### 10-15. 同步修改 Spark 3.5 对应测试文件

**文件**：
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkDistributionAndOrderingUtil.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkCatalogCacheExpiration.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkPlanningUtil.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReadMetrics.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`

**修改内容**：
- 添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解
- `TestSparkReadMetrics.java` 中将 `metricsMap.get("key").value()` 断言改为 `hasEntrySatisfying` 形式
- `TestSparkScan.java` 和 `TestSparkPlanningUtil.java` 中也有对应的断言改进

**目的**：确保 Spark 3.5 模块与 Spark 3.4 模块的测试保持一致。Spark 3.5 中的测试此前已经在 `TestBaseWithCatalog` 基类上运行，但缺少 `@ExtendWith(ParameterizedTestExtension.class)` 注解，本提交补齐了这一缺失，同时同步了断言改进。

## 总结

本提交是 Iceberg 项目 JUnit 4 到 JUnit 5 迁移工作的延续，专门处理 Spark 3.4 模块中仍然依赖旧基类 `SparkTestBaseWithCatalog` 的测试类。核心变更包括：

1. **删除旧基类** `SparkTestBaseWithCatalog`，所有测试类改用前序提交创建的 `TestBaseWithCatalog`
2. **系统性注解迁移**：`@Test` → `@TestTemplate`、`@Before/@After` → `@BeforeEach/@AfterEach`、`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
3. **参数化测试机制变更**：JUnit 5 不再需要构造函数注入参数，改为通过扩展机制自动注入
4. **断言改进**：从 JUnit 4 Assert 迁移到 AssertJ，并在 `TestSparkReadMetrics` 中将不安全的 map 直接取值改为 `hasEntrySatisfying` 形式
5. **跨版本同步**：同步修改 Spark 3.5 对应测试文件，确保两个 Spark 版本的测试一致性

共修改 15 个文件，删除 655 行，新增 711 行，净增 56 行（主要来自断言写法的扩展）。
