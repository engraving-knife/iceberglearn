# 提交 2194：Build, Core: Move assertions to AssertJ / Fix checkstyle rules (#13213)

## 提交信息

- **序号**：2194 / 4088
- **哈希**：1d8b91a345175ab09a69cf0ea8d74c6cb297ee87
- **短哈希**：1d8b91a34
- **日期**：2025-06-02 22:45:57 -0700
- **作者**：Prashant Singh
- **提交说明**：Build, Core: Move assertions to AssertJ / Fix checkstyle rules (#13213)
- **PR/Issue**：#13213

## 总体目的

这个提交的目的是将测试中的 JUnit5 原生断言迁移到 AssertJ 风格的断言，并修复 checkstyle 规则中相关的配置问题。Iceberg 项目在代码规范上偏好使用 AssertJ 的流式断言 API，但在 checkstyle 配置中使用 `illegalPkgs` 属性来禁止 JUnit5 的 `Assertions` 和 `Assumptions` 类导入，然而 `illegalPkgs` 是按包名（package）匹配的，这会导致对整个 `org.junit.jupiter.api` 包下所有类的导入都被禁止，而非仅针对特定类。本提交将 checkstyle 规则从 `illegalPkgs` 改为 `illegalClasses`，实现更精确的类级别禁止，同时将 TestRESTCatalog 中残留的 JUnit5 `Assertions.assertThrows` 调用改为 AssertJ 的 `assertThatThrownBy` 风格。

## 如何达成设计目的

- 修改 checkstyle 配置文件，将 `BanJUnit5AssumptionsUsage` 和 `BanJUnit5Assertions` 两个规则模块的属性从 `illegalPkgs`（按包名禁止）改为 `illegalClasses`（按类全限定名禁止），这样只禁止特定类的导入而非整个包。
- 在 `TestRESTCatalog` 中，移除 `org.junit.jupiter.api.Assertions` 的导入，将 `testErrorHandlingForConflicts` 测试方法中的 `Assertions.assertThrows(...)` 调用替换为 AssertJ 的 `assertThatThrownBy(...)` 链式断言，并增加对异常消息内容的验证。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (修改, +2/-2 lines)

**修改目的**：修正 checkstyle 规则，使其精确禁止特定 JUnit5 类的导入而非整个包。

**工作逻辑**：
- 对于 `BanJUnit5AssumptionsUsage` 模块，将 `<property name="illegalPkgs" value="org.junit.jupiter.api.Assumptions"/>` 改为 `<property name="illegalClasses" value="org.junit.jupiter.api.Assumptions"/>`。
- 对于 `BanJUnit5Assertions` 模块，将 `<property name="illegalPkgs" value="org.junit.jupiter.api.Assertions"/>` 改为 `<property name="illegalClasses" value="org.junit.jupiter.api.Assertions"/>`。
- `illegalClasses` 是 Checkstyle IllegalImport 模块支持的属性，按类的全限定名精确匹配，避免误禁止同包下其他合法类的导入。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (修改, +7/-7 lines)

**修改目的**：将测试中的 JUnit5 断言迁移为 AssertJ 风格，符合项目规范。

**工作逻辑**：
- 移除 `import org.junit.jupiter.api.Assertions;` 导入。
- 在 `testErrorHandlingForConflicts` 方法中，将两处 `Assertions.assertThrows(ExceptionClass.class, () -> ...)` 替换为 `assertThatThrownBy(() -> ...).hasMessageContaining("...").isInstanceOf(ExceptionClass.class)`：
  - 第一处：验证 409 无重试时抛出 `CommitFailedException`，并断言消息包含 "Commit failed"。
  - 第二处：验证 409 有重试时抛出 `CommitStateUnknownException`，并断言消息包含 "Commit status unknown"。
- 相比原来的 `assertThrows`，新写法额外验证了异常消息内容，增强了测试的断言强度。

## 总结

该提交做了两件事：一是修正 checkstyle 规则配置，将包级别的导入禁止改为类级别的精确禁止（`illegalPkgs` → `illegalClasses`），避免过度限制；二是将 TestRESTCatalog 中残留的 JUnit5 原生断言迁移到项目偏好的 AssertJ 风格，并顺带增强了异常消息的断言验证。属于代码规范和测试质量的小幅改进。
