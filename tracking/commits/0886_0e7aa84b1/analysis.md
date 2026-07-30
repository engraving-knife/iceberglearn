# 提交 0886：Build: Bump software.amazon.awssdk:bom from 2.26.7 to 2.26.12 (#10611)

## 提交信息

- **序号**：0886 / 4088
- **哈希**：0e7aa84b1dd378b4be56f5b45b6744b383501bd9
- **短哈希**：0e7aa84b1
- **日期**：2024-06-30（Sun Jun 30 19:12:33 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.26.7 to 2.26.12 (#10611)
- **PR/Issue**：#10611

## 总体目的

本提交由 Dependabot 自动生成，目的是将 AWS SDK for Java v2 的 BOM（Bill of Materials）版本从 2.26.7 升级到 2.26.12。AWS SDK BOM 用于统一管理所有 AWS SDK 模块（S3、Glue、DynamoDB、KMS 等）的版本，Iceberg 在 `aws` 模块及多个 catalog 实现中依赖这些 SDK 组件与 AWS 服务交互。

2.26.7 → 2.26.12 是 semver-patch 升级，通常包含 bug 修复与小幅改进，不引入破坏性 API 变更，属于低风险的例行依赖维护。通过 BOM 升级可一次性同步所有 AWS SDK 子模块版本，避免版本碎片化。

## 如何达成设计目的

AWS SDK 版本在 Iceberg Gradle 版本目录 `gradle/libs.versions.toml` 中以变量 `awssdk-bom` 管理。BOM 是一个 POM 依赖，通过 `platform` 方式引入后统一约束所有 `software.amazon.awssdk:*` 模块的版本。只需将 `awssdk-bom` 变量值从 `2.26.7` 改为 `2.26.12`，即可让所有 AWS SDK 模块同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本从 2.26.7 升级到 2.26.12。

**工作逻辑**：将版本目录中 `awssdk-bom = "2.26.7"` 一行改为 `awssdk-bom = "2.26.12"`。该变量驱动的 BOM 通过 Gradle platform 约束所有 `software.amazon.awssdk:*` 模块版本，改一处即全部生效。

## 小结

- **成效**：完成 AWS SDK for Java v2 BOM 的补丁版本升级（2.26.7 → 2.26.12），获取上游 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行改动，影响所有 AWS SDK 模块版本；无代码改动。
- **回迁到 1.4.x 的注意事项**：可按需回迁。属于例行依赖升级，风险低，patch 版本通常 API 兼容。需确认 1.4.x 分支基线版本兼容性。若 1.4.x 已冻结依赖策略，可不必回迁。
