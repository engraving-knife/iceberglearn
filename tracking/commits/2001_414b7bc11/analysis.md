# 提交 2001：Spark 3.4: Migrate integration test to JUnit5

## 提交信息

- **序号**：2001 / 4088
- **哈希**：414b7bc11531647054f333400a74be23982b6270
- **短哈希**：414b7bc11
- **日期**：2025-04-15 17:20:57 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Migrate integration test to JUnit5 (#12796)
- **PR/Issue**：#12796

## 总体目的

本提交将 Spark 3.4 模块的集成测试从 JUnit 4 迁移到 JUnit 5，并配置 Gradle 构建脚本以支持 JUnit 5 平台执行集成测试。这是 Iceberg 项目系统性 JUnit 4 到 JUnit 5 迁移工作的一部分。

此前，Spark 3.4 的集成测试仅使用 JUnit Vintage 引擎（用于在 JUnit 5 平台上运行 JUnit 4 测试），但实际的 `SmokeTest` 仍使用 JUnit 4 的 API。本提交将 `SmokeTest` 迁移为原生 JUnit 5 测试，并在构建脚本中添加 JUnit Jupiter 和 Platform Launcher 依赖，同时配置 `useJUnitPlatform()` 使集成测试任务使用 JUnit 5 平台执行。

## 如何达成设计目的

1. **构建脚本配置**：在 `spark/v3.4/build.gradle` 中为集成测试添加 `junit.jupiter` 和 `junit.platform.launcher` 依赖，并在 `integrationTest` 任务上配置 `useJUnitPlatform()`。

2. **测试类迁移**：将 `SmokeTest` 从 JUnit 4 API 迁移到 JUnit 5 API，包括注解替换、断言迁移和基类替换。

## 修改详情

### `spark/v3.4/build.gradle` (修改, +3/-0 lines)

**修改目的**：为 Spark 3.4 集成测试添加 JUnit 5 依赖并配置平台执行。

**工作逻辑**：
- 在集成测试依赖中新增 `integrationImplementation libs.junit.jupiter` 和 `integrationImplementation libs.junit.platform.launcher`
- 在 `integrationTest` 任务中添加 `useJUnitPlatform()`，使该测试任务使用 JUnit Platform 执行引擎

### `spark/v3.4/spark-runtime/src/integration/java/org/apache/iceberg/spark/SmokeTest.java` (修改, +45/-44 lines)

**修改目的**：将 SmokeTest 从 JUnit 4 迁移到 JUnit 5。

**工作逻辑**：
- **import 替换**：移除 `org.junit.Assert`、`org.junit.Before`、`org.junit.Test`、`SparkExtensionsTestBase`；新增 `ParameterizedTestExtension`、`ExtensionsTestBase`、`AfterEach`、`TestTemplate`、`ExtendWith`、`Files`
- **类声明**：从 `extends SparkExtensionsTestBase` 改为 `@ExtendWith(ParameterizedTestExtension.class) extends ExtensionsTestBase`，移除构造函数（由参数化扩展通过字段注入处理）
- **注解迁移**：`@Before` → `@AfterEach`（语义上 dropTable 应在每个测试后执行），`@Test` → `@TestTemplate`
- **断言迁移**：所有 `Assert.assertEquals(msg, expected, actual)` 和 `Assert.assertTrue(msg, actual)` 改为 AssertJ 的 `assertThat(actual).as(msg).isEqualTo(expected)` 和 `assertThat(actual).as(msg).isTrue()`
- **临时目录**：将 `temp.newFolder()` 改为 `Files.createTempDirectory(temp, "junit")`，适配 JUnit 5 的临时目录扩展
- **异常声明**：`testAlterTable` 方法移除 `throws NoSuchTableException`（不再需要）

## 总结

本提交将 Spark 3.4 的集成测试（SmokeTest）从 JUnit 4 迁移到 JUnit 5，并配置 Gradle 构建脚本支持 JUnit Platform 执行。迁移包括注解替换、AssertJ 断言、基类替换和临时目录 API 适配，是 Iceberg JUnit 5 迁移工作的组成部分。
