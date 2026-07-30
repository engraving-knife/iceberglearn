# 提交 0033：Build: Bump software.amazon.awssdk:bom from 2.20.131 to 2.20.162 (#8773)

## 提交信息

- **序号**：0033 / 4088
- **哈希**：c3b9afe2fca2b84cbb5b937df2b1f83c1605e7b6
- **短哈希**：c3b9afe2f
- **日期**：2023-10-11 09:02:20 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.20.131 to 2.20.162 (#8773)
- **PR/Issue**：#8773

## 总体目的

这个提交由 dependabot 自动生成，把 Iceberg 依赖的 AWS SDK for Java（`software.amazon.awssdk:bom`）从 2.20.131 升级到 2.20.162，跨度为 31 个 patch 版本（semver-patch）。

AWS SDK BOM 是 Iceberg 与 AWS 服务交互的核心依赖：Iceberg 的 `aws` 模块（`org.apache.iceberg.aws`）基于该 SDK 实现了 `S3FileIO`/`S3Tables`、`DynamoDbCatalog`/`GlueCatalog`、`LakeFormation` 凭证集成、`KMS` 加密等能力，BOM 用于统一锁定所有 AWS SDK 子模块（s3、dynamodb、glue、kms、sts、apache-client、netty-nio-client 等）的版本，避免各子模块版本不一致。AWS SDK 在 2.20.x 系列持续高频发布 patch 版本，每个版本通常包含若干 bugfix、性能改进、新 region 支持，以及对 HTTP 客户端与各 service client 的稳定性修复。把 BOM 从 2.20.131 推进到 2.20.162 能让 Iceberg 跟上这些累积修复，降低用户在使用 S3FileIO、DynamoDb 锁、Glue catalog 等场景下踩到上游已修复 bug 的概率。

这次升级是 dependabot 自动化依赖维护流程的一部分，更新类型被标注为 `version-update:semver-patch`，符合 BOM 升级的低风险预期：patch 版本应保持二进制与行为兼容，只包含修复与改进。

## 如何达成设计目的

由于使用 BOM（Bill of Materials）模式，所有 AWS SDK 子模块通过 `version.ref = "awssdk-bom"` 或直接导入 BOM 来对齐版本，因此升级只需在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中把 `awssdk-bom` 这一个版本号改掉，所有引用该 BOM 的模块版本即同步升级，无需逐个修改坐标。这是一个单行版本号变更的标准 dependabot PR。

## 修改详情

### [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml)

**修改目的**：把 AWS SDK BOM 版本号从 2.20.131 升级到 2.20.162。

**工作逻辑**：在 `[versions]` 段把 `awssdk-bom = "2.20.131"` 改为 `awssdk-bom = "2.20.162"`。该版本号被 `[libraries]` 段中 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，并通过 platform 机制约束所有 AWS SDK 子模块的版本。改一行即可让 s3、dynamodb、glue、kms、sts、iam、apache-client、netty-nio-client 等所有 AWS SDK 模块同步升级到 2.20.162 对应版本。

## 小结

本提交是 dependabot 自动把 AWS SDK BOM 从 2.20.131 升级到 2.20.162 的单行依赖升级，覆盖 31 个 patch 版本的累积 bugfix/改进，影响范围是 Iceberg `aws` 模块下所有 S3/DynamoDB/Glue/KMS 相关集成，属低风险的常规依赖维护。
