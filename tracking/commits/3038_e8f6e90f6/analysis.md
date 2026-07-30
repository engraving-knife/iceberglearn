# 提交 3038：Build: Bump com.google.cloud:libraries-bom from 26.72.0 to 26.73.0 (#14902)

## 提交信息

- **序号**：3038 / 4088
- **哈希**：e8f6e90f67e2cf25e4bc330fde781fad6d2b6138
- **短哈希**：e8f6e90f6
- **日期**：2025-12-20
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.72.0 to 26.73.0 (#14902)
- **PR/Issue**：#14902

## 总体目的

本提交是 Dependabot 自动生成的依赖版本升级，将 Google Cloud Libraries BOM `com.google.cloud:libraries-bom` 从 `26.72.0` 升级到 `26.73.0`。Google Cloud Libraries BOM 是 Google 官方提供的 BOM（Bill of Materials），集中管理 Google Cloud 客户端库各模块（如 GCS 的 `google-cloud-storage`、BigQuery 的 `google-cloud-bigquery`、核心的 `google-cloud-core`、以及 OAuth/认证相关库等）的版本坐标，确保各模块版本一致。

在 Iceberg 项目中，`google-libraries-bom` 通过 `gradle/libs.versions.toml` 的 `google-libraries-bom` 条目导入，主要被 `iceberg-gcp` 模块引用，用于 `GCSFileIO`（Google Cloud Storage 文件读写）、`BigQueryMetastore` 或相关 GCP 集成场景。Dependabot 元数据显示该依赖为 `direct:production` 类型，进入发布制品运行时路径。本次升级属于语义版本的次版本升级（semver-minor，`26.72.0` → `26.73.0`），按语义化版本约定可包含向后兼容的新功能，不破坏现有 API。

此次升级的动机是常规依赖维护：Google Cloud 客户端库随 GCP 服务演进而持续发布新版本，包含新服务端特性支持、认证与重试逻辑改进、性能优化及缺陷修复。保持 BOM 同步有助于 Iceberg 的 GCP 集成与最新 GCS/BigQuery 服务端协同工作，并跟进 Google 上游的安全与稳定性修复。

## 如何达成设计目的

改动仅涉及 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 版本号的更新。该 BOM 通过 `version.ref = "google-libraries-bom"` 定义并以 `platform` 方式被 `iceberg-gcp` 模块导入，单点修改即可同步升级所有 Google Cloud 客户端库子模块的版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本号。

**工作逻辑**：
将版本目录中 `google-libraries-bom = "26.72.0"` 修改为 `google-libraries-bom = "26.73.0"`。该版本通过 `google-libraries-bom = { module = "com.google.cloud:libraries-bom", version.ref = "google-libraries-bom" }` 定义为 BOM 依赖，被 `iceberg-gcp` 模块以 `platform` 方式导入。修改后，依赖 `google-cloud-storage` 等 GCP 客户端库的模块将自动解析到 26.73.0 BOM 中声明的版本。

## 总结

本提交是 Dependabot 自动维护的 Google Cloud Libraries BOM 次版本升级（26.72.0 → 26.73.0），属于常规的生产依赖版本维护。该 BOM 是 Iceberg GCP 集成（GCS、BigQuery）的基础，次版本升级带来向后兼容的新功能与缺陷修复，确保 Iceberg 与 GCP 服务端的协同保持最新。
