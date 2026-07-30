# 提交分析：Spark 3.4: Migrate other JUnit 4 dependant tests to JUnit 5

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2123 |
| 短哈希 | `d44cba57c` |
| 完整哈希 | `d44cba57c859fef04eec1dcc26838b6331c33702` |
| 作者 | Tom Tanaka |
| 邮箱 | 43331405+tomtongue@users.noreply.github.com |
| 日期 | 2025-05-14 17:00:07 2025 +0900 |
| 提交信息 | Spark 3.4: Migrate other JUnit 4 dependant tests to JUnit 5 (#13044) |

## 总体目的

本提交将 Spark 3.4 模块中剩余的 JUnit 4 依赖测试类迁移到 JUnit 5。这是 JUnit 4 到 JUnit 5 迁移工作的第五批也是最后一批，处理之前批次未覆盖的测试类，包括过滤器测试、Schema 工具测试、表工具测试、读取投影测试、快照选择测试和数据写入测试等。同时同步重构了 Spark 3.5 模块中对应的测试类。

## 设计目的的实现方式

1. **JUnit 4 到 JUnit 5 注解迁移**：
   - `@Test`（`org.junit.Test`）→ `@Test`（`org.junit.jupiter.api.Test`）或 `@TestTemplate`（参数化测试）
   - `@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`
   - `@Rule public TemporaryFolder temp` → `@TempDir private Path temp`

2. **参数化测试迁移**：
   - `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
   - 构造函数注入 → `@Parameter` 字段注入
   - 参数重构：将 `PlanningMode` 枚举参数改为 `Map<String, String>` 属性映射参数（与 2109 中的重构模式一致）

3. **断言库统一**：`org.junit.Assert.*` → AssertJ `assertThat`

## 修改详情

### Spark 3.4 模块修改（12 个文件）

| 文件 | 主要变更 |
|------|---------|
| `SparkTestHelperBase.java` | 断言库替换 |
| `TestChangelogIterator.java` | 注解迁移、断言库替换 |
| `TestSparkFilters.java` | 注解迁移、断言库替换 |
| `TestSparkSchemaUtil.java` | 注解迁移、断言库替换、临时文件 API 适配 |
| `TestSparkTableUtil.java` | 注解迁移、断言库替换 |
| `TestSparkV2Filters.java` | 注解迁移、断言库替换 |
| `TestSparkValueConverter.java` | 注解迁移、断言库替换 |
| `TestReadProjection.java` | 参数化测试迁移、注解迁移、断言库替换、临时文件 API 适配（429 行变更，最大的文件） |
| `TestSnapshotSelection.java` | 参数化测试迁移（PlanningMode → 属性映射）、注解迁移、断言库替换 |
| `TestSparkDataWrite.java` | 参数化测试迁移、注解迁移、断言库替换、临时文件 API 适配 |
| `TestSparkReadProjection.java` | 注解迁移、断言库替换 |
| `TestSparkTable.java` | 断言库替换 |

### Spark 3.5 模块修改（8 个文件）

对以下文件同步 Spark 3.4 的修改：
- `SparkTestHelperBase.java`
- `TestSparkFilters.java`
- `TestSparkSchemaUtil.java`
- `TestSparkTableUtil.java`
- `TestSparkV2Filters.java`
- `TestReadProjection.java`
- `TestSnapshotSelection.java`
- `TestSparkDataWrite.java`

## 总结

本提交是 Spark 3.4 JUnit 4 到 JUnit 5 迁移的第五批也是最后一批工作，处理剩余的 JUnit 4 依赖测试类。核心变更模式与前几批一致：

1. **注解迁移**：`@Test` → JUnit 5 `@Test`/`@TestTemplate`，`@BeforeClass/@AfterClass` → `@BeforeAll/@AfterAll`
2. **参数化测试迁移**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，构造函数注入 → `@Parameter` 字段注入，`PlanningMode` → 属性映射参数
3. **断言统一**：JUnit Assert → AssertJ
4. **临时文件 API 适配**：`TemporaryFolder` → `@TempDir`
5. **Spark 3.5 同步**：8 个文件同步修改

共修改 20 个文件，新增 871 行，删除 731 行，净增 140 行。这标志着 Spark 3.4 模块的 JUnit 4 到 JUnit 5 迁移工作基本完成。
