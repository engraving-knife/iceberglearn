# 提交 2424：Build: Bump software.amazon.awssdk:bom from 2.31.78 to 2.32.9 (#13687)

## 提交信息

- **序号**：2424 / 4088
- **哈希**：57dbed55714c222a7dbbc1f13a30d52774029c5d
- **短哈希**：57dbed557
- **日期**：2025-07-28 09:18:49 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.31.78 to 2.32.9 (#13687)
- **PR/Issue**：#13687

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）从 2.31.78 升级到 2.32.9。

AWS SDK for Java 2.x 是 Iceberg 项目用于与 AWS 服务（特别是 S3 存储）交互的核心依赖。BOM（Bill of Materials）是一种特殊的 POM，用于集中管理 AWS SDK 所有组件的版本号，确保各组件版本兼容。Iceberg 通过引入 BOM 来管理 S3、DynamoDB、KMS 等 AWS 服务客户端的版本。

此次升级为 semver minor 版本升级（2.31.78 → 2.32.9），跨越了多个版本，可能包含新功能、性能改进、bug 修复和安全补丁。这对于 Iceberg 的 AWS 集成模块（`aws/` 子项目）的稳定性和功能完整性很重要。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中 AWS SDK BOM 的版本定义，将其更新为新版本。由于使用 BOM 管理依赖，一次版本更新即可覆盖所有 AWS SDK 组件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将版本目录中 AWS SDK BOM 的版本号从 `2.31.78` 更新为 `2.32.9`。

## 总结

这是一个常规的依赖升级提交，将 AWS SDK for Java 2.x BOM 从 2.31.78 升级到 2.32.9。由于 AWS SDK 是 Iceberg 与 S3 等云存储交互的核心依赖，保持其最新版本对于功能完整性、性能和安全性都很重要。
