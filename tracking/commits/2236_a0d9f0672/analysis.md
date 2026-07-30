# 提交 2236：Build: Bump software.amazon.awssdk:bom from 2.31.50 to 2.31.63

## 提交信息

- **序号**：2236 / 4088
- **哈希**：a0d9f067283c6bd01b6c00ae26a59d0cbaa4e41b
- **短哈希**：a0d9f0672
- **日期**：2025-06-15 08:36:00 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.31.50 to 2.31.63
- **PR/Issue**：#13316

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.31.50 升级到 2.31.63。AWS SDK BOM 用于统一管理 AWS SDK 各模块的版本，Iceberg 使用 AWS SDK 进行 S3 等云存储服务的交互。此次升级为 patch 级别的版本更新，包含多个小版本的 bug 修复和改进。

## 如何达成设计目的

- 在 Gradle 版本目录文件中修改 `awssdk-bom` 的版本号。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.31.50"` 修改为 `awssdk-bom = "2.31.63"`，所有引用该 BOM 的 AWS SDK 模块将自动使用新版本。

## 总结

常规依赖升级提交，将 AWS SDK BOM 从 2.31.50 升级到 2.31.63，获取最新的 bug 修复和改进。
