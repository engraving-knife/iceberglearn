# 提交 1739：Build: Bump software.amazon.awssdk:bom from 2.30.16 to 2.30.21 (#12286)

## 提交信息

- **序号**：1739 / 4088
- **哈希**：aec763a607054569b87ac9672fcf7b29a50c7c42
- **短哈希**：aec763a60
- **日期**：2025-02-17 09:42:50 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.30.16 to 2.30.21 (#12286)
- **PR/Issue**：#12286

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级 PR。AWS SDK for Java 2.x 的 BOM（Bill of Materials）是 Iceberg 项目用于与 AWS 服务（如 S3、DynamoDB、Glue 等）交互的 SDK 依赖。本次升级将 `software.amazon.awssdk:bom` 从 2.30.16 升级到 2.30.21，属于补丁版本升级，包含 AWS SDK 各组件的 bug 修复和改进。

BOM（Bill of Materials）通过 POM 依赖管理机制统一控制 AWS SDK 所有组件的版本号，确保各组件版本兼容。升级 BOM 版本即可同时升级所有 AWS SDK 组件。

## 如何达成设计目的

提交修改了 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `awssdk-bom` 的版本号从 `2.30.16` 更新为 `2.30.21`。这是一个单行修改，通过 Gradle 版本目录机制自动传播到所有 AWS SDK 相关依赖。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 AWS SDK BOM 依赖版本。

**工作逻辑**：将版本目录中 `awssdk-bom = "2.30.16"` 更新为 `awssdk-bom = "2.30.21"`。所有引用 `libs.awssdk.bom` 的模块（如 S3 文件 IO、Glue Catalog、DynamoDB 等）将自动使用新版本的 AWS SDK 组件。

## 小结

- **成效**：将 AWS SDK BOM 从 2.30.16 升级到 2.30.21，获取了最新的 bug 修复和改进。
- **影响范围**：影响所有使用 AWS SDK 的模块（如 `aws-bundle`、`aws`、`s3` 等），但由于是补丁版本升级，不涉及 API 变更，对功能无影响。
- **回迁到 1.4.x 的注意事项**：此提交为依赖版本升级，回迁风险低。需确认 1.4.x 分支中使用的是相同版本的 AWS SDK BOM 依赖。如果 1.4.x 已经使用了更高版本，则无需回迁。建议回迁以保持依赖版本一致。
