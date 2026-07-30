# 提交 3391：Build: Bump software.amazon.awssdk:bom from 2.42.8 to 2.42.13 (#15638)

## 提交信息

- **序号**：3391 / 4088
- **哈希**：11dbe2f091edd4ac492f210c878d22386ec9d605
- **短哈希**：11dbe2f09
- **日期**：2026-03-14 23:43:01 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.42.8 to 2.42.13 (#15638)
- **PR/Issue**：#15638

## 总体目的

由 dependabot 自动发起的依赖升级，将 AWS SDK for Java 2.x 的 BOM 从 2.42.8 升级到 2.42.13（semver-patch 级别）。`software.amazon.awssdk:bom` 是 AWS SDK 的 BOM，用于统一管理 S3、DynamoDB、Glue 等 AWS 服务客户端的版本。Iceberg 的 S3 集成、Glue Catalog、DynamoDB 锁等模块均依赖该 BOM 管理版本。patch 级别升级主要包含 bug 修复和小改进。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本变量的值，所有引用该 BOM 的 AWS SDK 依赖会自动同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：
- 将 `awssdk-bom = "2.42.8"` 改为 `awssdk-bom = "2.42.13"`。该 BOM 管理所有 AWS SDK for Java 2.x 客户端版本，影响 S3、Glue、DynamoDB 等多个集成模块。

## 总结

这是一次由 dependabot 自动生成的 patch 级依赖升级，将 AWS SDK BOM 从 2.42.8 提升到 2.42.13。改动仅涉及版本目录中的一行版本号。BOM 升级会带动其管理的多个 AWS 客户端库版本更新，patch 级升级通常用于获取 bug 修复，风险较低。
