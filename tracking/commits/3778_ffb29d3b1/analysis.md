# 提交 3778：Build: Bump software.amazon.awssdk:bom from 2.44.4 to 2.44.7 (#16555)

## 提交信息

- **序号**：3778 / 4088
- **哈希**：ffb29d3b1e7ed867afd7ad2f94af071fd7a37982
- **短哈希**：ffb29d3b1
- **日期**：2026-05-24 10:29:36 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.44.4 to 2.44.7 (#16555)
- **PR/Issue**：#16555

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.44.4 升级到 2.44.7。这是一个 semver-patch 级别的升级，包含 bug 修复和小改进。AWS SDK BOM 用于统一管理 AWS SDK 各模块的版本，Iceberg 使用它来与 S3、DynamoDB 等 AWS 服务交互。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中更新 `awssdk-bom` 版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.44.4"` 改为 `awssdk-bom = "2.44.7"`，升级 3 个 patch 版本。

## 总结

常规的依赖维护提交，将 AWS SDK BOM 从 2.44.4 升级到 2.44.7，获取最新的 bug 修复和改进。
