# 提交 3193：Build: Bump software.amazon.awssdk:bom from 2.41.14 to 2.41.19 (#15203)

## 提交信息

- **序号**：3193 / 4088
- **哈希**：8b4e22fe5e8efa3bcc7ebcd4d245f6e2174a8777
- **短哈希**：8b4e22fe5
- **日期**：2026-01-31
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.41.14 to 2.41.19 (#15203)
- **PR/Issue**：#15203

## 总体目的

这是 Dependabot 自动发起的依赖升级，将 AWS SDK for Java v2 的 BOM 从 `2.41.14` 升至 `2.41.19`。`software.amazon.awssdk:bom` 是 AWS SDK v2 的总 BOM，统一管理 S3、Glue、DynamoDB、KMS、STS 等所有 AWS 服务客户端模块的版本。在 Iceberg 中，AWS 模块是体量最大的存储/目录集成之一：S3 FileIO 用 S3 客户端读写数据与元数据，Glue Catalog 用 Glue 客户端管理表元数据，DynamoDB 用于目录锁与 committing 表，KMS 用于加密。因此该 BOM 影响面极广，属关键生产依赖。

本次为语义化版本的 **patch** 升级（`2.41.14` → `2.41.19`，跨 5 个 patch 版本）。按 SemVer 约定，AWS SDK v2 在同一 `2.41.x` 序列内仅提供向后兼容的缺陷修复，不引入新 API 也不破坏行为。预期效果是获取 AWS SDK 在 `2.41.15`~`2.41.19` 之间累积的修复（可能涵盖 S3 传输、重试、异步客户端、签名或服务模型更新等），保持 Iceberg 全部 AWS 集成的稳定性。由于升级停留在同一 patch 序列，与同批升级的 `awssdk-s3accessgrants`（`2.4.0`，构建于 AWS SDK v2 之上）应保持兼容。

## 如何达成设计目的

Dependabot 仅修改版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本变量。该变量经 `[libraries]` 段的 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，并以平台依赖形式导入到 AWS 模块依赖图，Gradle 解析时自动用新 BOM 统一管理全部 AWS SDK 模块版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS SDK for Java v2 BOM 版本从 `2.41.14` 提升至 `2.41.19`。

**工作逻辑**：
将 `[versions]` 段中的 `awssdk-bom = "2.41.14"` 改为 `awssdk-bom = "2.41.19"`。由于 AWS 各客户端模块经 BOM 统一管理版本，这一处改动即可让 S3、Glue、DynamoDB、KMS 等全部 AWS SDK 模块同步升至 `2.41.19`，从而把上游 patch 修复纳入 Iceberg 全部 AWS 集成。

## 总结

本次提交通过单点修改 BOM 版本变量，将 AWS SDK for Java v2 patch 升级到 `2.41.19`，为 Iceberg 广泛的 AWS 集成（S3、Glue、DynamoDB、KMS 等）引入上游累积修复，属低风险的常规依赖维护，并与同期升级的 S3 Access Grants 插件保持版本协同。
