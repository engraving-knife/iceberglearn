# 提交 3252：Build: Bump software.amazon.awssdk:bom from 2.41.24 to 2.41.29 (#15324)

## 提交信息

- **序号**：3252 / 4088
- **哈希**：113e71a31a95460f3a0ab2874167e8c927b52056
- **短哈希**：113e71a31
- **日期**：2026-02-14
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.41.24 to 2.41.29 (#15324)
- **PR/Issue**：#15324

## 总体目的

`software.amazon.awssdk:bom` 是 AWS SDK for Java v2 的 BOM（Bill of Materials），用于统一管理 AWS SDK 各模块（S3、DynamoDB、KMS、STS、Glue 等）的版本兼容性。在 Iceberg 项目中，该 BOM 是 AWS 集成的核心基础，支撑 S3 文件系统访问、AWS KMS 加密、DynamoDB 锁定目录、Glue Catalog 等多个模块。该依赖属于 `direct:production` 类型。

本次提交由 Dependabot 自动生成，将 `awssdk-bom` 从 `2.41.24` 升级到 `2.41.29`。根据语义版本规范，这是一个 patch 级别升级（`version-update:semver-patch`），即修订号从 24 增至 29（跨 5 个修订版本）。patch 升级通常只包含 bug 修复和服务端 API 更新，不引入破坏性 API 变更，预期对 Iceberg 的 AWS 集成功能无行为影响。值得注意，此升级与同日提交的 `awssdk-s3accessgrants` 升级（#15325）协调一致，确保 S3 Access Grants 插件与 AWS SDK 核心版本兼容。

## 如何达成设计目的

仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本声明，从 `2.41.24` 改为 `2.41.29`。通过 BOM 机制，所有 AWS SDK 客户端库的传递依赖版本将自动对齐到 2.41.29 版本集。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS SDK v2 BOM 版本从 2.41.24 升级至 2.41.29。

**工作逻辑**：
在版本目录的第 36 行，将 `awssdk-bom = "2.41.24"` 修改为 `awssdk-bom = "2.41.29"`。该 BOM 统一管理项目中所有 AWS SDK 模块的版本，升级后 S3、KMS、DynamoDB、Glue 等 AWS 集成模块将获得最新的 bug 修复和服务端兼容性更新。作为 patch 级别升级，保持 API 向后兼容。从 diff 可见此时 `awssdk-s3accessgrants` 已是 `2.4.1`（由前一个提交 #15325 升级），说明这两个依赖升级在版本目录中保持顺序一致。

## 总结

本次提交是 Dependabot 自动执行的 AWS SDK v2 BOM patch 升级（2.41.24 → 2.41.29），使 Iceberg 的 AWS 集成模块（S3、KMS、DynamoDB、Glue 等）使用最新补丁版本的 AWS SDK，获取 bug 修复和服务端兼容性更新，对项目功能无影响。
