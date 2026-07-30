# 提交 2668：Build: Bump software.amazon.awssdk:bom from 2.33.9 to 2.34.0 (#14133)

## 提交信息

- **序号**：2668 / 4088
- **哈希**：91f457e0cde5d6e51c23f8df73fe3f4cf2c371c8
- **短哈希**：91f457e0c
- **日期**：2025-09-20 22:31:58 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.33.9 to 2.34.0 (#14133)
- **PR/Issue**：#14133

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.33.9 升级到 2.34.0。这是一个次版本（minor version）升级，属于 `version-update:semver-minor` 类型。

AWS SDK BOM 用于统一管理所有 AWS SDK 依赖模块的版本。Iceberg 的 AWS 模块（`iceberg-aws`）依赖 AWS SDK 提供 S3、DynamoDB、STS 等服务的客户端。通过 BOM 方式管理版本，可以确保所有 AWS SDK 模块使用一致的版本，避免版本冲突。

2.34.0 作为次版本升级，通常包含新功能添加、Bug 修复和改进，但不涉及破坏性 API 变更（遵循语义化版本规范）。

## 如何达成设计目的

通过修改 Gradle 版本目录中 AWS SDK BOM 的版本引用，从 2.33.9 更新为 2.34.0。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `software.amazon.awssdk` 的版本引用从 `2.33.9` 改为 `2.34.0`。该版本通过 BOM 方式管理，所有引用 BOM 的 AWS SDK 模块（如 s3、dynamodb、sts、apache-client、url-connection-client 等）会自动使用 2.34.0 版本。

## 总结

本提交是 Dependabot 自动生成的依赖升级，将 AWS SDK for Java BOM 从 2.33.9 升级到 2.34.0（次版本升级）。修改仅涉及版本目录中一行版本号变更。这次升级为 Iceberg 的 AWS 模块带来了 AWS SDK 2.34.0 版本的改进和修复，有助于保持依赖的最新状态。
