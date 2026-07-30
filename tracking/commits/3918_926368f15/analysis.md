# 提交 3918：Build: Bump software.amazon.awssdk:bom from 2.46.5 to 2.46.10 (#16901)

## 提交信息

- **序号**：3918 / 4088
- **哈希**：926368f15be78fe16b12f7ab553797a0926aa07e
- **短哈希**：926368f15
- **日期**：2026-06-21 00:06:38 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.46.5 to 2.46.10 (#16901)
- **PR/Issue**：#16901

## 总体目的

这是由 Dependabot 发起的依赖升级，将 AWS SDK for Java v2 的 BOM（Bill of Materials）从 2.46.5 升级到 2.46.10。AWS SDK BOM 用于统一管理所有 AWS SDK 模块（如 S3、Glue、DynamoDB、KMS 等）的版本，避免不同模块间版本不一致带来的兼容性问题。Iceberg 的 AWS 集成模块（`iceberg-aws`）以及 GCP/Azure bundles 等都依赖该 BOM。

此次升级为 semver-patch 更新，主要是为了获取 AWS SDK 的 bug 修复和小幅改进，属于低风险的常规维护。

## 如何达成设计目的

通过修改 Iceberg 的版本目录（version catalog）`gradle/libs.versions.toml`，将 `awssdk-bom` 版本条目从 `2.46.5` 更新为 `2.46.10`。版本目录是 Gradle 7+ 引入的集中式依赖版本管理机制，所有子项目通过引用该目录中的版本别名来获取统一版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：
将 `awssdk-bom = "2.46.5"` 改为 `awssdk-bom = "2.46.10"`。该条目被 `iceberg-aws` 等模块通过 `platform` 方式引入，从而对所有 AWS SDK 子模块版本进行统一管控。

## 总结

这是一次 AWS SDK BOM 的补丁版本升级，通过版本目录统一升级到 2.46.10，使所有 AWS SDK 子模块获取最新的 bug 修复。作为补丁版本更新，预期完全向后兼容，风险极低。
