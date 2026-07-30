# 提交 2421：Build: Bump junit from 5.13.2 to 5.13.4 (#13684)

## 提交信息

- **序号**：2421 / 4088
- **哈希**：62cc9525a6379f8c5b046b95c2b86d02d515a219
- **短哈希**：62cc9525a
- **日期**：2025-07-28 09:03:06 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit from 5.13.2 to 5.13.4 (#13684)
- **PR/Issue**：#13684

## 总体目的

本提交由 Dependabot 自动生成，将 JUnit 5 从 5.13.2 升级到 5.13.4。

JUnit 5（JUnit Jupiter）是 Iceberg 项目使用的 Java 测试框架。此次升级涉及两个 JUnit 组件：
- `org.junit.jupiter:junit-jupiter`：JUnit 5 的聚合依赖，包含 JUnit Jupiter API 和 Engine
- `org.junit.jupiter:junit-jupiter-engine`：JUnit 5 的测试引擎

这是一个 semver patch 版本升级（5.13.2 → 5.13.4），主要包含 bug 修复和稳定性改进。保持测试框架的最新版本有助于获得最新的修复和改进测试体验。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中 JUnit 的版本定义，将其更新为新版本。Iceberg 使用 Gradle 版本目录（version catalog）来集中管理依赖版本，所有子项目通过引用版本目录中的版本号来使用 JUnit。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 JUnit 5 版本。

**工作逻辑**：将版本目录中 JUnit 的版本号从 `5.13.2` 更新为 `5.13.4`。由于 `junit-jupiter` 和 `junit-jupiter-engine` 共用同一个版本引用，一次更新即覆盖两个组件。

## 总结

这是一个常规的依赖升级提交，将测试框架 JUnit 5 从 5.13.2 升级到 5.13.4，获取最新的 bug 修复和稳定性改进。
