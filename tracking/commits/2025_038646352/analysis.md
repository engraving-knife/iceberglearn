# 提交 2025：Core: Use assumeThat instead of assumeTrue (#12822)

## 提交信息

- **序号**：2025 / 4088
- **哈希**：03864635296c7e7507919d5f1e0514594555fb61
- **短哈希**：038646352
- **日期**：2025-04-22 12:02:05 +0200
- **作者**：slfan1989
- **提交说明**：Core: Use assumeThat instead of assumeTrue (#12822)
- **PR/Issue**：#12822

## 总体目的

这个提交将测试代码中的 JUnit 5 `Assumptions.assumeTrue()` 替换为 AssertJ 的 `Assumptions.assumeThat().isTrue()`，并在 checkstyle 规则中禁止使用 `org.junit.jupiter.api.Assumptions` 包，统一测试假设（assumption）的写法为 AssertJ 风格。

Iceberg 项目在测试中已经广泛使用 AssertJ（`assertThat`）替代 JUnit 的 `Assert.assertEquals` 等，以获得更流畅的断言语法和更丰富的错误信息。但对于测试假设（conditional test execution），仍在使用 JUnit 5 的 `Assumptions.assumeTrue()`。本提交将假设也统一为 AssertJ 风格的 `assumeThat()`，保持测试代码风格的一致性。

## 如何达成设计目的

1. 在 checkstyle 配置中新增 `IllegalImport` 模块，禁止导入 `org.junit.jupiter.api.Assumptions` 包，提示使用 `Assertions.assumeThat(...).isTrue()`。
2. 在 `CatalogTests.java` 和 `TestEnvironmentUtil.java` 中将所有 `Assumptions.assumeTrue(...)` 调用替换为 `assumeThat(...).isTrue()`（或 `.isPresent()` 等）。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (修改, +5/-0 lines)

**修改目的**：禁止使用 JUnit 5 Assumptions，强制使用 AssertJ 的 assumeThat。

**工作逻辑**：
新增 `IllegalImport` 模块，id 为 `BanJUnit5AssumptionsUsage`，illegalPkgs 为 `org.junit.jupiter.api.Assumptions`，错误消息为"Prefer using Assertions.assumeThat(...).isTrue() instead."。这确保未来不会有新的代码使用 JUnit 5 的 Assumptions。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +28/-29 lines)

**修改目的**：将所有 `Assumptions.assumeTrue` 替换为 `assumeThat().isTrue()`。

**工作逻辑**：
在多个测试方法中（如 `testCreateNamespaceWithProperties`、`testSetNamespaceProperties`、`testListNestedNamespaces`、`testNamespaceWithSlash`、`testTableNameWithSlash` 等），将 `Assumptions.assumeTrue(condition)` 替换为 `assumeThat(condition).isTrue()`，将 `Assumptions.assumeTrue(condition, "description")` 替换为 `assumeThat(condition).as("description").isTrue()`。移除 `org.junit.jupiter.api.Assumptions` 的 import，新增 `org.assertj.core.api.Assumptions.assumeThat` 的静态 import。

### `core/src/test/java/org/apache/iceberg/util/TestEnvironmentUtil.java` (修改, +3/-2 lines)

**修改目的**：将 `Assumptions.assumeTrue` 替换为 `assumeThat().isPresent()`。

**工作逻辑**：
在 `testEnvironmentSubstitution` 方法中，将 `Assumptions.assumeTrue(envEntry.isPresent(), "Expecting at least one env. variable to be present")` 替换为 `assumeThat(envEntry).as("Expecting at least one env. variable to be present").isPresent()`。移除 `Assumptions` import，新增 `assumeThat` 静态 import。

## 总结

本提交是测试代码风格统一重构，将 JUnit 5 的 `Assumptions.assumeTrue()` 替换为 AssertJ 的 `assumeThat()` 风格，并通过 checkstyle 规则禁止后续使用 JUnit 5 Assumptions。这使得测试断言和假设都统一使用 AssertJ API，代码风格更一致。
