# 提交 0758：Build: Bump software.amazon.awssdk:bom from 2.25.45 to 2.25.50 (#10323)

## 提交信息

- **序号**：0758 / 4088
- **哈希**：d0dbc9cba264572a10fb96c001e4987fd6e07676
- **短哈希**：d0dbc9cba
- **日期**：2024-05-13 14:29:06 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.45 to 2.25.50 (#10323)
- **PR/Issue**：#10323

## 总体目的

本提交由 dependabot 自动生成，将项目依赖的 AWS SDK for Java 2.x 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 2.25.45 升级到 2.25.50。AWS SDK BOM 是 Iceberg 与 AWS 服务交互（S3、DynamoDB、Glue、KMS、STS、S3 Access Grants 等）的核心依赖，BOM 通过 Maven/Gradle 的平台机制统一管理 AWS SDK 各子模块的版本，确保各子模块版本兼容。本次为 semver-patch（补丁版本）升级，跨越 5 个 patch 版本（2.25.45 → 2.25.50），主要包含缺陷修复与服务端 API 模型更新，API 保持兼容。升级目的是获取 AWS SDK 上游修复，保持与 AWS 服务的稳定交互。

## 如何达成设计目的

Iceberg 使用 Gradle 进行构建，依赖版本统一通过版本目录（version catalog）文件 `gradle/libs.versions.toml` 集中管理。该文件中 `awssdk-bom = "2.25.45"` 这一行定义了 AWS SDK BOM 的版本别名，所有 AWS SDK 子模块（如 `software.amazon.awssdk:s3`、`software.amazon.awssdk:glue`、`software.amazon.awssdk:dynamodb`、`software.amazon.awssdk:sts`、`software.amazon.awssdk:kms`、`software.amazon.awssdk:iam` 以及 `awssdk-s3accessgrants` 等）均通过该 BOM 统一版本。因此，仅需将该行的版本号字符串从 `2.25.45` 改为 `2.25.50`，即可使整个项目的所有 AWS SDK 子模块同步升级。这是 dependabot 处理 BOM 类依赖升级的标准模式：单行修改，BOM 平台机制保证所有子模块版本一致。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本别名从 2.25.45 升级到 2.25.50。

**工作逻辑**：在 `gradle/libs.versions.toml` 第 31 行附近（`awaitility` 之后、`azuresdk-bom` 之前），将：

```toml
awssdk-bom = "2.25.45"
```

改为：

```toml
awssdk-bom = "2.25.50"
```

该别名被项目中所有引用 `libs.awssdk.bom` 平台的模块共享，一处修改即可让所有 AWS SDK 子模块（S3、Glue、DynamoDB、KMS、STS、IAM、S3 Access Grants 等）同步升级到 2.25.50。其余依赖版本（`azuresdk-bom`、`awssdk-s3accessgrants`、`caffeine` 等）未变。注意 `awssdk-s3accessgrants = "2.0.0"` 是独立的版本别名（S3 Access Grants 插件），不受本次 BOM 升级影响。

## 小结

- **成效**：将 AWS SDK BOM 依赖升级到 2.25.50，获取上游 5 个 patch 版本的缺陷修复与服务端 API 模型更新。由于是 semver-patch 升级，API 保持兼容，预期不需要修改任何调用方代码。BOM 机制保证所有 AWS SDK 子模块版本一致，避免版本不匹配风险。
- **影响范围**：影响所有模块的 AWS SDK 依赖版本，特别是 `aws-bundle`、`aws` 模块及 S3、Glue、DynamoDB 等 catalog 实现的运行时行为。由于 Iceberg 的 `aws-bundle` 会将 AWS SDK 打包发布，最终用户依赖 Iceberg `aws-bundle` 时会直接使用 2.25.50 的 AWS SDK。AWS SDK 的 patch 升级通常包含对 AWS 服务端 API 模型的更新（如新 API 字段、错误码调整），可能影响与 AWS 服务的交互细节，但向后兼容。
- **回迁注意事项**：此为依赖版本号单行修改，回迁到 1.4.x 分支非常简单。但需注意：
  1. 1.4.x 分支的 `libs.versions.toml` 中 `awssdk-bom` 版本可能本身就是 2.25.45 或更早版本，cherry-pick 时可能无冲突直接应用；若 1.4.x 已有其他提交调整了该行相邻内容（如 `awssdk-s3accessgrants` 行），需手动解决上下文冲突。
  2. 回迁后需确认 1.4.x 分支的 CI 环境能正常解析并下载 AWS SDK 2.25.50 各子构件（该版本已于 2024 年发布，Maven Central 可用）。
  3. AWS SDK 2.25.45 → 2.25.50 跨越 5 个 patch 版本，虽为 patch 升级，但 AWS SDK 的 patch 版本偶尔会包含服务端 API 模型（如 S3、Glue 的 API 模型类）的更新，回迁后建议跑一遍 AWS 相关集成测试（如 `TestS3FileIO`、`GlueCatalog` 相关测试）以验证。
  4. 若 1.4.x 分支有针对 AWS SDK 的特定兼容性补丁（如对某个 SDK 内部 API 的反射访问），需确认这些补丁在 2.25.50 下依然有效。
