# 提交 2425：Build: Bump junit-platform from 1.13.2 to 1.13.4 (#13682)

## 提交信息

- **序号**：2425 / 4088
- **哈希**：ce63292ebad9cfdcff3f2a8ab90bbf505302bbe1
- **短哈希**：ce63292eb
- **日期**：2025-07-28 10:17:41 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit-platform from 1.13.2 to 1.13.4 (#13682)
- **PR/Issue**：#13682

## 总体目的

本提交由 Dependabot 自动生成，将 JUnit Platform 从 1.13.2 升级到 1.13.4。

JUnit Platform 是 JUnit 5 的基础框架层，提供了测试引擎的 SPI（Service Provider Interface）和运行测试的基础设施。此次升级涉及三个 JUnit Platform 组件：
- `org.junit.platform:junit-platform-launcher`：用于以编程方式启动测试
- `org.junit.platform:junit-platform-suite-api`：用于定义测试套件的 API
- `org.junit.platform:junit-platform-suite-engine`：测试套件引擎

这是一个 semver patch 版本升级（1.13.2 → 1.13.4），与提交 2421 中的 JUnit Jupiter 升级（5.13.2 → 5.13.4）配套，因为 JUnit Platform 和 JUnit Jupiter 的版本号通常保持同步。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中 JUnit Platform 的版本定义，将其更新为新版本。由于三个组件共用同一个版本引用，一次更新即覆盖全部。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 JUnit Platform 版本。

**工作逻辑**：将版本目录中 JUnit Platform 的版本号从 `1.13.2` 更新为 `1.13.4`，自动覆盖 `junit-platform-launcher`、`junit-platform-suite-api` 和 `junit-platform-suite-engine` 三个组件。

## 总结

这是一个常规的依赖升级提交，将 JUnit Platform 从 1.13.2 升级到 1.13.4，与 JUnit Jupiter 的升级配套，确保测试框架各组件版本一致性和最新的 bug 修复。
