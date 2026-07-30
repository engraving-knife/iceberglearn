# 提交 2732：Build: Bump software.amazon.awssdk:bom from 2.35.0 to 2.35.5

## 提交信息

- **序号**：2732 / 4088
- **哈希**：6ad5dd8188bfeb551b543a1cae47cfe936893d55
- **短哈希**：6ad5dd818
- **日期**：2025-10-11 22:21:42 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.35.0 to 2.35.5
- **PR/Issue**：#14302

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。AWS SDK for Java v2（software.amazon.awssdk）是 Iceberg AWS 集成模块的核心依赖，提供 S3、Glue、DynamoDB、KMS 等 AWS 服务的客户端。BOM（Bill of Materials）用于统一管理 AWS SDK 各子模块的版本，确保彼此兼容。

本次升级将 AWS SDK BOM 从 2.35.0 升级到 2.35.5，属于 semver-patch（补丁版本）升级，通常包含 bug 修复和小改进，不引入破坏性变更。保持 AWS SDK 最新对 Iceberg 至关重要，因为 AWS 服务 API 和 SDK 的 bug 修复可能直接影响数据读写的可靠性。

## 如何达成设计目的

Dependabot 通过修改 Gradle 版本目录中的 BOM 版本声明完成升级。由于使用 BOM 管理方式，所有 AWS SDK 子模块（S3、Glue、KMS 等）的版本会自动跟随 BOM 版本统一升级，确保模块间兼容性。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.35.0"` 修改为 `awssdk-bom = "2.35.5"`。项目通过 BOM 引入 AWS SDK 依赖，修改此版本号即可使所有 awssdk 子模块统一升级到 2.35.5 对应的版本。

## 总结

这是常规的依赖维护升级，将 AWS SDK for Java BOM 从 2.35.0 升级到 2.35.5。作为 semver-patch 升级，风险很低，主要获取 bug 修复。由于 AWS SDK 是 Iceberg AWS 集成的基础依赖，及时升级有助于保持与 AWS 服务的兼容性和稳定性。
