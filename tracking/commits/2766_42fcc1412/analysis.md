# 提交 2766：Build: Bump software.amazon.awssdk:bom from 2.35.5 to 2.35.10 (#14368)

## 提交信息

- **序号**：2766 / 4088
- **哈希**：42fcc14124e5f3e3ecc0739263dc650047a450ea
- **短哈希**：42fcc1412
- **日期**：2025-10-18 22:22:48 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.35.5 to 2.35.10 (#14368)
- **PR/Issue**：#14368

## 总体目的

本提交由 dependabot 自动生成，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）从 2.35.5 升级到 2.35.10（补丁版本升级）。

背景在于：Iceberg 的 `aws` 模块（S3 文件 IO、Glue catalog、DynamoDB catalog 等）依赖 AWS SDK for Java 2.x。通过引入 `software.amazon.awssdk:bom` BOM，可以统一管理所有 AWS SDK 模块的版本，避免版本不一致。dependabot 定期检查 BOM 版本更新并提交 PR。本次是 2.35.x 系列内的补丁升级（2.35.5 → 2.35.10），跨越 5 个补丁版本，属于 `version-update:semver-patch`，通常包含 bug 修复、安全补丁和小幅改进，不引入破坏性 API 变化。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中将 `awssdk-bom` 版本引用从 `2.35.5` 改为 `2.35.10`。由于使用 BOM 管理版本，所有 AWS SDK 模块的版本会随之统一升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.35.5"` 改为 `awssdk-bom = "2.35.10"`。所有引用 `awssdk-bom` 版本的 AWS SDK 模块（如 s3、glue、dynamodb、sts 等）将统一升级到 2.35.10 对应的版本。

## 总结

本提交是 dependabot 自动发起的 AWS SDK BOM 补丁升级（2.35.5 → 2.35.10）。作为 semver-patch 级别升级，预期包含 bug 修复和安全补丁，对 Iceberg 的 AWS 集成模块（S3、Glue、DynamoDB 等）无破坏性影响。建议升级后运行 AWS 相关集成测试验证。
