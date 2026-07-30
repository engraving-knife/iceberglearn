# 提交 1426：Build: Bump software.amazon.awssdk:bom from 2.29.15 to 2.29.20 (#11639)

## 提交信息

- **序号**：1426 / 4088
- **哈希**：1f23dcd0e1c1a2360e2741f0ef562ca7fdd05eab
- **短哈希**：1f23dcd0e
- **日期**：2024-11-25（Mon Nov 25 08:49:38 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.15 to 2.29.20 (#11639)
- **PR/Issue**：#11639

## 总体目的

由 dependabot 自动发起的依赖升级，将 AWS SDK for Java 2 的 BOM（`software.amazon.awssdk:bom`）从 2.29.15 升级到 2.29.20。Iceberg 的 `aws` 模块（S3、Glue、DynamoDB 等集成）以及 `aws-bundle` 都通过该 BOM 统一管理 AWS SDK 各子模块的版本。2.29.20 是一个 patch 版本，包含 AWS SDK 的 bug 修复与稳定性改进。本次升级用于跟随上游补丁，避免 S3/Glue 等集成场景遇到已知问题。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本号即可。该 BOM 通过 `platform` 方式引入，统一约束所有 `software.amazon.awssdk:*` 子模块的版本，因此改一行即可同步升级所有 AWS SDK 子模块。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：

```toml
-awssdk-bom = "2.29.15"
+awssdk-bom = "2.29.20"
```

仅此一行变更。`awssdk-bom` 变量在 catalog 中被 `awssdk-s3accessgrants` 等依赖以及各 build.gradle 的 `platform` 引用，改一行即可同步所有 AWS SDK 子模块版本。

## 小结

- **成效**：跟随上游 patch 版本，获得 2.29.20 的 bug 修复与稳定性提升，S3/Glue/DynamoDB 等集成更可靠。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行，无源码或测试逻辑改动。
- **回迁到 1.4.x 的注意事项**：可以回迁，但需谨慎。AWS SDK 是 `aws` 模块和 `aws-bundle` 的核心依赖，patch 版本通常向后兼容，但仍建议回迁后跑一遍 `aws` 模块的集成测试（MinIO/S3 容器化测试）确认无回归。如果 1.4.x 已发布的 AWS SDK 版本与 2.29.20 在客户端行为上有差异（如 S3 客户端配置、重试策略等），需在 release notes 中提示。如果 1.4.x 当前 AWS SDK 版本已稳定通过 CI，则可视情况不强求回迁。
