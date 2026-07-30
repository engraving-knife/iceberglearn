# 提交 2518：Build: Bump com.google.cloud:libraries-bom from 26.65.0 to 26.66.0 (#13841)

## 提交信息

- **序号**：2518 / 4088
- **哈希**：4b0fecd371ea4278c6d59e3c3d8c0fd339b9ad3a
- **短哈希**：4b0fecd37
- **日期**：2025-08-18 09:35:52 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.65.0 to 26.66.0 (#13841)
- **PR/Issue**：#13841

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 Google Cloud Libraries BOM 从 26.65.0 升级到 26.66.0。

`com.google.cloud:libraries-bom` 是 Google Cloud 客户端库的 BOM（Bill of Materials），用于统一管理 Google Cloud 各个客户端库的版本。Iceberg 项目使用 Google Cloud 库来支持 GCS（Google Cloud Storage）文件系统访问和 BigQuery 等服务的集成。

此次升级属于次版本更新（semver-minor），通常包含新功能特性和改进。

## 如何达成设计目的

Dependabot 自动检测到 Google Cloud Libraries BOM 有新版本发布，在 `gradle/libs.versions.toml` 中将版本号从 26.65.0 更新为 26.66.0。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Google Cloud Libraries BOM 版本号。

**工作逻辑**：将 Gradle 版本目录文件中 `libraries-bom` 的版本号从 `26.65.0` 改为 `26.66.0`，使所有引用 Google Cloud 客户端库的项目模块自动使用新版本。

## 总结

这是一个常规的依赖维护提交，将 Google Cloud 客户端库升级到新的次版本。对于使用 GCS 存储或 BigQuery 集成的用户，保持 Google Cloud 库最新有助于确保与 Google Cloud 平台的兼容性。
