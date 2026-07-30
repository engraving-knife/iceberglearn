# 提交 3988：Build: Bump com.google.cloud.gcs.analytics:gcs-analytics-core (#17108)

## 提交信息

- **序号**：3988 / 4088
- **哈希**：04a35e412d2f37bb03e0cc68d56111be5d8dbb5b
- **短哈希**：04a35e412
- **日期**：2026-07-06 09:18:36 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump com.google.cloud.gcs.analytics:gcs-analytics-core (#17108)
- **PR/Issue**：#17108

## 总体目的

Dependabot 自动升级 Google Cloud GCS Analytics Core 库从 1.3.1 到 1.4.0，次版本升级。该库用于 GCP bundle 中 Google Cloud Storage 的分析功能。本次升级还连带升级了多个相关的 OpenTelemetry 和 GCP 依赖（因为 gcs-analytics-core 的传递依赖版本变化）。

## 如何达成设计目的

1. 更新 `gradle/libs.versions.toml` 中的 `gcs-analytics-core` 版本号。
2. 更新 `gcp-bundle/runtime-deps.txt` 中所有受影响的依赖版本，包括 gcs-analytics 相关模块（1.3 → 1.4）、Google Cloud OpenTelemetry 模块（0.33 → 0.36）、OpenTelemetry contrib/semconv 模块等。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 gcs-analytics-core 版本。

**工作逻辑**：
```toml
gcs-analytics-core = "1.4.0"  # 原为 "1.3.1"
```

### `gcp-bundle/runtime-deps.txt` (+10/-9 lines)

**修改目的**：同步更新 GCP bundle 中所有受影响的传递依赖版本。

**工作逻辑**：
- `com.google.cloud.gcs.analytics:client/common/gcs-analytics-core`: 1.3 → 1.4
- `com.google.cloud.opentelemetry:detector-resources-support/exporter-metrics/shared-resourcemapping`: 0.33 → 0.36
- `io.opentelemetry.contrib:opentelemetry-gcp-resources`: 1.37 → 1.54
- `io.opentelemetry.semconv:opentelemetry-semconv`: 1.29 → 1.32，新增 `opentelemetry-semconv-incubating:1.32`
- `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi`: 1.62 → 1.63

## 总结

常规依赖升级，将 GCS Analytics Core 从 1.3.1 升级到 1.4.0，连带升级多个 OpenTelemetry 相关传递依赖，仅影响 GCP bundle 模块。
