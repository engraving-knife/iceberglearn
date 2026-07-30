# 提交 0681：升级 AWS SDK for Java BOM 依赖版本

## 提交信息
- **序号**：0681 / 4088
- **哈希**：47825ffd8b66d851baaa5fee0b55de8081192024
- **短哈希**：47825ffd8
- **日期**：2024-04-14
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.21 to 2.25.31 (#10138)
- **PR/Issue**：#10138

## 总体目的

这是 Dependabot 自动生成的依赖版本升级提交。AWS SDK for Java v2 的 BOM（Bill of Materials）从 `2.25.21` 升级到 `2.25.31`。

Iceberg 项目在 `gradle/libs.versions.toml` 中通过版本目录（version catalog）集中管理依赖版本。`awssdk-bom` 作为 AWS SDK 全家桶的统一版本锚点，定义了 S3 客户端、DynamoDB、STS、KMS 等所有 AWS SDK 模块的统一版本号。当 BOM 版本提升时，Iceberg 中所有从该 BOM 继承版本号的 AWS SDK 模块都会同步升级到 2.25.31。

Dependabot 监控到上游 `software.amazon.awssdk:bom` 发布了从 2.25.21 到 2.25.31 共 10 个 patch 版本迭代，自动创建 PR 以保持依赖处于较新状态。patch 级别（semver-patch）升级通常只包含 bug 修复与小的功能改进，理论上不引入破坏性变更。

## 如何达成设计目的

Dependabot 通过单个版本号字符串替换完成升级：在 `gradle/libs.versions.toml` 中将 `awssdk-bom = "2.25.21"` 改为 `awssdk-bom = "2.25.31"`。由于 Iceberg 使用 Gradle 版本目录声明依赖，BOM 版本只需在版本目录中改一处，所有引用 `awssdk-bom` 的模块（如 `aws-bundle`、`aws` 模块、S3 access grants 等）都会通过 Gradle 的 BOM 依赖机制自动应用新版本。这种集中式版本管理方式使得依赖升级成本极低，也是 Dependabot 能以最小改动完成升级的前提。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：升级 AWS SDK BOM 版本号。
**工作逻辑**：仅将第 31 行的 `awssdk-bom = "2.25.21"` 修改为 `awssdk-bom = "2.25.31"`。该文件是 Gradle 版本目录，定义项目所有依赖的统一版本号。`awssdk-bom` 条目对应 AWS SDK v2 的 BOM 依赖，被 `libs.versions.toml` 下游各 AWS 相关模块通过 `platform(...)` 引用，保证所有 AWS SDK 子模块版本一致。

## 小结
- **成效**：成功完成版本升级，改动最小化（1 行修改）。
- **影响范围**：影响所有依赖 AWS SDK 的模块（`aws-bundle`、`aws`、S3 相关集成测试等），但由于是 patch 级别升级，预计行为兼容。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支若已包含 AWS SDK 集成，可直接 cherry-pick；需确认 1.4.x 分支的 `libs.versions.toml` 中仍为 2.25.x 系列以避免与更高版本冲突。属于纯依赖升级，无代码逻辑变更，回迁风险极低。
