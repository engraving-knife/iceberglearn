# 提交 0436：Core: Add missing @Test to TestRESTCatalog (#9607)

## 提交信息

- **序号**：0436
- **哈希**：a05cbcb4b8b531c94d5f35d5abcebf91a5da5187
- **短哈希**：a05cbcb4b
- **日期**：2024-02-01 09:53:10 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Add missing @Test to TestRESTCatalog (#9607)
- **PR/Issue**：#9607

## 总体目的

这是一个极小但重要的测试修复提交。`TestRESTCatalog` 中的测试方法 `testCatalogTokenRefreshDisabledWithToken` 缺少 `@Test` 注解。在 JUnit5（JUnit Jupiter）下，测试方法必须显式标注 `@Test` 才会被测试引擎发现并执行——这一点与 JUnit4 一致。缺失注解意味着该方法会被静默跳过：编译能通过、不会报错，但该测试用例从未真正运行过，形成"测试覆盖率假象"。

该方法验证的是"在 token 刷新被禁用、且初始已持有 token 的场景下，REST Catalog 的认证 token 刷新行为"。这是 REST Catalog 安全/认证链路的关键场景。缺少 `@Test` 使这一关键路径长期未被验证，潜在缺陷无法被测试捕获。本提交仅补上注解，让该用例回归测试执行流。

## 如何达成设计目的

仅需在目标方法上方添加 `@Test`（`org.junit.jupiter.api.Test`）注解一行。无其他改动。这使 JUnit5 引擎在执行 `TestRESTCatalog` 时把该方法识别为测试用例并执行。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：为 `testCatalogTokenRefreshDisabledWithToken` 方法补上缺失的 `@Test` 注解。

**工作逻辑**：在位于第 1851 行之后、`public void testCatalogTokenRefreshDisabledWithToken()` 方法声明之前，新增一行 `@Test`。方法本身的方法体（构造 catalogHeaders、设置 token、断言刷新被禁用时的行为）未改动。补注解后该方法将随测试套件正常执行。

## 小结

本提交是典型的"低代码量、高价值"测试修复。它揭示了一个易被忽视的风险点：缺少测试注解不会导致编译失败，只会让用例静默失效，从而产生虚假的安全感。这也提示在 JUnit4→5 迁移或新增测试时，应核查注解是否齐全。该提交模式简单，但对保障 REST Catalog 认证刷新场景的真实测试覆盖有实质意义。
