# 提交 3562：Build: Bump software.amazon.awssdk:bom from 2.42.28 to 2.42.33 (#16040)

## 提交信息

- **序号**：3562 / 4088
- **哈希**：49839545d0027f17970868c5044f8005abce1267
- **短哈希**：49839545d
- **日期**：2026-04-19 10:21:37 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.42.28 to 2.42.33 (#16040)
- **PR/Issue**：#16040

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从版本 2.42.28 升级到 2.42.33。AWS SDK BOM 用于统一管理 AWS 相关依赖的版本，确保各 AWS 模块版本兼容。Iceberg 在 S3、DynamoDB、Glue 等 AWS 服务集成中使用该 SDK。这是一个 semver-patch（补丁版本）升级，包含跨 5 个补丁版本的累积更新。

## 如何达成设计目的

Dependabot 自动扫描项目依赖，发现 `gradle/libs.versions.toml` 中定义的 `awssdk-bom` 版本有新补丁版本可用，自动创建 PR 进行升级。升级仅修改版本号声明，不涉及代码逻辑变更。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 AWS SDK BOM 版本声明。

**工作逻辑**：
将 `awssdk-bom` 的版本从 `2.42.28` 升级到 `2.42.33`。该版本声明通过 Gradle 版本目录机制被项目中所有 AWS SDK 模块（如 S3、DynamoDB、Glue、STS 等）引用，升级后所有依赖该 BOM 的 AWS SDK 组件版本会统一更新到 2.42.33 对应的兼容版本，获取该版本范围内的 bug 修复和安全改进。

## 总结

这是一个常规的依赖维护提交，通过补丁版本升级保持 AWS SDK 依赖的最新状态。由于跨越了 5 个补丁版本（2.42.28 到 2.42.33），可能包含多个 bug 修复和安全补丁，对 Iceberg 的 AWS 集成模块（S3、Glue、DynamoDB 等）较为重要。
