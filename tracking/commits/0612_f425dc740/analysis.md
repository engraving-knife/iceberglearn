# 提交 0612：AWS, Core: Replace .withFailMessage() usage with .as()

## 提交信息

- **序号**：0612 / 4088
- **哈希**：f425dc7401b268f92778c3c9b2c6abfc7fb661df
- **短哈希**：f425dc740
- **日期**：2024-03-19（Tue Mar 19 17:05:41 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：AWS, Core: Replace .withFailMessage() usage with .as() (#10000)
- **PR/Issue**：#10000

## 总体目的

本提交的目标是改进测试断言失败时的诊断信息质量。提交说明明确阐述了动机：

> "We almost never want to use `.withFailMessage()` as that will override any enriched contextual information that AssertJ generally provides about actual/expected. It's better to use `.as()` to add some description to the check being done, which allows to still show contextual information about actual/expected if the assertion ever fails."

简而言之，这是对 AssertJ 断言 API 用法的规范化整改。Iceberg 的测试代码中此前广泛使用 `.withFailMessage("...")` 来为断言附加自定义失败信息，但这种用法会**覆盖（override）** AssertJ 默认的错误输出，导致断言失败时丢失对调试至关重要的"实际值/期望值"上下文。本次提交将所有此类用法替换为 `.as("...")`，后者只是**附加描述**，断言失败时既能看到自定义描述，也能看到 AssertJ 自动生成的实际值/期望值对比。

### 两个 API 的关键差异

- **`.withFailMessage(msg)`**（AssertJ 中等同于 `overridingErrorMessage(msg)`）：当断言失败时，**完全替换**默认错误信息为 `msg`，AssertJ 不再打印 actual/expected。这意味着若断言为 `assertThat(region.id()).withFailMessage("region should match").isEqualTo("us-east-1")`，失败时只会看到 `"region should match"`，看不到 region 实际是什么值。
- **`.as(msg)`**（等同于 `describedAs(msg)`）：把 `msg` 作为断言的**描述前缀**，失败时输出形如 `"[region should match] expected:<[us-east-1]> but was:<[us-west-2]>"`，既保留了自定义描述，又暴露了 actual/expected，便于快速定位问题。

由于 `.withFailMessage()` 在绝大多数测试场景下都是反模式（除非有意隐藏 actual/expected 以做模糊断言），作者选择批量替换为 `.as()`，以提升测试失败时的可诊断性。

## 如何达成设计目的

作者采用"机械式批量替换 + 顺手改进断言惯用法"的策略，覆盖 `aws` 模块 4 个测试类与 `core` 模块 1 个测试类（`CatalogTests`，这是所有 Catalog 实现的共享基类测试）。整体实现路径如下：

1. **逐处将 `.withFailMessage("...")` 替换为 `.as("...")`**：保持参数字符串不变，仅替换方法名。这是改动的主体，涉及所有 5 个文件。
2. **顺手把 `instanceof X + .isTrue()` 的反模式重构为 `.isInstanceOf(X.class)`**：在 `AwsClientPropertiesTest` 与 `HttpClientPropertiesTest` 中，原代码形如 `assertThat(credential instanceof DefaultCredentialsProvider).withFailMessage(...).isTrue()`，重构为 `assertThat(credential).as(...).isInstanceOf(DefaultCredentialsProvider.class)`。这是更地道的 AssertJ 用法：`isInstanceOf` 在失败时会打印实际对象的类型，而 `isTrue()` 配合 `instanceof` 布尔表达式只会告诉你"不是 true"，信息量更少。
3. **保持所有断言的语义不变**：不修改被测代码、不调整测试用例逻辑，仅做断言 API 用法的等价改写。

这种"只动测试不动生产代码"的改动天然风险可控，且让未来的断言失败排查更高效。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/AwsClientPropertiesTest.java`

**修改目的**：将 6 处 `.withFailMessage()` 替换为 `.as()`，并把 3 处 `instanceof + isTrue()` 重构为 `isInstanceOf()`。

**工作逻辑**：

文件改动涉及多个测试方法，分两类：

1. **纯方法名替换**（如 `testRegion()`、`testS3FileIOAwsClientFactory()` 等）：
   ```java
   // 改动前
   Assertions.assertThat(region.id())
       .withFailMessage("region parameter should match what is set in CLIENT_REGION")
       .isEqualTo("us-east-1");
   // 改动后
   Assertions.assertThat(region.id())
       .as("region parameter should match what is set in CLIENT_REGION")
       .isEqualTo("us-east-1");
   ```
   此处 `region.id()` 的实际值若不等于 `"us-east-1"`，原写法只会输出 `"region parameter should match what is set in CLIENT_REGION"`；新写法会输出类似 `"[region parameter should match what is set in CLIENT_REGION] expected:<"us-east-1"> but was:<"us-west-2">"`，调试价值显著提升。

   类似的纯替换还出现在 `testCredentialsProviderCreatesNewInstance()`（`isNotSameAs` 断言）、`testBasicCredentials`（accessKeyId/secretAccessKey 的 `isEqualTo` 断言）、`testSessionCredentials`（session credentials 的多个断言）。

2. **`instanceof` 重构为 `isInstanceOf`**（`testDefaultCredentials`、`testBasicCredentials`、`testSessionCredentials`）：
   ```java
   // 改动前
   Assertions.assertThat(credentialsProvider instanceof DefaultCredentialsProvider)
       .withFailMessage("Should use default credentials if nothing is set")
       .isTrue();
   // 改动后
   Assertions.assertThat(credentialsProvider)
       .as("Should use default credentials if nothing is set")
       .isInstanceOf(DefaultCredentialsProvider.class);
   ```
   原写法把 `instanceof` 表达式结果作为布尔值断言，失败时只能得知"不是 true"；新写法直接对 `credentialsProvider` 断言其类型，失败时会打印实际类型（如 `<DefaultCredentialsProvider>` vs `<AwsBasicCredentials>`），与 `.as()` 描述叠加后诊断信息最丰富。`testBasicCredentials` 中对 `AwsBasicCredentials`、`testSessionCredentials` 中对 `AwsSessionCredentials` 也做了同样重构。

### `aws/src/test/java/org/apache/iceberg/aws/HttpClientPropertiesTest.java`

**修改目的**：将 2 处 `instanceof + .isTrue() + .withFailMessage()` 重构为 `.isInstanceOf() + .as()`。

**工作逻辑**：

涉及 `testHttpClientUrlConnection()`（断言使用 `UrlConnectionHttpClient.Builder`）与 `testHttpClientApache()`（断言使用 `ApacheHttpClient.Builder`）两个测试方法。两处都遵循同一模式：原代码断言 `capturedHttpClientBuilder instanceof XxxBuilder` 为 true，重构为对 `capturedHttpClientBuilder` 断言 `isInstanceOf(XxxBuilder.class)`，并把 `.withFailMessage` 换成 `.as`。失败时能直接看到捕获到的 builder 实际类型，便于排查 S3 客户端 HTTP 配置错误。

### `aws/src/test/java/org/apache/iceberg/aws/TestS3FileIOAwsClientFactories.java`

**修改目的**：将 2 处 `.withFailMessage()` 替换为 `.as()`，纯方法名替换，无逻辑变化。

**工作逻辑**：

涉及两个测试方法，分别验证 `s3.client-factory-impl` 属性设置与不设置时工厂类的实例化类型。改动仅是把多行的 `.withFailMessage(` 换成 `.as(`，描述字符串与 `isInstanceOf` 断言均保留。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOProperties.java`

**修改目的**：将 3 处 `.withFailMessage()` 替换为 `.as()`，纯方法名替换。

**工作逻辑**：

位于一个验证 S3 配置（path style access、use arn region、accelerate mode）的测试方法中。三处断言分别检查 `pathStyleAccessEnabled()`、`useArnRegionEnabled()`、`accelerateModeEnabled()` 三个布尔属性，前两处期望 `true`，第三处期望 `false`。改动仅替换方法名，让断言失败时仍能看到实际布尔值。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：将 namespace 相关测试方法中的 8 处 `.withFailMessage()` 替换为 `.as()`，并对最后一段做格式合并。

**工作逻辑**：

`CatalogTests` 是一个抽象基类，定义了所有 Catalog 实现必须通过的共享测试契约（被 `JdbcCatalog`、`HiveCatalog`、`RESTCatalog`、`GlueCatalog` 等的具体测试类继承）。因此此处的改动影响面广——任何 Catalog 实现的 namespace 行为若回归，错误信息都会更清晰。

改动集中在 `createNamespace` 与 `dropNamespace` 相关的两个测试方法（约 380-449 行），覆盖：

- 创建 ns1 后列出 namespaces 应包含 ns1；
- 创建 ns2 后列出 namespaces 应同时包含 ns1、ns2；
- 删除 ns1 后列表只剩 ns2；
- 创建 parent namespace、列出 parent 应为空；
- 创建 child1、child2 后 parent 下应包含两个子 namespace；
- 删除 child1、child2 后 parent 下应再次为空。

所有断言都是 `hasSameElementsAs(...)` 或 `isEmpty()`，原写法用 `.withFailMessage` 覆盖默认信息，重构后用 `.as` 附加描述。最后一处 `catalog.listNamespaces(parent)` 的 `isEmpty()` 断言被合并为单行 `Assertions.assertThat(...).as("Should be empty").isEmpty();`，纯属代码风格统一。

## 小结

本提交是 Iceberg 测试基础设施的一次质量改进，将 5 个测试类中所有 `.withFailMessage()` 用法替换为 `.as()`，并顺手把 `instanceof + isTrue()` 反模式重构为更地道的 `isInstanceOf()`。

- **影响范围**：仅测试代码（aws 模块 4 个文件 + core 模块 `CatalogTests` 基类），不触及生产代码，无运行时行为变化。
- **成效**：未来这些测试用例失败时，错误输出会同时包含自定义描述与 AssertJ 自动生成的 actual/expected 对比，显著降低排查成本。由于 `CatalogTests` 是共享基类，所有 Catalog 实现的 namespace 测试都受益。
- **回迁到 1.4.x 的注意事项**：本提交是纯测试改动，回迁风险低。但需注意 1.4.x 分支的这些测试文件可能已有其他改动（如不同的 namespace 测试用例或额外的 `.withFailMessage` 用法），cherry-pick 时可能需要在冲突处手工延续"用 `.as()` 而非 `.withFailMessage()`"的约定。建议回迁后顺手在 1.4.x 全量检索 `withFailMessage`，确认是否还有遗漏的同类反模式可一并清理。
