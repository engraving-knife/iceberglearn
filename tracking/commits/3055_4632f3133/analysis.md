# 提交 3055：Build: Bump software.amazon.awssdk:bom from 2.40.13 to 2.40.16 (#14936)

## 提交信息

- **序号**：3055 / 4088
- **哈希**：4632f3133efbf6b8232ed9526de7142fd50e1aa6
- **短哈希**：4632f3133
- **日期**：2025-12-27
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.40.13 to 2.40.16 (#14936)
- **PR/Issue**：#14936

## 总体目的

这是一个 Dependabot 自动依赖升级提交。`software.amazon.awssdk:bom` 是 AWS SDK for Java v2 的物料清单（BOM），通过 Gradle 版本目录以 `awssdk-bom` 引入，用于统一管理所有 AWS SDK 模块（如 S3、DynamoDB、STS、KMS 等）的版本，避免各模块版本不一致。Iceberg 的 `iceberg-aws` 模块依赖该 BOM 提供 `S3FileIO`、`GlueCatalog` 等实现所需的 AWS 客户端。

本次升级从 `2.40.13` 到 `2.40.16`，属于 **semver-patch**（补丁）升级（2.40 系列内的 3 个补丁迭代）。Dependabot 将其归类为 `direct:production` 依赖、`version-update:semver-patch`。补丁升级通常只包含 bug 修复与小改进，预期不引入破坏性变更。升级动机是跟进 AWS SDK 的 bug 修复与安全补丁，保持 S3 等云存储集成的稳定性。

## 如何达成设计目的

Dependabot 直接修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本钉，从 `2.40.13` 改为 `2.40.16`，所有通过该 BOM 管理版本的 AWS SDK 模块会随之统一升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.40.13"` 改为 `awssdk-bom = "2.40.16"`。该 BOM 在版本目录中被引用，控制 `iceberg-aws` 等模块中所有 `software.amazon.awssdk:*` 依赖（S3、STS、Glue、KMS、dynamodb 等）的版本。升级后这些模块统一使用 2.40.16 版本的客户端。预期影响：获得 2.40.14~2.40.16 累积的 bug 修复（如 S3 客户端在重试、签名、分块上传等场景的修正）与潜在安全补丁；属补丁级升级，对 `S3FileIO`、`GlueCatalog` 等运行时行为保持兼容。

## 总结

此提交是 AWS SDK BOM 的补丁级依赖升级，把 `awssdk-bom` 从 2.40.13 提升到 2.40.16，统一刷新 Iceberg AWS 集成所依赖的全部 AWS SDK 模块版本，获取上游 bug 修复与安全补丁，属于低风险的运行时依赖维护。
