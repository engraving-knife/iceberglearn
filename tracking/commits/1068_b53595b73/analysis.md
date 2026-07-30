# 提交 1068：Build: Bump software.amazon.awssdk:bom from 2.27.2 to 2.27.7 (#10961)

## 提交信息

- **序号**：1068 / 4088
- **哈希**：b53595b73886f14cb4b71c25b760ac988afbd310
- **短哈希**：b53595b73
- **日期**：2024-08-19 16:47:49 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.27.2 to 2.27.7 (#10961)
- **PR/Issue**：#10961

## 总体目的

这是一次由 GitHub Dependabot 自动发起的依赖升级，目标是把 Iceberg 仓库依赖的 AWS SDK for Java v2 的 BOM（`software.amazon.awssdk:bom`）从 `2.27.2` 升级到 `2.27.7`。AWS SDK BOM 用于统一管理所有 AWS SDK 模块（S3、DynamoDB、Glue、STS、KMS 等）的版本，保证它们彼此兼容。

Iceberg 的 `aws` 模块以及多个集成测试（S3FileIO、DynamoDB 锁、Glue Catalog、S3 access grants 等）依赖 AWS SDK，保持 SDK 版本更新有助于获取 AWS 服务端点行为变化对应的客户端修复、性能改进以及安全补丁。AWS SDK v2 在 2.27.x 系列内会持续发布 patch 版本，每个 patch 通常包含若干 bug 修复与小特性。

本次升级属于 SemVer 中的 patch 升级（2.27.2 → 2.27.7），按 AWS SDK v2 的兼容性承诺，对 API 是二进制兼容的，预期不会破坏现有调用方。

## 如何达成设计目的

实现方式非常直接：仅在 Gradle version catalog 文件 `gradle/libs.versions.toml` 中，把 `awssdk-bom` 这一行的版本字符串从 `"2.27.2"` 改为 `"2.27.7"`。所有通过 BOM 引入的 AWS SDK 子模块会自动统一到 2.27.7 版本，无需在多个 build.gradle 中重复修改。注意仓库中还有一个独立变量 `awssdk-s3accessgrants = "2.0.0"`，它不通过 BOM 管理而是单独指定版本（因为该组件目前还处于独立的 2.0.0 线），本次升级不影响它。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK for Java v2 BOM 版本号从 `2.27.2` 升级到 `2.27.7`，统一刷新所有 AWS SDK 子模块版本。

**工作逻辑**：

```diff
-awssdk-bom = "2.27.2"
+awssdk-bom = "2.27.7"
```

修改前后相邻行 `azuresdk-bom`、`awssdk-s3accessgrants` 保持不变，仅 BOM 版本一行被替换；所有依赖该 BOM 的 AWS SDK 模块（如 s3、sts、dynamodb、glue、kms 等）会自动随之升级到 2.27.7。

## 小结

- **成效**：把 AWS SDK for Java v2 BOM 从 2.27.2 升级到 2.27.7，统一刷新所有 AWS SDK 子模块版本，获取 5 个 patch 版本累积的 bug 修复与改进。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，1 行变更；主要影响 `aws` 模块及依赖 AWS SDK 的集成测试，属 patch 级升级，预期无破坏性影响。
- **回迁到 1.4.x 的注意事项**：Dependabot 类的 patch 升级回迁到 1.4.x 通常风险很低，可以直接 cherry-pick；需确认 1.4.x 分支的 `gradle/libs.versions.toml` 中 `awssdk-bom` 行的基线版本相近（例如也在 2.27.x 系列），并关注 1.4.x 是否对 AWS SDK 有额外的版本约束或已知兼容性问题。AWS SDK 在 patch 版本之间通常很稳定，但偶有服务客户端行为微调，回迁后建议跑一遍 AWS 相关集成测试。
