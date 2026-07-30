# 提交 3249：Build: Bump com.google.cloud:libraries-bom from 26.75.0 to 26.76.0 (#15321)

## 提交信息

- **序号**：3249 / 4088
- **哈希**：55ff7625b1d2d597a4511ffd74e8ed2324b197c1
- **短哈希**：55ff7625b
- **日期**：2026-02-14
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.75.0 to 26.76.0 (#15321)
- **PR/Issue**：#15321

## 总体目的

`com.google.cloud:libraries-bom` 是 Google Cloud 客户端库的 BOM（Bill of Materials），用于统一管理 Google Cloud 各客户端库（如 GCS Storage、BigQuery、KMS 等）的版本兼容性。在 Iceberg 项目中，该 BOM 主要支撑 GCP（Google Cloud Platform）相关集成，包括 GCS 文件系统访问（`gcs-bundle`）和 GCP KMS 加密客户端等模块。该依赖属于 `direct:production` 类型。

本次提交由 Dependabot 自动生成，将 `google-libraries-bom` 从 `26.75.0` 升级到 `26.76.0`。根据语义版本规范，这是一个 minor 级别升级（`version-update:semver-minor`），即次版本号从 75 增至 76。minor 升级通常包含新功能添加和 bug 修复，但保持向后兼容性，预期不会破坏 Iceberg 对 Google Cloud 客户端 API 的使用。

## 如何达成设计目的

仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 的版本声明，从 `26.75.0` 改为 `26.76.0`。通过 BOM 机制，所有 Google Cloud 客户端库的传递依赖版本将自动对齐到 26.76.0 版本集。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Google Cloud Libraries BOM 版本从 26.75.0 升级至 26.76.0。

**工作逻辑**：
在版本目录的第 54 行，将 `google-libraries-bom = "26.75.0"` 修改为 `google-libraries-bom = "26.76.0"`。该 BOM 统一管理项目中所有 Google Cloud 客户端库的版本，升级后 GCS、GCP KMS 等模块依赖的 Google Cloud 库将获得最新的功能增强和 bug 修复。作为 minor 级别升级，保持 API 向后兼容。

## 总结

本次提交是 Dependabot 自动执行的 Google Cloud Libraries BOM minor 升级（26.75.0 → 26.76.0），使 Iceberg 的 GCP 集成模块（GCS 文件 IO、GCP KMS 等）使用最新兼容版本的 Google Cloud 客户端库，获取新功能和稳定性改进。
