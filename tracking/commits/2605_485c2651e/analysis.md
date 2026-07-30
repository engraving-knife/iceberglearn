# 提交 2605：Build: Bump software.amazon.awssdk:bom from 2.33.0 to 2.33.4 (#14009)

## 提交信息

- **序号**：2605 / 4088
- **哈希**：485c2651e8fb73a2d4192885dbfd77d7368c2d92
- **短哈希**：485c2651e
- **日期**：2025-09-07 14:18:30 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.33.0 to 2.33.4 (#14009)
- **PR/Issue**：#14009

## 总体目的

本次提交由 Dependabot 自动生成，将 `software.amazon.awssdk:bom` 从 2.33.0 升级到 2.33.4。

AWS SDK for Java v2 的 BOM（Bill of Materials）是 AWS SDK 的依赖管理 POM，通过导入该 BOM 可以统一管理所有 AWS SDK 组件的版本。Iceberg 的 AWS 集成模块（aws-bundle 及 aws 模块）使用此 BOM 管理对 S3、DynamoDB、Glue 等 AWS 服务的依赖。

此次升级为 patch 级别更新（2.33.0 → 2.33.4），按照语义化版本规范，只包含 bug 修复和向后兼容的改进，不引入破坏性变更。

## 如何达成设计目的

在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `awssdk-bom` 的版本号从 `2.33.0` 修改为 `2.33.4`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.33.0"` 改为 `awssdk-bom = "2.33.4"`。该 BOM 管理所有 AWS SDK v2 组件的版本，升级后所有 AWS 相关依赖（如 S3Client、DynamoDbClient、GlueClient 等）将统一升至 BOM 中指定的对应版本。

**潜在影响**：作为 patch 级升级，预期包含 AWS SDK 各组件的 bug 修复和稳定性改进。AWS SDK 的 patch 版本通常包含安全修复和服务端兼容性改进。此次升级有助于修复 AWS 集成中的已知问题。需要注意的是，之前提交 2598（LICENSE 更新）中已将 LICENSE 文件中的 AWS SDK 版本更新为 2.33.0，本次升级到 2.33.4 后 LICENSE 可能需要再次同步。

## 总结

这是 Dependabot 自动生成的依赖升级提交，将 AWS SDK BOM 从 2.33.0 升至 2.33.4。作为 patch 级升级，风险低且包含 bug 修复。保持 AWS SDK 最新有助于确保 Iceberg 的 AWS 集成模块稳定运行并修复潜在安全问题。
