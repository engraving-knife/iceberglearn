# 提交 3354：Build: Bump com.google.cloud:libraries-bom from 26.76.0 to 26.77.0 (#15538)

## 提交信息

- **序号**：3354 / 4088
- **哈希**：26dc1ac1ba10ad31dfae4d1af32dc56f8e6ce692
- **短哈希**：26dc1ac1b
- **日期**：2026-03-07 23:16:22 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.76.0 to 26.77.0 (#15538)
- **PR/Issue**：#15538

## 总体目的

`com.google.cloud:libraries-bom` 是 Google Cloud 官方发布的 Java BOM，统一管理 Google Cloud Java 客户端库（如 GCS Storage、BigQuery、IAM 等模块）的版本，避免模块间版本不兼容。Iceberg 通过该 BOM 提供对 Google Cloud Storage（GCS）及 GCP 相关存储的支持，是 `gcp` 模块及云存储 IO 实现的依赖基础。

本次 dependabot 把 BOM 版本从 `26.76.0` 升到 `26.77.0`（语义版本 semver-minor 升级）。minor 升级通常引入新功能与新模块版本，但保持向后兼容（不破坏既有 API），目的是让 Iceberg 使用的 GCP 客户端库获得新特性与 bug 修复、保持与上游同步。

## 如何达成设计目的

Iceberg 把版本统一在 Gradle version catalog `gradle/libs.versions.toml` 中（键 `google-libraries-bom`），各模块通过 catalog 引用 BOM，因此一行改动即可全局生效，无需修改业务代码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：把 google libraries-bom 版本从 26.76.0 升到 26.77.0。

**工作逻辑**：`google-libraries-bom = "26.76.0"` 改为 `google-libraries-bom = "26.77.0"`。该键在 catalog 中被 `google-storage-bom` 依赖声明引用，进而导入 `google-cloud-storage`、`google-cloud-core`、`google-cloud-iam-admin` 等模块。26.77.0 是 minor 级升级，按 Google Cloud 库的兼容性承诺，预期向后兼容，无破坏性变更。

## 总结

本提交把 Google Cloud libraries-bom 从 26.76.0 升到 26.77.0（semver-minor），让 Iceberg GCP 存储集成的依赖组件获得新功能与修复。改动仅一行 version catalog 版本值，属常规依赖维护。
