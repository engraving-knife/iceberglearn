# 提交 3099：Build: Bump junit-platform from 1.14.1 to 1.14.2 (#15021)

## 提交信息

- **序号**：3099 / 4088
- **哈希**：95d7405d3c0c19f9333dd0759a1e2c49c4958d1d
- **短哈希**：95d7405d3
- **日期**：2026-01-11
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit-platform from 1.14.1 to 1.14.2 (#15021)
- **PR/Issue**：#15021

## 总体目的

该提交由 Dependabot 自动生成，将 JUnit Platform 从 1.14.1 升级到 1.14.2。JUnit Platform 是 JUnit 5 的核心基础层，提供测试引擎 SPI、启动器（Launcher）、测试套件（Suite）API 与引擎，是 Iceberg 全量单元/集成测试的运行底座。本次升级同时更新三个工件：`org.junit.platform:junit-platform-launcher`（用于程序化发现并执行测试）、`junit-platform-suite-api`（声明式测试套件注解，如 `@Suite`、`@SelectPackages`）和 `junit-platform-suite-engine`（运行套件的引擎）。它们共享 `gradle/libs.versions.toml` 中的 `junit-platform` 版本变量。

版本号从 1.14.1 升级到 1.14.2，属于语义版本中的 patch 级别升级（`version-update:semver-patch`）。根据提交元数据，三个依赖均归类为 `direct:production`。patch 升级通常只包含 bug 修复与小的稳定性改进，预期对测试发现、套件编排与执行行为无破坏性变更，主要价值是修复 Platform 自身在特定场景下的缺陷，使测试运行更稳定可靠。需注意：JUnit Platform 与 JUnit Jupiter（jupiter 工件）有版本配套关系，Platform 1.14.x 与 Jupiter 5.14.x 配套；本提交先于 Jupiter 的升级（见后续提交 3103），二者最终会在同一次发布窗口内对齐到 1.14.2 / 5.14.2。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中的 `junit-platform` 版本变量，从 `1.14.1` 改为 `1.14.2`。三个 Platform 工件通过 `version.ref = "junit-platform"` 引用该变量，单点修改即可同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 junit-platform 版本变量，同步更新三个 Platform 工件。

**工作逻辑**：
将 `junit-platform = "1.14.1"` 修改为 `junit-platform = "1.14.2"`。该变量被 `junit-platform-launcher`、`junit-platform-suite-api`、`junit-platform-suite-engine` 三个坐标通过 `version.ref` 引用。升级后，测试运行时使用的 Launcher / Suite API / Suite 引擎统一对齐到 1.14.2。文件中同区的 `junit = "5.14.1"`（Jupiter）此刻仍为旧值，会在后续提交 3103 中同步升级到 5.14.2 以保持 Platform/Jupiter 配套一致。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 JUnit Platform 从 1.14.1 提升到 1.14.2（patch 级别），同步更新 Launcher、Suite API 与 Suite 引擎三个工件。Platform 是 Iceberg 测试运行的底座。作为 patch 升级，预期仅含 bug 修复，无破坏性变更，使测试执行更稳定；与后续 Jupiter 升级（3103）配套对齐到 1.14.2 / 5.14.2。
