# 提交 0665：AWS: Migrate tests to JUnit5

## 提交信息
- **序号**：0665 / 4088
- **哈希**：b3261d07fc687d5df1f0f8309bc5a0f6022434cf
- **短哈希**：b3261d07f
- **日期**：2024-04-08
- **作者**：Tom Tanaka
- **提交说明**：AWS: Migrate tests to JUnit5 (#10086)
- **PR/Issue**：PR #10086

## 总体目的

本提交将 Iceberg AWS 模块（`iceberg-aws`）的集成测试从 JUnit 4 迁移到 JUnit 5（Jupiter），并同步将断言风格从 JUnit 4 的 `org.junit.Assert` 与 AssertJ 的 `Assertions.assertThat(...)` 混用，统一收敛为 AssertJ 的静态导入 `assertThat(...)` / `assertThatThrownBy(...)` 风格。

这是 Iceberg 项目整体向 JUnit 5 迁移的一部分。JUnit 5 提供了更现代的测试模型（扩展模型取代 JUnit 4 的 Runner/Rule 模型）、更好的依赖注入（`@ExtendWith`、参数化测试）、更清晰的断言 API 与生命周期注解。统一到 JUnit 5 后，AWS 集成测试与 Iceberg 其他模块（如 core 模块早已使用 JUnit 5）保持一致，降低测试框架的认知负担与维护成本，同时便于使用 JUnit 5 的新特性（如嵌套测试、动态测试、参数解析器等）。

迁移还顺带改善了断言可读性：将 JUnit 4 风格的 `Assert.assertEquals("msg", expected, actual)` 改为 AssertJ 的 `assertThat(actual).as("msg").isEqualTo(expected)`，后者流式 API 更接近自然语言，且失败信息更丰富。此外，对于 `try-catch-fail` 这种反模式，迁移为 `assertThatThrownBy(...).isInstanceOf(...)` 的声明式异常断言，使代码更简洁。

## 如何达成设计目的

迁移遵循一套系统化的、机械的模式，贯穿所有 17 个被修改的文件：

**1. 构建配置层（`build.gradle`）**：在 `iceberg-aws` 的 `integrationTest` 任务中添加 `useJUnitPlatform()`，使 Gradle 使用 JUnit Platform 引擎运行测试，这是 JUnit 5 测试运行的前提。

**2. Checkstyle 配置（`.baseline/checkstyle/checkstyle.xml`）**：在 `IllegalImport` 规则的 `IllegalImport` 的 `illegalClasses`/`ignore` 配置中，将 `org.assertj.core.api.Assertions.*` 和 `org.assertj.core.api.Assumptions.*` 加入允许的静态导入白名单。这样测试代码可以静态导入 `assertThat`、`assertThatThrownBy` 等，而不会触发 checkstyle 违规。注意此处保留了对 `org.junit.Assert.*` 的限制策略不变（仍允许但不鼓励）。

**3. 注解迁移模式**（适用于所有测试类）：
- `import org.junit.Test;` → `import org.junit.jupiter.api.Test;`
- `import org.junit.Before;` → `import org.junit.jupiter.api.BeforeEach;`，`@Before` → `@BeforeEach`
- `import org.junit.After;` → `import org.junit.jupiter.api.AfterEach;`，`@After` → `@AfterEach`
- `import org.junit.BeforeClass;` → `import org.junit.jupiter.api.BeforeAll;`，`@BeforeClass` → `@BeforeAll`
- `import org.junit.AfterClass;` → `import org.junit.jupiter.api.AfterAll;`，`@AfterClass` → `@AfterAll`
- `import org.junit.Assert;`（整体移除，改为静态导入 AssertJ）

**4. 断言迁移模式**：
- `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
- `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`
- `Assert.assertTrue(x)` → `assertThat(x).isTrue()`
- `Assert.assertFalse(x)` → `assertThat(x).isFalse()`
- `Assert.assertNull(x)` → `assertThat(x).isNull()`
- `Assert.assertNotNull(x)` → `assertThat(x).isNotNull()`
- `Assert.assertEquals(n, collection.size())` → `assertThat(collection).hasSize(n)`
- `Assert.assertTrue(collection.isEmpty())` → `assertThat(collection).isEmpty()`
- `Assert.assertTrue(collection.contains(e))` → `assertThat(collection).contains(e)`
- `Assertions.assertThatThrownBy(...)`（旧用法，前缀 AssertJ 类名）→ 静态导入的 `assertThatThrownBy(...)`
- `Assertions.assertThat(...)`（旧用法）→ 静态导入的 `assertThat(...)`
- try-catch + `Assert.fail("msg")` → `assertThatThrownBy(...).as("msg").isInstanceOf(...)`

**5. 异常测试迁移模式**（典型见 `TestLakeFormationAwsClientFactory`）：
- 原 JUnit 4 风格：`try { call(); Assert.fail("should fail"); } catch (Exception e) { Assert.assertEquals(ExpectedEx.class, e.getClass()); }`
- 迁移后：`assertThatThrownBy(() -> call()).isInstanceOf(ExpectedEx.class);`
- 对于"不应抛异常"的场景：`assertThatNoException().isThrownBy(() -> call());`

**6. 导入整理**：移除不再使用的导入（如 `org.assertj.core.api.Assertions`、`org.junit.Assert`、`org.junit.Before` 等），新增静态导入 `import static org.assertj.core.api.Assertions.assertThat;` 和 `import static org.assertj.core.api.Assertions.assertThatThrownBy;`。部分文件还移除了未使用的导入（如 `Streams`、`GlueException`）。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml`
**修改目的**：允许 AssertJ 的静态导入通过 checkstyle 检查。
**工作逻辑**：在 `IllegalImport` 模块的允许列表中加入 `org.assertj.core.api.Assertions.*` 和 `org.assertj.core.api.Assumptions.*`，使测试类可以静态导入 `assertThat`、`assertThatThrownBy`、`assertThatNoException`、`Assumptions.assumeThat` 等 AssertJ 方法而不被 checkstyle 拦截。

### `build.gradle`
**修改目的**：使 AWS 集成测试使用 JUnit Platform 运行。
**工作逻辑**：在 `project(':iceberg-aws')` 的 `task integrationTest(type: Test)` 中新增 `useJUnitPlatform()`，确保 Gradle 的 Test 任务用 JUnit 5 引擎发现和执行测试。

### `aws/src/integration/java/org/apache/iceberg/aws/TestAssumeRoleAwsClientFactory.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入。
**工作逻辑**：`@Before`→`@BeforeEach`、`@After`→`@AfterEach`、`org.junit.Test`→`org.junit.jupiter.api.Test`。`Assertions.assertThatThrownBy`→`assertThatThrownBy`，`Assertions.assertThat`→`assertThat`，`Assert.assertFalse("msg", x)`→`assertThat(x).isFalse()`。

### `aws/src/integration/java/org/apache/iceberg/aws/TestDefaultAwsClientFactory.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入。
**工作逻辑**：`org.junit.Test`→`org.junit.jupiter.api.Test`，`Assertions.assertThatThrownBy`→`assertThatThrownBy`。

### `aws/src/integration/java/org/apache/iceberg/aws/dynamodb/TestDynamoDbCatalog.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入，重构断言。
**工作逻辑**：`@BeforeClass`→`@BeforeAll`、`@AfterClass`→`@AfterAll`。将大量 `Assert.assertTrue/assertEquals` 改为 `assertThat(...)`。对 DynamoDB item 的多字段断言，用 `assertThat(response.item()).hasEntrySatisfying("key", av -> assertThat(av.s()).isEqualTo(...))` 链式断言替代多次 `Assert.assertEquals`。`Assert.assertEquals(n, list.size())`→`assertThat(list).hasSize(n)`。

### `aws/src/integration/java/org/apache/iceberg/aws/dynamodb/TestDynamoDbLockManager.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入。
**工作逻辑**：`@BeforeClass`→`@BeforeAll`、`@Before`→`@BeforeEach`、`@AfterClass`→`@AfterAll`。锁管理器的 `acquire`/`release` 布尔断言改为 `assertThat(...).isTrue()/isFalse()`。并发锁测试中 `Assert.assertEquals("msg", n, results.stream().filter(s->s).count())` 改为 `assertThat(results).as("msg").hasSize(n)`（注意：原代码先 filter 再 count，新代码直接断言 results 大小，语义上要求 results 全部为 true 才等价，这里 results 是 CompletableFuture 结果列表，确实收集了所有成功/失败结果，但严格来说原断言是"统计为 true 的个数"，新断言是"列表大小"——需确认 results 是否只含成功项；从上下文看 results 是 `futures` 收集的结果，包含所有任务的返回值，此处改为 hasSize(n) 实际改变了断言语义，可能是个需要关注的点）。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/GlueTestBase.java`
**修改目的**：迁移基类的生命周期注解。
**工作逻辑**：`@BeforeClass`→`@BeforeAll`、`@AfterClass`→`@AfterAll`。该类是其他 Glue 测试的基类，迁移后所有继承类自动获得 JUnit 5 生命周期。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogCommitFailure.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入，大量提交失败场景断言重构。
**工作逻辑**：`org.junit.Test`→`org.junit.jupiter.api.Test`。`Assertions.assertThatThrownBy`→`assertThatThrownBy`。提交失败后的元数据断言从 `Assert.assertEquals("msg", expected, ops.current())` 改为 `assertThat(ops.current()).as("msg").isEqualTo(expected)`，`Assert.assertTrue(metadataFileExists(...))`→`assertThat(...).isTrue()`，`Assert.assertEquals("msg", n, metadataFileCount(...))`→`assertThat(metadataFileCount(...)).as("msg").isEqualTo(n)`。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogLock.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入。
**工作逻辑**：`@BeforeClass`→`@BeforeAll`、`@AfterClass`→`@AfterAll`。并发提交断言 `Assert.assertEquals("msg", n, table.history().size())`→`assertThat(table.history()).as("msg").hasSize(n)`，同样对 `allManifests` 断言改为 `hasSize`。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogNamespace.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入。
**工作逻辑**：`org.junit.Test`→`org.junit.jupiter.api.Test`。namespace 属性断言从多次 `Assert.assertTrue(parameters.containsKey(k)) + assertEquals(v, parameters.get(k))` 收敛为 `assertThat(parameters).containsEntry(k, v).containsEntry(k2, v2)`，更简洁。`assertNull`/`assertNotNull` 改为 `isNull()`/`isNotNull()`。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogTable.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入，表属性断言重构。
**工作逻辑**：`org.junit.Test`→`org.junit.jupiter.api.Test`。表创建验证中多个 `Assert.assertEquals` 改为 `assertThat(...).isEqualTo(...)`，集合大小断言改为 `hasSize`/`hasSameSizeAs`。catalog 默认/覆盖属性测试中，5 个独立的 `Assert.assertEquals("msg", expected, table.properties().get(key))` 收敛为一个链式 `assertThat(table.properties()).as(...).containsEntry(k, v).as(...).containsEntry(...)` 断言。S3 tag 验证同样收敛为 `containsEntry` 链。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/LakeFormationTestBase.java`
**修改目的**：迁移基类生命周期注解 + Awaitility 中的断言。
**工作逻辑**：`@BeforeClass`→`@BeforeAll`、`@AfterClass`→`@AfterAll`。Awaitility `.untilAsserted(...)` 内部的 `Assertions.assertThat(...)`→`assertThat(...)`（静态导入）。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationAwsClientFactory.java`
**修改目的**：迁移到 JUnit 5 + AssertJ，将 try-catch-fail 改为声明式异常断言。
**工作逻辑**：`@Before`→`@BeforeEach`、`@After`→`@AfterEach`。原 `try { createNamespace(denied); Assert.fail("Access denied"); } catch (GlueException e) { Assert.assertEquals(AccessDeniedException.class, e.getClass()); }` 改为 `assertThatThrownBy(() -> createNamespace(denied)).isInstanceOf(AccessDeniedException.class);`。原 `try { createNamespace(allowed); } catch (GlueException e) { Assert.fail("should succeed"); }` 改为 `assertThatNoException().isThrownBy(() -> createNamespace(allowed));`。移除了未使用的 `GlueException` 导入。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationDataOperations.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入。
**工作逻辑**：`@Before`→`@BeforeEach`、`@After`→`@AfterEach`、`org.junit.Test`→`org.junit.jupiter.api.Test`。`Assertions.assertThatThrownBy`→`assertThatThrownBy`。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationMetadataOperations.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入。
**工作逻辑**：`org.junit.Test`→`org.junit.jupiter.api.Test`。`Assertions.assertThatThrownBy`→`assertThatThrownBy`。`Assert.assertTrue(namespaces.contains(ns))`→`assertThat(namespaces).contains(ns)`。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入，合并重复的 setup 方法。
**工作逻辑**：`@BeforeClass`→`@BeforeAll`、`@AfterClass`→`@AfterAll`。原代码同时有 `@Before public void before()` 和 `@BeforeEach public void beforeEach()` 两个方法（历史遗留），迁移时合并为单个 `@BeforeEach beforeEach()`，将原 `before()` 中设置 objectKey/objectUri 的逻辑移入 `beforeEach()`。`import static org.junit.Assert.assertEquals`→`import static org.assertj.core.api.Assertions.assertThat`。所有 `Assert.assertEquals`/`Assert.assertTrue`/`Assert.assertNull`/`Assertions.assertEquals` 改为 AssertJ 风格。`assertEquals(n, Streams.stream(list).count())`→`assertThat(list).hasSize(n)`。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3MultipartUpload.java`
**修改目的**：迁移到 JUnit 5 + AssertJ 静态导入。
**工作逻辑**：`@BeforeClass`→`@BeforeAll`、`@AfterClass`→`@AfterAll`、`@Before`→`@BeforeEach`。`Assert.assertEquals(expected, actual)`→`assertThat(actual).isEqualTo(expected)`。移除了测试方法签名上不再需要的 `throws IOException`（当方法体不再直接抛 IOException 时）。

## 小结
- **成效**：成功将 AWS 模块全部集成测试迁移至 JUnit 5，并统一为 AssertJ 静态导入断言风格。迁移后测试与 Iceberg 其他模块的测试框架一致，可读性与可维护性提升。
- **影响范围**：影响 `iceberg-aws` 模块的集成测试（`aws/src/integration/`）、构建配置（`build.gradle`）和 checkstyle 配置（`.baseline/checkstyle/checkstyle.xml`）。不改变任何产品代码或运行时行为，仅影响测试。
- **回迁到 1.4.x 的注意事项**：回迁前需确认 1.4.x 分支的 `iceberg-aws` 模块测试是否仍在 JUnit 4 上。若是，需整体回迁本提交（含 build.gradle 的 `useJUnitPlatform()` 和 checkstyle 白名单），否则单独回迁部分文件会导致测试框架混用。需注意 `TestDynamoDbLockManager` 和 `TestS3FileIOIntegration` 中个别断言语义的微调（如 `hasSize(n)` 替代 `stream().filter().count()` 的等价性），建议回迁后实际运行集成测试验证。集成测试依赖真实 AWS 环境，回迁验证可能受环境限制。checkstyle 白名单的回迁是必须的，否则静态导入会被 checkstyle 拦截。
