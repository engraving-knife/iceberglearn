# 提交 2696：Build: Bump software.amazon.awssdk:bom from 2.34.0 to 2.34.5 (#14206)

## 提交信息

- **序号**：2696 / 4088
- **哈希**：124d29ef7ad26a0c291d0caeb66a31e51ed1da6f
- **短哈希**：124d29ef7
- **日期**：2025-09-29 09:00:13 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.34.0 to 2.34.5 (#14206)
- **PR/Issue**：#14206

## 总体目的

Dependabot 自动将 AWS SDK for Java v2 的 BOM（`software.amazon.awssdk:bom`）从 `2.34.0` 升级到 `2.34.5`。AWS SDK BOM 用于统一管理 AWS SDK 各模块（S3、Glue、DynamoDB、KMS 等）的版本，Iceberg 的 `aws` 模块在访问 S3、Glue Catalog 等服务时依赖该 SDK。

本次为 semver-patch 升级（`version-update:semver-patch`，`direct:production`），属同 2.34.x 系列内的补丁更新，通常包含服务客户端的 bug 修复、API 模型更新与小改进，保持 API 兼容。升级有助于获取 AWS 服务端模型变化对应的客户端修复。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `awssdk-bom` 别名的版本号，从 `2.34.0` 改为 `2.34.5`。由于使用 BOM 管理版本，单一改动即可让所有 AWS SDK 模块版本对齐到 2.34.5。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.34.0"` 改为 `awssdk-bom = "2.34.5"`。BOM 通过 platform 机制统一约束所有 AWS SDK 模块版本，改后相关模块（如 S3、Glue 客户端）自动获得 2.34.5 的修复。

## 总结

Dependabot 自动将 AWS SDK for Java v2 BOM 从 2.34.0 升级到 2.34.5，属补丁级依赖维护。改动仅一行版本号，通过 BOM 机制集中生效到所有 AWS SDK 模块，风险低，旨在获取上游修复与服务模型更新。
