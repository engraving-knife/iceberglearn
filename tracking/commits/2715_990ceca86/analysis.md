# 提交 2715：Build: Bump software.amazon.awssdk:bom from 2.34.5 to 2.35.0

## 提交信息

- **序号**：2715 / 4088
- **哈希**：990ceca865d53ba7ebb99879c5ee4801bd2a7ec2
- **短哈希**：990ceca86
- **日期**：2025-10-04 23:10:14 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.34.5 to 2.35.0
- **PR/Issue**：#14252

## 总体目的

此提交是 Dependabot 自动生成的依赖版本升级，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.34.5 升级到 2.35.0。

AWS SDK for Java 是 Iceberg 中 AWS 模块（`iceberg-aws`）的核心依赖，用于与 S3、DynamoDB、KMS、STS 等 AWS 服务交互。BOM（Bill of Materials）是一种 POM 文件，用于统一管理 AWS SDK 各子模块的版本号，确保各模块版本兼容。

2.34.5 到 2.35.0 是一个 semver-minor 版本升级，意味着包含新功能和改进，同时保持向后兼容。Dependabot 定期检查依赖更新并自动创建 PR，以帮助项目保持依赖的最新状态，获取最新的 bug 修复、安全补丁和功能改进。

## 如何达成设计目的

通过在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中将 `awssdk-bom` 的版本号从 `2.34.5` 更新为 `2.35.0` 来完成升级。由于 Iceberg 使用 Gradle 版本目录来集中管理所有依赖版本，只需修改一处即可让所有引用该 BOM 的模块统一升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：将 `awssdk-bom` 变量从 `"2.34.5"` 更改为 `"2.35.0"`。所有通过 `version.ref = "awssdk-bom"` 引用此版本的 AWS SDK 模块（如 S3、DynamoDB、STS 等）将自动使用新版本。这是一次 semver-minor 升级，通常包含新 API、bug 修复和性能改进，不影响现有 API 兼容性。

## 总结

此提交是例行依赖维护，将 AWS SDK for Java 从 2.34.5 升级到 2.35.0。作为 semver-minor 升级，预计包含新功能和改进，同时保持向后兼容。这确保了 Iceberg 的 AWS 集成使用最新的 AWS SDK，获得最新的功能和安全修复。
