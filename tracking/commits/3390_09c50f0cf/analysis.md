# 提交 3390：Build: Bump com.google.cloud:libraries-bom from 26.77.0 to 26.78.0 (#15637)

## 提交信息

- **序号**：3390 / 4088
- **哈希**：09c50f0cf5e8ce5cda698033094733b63bc98b01
- **短哈希**：09c50f0cf
- **日期**：2026-03-14 23:42:48 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.77.0 to 26.78.0 (#15637)
- **PR/Issue**：#15637

## 总体目的

由 dependabot 自动发起的依赖升级，将 Google Cloud Libraries BOM 从 26.77.0 升级到 26.78.0（semver-minor 级别）。`com.google.cloud:libraries-bom` 是 Google Cloud Java 客户端的 BOM（Bill of Materials），用于统一管理 Google Cloud 相关依赖（如 GCS、BigQuery 客户端等）的版本。Iceberg 的 GCS 集成模块通过该 BOM 管理依赖版本。minor 级别升级通常包含新功能和小改进，并保持向后兼容。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 版本变量的值，所有引用该 BOM 的模块会自动同步升级其管理的 Google Cloud 依赖版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：
- 将 `google-libraries-bom = "26.77.0"` 改为 `google-libraries-bom = "26.78.0"`。该 BOM 用于 GCS（Google Cloud Storage）等集成模块，统一管理 Google Cloud 客户端库的版本。

## 总结

这是一次由 dependabot 自动生成的 minor 级依赖升级，将 Google Cloud Libraries BOM 从 26.77.0 提升到 26.78.0。改动仅涉及版本目录中的一行版本号。BOM 升级会带动其管理的多个 Google Cloud 客户端库版本更新，通常保持向后兼容，用于获取新功能和 bug 修复。
