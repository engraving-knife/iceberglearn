# 提交 1896：Spark 3.4: Migrate TestBase-related remaining tests in actions (#12579)

## 提交信息

- **序号**：1896 / 4088
- **哈希**：2c157cbaf552f48a18d959f0c6b95be36b9dd9e3
- **短哈希**：2c157cbaf
- **日期**：2025-03-21 10:50:50 +0100
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate TestBase-related remaining tests in actions (#12579)
- **PR/Issue**：#12579

## 总体目的

这个提交将 Spark 3.4 模块中 actions 相关的剩余测试从旧的 `SparkTestBase`（基于 JUnit 4）迁移到新的 `TestBase`（基于 JUnit 5），完成测试框架的统一迁移工作。

Iceberg 的 Spark 3.4 测试代码此前使用 JUnit 4 框架，继承自 `SparkTestBase`。社区正在将所有测试迁移到 JUnit 5（使用 `TestBase`），以利用 JUnit 5 的扩展模型、参数化测试等现代特性。本提交处理了 actions 包中尚未迁移的四个测试类。

## 如何达成设计目的

整体设计思路是将测试基类从 `SparkTestBase`（JUnit 4）改为 `TestBase`（JUnit 5），并相应地更新所有测试注解和断言方式：

1. 将 `@Test` 替换为 `@TestTemplate`（配合参数化测试扩展）
2. 将 `@Before` 替换为 `@BeforeEach`
3. 将 `@Rule TemporaryFolder` 替换为 `@TempDir`
4. 将 `Assert.*` 断言替换为 AssertJ 的 `assertThat`
5. 使用 `ParameterizedTestExtension` 和 `Parameter`/`Parameters` 进行参数化测试
6. 添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (修改, +268/-)

**修改目的**：从 JUnit 4 迁移到 JUnit 5。

**工作逻辑**：基类从 `SparkTestBase` 改为 `TestBase`，添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解。所有 `@Test` 改为 `@TestTemplate`，`@Before` 改为 `@BeforeEach`，`@Rule TemporaryFolder` 改为 `@TempDir`。JUnit 4 的 `Assert` 断言替换为 AssertJ 的 `assertThat`。移除不再需要的导入（如 `Iterables`、`Sets`、`StreamSupport`），添加新需要的导入（如 `UUID`、`Parameter`、`ParameterizedTestExtension`、`Parameters`）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction3.java` (修改, +107/-)

**修改目的**：子类适配 JUnit 5 迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, +387/-)

**修改目的**：从 JUnit 4 迁移到 JUnit 5，最大的迁移文件。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java` (修改, +228/-)

**修改目的**：从 JUnit 4 迁移到 JUnit 5。

### spark/v3.5 对应文件 (4 files, small changes)

**修改目的**：Spark 3.5 模块中对应测试的少量适配性修改。

## 总结

本提交完成了 Spark 3.4 actions 包中剩余测试类从 JUnit 4 到 JUnit 5 的迁移。涉及四个测试类（约 1000+ 行变更），主要是注解替换、断言方式更新和基类切换。这是 Iceberg 社区测试框架现代化工作的一部分。
