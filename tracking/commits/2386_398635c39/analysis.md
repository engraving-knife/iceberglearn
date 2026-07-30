# 提交 2386：Build: Bump com.google.cloud:libraries-bom from 26.62.0 to 26.64.0 (#13632)

## 提交信息

- **序号**：2386 / 4088
- **哈希**：398635c39db91342edb2e453400f45d3fd398fc1
- **短哈希**：398635c39
- **日期**：2025-07-23 07:22:23 +0200
- **作者**：Liam Bao
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.62.0 to 26.64.0 (#13632)
- **PR/Issue**：#13632

## 总体目的

本提交将 Google Cloud Libraries BOM（Bill of Materials）从 26.62.0 升级到 26.64.0。Google Cloud Libraries BOM 是一个依赖版本管理清单，用于统一管理 Google Cloud 相关库的版本，在 Iceberg 项目中主要用于 BigQuery 集成模块。

此次升级跨越了两个次版本（26.62 -> 26.64），可能包含功能增强、bug 修复和 API 变更。由于 Google Cloud 库的新版本中 `BigQueryRetryHelper` 的构造函数签名发生了变化（新增了 OpenTelemetry 追踪相关参数），本提交不仅升级了依赖版本，还修改了调用代码以适配新的 API。

## 如何达成设计目的

设计思路分为两步：首先升级 BOM 版本号，然后修改受 API 变更影响的代码以适配新版本。关键设计点如下：

1. **升级 BOM 版本**：在 `gradle/libs.versions.toml` 中将 `google-libraries-bom` 从 26.62.0 改为 26.64.0。
2. **适配 API 变更**：在 `BigQueryMetastoreClientImpl.java` 中，5 处 `BigQueryRetryHelper` 构造调用新增了 `bigqueryOptions.isOpenTelemetryTracingEnabled()` 和 `bigqueryOptions.getOpenTelemetryTracer()` 两个参数，以适配新版 Google Cloud BigQuery 库中新增的 OpenTelemetry 追踪支持。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.62.0"` 修改为 `google-libraries-bom = "26.64.0"`。该 BOM 管理所有 Google Cloud 相关依赖的版本。

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreClientImpl.java` (+10/-0 lines)

**修改目的**：适配新版 BigQuery 库的 API 变更。

**工作逻辑**：在 5 处 `BigQueryRetryHelper` 构造调用中（分别对应 `query`、`insertJob`、`getTable`、`createDataset`、`updateTable` 等方法），在原有的 `DEFAULT_RETRY_CONFIG` 参数后新增两个参数：
- `bigqueryOptions.isOpenTelemetryTracingEnabled()`：是否启用 OpenTelemetry 追踪
- `bigqueryOptions.getOpenTelemetryTracer()`：OpenTelemetry tracer 实例

这些新参数是 Google Cloud BigQuery 库 26.64.0 版本中 `BigQueryRetryHelper` 构造函数的新增参数，用于支持分布式追踪。

## 总结

本提交升级了 Google Cloud Libraries BOM 从 26.62.0 到 26.64.0，并适配了新版 BigQuery 库中 `BigQueryRetryHelper` 构造函数的 API 变更（新增 OpenTelemetry 追踪参数）。修改涉及 2 个文件，11 行新增和 1 行删除。该升级使 Iceberg 的 BigQuery 集成模块与最新的 Google Cloud 库保持同步，并获得了 OpenTelemetry 追踪支持的能力。
