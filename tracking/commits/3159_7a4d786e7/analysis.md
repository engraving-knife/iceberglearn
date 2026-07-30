# 提交 3159：Build: Bump software.amazon.awssdk:bom from 2.41.10 to 2.41.14 (#15134)

## 提交信息

- **序号**：3159 / 4088
- **哈希**：7a4d786e713cd4e120b474f235bd2a243fc1f2a7
- **短哈希**：7a4d786e7
- **日期**：2026-01-26 09:34:21 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.41.10 to 2.41.14
- **PR/Issue**：#15134

## 总体目的

本提交是 Dependabot 自动发起的依赖升级，将 AWS SDK for Java 的 BOM（`software.amazon.awssdk:bom`）从 `2.41.10` 升级到 `2.41.14`。该 BOM 由 AWS 官方维护，统一管理 AWS SDK v2 各客户端构件的版本。Iceberg 的 AWS 集成模块（`aws` 模块、`s3` 文件 IO、Glue Catalog、DynamoDB 锁管理器、KMS 加密、STS/LakeFormation 集成等）通过 `awssdk-bom` 统一引入并管理这些 AWS 客户端 SDK 版本，避免构件间版本不一致。版本声明位于 `gradle/libs.versions.toml` 的 `awssdk-bom` 属性，并通过 `version.ref` 供多个 AWS 相关依赖引用。

本次升级为补丁（patch）级别升级（`version-update:semver-patch`，2.41.10 → 2.41.14），跨 4 个 patch 版本。AWS SDK v2 的 patch 升级通常只包含 bug 修复与小幅改进，不引入 API 破坏性变更，预期对 Iceberg 的 S3/Glue/DynamoDB 等 AWS 集成保持二进制与行为兼容。Dependabot 在提交信息中附带了上游 changelog 与 commits 对比链接。

## 如何达成设计目的

直接在 `gradle/libs.versions.toml` 中将 `awssdk-bom` 属性从 `2.41.10` 改为 `2.41.14`，所有通过该 BOM 引入 AWS SDK 客户端构件的模块（s3、glue、dynamodb、kms、sts、lakeformation 等）自动跟随升级，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 awssdk-bom 版本从 2.41.10 提升到 2.41.14。

**工作逻辑**：将 `awssdk-bom = "2.41.10"` 修改为 `awssdk-bom = "2.41.14"`。该 BOM 统一管理 Iceberg AWS 集成模块所依赖的 AWS SDK v2 客户端 SDK 版本。升级到 2.41.14 为 semver-patch 级别，获取上游 bug 修复与改进，预期对 S3 文件 IO、Glue Catalog、DynamoDB 锁、KMS 加密等集成保持 API 与行为兼容。

## 总结

本提交通过将 AWS SDK for Java BOM 从 2.41.10 升级到 2.41.14，刷新 Iceberg AWS 集成模块所依赖的 AWS 客户端 SDK 版本以获取上游 patch 修复，保持云集成依赖的及时更新；作为 semver-patch 升级，预期对 S3/Glue/DynamoDB/KMS 等集成的 API 与行为保持向后兼容。
