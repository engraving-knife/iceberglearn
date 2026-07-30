# 提交 2794：Build: Bump software.amazon.awssdk:bom from 2.35.10 to 2.36.2 (#14420)

## 提交信息

- **序号**：2794 / 4088
- **哈希**：6ffc8a9658762970b6cdb15a784d272d55a22950
- **短哈希**：6ffc8a965
- **日期**：2025-10-26 00:05:14 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.35.10 to 2.36.2 (#14420)
- **PR/Issue**：#14420

## 总体目的

本提交由 dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.35.10 升级到 2.36.2。

AWS SDK for Java 是 Iceberg 项目访问 AWS 云存储服务（如 S3、DynamoDB 等）的核心依赖。BOM（Bill of Materials）是一种 POM 文件，用于统一管理 AWS SDK 各组件的版本，确保各组件之间版本兼容。Iceberg 使用 `awssdk-bom` 来管理所有 AWS SDK 相关依赖的版本。

这是一个 semver-minor 级别升级（2.35.10 → 2.36.2），跨越了一个 minor 版本（2.35 → 2.36），通常包含新功能、改进和 bug 修复，向后兼容。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `awssdk-bom` 的版本号从 `2.35.10` 更新为 `2.36.2`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将版本目录中 `awssdk-bom = "2.35.10"` 修改为 `awssdk-bom = "2.36.2"`。所有引用 AWS SDK BOM 的组件（如 S3 客户端、DynamoDB 客户端等）将统一升级到 2.36.2 版本，确保各 AWS SDK 组件之间的版本一致性。

## 总结

本提交是依赖升级，将 AWS SDK for Java BOM 从 2.35.10 升级到 2.36.2。作为 semver-minor 升级，预期包含新功能和改进，保持 Iceberg 与最新 AWS SDK 的兼容性。由于 Iceberg 广泛使用 AWS 服务（特别是 S3 存储），保持 AWS SDK 的最新版本对稳定性和功能支持至关重要。
