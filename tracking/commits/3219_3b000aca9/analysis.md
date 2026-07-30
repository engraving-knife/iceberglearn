# 提交 3219：Build: Bump software.amazon.awssdk:bom from 2.41.19 to 2.41.24 (#15261)

## 提交信息

- **序号**：3219 / 4088
- **哈希**：3b000aca9d021b40a9149e2514aab7ca1e34d393
- **短哈希**：3b000aca9
- **日期**：2026-02-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.41.19 to 2.41.24 (#15261)
- **PR/Issue**：#15261

## 总体目的

这是一次 dependabot 发起的依赖版本升级，针对 AWS SDK for Java v2 的 BOM（Bill of Materials）。`software.amazon.awssdk:bom` 是 AWS SDK v2 提供的版本清单 POM，Iceberg 通过在 `gradle/libs.versions.toml` 中以 `awssdk-bom` 变量声明其版本，并在构建中以 platform/BOM 形式引入，从而统一管理 S3、Glue、DynamoDB、KMS、STS 等众多 AWS SDK 模块的版本，避免各模块版本不一致。Iceberg 的 S3FileIO、GlueCatalog、DynamoDB 锁与状态存储等核心集成能力都依赖该 SDK。

本次把 `awssdk-bom` 从 `2.41.19` 提升到 `2.41.24`，属于 `semver-patch`（修订号）升级。AWS SDK v2 的 2.x 线上 patch 升级通常包含缺陷修复、服务模型更新与小改进，保持 API 兼容。升级动机是跟进 AWS 服务的最新服务模型与修复，确保 S3/Glue 等集成的稳定性与兼容性。

## 如何达成设计目的

作为 dependabot 自动化升级，整体思路是在集中式版本目录 `gradle/libs.versions.toml` 中把 `awssdk-bom` 版本变量从 `2.41.19` 改为 `2.41.24`。由于所有 AWS SDK 模块都通过该 BOM 统一对齐版本，单点修改即覆盖全部 AWS SDK 制品。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将统一管理的 AWS SDK v2 BOM 版本变量从 2.41.19 升到 2.41.24。

**工作逻辑**：
在 `[versions]` 段中，将 `awssdk-bom = "2.41.19"` 修改为 `awssdk-bom = "2.41.24"`。该变量作为 AWS SDK BOM 的版本引用，被构建中以 platform 形式导入，从而把项目依赖的所有 `software.amazon.awssdk:*` 模块（如 `s3`、`glue`、`dynamodb`、`kms`、`sts`、`s3accessgrants` 等）统一对齐到 BOM 内声明的版本。修订号升级预期不引入破坏性 API 变更，对 Iceberg 的 S3/Glue/DynamoDB 等集成应为透明或仅带来修复与服务模型更新收益。

## 总结

本提交由 dependabot 将集中式版本目录中的 AWS SDK v2 BOM 版本变量从 2.41.19 升级到 2.41.24（修订号升级，兼容），通过 BOM 统一对齐全部 AWS SDK 模块版本，用于跟进 AWS 服务模型更新与缺陷修复，保障 S3/Glue/DynamoDB 等集成的稳定性；改动为单点版本号替换，不涉及代码逻辑。
