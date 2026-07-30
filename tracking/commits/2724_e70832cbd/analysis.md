# 提交 2724：Build: Bump com.google.cloud:libraries-bom from 26.67.0 to 26.68.0

## 提交信息

- **序号**：2724 / 4088
- **哈希**：e70832cbd85700843020d49401db22fe27e32ae9
- **短哈希**：e70832cbd
- **日期**：2025-10-08 17:46:46 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.67.0 to 26.68.0
- **PR/Issue**：#14134

## 总体目的

此提交是 Dependabot 自动生成的依赖版本升级，将 Google Cloud Libraries BOM 从 26.67.0 升级到 26.68.0。

Google Cloud Libraries BOM 是 Iceberg 中 GCP 模块（`iceberg-gcp`）使用的依赖，用于管理 Google Cloud 各客户端库（如 Cloud Storage、BigQuery、Bigtable 等）的版本号。BOM 确保所有 Google Cloud SDK 子模块版本兼容。

26.67.0 到 26.68.0 是一个 semver-minor 版本升级，包含新功能和改进。此次升级也由 Fokko Driesprong 共同审核。

## 如何达成设计目的

通过在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中将 `google-libraries-bom` 的版本号从 `26.67.0` 更新为 `26.68.0` 来完成升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本号。

**工作逻辑**：将 `google-libraries-bom` 变量从 `"26.67.0"` 更改为 `"26.68.0"`。所有通过 `version.ref = "google-libraries-bom"` 引用此版本的 Google Cloud SDK 模块将自动使用新版本。这是一次 semver-minor 升级，预计包含新 API、功能增强和 bug 修复，同时保持向后兼容。

## 总结

此提交是例行依赖维护，将 Google Cloud Libraries BOM 从 26.67.0 升级到 26.68.0。作为 semver-minor 升级，确保了 Iceberg 的 GCP 集成使用最新的 Google Cloud SDK，获得新功能和安全修复。对于 BigQuery 目录的集成也有间接帮助。
