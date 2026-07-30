# 提交 2074：Build: Bump software.amazon.awssdk:bom from 2.31.30 to 2.31.35

## 提交信息

- **序号**：2074 / 4088
- **哈希**：0db08b7c697b5a628318254dcb7d758a028819b8
- **短哈希**：0db08b7c6
- **日期**：2025-05-05 08:14:32 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.31.30 to 2.31.35 (#12967)
- **PR/Issue**：#12967

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.31.30 升级到 2.31.35。这是一个 patch 版本升级，通常包含 Bug 修复和稳定性改进。AWS SDK BOM 用于统一管理所有 AWS SDK 构件的版本，Iceberg 使用 AWS SDK 提供 S3、DynamoDB、Glue 等 AWS 服务的集成支持（如 S3FileIO、GlueCatalog 等）。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本号定义，统一升级所有 AWS SDK 构件版本。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：将 AWS SDK BOM 版本号从 2.31.30 升级到 2.31.35。

**工作逻辑**：
将 `awssdk-bom = "2.31.30"` 修改为 `awssdk-bom = "2.31.35"`。该 BOM 版本变量用于统一管理所有 AWS SDK 构件的版本。

## 总结

本提交由 Dependabot 自动生成，将 AWS SDK for Java BOM 从 2.31.30 升级到 2.31.35（patch 版本升级），仅修改版本目录文件中一行版本号定义。
