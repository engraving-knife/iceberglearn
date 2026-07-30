# 提交 1570：Build: Bump software.amazon.awssdk:bom from 2.29.45 to 2.29.50 (#11949)

## 提交信息

- **序号**：1570 / 4088
- **哈希**：a9d320eb8d3e42038f259cda1ed0a8b2c13c10d6
- **短哈希**：a9d320eb8
- **日期**：2025-01-13（Mon Jan 13 07:11:36 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.45 to 2.29.50 (#11949)
- **PR/Issue**：#11949

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖版本升级提交。AWS SDK for Java 2（`software.amazon.awssdk:bom`）是 Iceberg 依赖的核心第三方库之一，用于访问 S3、DynamoDB 等 AWS 服务（Iceberg 的 `aws` 模块、`s3` 文件 IO 实现、`glue` catalog、`dynamodb` catalog 等都依赖它）。Dependabot 监测到该 BOM 有新的 patch 版本发布（从 2.29.45 升至 2.29.50），自动创建 PR 把版本号向前滚动 5 个 patch 版本。

按 Dependabot 的分类，这属于 `version-update:semver-patch`（语义化版本的 patch 级升级），即仅修复 bug 和小改进，不引入破坏性 API 变更。AWS SDK 2.x 严格遵守语义化版本，patch 版本升级通常包含：bug 修复（如 S3 客户端的连接泄漏、重试逻辑问题）、性能改进、安全漏洞修补、新服务 API 的小幅支持补充等。这类升级风险极低，但能及时获得上游修复，避免积累技术债。

Iceberg 在 `gradle/libs.versions.toml` 中以 BOM（Bill of Materials）形式统一管理 AWS SDK 各子模块的版本，因此只需修改 BOM 版本号一处，所有 `awssdk-*` 子模块（如 `s3`、`sts`、`dynamodb`、`apache-client`、`url-connection-client` 等）都会自动对齐到 2.29.50。

## 如何达成设计目的

Dependabot 直接修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本声明，从 `2.29.45` 改为 `2.29.50`。Gradle 在构建时会通过版本目录（Version Catalog）机制把该 BOM 导入到所有依赖 `software.amazon.awssdk` 子模块的模块中，确保子模块版本一致。这是纯依赖版本号变更，不涉及任何代码、构建脚本逻辑或配置改动。

### 修改详情

#### `gradle/libs.versions.toml`
**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：把 `[versions]` 段中的 `awssdk-bom = "2.29.45"` 改为 `awssdk-bom = "2.29.50"`。该版本号被 `[libraries]` 段中的 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，并在各模块的 `build.gradle` 中通过 `platform(libs.awssdk.bom)` 导入为 BOM 平台依赖，从而统一约束所有 `software.amazon.awssdk:*` 子模块的版本。升级后，`aws`、`s3`、`glue`、`dynamodb` 等模块使用的 AWS SDK 子模块都会从 2.29.45 对齐到 2.29.50。

## 小结

- **成效**：把 AWS SDK for Java 2 的 BOM 版本从 2.29.45 升级到 2.29.50，获得上游 5 个 patch 版本的 bug 修复、性能改进和安全修补。属于低风险的常规依赖维护。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行修改。影响所有依赖 AWS SDK 的模块（`aws`、`s3`、`glue`、`dynamodb` 等）的传递依赖版本，但不改变任何 API 用法或代码逻辑。
- **回迁到 1.4.x 的注意事项**：这是依赖版本升级，与代码逻辑无关，1.4.x 是否回迁移取决于 1.4.x 当前的 AWS SDK 版本和是否需要上游修复。**通常可以回迁**（风险低），但需确认 1.4.x 的 `libs.versions.toml` 中 `awssdk-bom` 版本基线；若 1.4.x 已有更高版本或经过独立的安全审计，则不必强行对齐。若 1.4.x 用户遇到 AWS SDK 相关 bug，回迁此升级可能直接解决问题。
