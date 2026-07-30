# 提交 2057：Build: Bump software.amazon.awssdk:bom from 2.29.52 to 2.31.30 (#12905)

## 提交信息

- **序号**：2057 / 4088
- **哈希**：0580793c472333ddbe19e043843a0a3384467ea8
- **短哈希**：0580793c4
- **日期**：2025-04-30 10:56:06 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.52 to 2.31.30 (#12905)
- **PR/Issue**：#12905

## 总体目的

由 Dependabot 自动发起的依赖升级，将 AWS SDK for Java 的 BOM（`software.amazon.awssdk:bom`）从 `2.29.52` 升级到 `2.31.30`。该 BOM 用于统一管理 AWS SDK 各模块（S3、DynamoDB、KMS 等）的版本，Iceberg 在 S3 集成、S3 access grants 等模块依赖此 BOM。升级可获取新版本中的缺陷修复、性能改进与安全补丁，属于 semver-minor 级别的版本更新。

## 如何达成设计目的

Gradle 版本目录（version catalog）`gradle/libs.versions.toml` 集中管理依赖版本。本次仅修改 `awssdk-bom` 这一项的版本字符串，所有引用该 BOM 的模块会自动跟随新版本。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：
将 `awssdk-bom = "2.29.52"` 改为 `awssdk-bom = "2.31.30"`。该别名被项目用于导入 AWS SDK BOM 平台依赖与 `awssdk-s3accessgrants` 等模块，升级后所有相关模块版本统一对齐到 2.31.30。

## 总结

Dependabot 自动升级 AWS SDK BOM 版本（2.29.52 → 2.31.30），单行配置变更，用于获取 SDK 的修复与改进。
