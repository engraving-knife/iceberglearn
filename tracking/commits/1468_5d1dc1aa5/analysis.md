# 提交 1468：Build: Bump software.amazon.awssdk:bom from 2.29.23 to 2.29.29 (#11723)

## 提交信息

- **序号**：1468 / 4088
- **哈希**：5d1dc1aa52dd92180bf0f3bc0b9eb57dbd0690e3
- **短哈希**：5d1dc1aa5
- **日期**：2024-12-09（Mon Dec 9 11:35:58 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.23 to 2.29.29 (#11723)
- **PR/Issue**：#11723

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。`software.amazon.awssdk:bom` 是 AWS SDK for Java v2 的 BOM（Bill of Materials），通过导入该 BOM 统一管理所有 AWS SDK 模块（S3、Glue、DynamoDB、STS、KMS 等）的版本，避免模块间版本不一致。Iceberg 的 S3 catalog、Glue catalog、DynamoDB catalog 等都依赖 AWS SDK，因此该 BOM 版本直接决定 Iceberg 与 AWS 交互的客户端版本。

Dependabot 检测到 AWS SDK 从 2.29.23 升级到 2.29.29（6 个 patch 版本），属于 `direct:production` 类型、`version-update:semver-patch` 级别的升级。本提交的目的是跟进 AWS SDK 上游 patch 修复，通常包含 S3 客户端 bug 修复、HTTP 客户端稳定性改进以及潜在安全问题修复。

## 如何达成设计目的

实现方式是单行版本号替换：在 `gradle/libs.versions.toml` 的版本目录中，把 `awssdk-bom = "2.29.23"` 改为 `awssdk-bom = "2.29.29"`。引用该版本变量的 BOM 依赖会自动同步升级，所有从 BOM 继承版本的 AWS SDK 模块随之统一到 2.29.29。Dependabot 在提交说明中附带了更新依赖元数据（updated-dependencies），便于追溯升级类型。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 AWS SDK BOM 版本变量从 2.29.23 升级到 2.29.29。

**工作逻辑**：在 `[versions]` 段中：
```
-awssdk-bom = "2.29.23"
+awssdk-bom = "2.29.29"
```
该变量被 `[libraries]` 段中 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，BOM 通过 `platform`/`enforcedPlatform` 方式导入到各模块的依赖配置中，从而统一所有 `software.amazon.awssdk:*` 模块的版本。相邻行（arrow、avro、assertj-core、awaitility、azuresdk-bom、awssdk-s3accessgrants、caffeine 等）保持不变。

## 小结

- **成效**：将 AWS SDK BOM 从 2.29.23 升级到 2.29.29，统一提升所有 AWS SDK 模块版本，跟进上游 6 个 patch 版本的修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动。影响所有依赖 AWS SDK 的模块（S3/Glue/DynamoDB/STS/KMS 等 catalog 与 IO 实现）的运行时版本，但不修改产品代码逻辑。
- **回迁到 1.4.x 的注意事项**：本提交是依赖版本升级，回迁风险较低但需谨慎。需注意：(1) 1.4.x 分支的 `libs.versions.toml` 中 `awssdk-bom` 版本可能与 main 不同（1.4.x 通常使用更保守的版本，可能仍在 2.x 较早的 patch 系列），cherry-pick 时直接同步版本号即可；(2) AWS SDK 2.29.x 系列内部 patch 升级一般 API 兼容，但仍建议回迁后跑一遍 S3/Glue 相关集成测试确认无回归；(3) AWS SDK 升级有时会带来 HTTP 客户端行为微调（重试、超时、签名），生产环境升级前需验证。整体可选回迁，建议结合 1.4.x 实际 AWS 集成测试结果决定。
