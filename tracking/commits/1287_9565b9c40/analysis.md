# 提交 1287：Build: Bump software.amazon.awssdk:bom from 2.28.26 to 2.29.1 (#11400)

## 提交信息

- **序号**：1287 / 4088
- **哈希**：9565b9c401e87c9d6c31d91e44b439c1cb16d123
- **短哈希**：9565b9c40
- **日期**：2024-10-28（Mon Oct 28 10:06:12 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.28.26 to 2.29.1 (#11400)
- **PR/Issue**：#11400

## 总体目的

Iceberg 的 `aws` 模块（`iceberg-aws`）通过 AWS SDK for Java v2 与 S3、DynamoDB、Glue 等 AWS 服务交互。该 SDK 的版本由 BOM（`software.amazon.awssdk:bom`）统一管理，版本号在 `gradle/libs.versions.toml` 中以 `awssdk-bom` 声明。dependabot 检测到 AWS SDK v2 从 2.28.26 升级到 2.29.1（minor 发布），本提交把 BOM 版本对齐，使 `iceberg-aws` 使用的所有 AWS SDK 组件获得上游的新特性与 bug 修复。由于 BOM 管理整个 SDK 的版本矩阵，一行改动即覆盖 S3、STS、Glue、DynamoDB 等所有 aws-sdk 依赖。

## 如何达成设计目的

在版本目录把 `awssdk-bom = "2.28.26"` 改为 `awssdk-bom = "2.29.1"`。`aws` 模块通过 `platform("software.amazon.awssdk:bom:...")` 引入 BOM，所有未显式指定版本的 aws-sdk 依赖都由 BOM 统一锁定到 2.29.1 矩阵。这是 dependabot 自动生成的单行 minor 升级，无代码逻辑变更。本提交未同步升级 `awssdk-s3accessgrants`（仍为 2.2.0，由 #11405 单独处理）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 AWS SDK v2 BOM 从 2.28.26 升到 2.29.1。

**工作逻辑**：

```toml
-awssdk-bom = "2.28.26"
+awssdk-bom = "2.29.1"
```

相邻的 `awssdk-s3accessgrants = "2.2.0"` 本提交不动（由 #11405 升到 2.3.0）。`azuresdk-bom`、`caffeine` 等其他依赖不受影响。

## 小结

- **成效**：AWS SDK for Java v2 基线升级到 2.29.1（minor），`iceberg-aws`（S3/DynamoDB/Glue/STS 等）获得上游新特性与修复。
- **影响范围**：改动 1 个文件、1 行，仅构建依赖版本变更，无源代码变更。影响 `aws` 模块及其依赖 aws-sdk 的模块（如 `s3` 文件 IO、`glue` catalog、`dynamodb` 锁）的产物与测试 classpath。
- **回迁到 1.4.x 的注意事项**：
  - **谨慎回迁**：AWS SDK 是 `iceberg-aws` 的**产物依赖**（非仅测试），minor 版本升级可能引入行为变化（如 HTTP 客户端、重试、签名等）。回迁前应确认 1.4.x 的 aws 模块在 2.29.1 下无回归——重点跑 `TestS3FileIO`、`TestGlueCatalog`、`TestDynamoDBLockManager` 及 S3 集成测试。
  - **配套性**：建议与 #11405（`awssdk-s3accessgrants` 2.2.0 → 2.3.0）配套回迁，使 access grants 插件与 SDK BOM 主版本对齐；二者在 1.4.x 上需独立验证兼容性。
  - **风险**：中（产物依赖 minor 升级）。若 1.4.x 已发布且 aws 行为稳定，可不强求回迁；若回迁，务必跑 aws 相关集成测试。
