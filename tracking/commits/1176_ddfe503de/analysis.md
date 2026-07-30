# 提交 1176：Build: Bump com.google.cloud:libraries-bom from 26.44.0 to 26.47.0 (#11185)

## 提交信息

- **序号**：1176 / 4088
- **哈希**：ddfe503de70b9be94c8de7f02203acbc7b3d877e
- **短哈希**：ddfe503de
- **日期**：2024-09-23（Mon Sep 23 14:43:45 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.44.0 to 26.47.0 (#11185)
- **PR/Issue**：#11185

## 总体目的

本提交由 Dependabot 自动生成，将 Google Cloud Libraries BOM（`com.google.cloud:libraries-bom`）从 26.44.0 升级到 26.47.0，属于 `version-update:semver-minor` 次版本升级，跨三个次版本（26.44 → 26.45 → 26.46 → 26.47）。

`libraries-bom` 是 Google Cloud Java 客户端的物料清单（BOM），用于统一管理 Google Cloud 各客户端库（如 GCS Storage、BigQuery 等）的版本组合。Iceberg 的 `iceberg-gcp` 模块通过 `implementation platform(libs.google.libraries.bom)` 引入此 BOM，从而在使用 `google-cloud-storage`、`google-cloud-nio` 等依赖时由 BOM 决定具体版本，保证各 Google Cloud 库版本互相兼容。升级 BOM 让 `iceberg-gcp` 获取最新的 GCS 客户端修复与特性。

## 如何达成设计目的

仅修改 `gradle/libs.versions.toml`，把 `google-libraries-bom` 版本号从 `26.44.0` 改为 `26.47.0`。`iceberg-gcp` 的 `build.gradle` 通过 `platform(libs.google.libraries.bom)` 引用该 BOM 版本，BOM 内部会自动协调 Google Cloud 各子库的版本。无代码改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 google-libraries-bom 版本号。

**工作逻辑**：在 `[versions]` 段把：

```toml
google-libraries-bom = "26.44.0"
```

改为：

```toml
google-libraries-bom = "26.47.0"
```

该变量在 `[libraries]` 段被 `google-libraries-bom = { module = "com.google.cloud:libraries-bom", version.ref = "google-libraries-bom" }` 引用（本提交未改这一行），`iceberg-gcp/build.gradle` 再通过 `platform(libs.google.libraries.bom)` 把它当作 BOM 引入，从而把 Google Cloud 各依赖（如 `google-cloud-storage`）的版本交给 BOM 统一管理。

**升级背景**：BOM 26.45、26.46、26.47 各版本会调整其管理的子库版本（如 GCS 客户端版本），通常包含 bug 修复、安全补丁与新特性，且 BOM 内部保证子库版本组合兼容。次版本升级理论向后兼容。

## 小结

- **成效**：Google Cloud Libraries BOM 升级到 26.47.0，`iceberg-gcp` 模块所使用的 GCS、BigQuery 等客户端获取最新修复与特性，且各子库版本组合由 BOM 保证兼容。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，无代码改动。运行时影响限于 `iceberg-gcp` 模块（用户在 GCP 环境使用 Iceberg 时）。
- **回迁到 1.4.x 的注意事项**：BOM 升级跨三个次版本，子库版本变化较大。1.4.x 回迁前需确认：(1) 1.4.x 的 `iceberg-gcp` 代码与 BOM 26.47.0 协调出的 GCS 客户端版本兼容（main 可能已合入适配新 GCS API 的其他提交）；(2) 跑 `iceberg-gcp` 集成测试（特别是 GCS live 测试）。若 1.4.x 用户在 GCP 环境无已知问题，**可不回迁**；如有 GCS 相关 bug 或安全需求再回迁。注意 BOM 仅声明版本组合，本身不含代码，回迁风险主要在它拉起的具体子库版本。
