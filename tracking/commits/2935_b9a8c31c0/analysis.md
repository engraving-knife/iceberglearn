# 提交 2935：Build: Bump software.amazon.awssdk:bom from 2.39.4 to 2.39.5 (#14718)

## 提交信息

- **序号**：2935 / 4088
- **哈希**：b9a8c31c048a03a29f2014ecea01cac4b5078c33
- **短哈希**：b9a8c31c0
- **日期**：2025-11-29 23:41:16 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.39.4 to 2.39.5 (#14718)
- **PR/Issue**：#14718

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，目的是将 AWS SDK for Java 的 BOM（Bill of Materials）从 `2.39.4` 升级到 `2.39.5`。

`software.amazon.awssdk:bom` 是 AWS SDK for Java 2.x 的版本清单（BOM），通过 Gradle 的版本目录（`gradle/libs.versions.toml`）以 `platform`/`bom` 方式引入，用于统一管理所有 AWS SDK 子模块（如 S3、DynamoDB、Glue、KMS、STS 等）的版本，确保各模块版本相互兼容、避免冲突。Iceberg 的 `aws` 模块（`S3FileIO`、`DynamoDbLockManager`、`GlueCatalog` 等集成）以及 `aws-bundle` 依赖于此 BOM 来协调 AWS 相关客户端的版本。

Dependabot 元数据显示此次升级类型为 `version-update:semver-patch`，即从 2.39.4 到 2.39.5 仅是补丁版本升级。在 AWS SDK 2.x 的版本策略中，同一次版本（2.39.x）内的补丁升级通常只包含 bug 修复与小的稳定性改进，不引入破坏性 API 变更，因此升级风险极低、预期影响为获得最新的缺陷修复与安全补丁。

## 如何达成设计目的

Dependabot 修改版本目录文件 `gradle/libs.versions.toml`，将 `awssdk-bom` 的版本引用从 `2.39.4` 改为 `2.39.5`。该变量通过 `version.ref = "awssdk-bom"` 被 BOM 依赖条目引用，因此一处改动即可让所有依赖该 BOM 的 AWS SDK 模块统一升到新版本。改动仅 1 行。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS SDK BOM 版本从 2.39.4 升级到 2.39.5。

**工作逻辑**：

```toml
# 旧
awssdk-bom = "2.39.4"
# 新
awssdk-bom = "2.39.5"
```

该变量在文件下方通过 `{ module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 被 `awssdk-bom` 依赖条目引用。Gradle 在解析时会以该 BOM 为平台依赖统一管理 AWS SDK 各子模块版本，因此把版本号改为 `2.39.5` 后，Iceberg 中所有 AWS SDK 相关模块（S3、DynamoDB、Glue、KMS、STS 等）都会在下次构建时使用 2.39.5 提供的版本，获得该补丁版本的 bug 修复与安全更新。

## 总结

该提交将 Iceberg 版本目录中 AWS SDK for Java BOM 的版本从 2.39.4 升级到 2.39.5（补丁级升级），使所有 AWS SDK 子模块获得最新的缺陷修复与安全补丁。改动仅 1 行，依赖 BOM 机制统一传导版本，升级风险极低。
