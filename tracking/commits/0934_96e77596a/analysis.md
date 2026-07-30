# 提交 0934：Build: Bump software.amazon.awssdk:bom from 2.26.16 to 2.26.20 (#10700)

## 提交信息

- **序号**：0934 / 4088
- **哈希**：96e77596ad85275c07146f37aa0edcf4d660b93c
- **短哈希**：96e77596a
- **日期**：2024-07-15（Mon Jul 15 08:43:35 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.26.16 to 2.26.20 (#10700)
- **PR/Issue**：#10700

## 总体目的

这是由 Dependabot 自动生成的依赖升级 PR，将 Iceberg 项目依赖的 AWS SDK for Java v2 BOM（`software.amazon.awssdk:bom`）从 `2.26.16` 升级到 `2.26.20`。

AWS SDK for Java v2 是 Iceberg 与 AWS 服务（S3、DynamoDB、Glue、KMS 等）交互的基础，被 `aws`、`s3`、`glue` 等多个模块广泛使用。`awssdk-bom` 作为 BOM 统一管理所有 AWS SDK v2 组件的版本，避免组件间版本不一致。本次为 patch 版本升级（2.26.16 → 2.26.20），主要是 bug 修复与小改进，API 兼容。升级可获取 S3 客户端、S3 access grants 等组件的稳定性修复。

## 如何达成设计目的

实现方式与其它依赖升级一致：仅修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本别名的字符串值，所有引用该别名的 AWS SDK 组件会通过版本目录机制自动升级到 2.26.20。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK v2 BOM 版本从 2.26.16 升级到 2.26.20，统一升级所有 AWS SDK 组件版本。

**工作逻辑**：

```diff
-awssdk-bom = "2.26.16"
+awssdk-bom = "2.26.20"
```

`awssdk-bom` 是版本目录中的版本别名，被 `[libraries]` 段中 AWS SDK 相关条目（如 `software.amazon.awssdk:bom`，以及 `awssdk-s3accessgrants` 等通过 BOM 管理版本的组件）引用。Iceberg 在 `aws-bundle`、`s3`、`glue`、`dynamodb` 等模块中以 `platform(...)` 形式引入此 BOM，从而让 S3Client、GlueClient、DynamoDbClient 等所有 AWS 客户端使用统一版本。改这一行即可让全部 AWS SDK v2 组件同步升级到 2.26.20。

## 小结

- **成效**：将 AWS SDK for Java v2 BOM 由 2.26.16 升级至 2.26.20，获取 patch 版本的 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动；影响所有依赖 AWS SDK v2 的模块（`aws`、`s3`、`glue`、`dynamodb`、`kms` 等）。
- **回迁到 1.4.x 的注意事项**：patch 版本升级、API 兼容，适合回迁。AWS SDK 是 Iceberg AWS 集成的核心依赖，1.4.x 分支应保持与 main 接近的版本以获取安全与稳定性修复。回迁前建议运行 `aws`、`s3` 模块的集成测试（特别是 S3 access grants 相关）确认无回归。若 1.4.x 已使用相近版本，可直接 cherry-pick；若版本差距较大，需整体评估 AWS SDK 版本策略。
