# 提交 2876：Build: Bump software.amazon.awssdk:bom from 2.38.2 to 2.38.7 (#14596)

## 提交信息

- **序号**：2876 / 4088
- **哈希**：3873f6e2f0e40d84f9434a64653fe50621474f53
- **短哈希**：3873f6e2f
- **日期**：2025-11-16 00:00:37 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.38.2 to 2.38.7 (#14596)
- **PR/Issue**：#14596

## 总体目的

AWS SDK for Java (v2) 的 BOM（Bill of Materials）用于统一管理所有 AWS SDK 模块的版本，确保各模块之间版本兼容。Iceberg 项目使用 AWS SDK 与 S3、DynamoDB、Glue 等 AWS 服务交互，包括核心存储操作和 S3 Access Grants 等功能。

此提交由 Dependabot 自动生成，将 AWS SDK BOM 从 2.38.2 升级到 2.38.7。这是同一个次版本（2.38.x）内的补丁升级，跨 5 个补丁版本（2.38.2 → 2.38.3 → 2.38.4 → 2.38.5 → 2.38.6 → 2.38.7），通常包含多项 bug 修复、安全补丁和小的功能改进。BOM 升级后，所有依赖 AWS SDK 的模块版本会自动统一对齐到 2.38.7。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本号，从 `2.38.2` 改为 `2.38.7`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：将 `[versions]` 区段中的 `awssdk-bom = "2.38.2"` 改为 `awssdk-bom = "2.38.7"`。该版本变量用于 BOM 依赖声明，BOM 会统一管理所有 AWS SDK 模块（如 s3、sts、glue、dynamodb 等）的版本，确保它们版本一致且互相兼容。文件中另有 `awssdk-s3accessgrants = "2.3.0"` 是独立管理的 S3 Access Grants 插件版本，不受此 BOM 升级影响。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 AWS SDK for Java BOM 从 2.38.2 升级到 2.38.7（补丁版本升级，跨 5 个版本）。这是一次常规的依赖维护升级，获取 AWS SDK 的最新 bug 修复和安全补丁，同时通过 BOM 机制确保所有 AWS SDK 模块版本一致。风险较低，但对 S3 等存储交互的稳定性有积极影响。
