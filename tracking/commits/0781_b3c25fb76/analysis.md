# 提交 0781：Build: Bump software.amazon.awssdk:bom from 2.25.50 to 2.25.57 (#10367)

## 提交信息

- **序号**：0781 / 4088
- **哈希**：b3c25fb7608934d975a054b353823ca001ca3742
- **短哈希**：b3c25fb76
- **日期**：2024-05-23 09:18:20 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.50 to 2.25.57 (#10367)
- **PR/Issue**：#10367

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials）依赖版本从 `2.25.50` 升级到 `2.25.57`。这是一次 semver-patch（补丁版本）升级，属于常规的依赖维护工作，目的是引入 AWS SDK 近期的 bug 修复与小改进，同时保持 API 兼容性。由于使用的是 BOM，本次升级会同时统一管控所有 `software.amazon.awssdk:*` 模块（如 s3、sts、kms、dynamodb 等）的版本，确保 Iceberg AWS 模块所依赖的 AWS SDK 各组件版本一致。

## 如何达成设计目的

Iceberg 在 `gradle/libs.versions.toml` 中集中管理第三方依赖版本，其中 `awssdk-bom` 定义了 AWS SDK BOM 的版本号。Gradle 通过引入该 BOM（`platform("software.amazon.awssdk:bom:<version>")`）来对齐所有 AWS SDK 子模块的版本，避免显式地为每个子模块声明版本。

Dependabot 的工作流程是：
1. 定期扫描 `gradle/libs.versions.toml` 中声明的依赖版本。
2. 比对 Maven Central 上 `software.amazon.awssdk:bom` 的最新发布版本。
3. 发现 `2.25.57` 较当前 `2.25.50` 新，且属于 patch 级别升级（`2.25.50` → `2.25.57`，仅修订号递增），在兼容性范围内。
4. 提交 PR 将 `awssdk-bom` 的值改为 `2.25.57`，并在 commit message 中附带 `updated-dependencies` 元数据（依赖名、依赖类型 `direct:production`、更新类型 `version-update:semver-patch`），供 GitHub 安全/依赖洞察使用。

由于是 BOM 升级，无需修改任何业务代码，构建系统会自动解析到新版本的所有 AWS SDK 子模块。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本从 `2.25.50` 提升到 `2.25.57`。

**修改内容**：

```toml
- awssdk-bom = "2.25.50"
+ awssdk-bom = "2.25.57"
```

仅此一行变更。该文件第 28 行附近集中声明各依赖版本，`awssdk-bom` 与 `azuresdk-bom`、`awssdk-s3accessgrants`、`caffeine` 等并列。本次升级不触及 `awssdk-s3accessgrants = "2.0.0"`（S3 访问授权插件单独管理版本，不随 BOM 走）。

**影响链路**：所有引用 `libs.awssdk.bom` 的 Gradle 模块（主要是 `aws/` 子项目及其集成测试）在构建时会拉取 `2.25.57` 版本的 AWS SDK，包括 `s3`、`kms`、`sts`、`apache-client`/`netty-nio-client` 等。运行时行为上，2.25.50 → 2.25.57 之间的 7 个 patch 版本通常包含若干缺陷修复（如 S3 客户端重试、异步客户端资源泄漏、HTTP 客户端兼容性等）和小幅性能/稳定性改进，但无破坏性 API 变更。

## 小结

- **成效**：将 AWS SDK BOM 从 `2.25.50` 升级到 `2.25.57`，获取近期的 patch 级缺陷修复与稳定性改进，保持 Iceberg AWS 模块依赖的时效性。变更最小化（单行 toml 修改），无业务代码改动，API 兼容。
- **影响范围**：影响所有依赖 `software.amazon.awssdk:*` 的模块，主要是 `aws/` 子项目（`S3FileIO`、`AssumeRoleAwsClientFactory`、`GlueCatalog`、`DynamoDbLockManager` 等）及其集成测试。由于是 patch 级 BOM 升级，对 Iceberg 自身代码无功能性影响，外部用户行为不变。
- **回迁注意事项**：
  1. 这是纯依赖版本变更，回迁到 1.4.x 分支无任何代码冲突风险，直接 cherry-pick 即可。
  2. 回迁前需确认 1.4.x 分支的 `gradle/libs.versions.toml` 中 `awssdk-bom` 当前值；若 1.4.x 已有其他 Dependabot 升级（如已升到 `2.25.55`），cherry-pick 仍会将其改为 `2.25.57`，属正常升级。
  3. 若 1.4.x 分支有针对 AWS SDK 2.25.50~2.25.56 之间特定行为的临时 workaround，升级后应一并清理。
  4. AWS SDK 2.25.x 系列内部 patch 升级一般无需调整代码，但建议回迁后跑一遍 `aws/` 模块的单元测试与（如有条件）S3 集成测试以确认无回归。
