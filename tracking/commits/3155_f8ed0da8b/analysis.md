# 提交 3155：Build: Bump com.google.cloud:libraries-bom from 26.73.0 to 26.74.0 (#15135)

## 提交信息

- **序号**：3155 / 4088
- **哈希**：f8ed0da8b89645506b6a57e83d98b54922e557fd
- **短哈希**：f8ed0da8b
- **日期**：2026-01-25 09:32:37 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.73.0 to 26.74.0 (#15135)
- **PR/Issue**：#15135

## 总体目的

本提交是 Dependabot 自动发起的依赖升级，将 Google Cloud Libraries BOM（`com.google.cloud:libraries-bom`）从 `26.73.0` 升级到 `26.74.0`。该 BOM 由 Google 官方维护，统一管理 Google Cloud 客户端库各构件的版本，包括 GCS（Google Cloud Storage）、BigQuery 等服务的客户端 SDK。Iceberg 提供了 GCS 与 BigQuery 等云存储/数据集成模块（如 `gcp` 模块、`gcs` 文件 IO 实现、BigQuery 相关集成），通过 `libraries-bom` 统一管理这些 Google Cloud 依赖的版本，避免构件间版本不一致。版本声明在 `gradle/libs.versions.toml` 的 `google-libraries-bom` 属性中。

本次升级为次版本（minor）升级（`version-update:semver-minor`，26.73.0 → 26.74.0），按语义化版本约定可包含新功能与向后兼容的改进。Google Cloud BOM 的次版本升级通常会刷新所管理的多个客户端 SDK 版本，预期对 Iceberg 的 GCS/BigQuery 集成保持 API 兼容。Dependabot 在提交信息中附带了上游 release notes、changelog 与 commits 对比链接。

## 如何达成设计目的

直接在 `gradle/libs.versions.toml` 中将 `google-libraries-bom` 属性从 `26.73.0` 改为 `26.74.0`，所有通过该 BOM 引入 Google Cloud 客户端构件的模块（GCS、BigQuery 等）自动跟随升级，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 google-libraries-bom 版本从 26.73.0 提升到 26.74.0。

**工作逻辑**：将 `google-libraries-bom = "26.73.0"` 修改为 `google-libraries-bom = "26.74.0"`。该 BOM 统一管理 Iceberg 的 GCP 集成模块（GCS 文件 IO、BigQuery 等）所依赖的 Google Cloud 客户端 SDK 版本。升级到 26.74.0 为 semver-minor 级别，刷新所管理的客户端 SDK 版本以获取上游改进，预期对 Iceberg 的 GCS/BigQuery 集成保持 API 兼容。

## 总结

本提交通过将 Google Cloud Libraries BOM 从 26.73.0 升级到 26.74.0，刷新 Iceberg GCP 集成模块所依赖的 Google Cloud 客户端 SDK 版本，保持云集成依赖的及时更新；作为 semver-minor 升级，预期对 GCS/BigQuery 集成的 API 保持向后兼容。
