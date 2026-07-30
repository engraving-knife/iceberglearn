# 提交 2604：Build: Bump com.google.cloud:libraries-bom from 26.66.0 to 26.67.0 (#13952)

## 提交信息

- **序号**：2604 / 4088
- **哈希**：b9014648dbe5ab42c7f7cec601313dd2a57a0811
- **短哈希**：b9014648d
- **日期**：2025-09-07 22:14:44 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.66.0 to 26.67.0 (#13952)
- **PR/Issue**：#13952

## 总体目的

本次提交由 Dependabot 自动生成，将 `com.google.cloud:libraries-bom` 从 26.66.0 升级到 26.67.0。

Google Cloud Libraries BOM 是 Google Cloud Java SDK 的依赖管理 BOM，通过导入该 BOM 可以统一管理所有 Google Cloud 客户端库的版本。Iceberg 的 GCP 集成模块（gcp-bundle）使用此 BOM 管理对 Google Cloud Storage 等服务的依赖。

此次升级为 minor 级别更新（26.66.0 → 26.67.0），按照语义化版本规范，minor 升级包含向后兼容的新功能和改进，也可能包含 bug 修复。

## 如何达成设计目的

在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `google-libraries-bom` 的版本号从 `26.66.0` 修改为 `26.67.0`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.66.0"` 改为 `google-libraries-bom = "26.67.0"`。该 BOM 管理 Google Cloud 各客户端库的版本，升级后所有 GCP 相关依赖将统一升至 BOM 中指定的对应版本。

**潜在影响**：作为 minor 级升级，可能包含新功能和 API 扩展，但保持向后兼容。实际影响的组件版本取决于 BOM 内部的版本定义。这类升级有助于获取 Google Cloud SDK 的最新功能支持和 bug 修复，确保 Iceberg 的 GCP 集成模块与最新的 Google Cloud 服务兼容。

## 总结

这是 Dependabot 自动生成的依赖升级提交，将 Google Cloud Libraries BOM 从 26.66.0 升至 26.67.0。作为 minor 级升级，包含新功能和改进但保持向后兼容。保持 Google Cloud SDK 最新有助于确保 Iceberg 的 GCP 集成模块获得最新的功能支持和 bug 修复。
