# 提交 2583：Build: Bump software.amazon.awssdk:bom from 2.32.29 to 2.33.0 (#13955)

## 提交信息

- **序号**：2583 / 4088
- **哈希**：eb36ea62abfbb7a9accd36ae08ae2c64800fbab6
- **短哈希**：eb36ea62a
- **日期**：2025-09-01 05:11:50 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.32.29 to 2.33.0 (#13955)
- **PR/Issue**：#13955

## 总体目的

此次提交由 Dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials）版本从 2.32.29 升级到 2.33.0。AWS SDK BOM 用于统一管理 AWS SDK 各模块（如 S3、DynamoDB、Glue、STS 等）的版本，确保各模块间兼容性。Iceberg 在 S3 表目录、Glue Catalog、DynamoDB 锁等集成中使用 AWS SDK。

此次升级为 semver-minor 版本更新（2.32.29 -> 2.33.0），属于常规依赖维护，用于获取 AWS SDK 的最新功能、bug 修复和安全补丁。Dependabot 自动检测到新版本发布并提交 PR，CI 通过后合并。

## 如何达成设计目的

- 在 `gradle/libs.versions.toml` 版本目录中将 `awssdk-bom` 的版本值从 `2.32.29` 改为 `2.33.0`。
- 由于使用 BOM 管理方式，所有引用 `awssdk-bom` 的模块版本会自动跟随升级，无需逐个修改模块依赖声明。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.32.29"` 改为 `awssdk-bom = "2.33.0"`，所有通过 `platform(...)` 引用该 BOM 的 AWS SDK 模块版本随之统一升级。

## 总结

一次 Dependabot 自动依赖升级提交，将 AWS SDK for Java BOM 从 2.32.29 升级到 2.33.0（semver-minor 更新），通过修改版本目录 `libs.versions.toml` 中的一行完成。属于常规依赖维护，获取 AWS SDK 最新改进与修复。
