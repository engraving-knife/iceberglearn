# 提交 0748：Build: Bump software.amazon.awssdk:bom from 2.25.40 to 2.25.45 (#10266)

## 提交信息

- **序号**：0748 / 4088
- **哈希**：5d3d647ead118bdbfa718c7b46040e59ecae9b11
- **短哈希**：5d3d647ea
- **日期**：2024-05-09 15:21:39 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.40 to 2.25.45 (#10266)
- **PR/Issue**：#10266

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）从 `2.25.40` 升级到 `2.25.45`，属于 semver-patch 级别的版本提升。

AWS SDK BOM 是 Iceberg 的 `aws` 模块（以及 `s3`、`glue`、`dynamodb` 等集成）所依赖的核心 BOM，用于统一管理 AWS SDK 各组件（S3、DynamoDB、Glue、STS、KMS 等）的版本。Dependabot 元数据将其标记为 `direct:production`、`version-update:semver-patch`，即生产环境直接依赖的补丁版本升级，主要用于获取 2.25.40 到 2.25.45 之间的 bug 修复与稳定性改进。

## 如何达成设计目的

改动集中在唯一的版本目录文件 `gradle/libs.versions.toml`：将 `awssdk-bom` 版本别名从 `"2.25.40"` 改为 `"2.25.45"`。该别名通过 Gradle 版本目录机制被各模块统一引用，升级后所有依赖该 BOM 的模块会统一拉取新版本，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 AWS SDK BOM 的版本别名从 `2.25.40` 提升到 `2.25.45`。

**工作逻辑**：原行 `awssdk-bom = "2.25.40"` 改为 `awssdk-bom = "2.25.45"`。该别名位于 `[versions]` 段，配合同文件 `[libraries]` 段中的 BOM 坐标引用使用，下游模块通过版本目录引入 AWS SDK 依赖时统一使用该版本。

## 小结

- **成效**：通过 Dependabot 将 AWS SDK BOM 从 2.25.40 升级到 2.25.45，获取 5 个补丁版本的 bug 修复与稳定性改进。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，影响所有使用 AWS SDK 的模块（aws、s3、glue 等），但由于是 patch 级升级，API 兼容，运行时行为变化极小。
- **回迁到 1.4.x 的注意事项**：依赖升级可与 1.4.x 分支当前使用的 AWS SDK 版本合并，属于低风险变更。回迁时确认 1.4.x 分支的 `libs.versions.toml` 中 `awssdk-bom` 别名存在即可。
