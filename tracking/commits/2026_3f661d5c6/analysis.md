# 提交 2026：Spark 3.4: Migrate tests in spark, extensions and functions (#12853)

## 提交信息

- **序号**：2026 / 4088
- **哈希**：3f661d5c6657542538a1e944db57405efdefea29
- **短哈希**：3f661d5c6
- **日期**：2025-04-22 12:03:14 +0200
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate tests in spark, extensions and functions (#12853)
- **PR/Issue**：#12853

## 总体目的

本提交将 Spark 3.4 模块的测试代码从 JUnit 4 迁移到 JUnit 5 + AssertJ，与已迁移的其他模块（如 Spark 3.5、core 等）保持一致。

Iceberg 项目一直在推进测试框架的统一迁移：从 JUnit 4（`org.junit.Test`、`org.junit.Assert`、`@Rule`/`TemporaryFolder`）迁移到 JUnit 5（`org.junit.jupiter.api.Test`、`@TempDir`）配合 AssertJ（`assertThat(...)`）。Spark 3.5 等模块已完成迁移，Spark 3.4 模块的测试仍使用旧的 JUnit 4 API，本提交补齐这一差距。

迁移的好处包括：JUnit 5 更现代的扩展模型、AssertJ 流式断言更好的可读性和失败诊断、与项目其他模块风格一致。

## 如何达成设计目的

对 Spark 3.4 模块下 `spark`、`spark-extensions`、`spark-extensions-3.4`（若独立）三个子目录的测试文件，逐个进行以下转换：

1. `import org.junit.Test` → `import org.junit.jupiter.api.Test`
2. `import org.junit.Assert` + `Assert.assertEquals(msg, expected, actual)` → `import static org.assertj.core.api.Assertions.assertThat` + `assertThat(actual).as(msg).isEqualTo(expected)`
3. `Assert.assertTrue(condition)` → `assertThat(condition).isTrue()`
4. `@Rule TemporaryFolder` → `@TempDir Path`
5. `@Before`/`@After` → `@BeforeEach`/`@AfterEach`（若涉及）

## 修改详情

### Spark 3.4 spark 子模块测试文件

- **`TaskCheckHelper.java`**（+35/-43）：`Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)`，`Assert.assertTrue` → `assertThat(...).isTrue()`。
- **`TestDataFileSerialization.java`**（+10/-7）：JUnit 4 → 5 + AssertJ。
- **`TestFileIOSerialization.java`**（+17/-10）：同上。
- **`TestHadoopMetricsContextSerialization.java`**（+1/-1）：同上。
- **`TestManifestFileSerialization.java`**（+55/-47）：同上。
- **`TestScanTaskSerialization.java`**（+19/-12）：同上。
- **`TestTableSerialization.java`**（+29/-20）：同上。

### Spark 3.4 spark actions 测试文件

- **`TestComputeTableStatsAction.java`**（+120/-93）：JUnit 4 → 5 + AssertJ，大量断言迁移。
- **`TestCreateActions.java`**（+220/-188）：同上，本提交中改动量最大的文件。
- **`TestSnapshotTableAction.java`**（+18/-11）：同上。

### Spark 3.4 spark functions 测试文件

- **`TestSparkFunctions.java`**（+1/-1）：仅 `org.junit.Test` → `org.junit.jupiter.api.Test`。

### Spark 3.4 spark-extensions 子模块测试文件

- **`TaskCheckHelper.java`**（+4/-2）：AssertJ 迁移。
- **`TestFileIOSerialization.java`**（+5/-3）：同上。
- **`TestScanTaskSerialization.java`**（+1/-1）：同上。
- **`TestComputeTableStatsAction.java`**（+75/-66）：JUnit 4 → 5 + AssertJ。
- **`TestCreateActions.java`**（+35/-25）：同上。

## 总结

本提交将 Spark 3.4 模块的 16 个测试文件从 JUnit 4 迁移到 JUnit 5 + AssertJ，包括 `@Test` 注解、`Assert.*` 断言、`TemporaryFolder` 等的转换，与其他已迁移模块保持一致。共 +601/-594 行，属测试框架统一迁移，无生产代码改动。
