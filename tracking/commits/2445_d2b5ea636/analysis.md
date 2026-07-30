# 提交 2445：Build: Bump software.amazon.awssdk:bom from 2.32.9 to 2.32.14 (#13721)

## 提交信息

- **序号**：2445 / 4088
- **哈希**：d2b5ea636e8f62f1d3668988416f9cb30035ecb8
- **短哈希**：d2b5ea636e
- **日期**：2025-08-04 18:03:51 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.32.9 to 2.32.14 (#13721)
- **PR/Issue**：#13721

## 总体目的

本提交由 dependabot 自动生成，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）从 2.32.9 升级到 2.32.14。AWS SDK BOM 用于统一管理 Iceberg 对 AWS 服务（如 S3、DynamoDB 等）客户端依赖的版本，确保各 AWS SDK 模块版本兼容。

这是一次 patch 版本升级（2.32.9 → 2.32.14），按照语义化版本约定，patch 升级仅包含 bug 修复和小改进，不引入破坏性变更。dependabot 标注为 `update-type: version-update:semver-patch`。升级 AWS SDK 可以获得最新的 bug 修复和性能改进，提升与 AWS 服务交互的稳定性。

## 如何达成设计目的

在版本目录文件中修改 `awssdk-bom` 变量的值即可，所有引用该 BOM 的 AWS SDK 模块版本会自动统一。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.32.9"` 修改为 `awssdk-bom = "2.32.14"`。该变量在 libraries 目录中作为 AWS SDK BOM 的版本引用，通过 Gradle 的 platform 机制统一管理所有 AWS SDK 模块（如 s3、dynamodb、sts 等）的版本。

## 总结

这是一个由 dependabot 自动发起的依赖版本升级，将 AWS SDK BOM 从 2.32.9 升级到 2.32.14。作为 patch 版本升级，风险较低，主要是获取 AWS SDK 的最新 bug 修复。改动仅涉及一行版本号配置。
