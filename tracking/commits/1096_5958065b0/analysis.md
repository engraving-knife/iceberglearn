# 提交 1096：Build: Bump software.amazon.awssdk:bom from 2.27.7 to 2.27.12 (#11006)

## 提交信息

- **序号**：1096 / 4088
- **哈希**：5958065b05c41cc5cd61292ab43d7f9894de06b4
- **短哈希**：5958065b0
- **日期**：2024-08-25 13:58:53 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.27.7 to 2.27.12 (#11006)
- **PR/Issue**：#11006

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）版本从 2.27.7 升级到 2.27.12。AWS SDK BOM 用于统一管理所有 AWS SDK 模块的版本，确保各模块之间版本兼容。这是一次 patch 级别（semver-patch）的版本升级，通常包含 bug 修复与小改进，不引入破坏性变更。

Dependabot 定期扫描项目依赖，发现新版后自动提交 PR。本次升级覆盖 2.27.8 到 2.27.12 共 5 个 patch 版本的累积更新，目的是让 Iceberg 的 AWS 集成模块使用最新的 SDK 修复版本，避免已知的 SDK bug。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本属性，将 `2.27.7` 改为 `2.27.12`。所有引用该 BOM 的模块（`iceberg-aws`、`iceberg-aws-bundle`、`iceberg-kafka-connect-runtime` 等）会自动继承新版本，无需逐个修改模块依赖声明。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：将 `awssdk-bom = "2.27.7"` 改为 `awssdk-bom = "2.27.12"`。该属性在构建脚本中通过 `platform(libs.awssdk.bom)` 引用，作为所有 `software.amazon.awssdk:*` 模块的版本基准。修改后，所有 AWS SDK 模块（s3、glue、kms、iam、auth、http-auth-aws-crt 等）的版本统一提升到 2.27.12。

## 小结

- **成效**：将 AWS SDK BOM 从 2.27.7 升级到 2.27.12，使 Iceberg 的 AWS 集成模块获得最新的 SDK patch 修复。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一行，影响所有依赖 `awssdk-bom` 的模块（iceberg-aws、iceberg-aws-bundle、kafka-connect-runtime）。
- **回迁到 1.4.x 的注意事项**：属于依赖版本 patch 升级，向后兼容，**可安全回迁到 1.4.x**。回迁时需确认 1.4.x 的 AWS SDK 基线版本是否已在 2.27.x 系列；若 1.4.x 使用更早的 2.x 版本，建议升级到对应 patch 最新版而非强制对齐 2.27.12。注意 1095 提交新增的 `http-auth-aws-crt` 模块依赖该 BOM，若 1.4.x 未回迁 1095 则需单独确认该模块在目标 BOM 版本下可用。
