# 提交 3010：Build: Bump software.amazon.awssdk:bom from 2.40.3 to 2.40.8 (#14843)

## 提交信息

- **序号**：3010 / 4088
- **哈希**：9daef17156613ba9a0e5c23f0047cbdd072ee7c7
- **短哈希**：9daef1715
- **日期**：2025-12-13 23:38:45 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.40.3 to 2.40.8 (#14843)
- **PR/Issue**：#14843

## 总体目的

`software.amazon.awssdk:bom` 是 AWS SDK for Java v2 的物料清单（BOM）POM，用于统一管理 Iceberg 依赖的所有 AWS SDK 模块版本。Iceberg 的 `aws` 模块（以及通过 `aws-bundle` 分发的运行时包）依赖大量 AWS SDK 组件——包括 S3（`ADLSFileIO` 的对端 `S3FileIO`）、DynamoDB（`DynamoDbLockManager` 等锁管理）、Glue（`GlueCatalog`）、KMS（`AwsKmsClient` 等 envelope 加密）、STS（凭证 AssumeRole）等。这些模块的版本由 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本号集中控制，子模块以 `implementation platform(libs.awssdk.bom)` 形式引入平台后不再单独指定版本。

本提交是 dependabot 触发的常规依赖升级，把 `awssdk-bom` 从 `2.40.3` 升到 `2.40.8`，跨越 5 个 patch 版本。AWS SDK v2 在 2.40.x 系列内以 patch 形式迭代，通常包含 bug 修复、安全补丁与小幅改进，不引入破坏性 API 变更。升级动机是保持依赖最新、获取已修复的缺陷与安全补丁，避免积压技术债。

## 如何达成设计目的

改动极小：仅在 `gradle/libs.versions.toml` 中把 `awssdk-bom = "2.40.3"` 改为 `awssdk-bom = "2.40.8"`。由于所有 AWS SDK 模块版本都通过 BOM 统一管理，单点修改即让 S3、DynamoDB、Glue、KMS、STS 等全部模块同步升到 BOM 对应的兼容版本，无需逐个调整。dependabot 元数据标注 `update-type: version-update:semver-patch`，属向后兼容的 patch 升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：
在版本目录中 `awssdk-bom = "2.40.3"` 改为 `awssdk-bom = "2.40.8"`。该键被 `aws` 模块（及 `aws-bundle`）以 `platform(libs.awssdk.bom)` 引用，Gradle 会据此解析所有 `software.amazon.awssdk:*` 依赖到 BOM 中声明的版本。patch 级升级预期不破坏现有 API，主要带来 bug 修复与安全补丁。

## 总结

该提交是一次 dependabot 驱动的 AWS SDK BOM patch 升级（2.40.3 → 2.40.8），单点修改版本目录即可让 Iceberg `aws` 模块依赖的全部 AWS SDK 组件（S3、DynamoDB、Glue、KMS、STS 等）同步获得最新 bug 修复与安全补丁。属于低风险、向后兼容的常规维护，保持 Iceberg 与 AWS SDK v2 的最新 patch 同步。
