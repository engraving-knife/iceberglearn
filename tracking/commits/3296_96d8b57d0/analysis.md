# 提交 3296：Build: Bump junit-platform from 1.14.2 to 1.14.3 (#15400)

## 提交信息

- **序号**：3296 / 4088
- **哈希**：96d8b57d0b78e59ca8013e797d3fcd62c3eb9a9b
- **短哈希**：96d8b57d0
- **日期**：2026-02-22
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit-platform from 1.14.2 to 1.14.3 (#15400)
- **PR/Issue**：#15400

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，目的是将 JUnit Platform 从 1.14.2 升级到 1.14.3。JUnit Platform 是 JUnit 5 测试框架的基础平台层，提供测试发现、执行和报告的核心 API，包含 `junit-platform-launcher`（用于程序化启动测试）、`junit-platform-suite-api`（声明式测试套件 API）以及 `junit-platform-suite-engine`（测试套件引擎）三个组件。

Iceberg 作为一个大型 Java 项目，测试覆盖面极广（core、spark、flink 等多个模块均依赖 JUnit 进行单元测试与集成测试）。保持测试基础设施在最新的补丁版本上，可以获取上游的 bug 修复与小幅改进，确保测试套件运行的稳定性和可靠性。由于本次升级为补丁版本（semver-patch），按照语义化版本约定不包含破坏性变更，预期对现有测试代码无任何影响。

Dependabot 在该提交说明中标注了三个依赖项的 `update-type` 均为 `version-update:semver-patch`，`dependency-type` 为 `direct:production`，说明这些是直接用于生产构建链路（含测试编译与执行）的依赖。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的版本变量声明，将 `junit-platform` 的版本号从 `1.14.2` 提升至 `1.14.3`。由于项目中所有 junit-platform 相关组件都通过该版本变量（`version.ref`）引用，单一变量修改即可同步升级 launcher、suite-api、suite-engine 三个制品，无需逐一修改各模块的依赖声明。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 junit-platform 版本变量。

**工作逻辑**：
将第 71 行的版本声明从 `junit-platform = "1.14.2"` 改为 `junit-platform = "1.14.3"`。该变量在文件后续的 `[libraries]` 段中被 `junit-platform-launcher`、`junit-platform-suite-api`、`junit-platform-suite-engine` 等库声明通过 `version.ref = "junit-platform"` 引用，因此一处修改即同步升级全部关联制品。这是一种集中式版本管理方式，避免了版本号散落在多个构建文件中难以维护的问题。

## 总结

本次提交是低风险的测试基础设施维护，通过版本目录统一升级 JUnit Platform 至最新补丁版本，获取上游 bug 修复，保持测试运行环境的稳定性。该升级属于 semver-patch 级别，无破坏性变更，体现了项目对依赖健康度的持续关注。
