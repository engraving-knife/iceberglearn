# 提交 3741：Build: Bump com.google.cloud:libraries-bom from 26.80.0 to 26.81.0 (#16382)

## 提交信息

- **序号**：3741 / 4088
- **哈希**：26a6f535a0236eab17b2396d775ee8738eab041c
- **短哈希**：26a6f535a
- **日期**：2026-05-18 19:29:19 -0700
- **作者**：Huaxin Gao
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.80.0 to 26.81.0 (#16382)
- **PR/Issue**：#16382

## 总体目的

Dependabot 自动发起的依赖升级，将 Google Cloud Libraries BOM（`com.google.cloud:libraries-bom`）从 26.80.0 升级到 26.81.0。该 BOM 用于统一管理 Google Cloud 相关依赖（如 GCS 客户端、BigQuery 客户端等）的版本。Iceberg 的 GCP 模块和 Kafka Connect 运行时模块使用该 BOM 管理传递依赖。本次为 minor 版本升级（26.80.0 → 26.81.0），会带动多个 Google Cloud 相关库的版本变化。

升级 BOM 后，需要同步更新 `gcp-bundle` 和 `kafka-connect-runtime` 的 `runtime-deps.txt` 基线文件，以反映传递依赖版本的变化（如 parquet 从 1.17.0 升级到 1.17.1、animal-sniffer-annotations 从 1.26 升级到 1.27 等）。

## 如何达成设计目的

Dependabot 修改 `gradle/libs.versions.toml` 中 `google-libraries-bom` 版本变量从 26.80.0 改为 26.81.0，同时更新 `gcp-bundle/runtime-deps.txt` 和 `kafka-connect-runtime/runtime-deps.txt` 两个运行时依赖基线文件，反映 BOM 升级带来的传递依赖版本变化。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：
将 `google-libraries-bom = "26.80.0"` 修改为 `google-libraries-bom = "26.81.0"`，使所有依赖该 BOM 的 Google Cloud 库版本统一升级。

### `gcp-bundle/runtime-deps.txt` (+58/-57 lines)

**修改目的**：同步 GCP bundle 的运行时依赖基线。

**工作逻辑**：
更新 GCP bundle 的传递依赖版本，主要变化包括：
- `org.apache.parquet:parquet-*` 从 1.17.0 升级到 1.17.1（parquet-avro、parquet-column、parquet-common、parquet-encoding、parquet-format-structures、parquet-hadoop、parquet-jackson、parquet-variant）
- `org.codehaus.mojo:animal-sniffers-annotations` 从 1.26 升级到 1.27
- 其他 Google Cloud 相关依赖的版本同步更新。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+63/-62 lines)

**修改目的**：同步 Kafka Connect 运行时的依赖基线。

**工作逻辑**：
与 GCP bundle 类似，更新传递依赖版本，反映 BOM 升级带来的变化。

## 总结

本提交是 Dependabot 自动发起的依赖升级，将 Google Cloud Libraries BOM 从 26.80.0 升级到 26.81.0（minor 版本），并同步更新 GCP bundle 和 Kafka Connect 运行时的依赖基线文件。主要传递依赖变化包括 Parquet 从 1.17.0 升级到 1.17.1、animal-sniffer-annotations 从 1.26 升级到 1.27 等。属于常规依赖维护，旨在获取最新版本的改进与修复。
