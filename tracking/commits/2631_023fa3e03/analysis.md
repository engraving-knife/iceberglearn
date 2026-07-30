# 提交 2631：Build: Bump software.amazon.awssdk:bom from 2.33.4 to 2.33.9 (#14073)

## 提交信息

- **序号**：2631 / 4088
- **哈希**：023fa3e03d96e000bf3f31d72b6a3deb3ef30a4d
- **短哈希**：023fa3e03
- **日期**：2025-09-14 18:50:31 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.33.4 to 2.33.9 (#14073)
- **PR/Issue**：#14073

## 总体目的

这是 dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.33.4 版本升级到 2.33.9 版本。

Iceberg 在访问 AWS S3、DynamoDB、Glue 等 AWS 服务时依赖 AWS SDK。使用 BOM 机制可以统一管理 AWS SDK 各个模块的版本，确保彼此兼容。从 2.33.4 到 2.33.9 共跨越了 5 个补丁版本，可能包含多个 bug 修复、性能改进和安全补丁。

本次升级属于 semver-patch 级别，按照语义化版本约定应保持向后兼容。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本声明，从 `2.33.4` 改为 `2.33.9`。所有引用 AWS SDK 模块的子项目会通过 BOM 统一获取到新版本，无需单独修改各模块版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 AWS SDK BOM 版本声明。

**工作逻辑**：将 `awssdk-bom = "2.33.4"` 修改为 `awssdk-bom = "2.33.9"`。BOM 作为版本对齐清单，导入后会自动约束所有 AWS SDK 模块的版本保持一致，避免版本不匹配问题。

## 总结

这是一次常规的 AWS SDK 补丁版本升级。由于 Iceberg 广泛使用 AWS 服务（S3、Glue、DynamoDB 等），保持 AWS SDK 最新有助于获取稳定性与安全修复。升级范围在补丁版本内，风险较低。
