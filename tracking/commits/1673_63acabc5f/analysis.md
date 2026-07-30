# 提交 1673：Build: Bump software.amazon.awssdk:bom from 2.30.6 to 2.30.11 (#12156)

## 提交信息

- **序号**：1673 / 4088
- **哈希**：63acabc5f3490849f7f2bde49d61b58bbcb864cc
- **短哈希**：63acabc5f
- **日期**：2025-02-02（Sun Feb 2 09:39:33 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.30.6 to 2.30.11 (#12156)
- **PR/Issue**：#12156

## 总体目的

Dependabot 自动升级，把 AWS SDK for Java 2.x 的 BOM（Bill of Materials）从 `2.30.6` 升到 `2.30.11`。`2.30.6 → 2.30.11` 是同一 minor（2.30.x）内的 patch 升级，按 AWS SDK 的版本约定仅含 bug 修复与服务端 API 更新，无破坏性 API 变更。

AWS SDK for Java 是 Iceberg AWS 集成模块（`iceberg-aws`）的核心依赖，用于 S3（数据文件读写）、Glue（Catalog）、DynamoDB（锁）、KMS（加密）等 AWS 服务的客户端调用。`awssdk-bom` 作为 BOM 统一管理 `s3`、`sts`、`glue`、`dynamodb`、`kms` 等子模块版本，确保彼此兼容。Iceberg 还支持 S3 Access Grants（`awssdk-s3accessgrants`），该插件版本独立管理，不受 BOM 升级影响。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本号。各 build 脚本通过 `libs.awssdk.bom` 引用 BOM，子模块版本由 BOM 统一解析，单点修改即整体平移。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

```diff
-awssdk-bom = "2.30.6"
+awssdk-bom = "2.30.11"
```

## 小结

- **成效**：AWS SDK for Java 全家桶从 2.30.6 升到 2.30.11，获取上游 patch 修复（通常含 S3/Glue/DynamoDB 等服务的 bug 修复与 AWS 区域端点更新）。
- **影响范围**：`iceberg-aws` 模块及依赖该模块的 Spark/Flink 集成。由于是 patch 升级，无 API 变更风险。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick。需确认 1.4.x 的 `gradle/libs.versions.toml` 中 `awssdk-bom` 命名一致。建议回迁后回归 S3 读写、Glue Catalog、DynamoDB 锁等路径的测试。若 1.4.x 上有针对 AWS SDK 特定版本的 workaround，升级后应检查是否仍需要。AWS SDK patch 升级通常无破坏，但仍建议跑一遍 `TestS3FileIO`、`TestGlueCatalog` 等测试。
