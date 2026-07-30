# 提交 2347：Build: Bump software.amazon.awssdk:bom from 2.31.77 to 2.31.78 (#13543)

## 提交信息

- **序号**：2347 / 4088
- **哈希**：5440f696a555d26488d5ffa7960ff005aa002d04
- **短哈希**：5440f696a
- **日期**：2025-07-14 09:20:59 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.31.77 to 2.31.78 (#13543)
- **PR/Issue**：#13543

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.31.77 升级到 2.31.78。这是一个补丁版本升级。

AWS SDK for Java 是 Iceberg AWS 模块（`iceberg-aws`）的核心依赖，用于与 S3、DynamoDB、STS 等 AWS 服务交互。BOM（Bill of Materials）是一种特殊的 POM，用于统一管理 AWS SDK 所有组件的版本，确保各组件之间版本兼容。

从 2.31.77 到 2.31.78 是补丁升级，通常包含 bug 修复和安全补丁，不引入新功能或破坏性变更。

## 如何达成设计目的

在 Gradle 版本目录中更新 AWS SDK BOM 的版本号，所有 AWS SDK 组件的版本会自动通过 BOM 统一管理。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：将 `awssdk-bom = "2.31.77"` 改为 `awssdk-bom = "2.31.78"`。BOM 版本号变更后，所有通过 BOM 管理的 AWS SDK 组件（如 S3、STS、DynamoDB 客户端等）会自动使用对应版本的依赖。

## 总结

本提交是 Dependabot 自动生成的依赖升级，将 AWS SDK for Java BOM 从 2.31.77 升级到 2.31.78（补丁版本），获取最新的 bug 修复和安全补丁。
