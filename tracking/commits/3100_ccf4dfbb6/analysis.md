# 提交 3100：Build: Bump software.amazon.awssdk:bom from 2.41.1 to 2.41.5 (#15022)

## 提交信息

- **序号**：3100 / 4088
- **哈希**：ccf4dfbb65146d9be11e5027404fb0c3df2af999
- **短哈希**：ccf4dfbb6
- **日期**：2026-01-11
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.41.1 to 2.41.5 (#15022)
- **PR/Issue**：#15022

## 总体目的

该提交由 Dependabot 自动生成，将 AWS SDK for Java v2 的 BOM（Bill of Materials）从 2.41.1 升级到 2.41.5。AWS SDK BOM 是一个 Maven/Gradle 平台工件，集中声明 AWS SDK 各模块（S3、Glue、DynamoDB、KMS、STS 等）的统一版本，避免各模块版本不一致。Iceberg 的 `aws` 模块（`iceberg-aws`）与 `S3FileIO`、`GlueCatalog`、`DynamoDbLockManager` 等核心集成高度依赖 AWS SDK，通过引入该 BOM 来管理所有 AWS SDK 模块的版本对齐。本次升级将整套 SDK 模块从 2.41.1 推进到 2.41.5。

版本号从 2.41.1 升级到 2.41.5，属于语义版本中的 patch 级别升级（`version-update:semver-patch`），跨四个 patch 版本。根据提交元数据，该依赖归类为 `direct:production`。AWS SDK v2 在 2.x 系列内保持向后兼容，patch 升级通常包含服务客户端的 API 模型更新（如新增服务操作/参数）、HTTP 客户端与重试逻辑的 bug 修复、以及对 S3、Glue 等服务端新行为的适配。对 Iceberg 而言，预期影响是：S3FileIO、GlueCatalog 等基于 SDK 的集成行为保持一致，但可能修复特定 S3 操作或认证流程的缺陷，提升与 AWS 服务端的兼容性。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本变量，从 `2.41.1` 改为 `2.41.5`。该变量被 `awssdk-bom` 平台坐标引用，Gradle 在依赖解析时会用 BOM 中声明的版本统一约束所有 AWS SDK 模块。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本变量。

**工作逻辑**：
将 `awssdk-bom = "2.41.1"` 修改为 `awssdk-bom = "2.41.5"`。该变量被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，作为 platform 依赖导入后，`iceberg-aws` 等模块中所有未显式声明版本的 AWS SDK 工件（如 `software.amazon.awssdk:s3`、`software.amazon.awssdk:glue`、`software.amazon.awssdk:dynamodb`、`software.amazon.awssdk:sts` 等）会自动对齐到 2.41.5。同区的 `awssdk-s3accessgrants`（S3 访问授权插件）版本独立，本次不受影响。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 AWS SDK for Java v2 的 BOM 从 2.41.1 提升到 2.41.5（patch 级别，跨四个 patch）。该 BOM 统一管理 `iceberg-aws`（S3FileIO、GlueCatalog、DynamoDb 锁等）所用的全部 AWS SDK 模块版本。作为 patch 升级，预期包含 API 模型更新与 bug 修复，无破坏性变更，提升与 AWS 服务端的兼容性。
