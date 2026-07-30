# 提交 3103：Build: Bump junit from 5.14.1 to 5.14.2 (#15023)

## 提交信息

- **序号**：3103 / 4088
- **哈希**：b7d9817cc97fb7b3c11bd105a91f1550b05dc63e
- **短哈希**：b7d9817cc
- **日期**：2026-01-11
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit from 5.14.1 to 5.14.2 (#15023)
- **PR/Issue**：#15023

## 总体目的

该提交由 Dependabot 自动生成，将 JUnit Jupiter（JUnit 5）从 5.14.1 升级到 5.14.2。JUnit Jupiter 是 Iceberg 全量单元测试与集成测试使用的测试框架，提供 `@Test`、`@ParameterizedTest`、`@BeforeEach`、`Assertions` 等编程与扩展模型。本次升级同时更新两个工件：`org.junit.jupiter:junit-jupiter`（聚合工件，含 API 与引擎）与 `org.junit.jupiter:junit-jupiter-engine`（运行引擎）。二者共享 `gradle/libs.versions.toml` 中的 `junit` 版本变量。

版本号从 5.14.1 升级到 5.14.2，属于语义版本中的 patch 级别升级（`version-update:semver-patch`）。根据提交元数据，两个依赖均归类为 `direct:production`。patch 升级通常包含测试执行引擎、参数化测试、断言与生命周期回调的 bug 修复，不引入 API 破坏性变更。对 Iceberg 而言，预期影响是：测试用例的编写与执行行为保持一致，但可能修复断言失败信息、参数解析或并发测试执行场景下的缺陷，使测试结果更可靠。本提交是先前提交 3099（JUnit Platform 升至 1.14.2）的配套——Jupiter 5.14.2 与 Platform 1.14.2 在 JUnit 5 中按配套版本发布，二者需对齐以避免版本不匹配导致的运行时问题。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中的 `junit` 版本变量，从 `5.14.1` 改为 `5.14.2`。两个 Jupiter 工件通过 `version.ref = "junit"` 引用该变量，单点修改即可同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 junit (Jupiter) 版本变量，同步更新聚合工件与引擎。

**工作逻辑**：
将 `junit = "5.14.1"` 修改为 `junit = "5.14.2"`。该变量被 `junit-jupiter` 与 `junit-jupiter-engine` 两个坐标通过 `version.ref` 引用。升级后，所有 Iceberg 测试模块使用的 Jupiter API 与引擎统一对齐到 5.14.2，与同窗口内已升级的 `junit-platform = "1.14.2"`（见提交 3099）配套一致，避免 Platform/Jupiter 版本错位。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 JUnit Jupiter 从 5.14.1 提升到 5.14.2（patch 级别），同步更新 `junit-jupiter` 聚合工件与 `junit-jupiter-engine`。Jupiter 是 Iceberg 测试框架。作为 patch 升级，预期仅含 bug 修复，无破坏性变更；本提交与先前 Platform 升级（3099）配套，保持 Platform 1.14.2 / Jupiter 5.14.2 版本对齐。
