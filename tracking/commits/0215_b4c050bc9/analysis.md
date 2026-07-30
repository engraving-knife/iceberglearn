# 提交 0215：Aliyun: Switch iceberg-aliyun's tests to JUnit5 (#9122)

## 提交信息

- **序号**：0215 / 4088
- **哈希**：b4c050bc9c425b7b614058f420ba79b99db5d75f
- **短哈希**：b4c050bc9
- **日期**：2023-12-05 13:30:25 +0100
- **作者**：Li Han
- **提交说明**：Aliyun: Switch iceberg-aliyun's tests to JUnit5 (#9122)
- **PR/Issue**：#9122

## 总体目的

本提交把 `iceberg-aliyun` 模块的整套测试从 JUnit 4 迁移到 JUnit 5（JUnit Jupiter），并把断言风格统一到 AssertJ。这是 Iceberg 项目范围内一项渐进式现代化工作的一部分——Iceberg 主仓库与多个模块（core、spark、flink 等）早已逐步迁移到 JUnit 5，`iceberg-aliyun` 是少数仍停留在 JUnit 4 的模块之一。迁移后，aliyun 模块的测试基础设施与项目其余部分保持一致，便于后续合并测试公用工具、共享扩展（extension）以及避免 JUnit 4/5 双栈维护负担。

迁移的关键技术点是把基于 JUnit 4 `@ClassRule` + `TestRule` 的 OSS 测试基础设施（启动/停止 OSS mock 或真实集成环境、创建 bucket、清理 bucket）改写为 JUnit 5 的 `@RegisterExtension` + `BeforeAllCallback`/`AfterAllCallback` 扩展模型。这是从 JUnit 4 的 `Rule`（基于 `Statement` 包装）到 JUnit 5 的 `Extension`（基于生命周期回调）的范式转换。同时所有断言从 `org.junit.Assert.*` 改为 AssertJ 的 `Assertions.assertThat(...)`，前缀假设从 `org.junit.Assume.assumeTrue` 改为 AssertJ 的 `Assumptions.assumeThat`。`build.gradle` 中通过 `useJUnitPlatform()` 启用 JUnit 5 测试运行器。

## 如何达成设计目的

整体改动分三组：(1) 构建配置——`build.gradle` 的 `iceberg-aliyun` 项目块加 `test { useJUnitPlatform() }`，激活 JUnit Platform 引擎；(2) 测试基础设施——把三个 `*TestRule` 类/接口重命名为 `*Extension` 并改造为 JUnit 5 扩展，更新 `TestUtility` 工厂与 `AliyunOSSTestBase` 基类；(3) 测试用例——8 个测试类把注解、import、断言全部切换到 JUnit 5 + AssertJ。改动覆盖 14 个文件、约 380 行新增 / 199 行删除。

## 修改详情

### `build.gradle`

**修改目的**：为 `iceberg-aliyun` 模块启用 JUnit 5 测试运行平台。

**工作逻辑**：在 `project(':iceberg-aliyun')` 块内、`dependencies` 之前新增

```groovy
test {
  useJUnitPlatform()
}
```

`useJUnitPlatform()` 是 Gradle 的 JUnit 5 入口，让 `test` 任务使用 JUnit Platform 引擎而非旧的 JUnit 4 runner。这必须先于测试代码迁移配置，否则 JUnit 5 注解（`@org.junit.jupiter.api.Test` 等）不会被执行。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/AliyunOSSTestRule.java` → `AliyunOSSExtension.java`（重命名）

**修改目的**：把 OSS 测试规则的接口从 JUnit 4 `TestRule` 改造为 JUnit 5 扩展接口。

**工作逻辑**：

- 接口签名从 `interface AliyunOSSTestRule extends TestRule` 改为 `interface AliyunOSSExtension extends BeforeAllCallback, AfterAllCallback`。
- import 从 `org.junit.rules.TestRule` / `org.junit.runner.Description` / `org.junit.runners.model.Statement` 改为 `org.junit.jupiter.api.extension.AfterAllCallback` / `BeforeAllCallback` / `ExtensionContext`。
- 原 `default Statement apply(Statement base, Description description)` 通过包装 `Statement` 实现"start → base.evaluate() → stop"的环绕逻辑；新接口拆分为两个回调：
  ```java
  default void beforeAll(ExtensionContext context) throws Exception { start(); }
  default void afterAll(ExtensionContext context) throws Exception { stop(); }
  ```
  这与 JUnit 5 的扩展模型契合——`@RegisterExtension` 标记的扩展会在所有测试方法前后由 JUnit 引擎回调 `beforeAll`/`afterAll`。接口其余方法（`createOSSClient`、`testBucketName`、`keyPrefix`、`setUpBucket`、`tearDownBucket`、`start`、`stop`）保持不变。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/OSSIntegrationTestRule.java` → `OSSIntegrationExtension.java`（重命名）

**修改目的**：把对接真实阿里云 OSS 集成环境的实现类从 JUnit 4 Rule 接口迁移到新的 Extension 接口。

**工作逻辑**：仅做接口与类型名同步——`implements AliyunOSSTestRule` → `implements AliyunOSSExtension`，类名 `OSSIntegrationTestRule` → `OSSIntegrationExtension`，内部 `synchronized (OSSIntegrationTestRule.class)` 同步锁对象也同步更名。该类用于真实 OSS 端到端集成测试（通过环境变量 `ALIYUN_TEST_OSS_RULE_CLASS` 指定），逻辑未变。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockRule.java` → `AliyunOSSMockExtension.java`（重命名）

**修改目的**：把本地 mock OSS（基于 `AliyunOSSMockApp`）的实现迁移到新扩展接口。

**工作逻辑**：`implements AliyunOSSTestRule` → `implements AliyunOSSExtension`；类名、构造器、`Builder.build()` 的返回类型从 `AliyunOSSTestRule` 改为 `AliyunOSSExtension`，便于工厂方法返回新类型。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/TestUtility.java`

**修改目的**：把测试基础设施工厂方法的返回类型和反射加载的目标类改为新扩展类型。

**工作逻辑**：

- 返回类型 `AliyunOSSTestRule initialize()` → `AliyunOSSExtension initialize()`。
- 反射构造目标 `DynConstructors.builder(AliyunOSSTestRule.class)` → `DynConstructors.builder(AliyunOSSExtension.class)`。
- 默认实现 `AliyunOSSMockRule.builder().silent().build()` → `AliyunOSSMockExtension.builder().silent().build()`。
- 相关日志、异常错误信息中的类名描述同步更新为 `AliyunOSSExtension`。该工厂仍支持通过环境变量 `ALIYUN_TEST_OSS_RULE_CLASS` 注入自定义实现，但环境变量名本身未改（保持向后兼容）。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/AliyunOSSTestBase.java`

**修改目的**：把所有 OSS 测试的抽象基类从 JUnit 4 `@ClassRule` + `@Before`/`@After` 切换到 JUnit 5 `@RegisterExtension` + `@BeforeEach`/`@AfterEach`。

**工作逻辑**：

- 静态字段
  ```java
  @ClassRule public static final AliyunOSSTestRule OSS_TEST_RULE = TestUtility.initialize();
  ```
  改为
  ```java
  @RegisterExtension
  private static final AliyunOSSExtension OSS_TEST_EXTENSION = TestUtility.initialize();
  ```
  `@ClassRule`（JUnit 4）→ `@RegisterExtension`（JUnit 5），可见性从 `public` 调整为 `private`（JUnit 5 扩展字段允许 private）。该扩展会在整个测试类的所有方法执行前后触发 `beforeAll`/`afterAll`（即 start/stop OSS 服务）。
- `@Before` → `@BeforeEach`、`@After` → `@AfterEach`，方法体内对 `OSS_TEST_RULE.setUpBucket/tearDownBucket` 的调用同步改名为 `OSS_TEST_EXTENSION.*`。
- `ossClient`、`bucketName`、`keyPrefix` 三个 final 字段的提供者也改名为 `OSS_TEST_EXTENSION`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/TestAliyunClientFactories.java`

**修改目的**：迁移客户端工厂加载测试到 JUnit 5 + AssertJ。

**工作逻辑**：import `org.junit.Test` → `org.junit.jupiter.api.Test`，`org.junit.Assert` → `org.assertj.core.api.Assertions`。所有 `Assert.assertEquals(msg, expected, actual)` / `Assert.assertTrue(msg, cond)` / `Assert.assertNull(msg, val)` 改写为 AssertJ 流式 `Assertions.assertThat(actual).as(msg).isEqualTo(expected)` / `.isInstanceOf(...)` / `.isNull()`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSFileIO.java`

**修改目的**：迁移 OSS FileIO 测试到 JUnit 5 + AssertJ。

**工作逻辑**：注解 `@Before/@After` → `@BeforeEach/@AfterEach`，import 切换。断言统一改写：`Assert.assertArrayEquals(msg, expected, actual)` → `Assertions.assertThat(actual).as(msg).isEqualTo(expected)`（AssertJ 的 `isEqualTo` 对数组也按值比较），`Assert.assertTrue(msg, cond)` → `Assertions.assertThat(cond).as(msg).isTrue()`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSInputFile.java`

**修改目的**：迁移 OSS 输入文件测试到 JUnit 5 + AssertJ。

**工作逻辑**：移除 `org.junit.Assert` / `org.junit.Test`，改用 `org.junit.jupiter.api.Test` 与已有的 AssertJ。`Assert.assertFalse/assertTrue` → `.isFalse()/.isTrue()`，`Assert.assertArrayEquals` → `.isEqualTo`，`Assert.assertEquals` → `.isEqualTo`。注意此处有一处原本 `Assertions.assertThat(inputFile.exists()).as("OSS file should  exist").isTrue();`（双空格，疑似原拼写问题保留），属于迁移时顺带保留下来的细节。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSInputStream.java`

**修改目的**：迁移 OSS 输入流测试到 JUnit 5 + AssertJ。

**工作逻辑**：移除 `static import` 的 `org.junit.Assert.assertArrayEquals/assertEquals`，改用 AssertJ。`assertEquals(msg, expected, actual)` → `Assertions.assertThat(actual).as(msg).isEqualTo(expected)`。`@Test` import 切到 `org.junit.jupiter.api.Test`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSOutputFile.java`

**修改目的**：迁移 OSS 输出文件测试到 JUnit 5 + AssertJ。

**工作逻辑**：注解、import、断言的切换与上述类一致。`Assert.assertArrayEquals` → `Assertions.assertThat(actual).isEqualTo(data)`，`Assert.assertEquals(String.format(...), expected, actual)` → `Assertions.assertThat(actual).as(String.format(...)).isEqualTo(expected)`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSOutputStream.java`

**修改目的**：迁移 OSS 输出流测试到 JUnit 5 + AssertJ。

**工作逻辑**：`org.junit.Assert/Test` → AssertJ + `org.junit.jupiter.api.Test`。`Assert.assertEquals("OSSOutputStream position", expected, out.getPos())` → `Assertions.assertThat(out.getPos()).as("OSSOutputStream position").isEqualTo(expected)`；`Assert.assertArrayEquals` → `.isEqualTo`；对 staging 目录清理的断言也改为 AssertJ。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSURI.java`

**修改目的**：迁移 OSS URI 解析测试到 JUnit 5 + AssertJ。

**工作逻辑**：`org.junit.Assert/Test` → AssertJ + JUnit5 `@Test`。`Assert.assertEquals("bucket", uri.bucket())` 改为 `Assertions.assertThat(uri.bucket()).isEqualTo("bucket")`（去除冗余消息字符串，因为该测试原本就只用 expected-actual 形式）。覆盖多个 URI 解析场景（普通、URL 编码、含 fragment、含 query、多 scheme）。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/TestLocalAliyunOSS.java`

**修改目的**：迁移本地 OSS mock 端到端测试到 JUnit 5 + AssertJ，并把假设判断（assume）切到 AssertJ Assumptions。

**工作逻辑**：

- 静态字段 `@ClassRule public static final AliyunOSSTestRule OSS_TEST_RULE = TestUtility.initialize();` 改为 `@RegisterExtension private static final AliyunOSSExtension OSS_TEST_EXTENSION = TestUtility.initialize();`。
- `@Before/@After` → `@BeforeEach/@AfterEach`，方法体引用 `OSS_TEST_EXTENSION`。
- import 新增 `org.assertj.core.api.Assumptions`，移除 `org.junit.Assume`、`org.junit.Assert`、`org.junit.Before/After/ClassRule/Test`。
- `Assume.assumeTrue(msg, OSS_TEST_RULE.getClass() == AliyunOSSMockRule.class)` 改为
  ```java
  Assumptions.assumeThat(OSS_TEST_EXTENSION.getClass())
      .as(msg)
      .isEqualTo(AliyunOSSMockExtension.class);
  ```
  这把 JUnit 4 的 `assumeTrue`（条件不满足时跳过整个测试）替换为 AssertJ 的 `assumeThat`（断言失败时抛 `AssumptionException` 让 JUnit 5 标记为跳过）。该假设用于跳过"删除/重建 bucket"类测试——这些操作在真实阿里云集成环境上不可逆，只能在 mock 环境运行。
- `Assert.assertTrue/assertFalse` → `.isTrue()/.isFalse()`，`Assert.assertEquals/Assert.assertArrayEquals` → `.isEqualTo`。

## 小结

本提交把 `iceberg-aliyun` 模块的测试基础设施与用例从 JUnit 4 全面迁移到 JUnit 5（`@RegisterExtension` + `BeforeAllCallback/AfterAllCallback`）并将断言统一到 AssertJ，与 Iceberg 其余模块的测试栈对齐，便于后续维护与共享测试工具。
