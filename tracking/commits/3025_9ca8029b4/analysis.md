# 提交 3025：GCS: bump up gcs-analytics-core version from 1.2.1 to 1.2.3 (#14873)

## 提交信息

- **序号**：3025 / 4088
- **哈希**：9ca8029b4f7932ca2c22b034a0abc0edf8972975
- **短哈希**：9ca8029b4
- **日期**：2025-12-17
- **作者**：Ajay Yadav
- **提交说明**：GCS: bump up gcs-analytics-core version from 1.2.1 to 1.2.3 (#14873)
- **PR/Issue**：#14873

## 总体目的

本提交将 Google Cloud Storage 分析核心库 `gcs-analytics-core` 的版本从 1.2.1 升级到 1.2.3。`gcs-analytics-core`（Maven 坐标 `com.google.cloud.gcs.analytics:gcs-analytics-core`）是 Google 提供的针对 GCS 分析型工作负载的优化读取库，在 Iceberg 中于 PR #14333（提交 2988）被首次集成。

该库在 Iceberg 的 GCP 模块（`iceberg-gcp`）中作为可选的输入流实现使用。当用户通过 `gcs.analytics-core.enabled` 属性（默认关闭）显式启用时，`GCSInputFile.newStream()` 会创建基于 `GoogleCloudStorageInputStream` 的 `GcsInputStreamWrapper`，替代默认的 `GCSInputStream`。该库在底层实现了更激进的预取（prefetching）、向量化读取（vectorized read）、并发分片拉取和请求合并优化，能够显著提升大规模扫描吞吐量。该库同时被打入 `gcp-bundle` 胖包，供使用 bundle 的用户开箱即用。

本次版本升级属于语义化版本中的 patch 级别升级（1.2.1 → 1.2.3，跳过 1.2.2），按照 semver 规范，patch 版本升级仅包含向后兼容的缺陷修复和改进，不引入 API 破坏性变更。因此升级风险较低，用户无需修改任何配置即可享受新版本带来的 bug 修复和性能改进。具体的 1.2.2 和 1.2.3 变更内容需参考该库的 release notes，但通常包含读取路径的稳定性修复和边缘 case 处理改进。

## 如何达成设计目的

改动仅需更新 `gradle/libs.versions.toml` 中的版本变量声明。由于 `gcs-analytics-core` 的库坐标（`{ module = "com.google.cloud.gcs.analytics:gcs-analytics-core", version.ref = "gcs-analytics-core" }`）使用 `version.ref` 引用版本变量，所有引用该变量的 build.gradle 文件（`:iceberg-gcp` 的 `compileOnly` 依赖和 `:iceberg-gcp-bundle` 的 `implementation` 依赖）会自动获取新版本，无需逐文件修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 `gcs-analytics-core` 版本变量从 1.2.1 升级到 1.2.3。

**工作逻辑**：
将 `[versions]` 段中的 `gcs-analytics-core = "1.2.1"` 改为 `gcs-analytics-core = "1.2.3"`。该变量被 `[libraries]` 段的 `gcs-analytics-core = { module = "com.google.cloud.gcs.analytics:gcs-analytics-core", version.ref = "gcs-analytics-core" }` 引用。由于 Iceberg 使用 Gradle 的 version catalog 机制，所有通过 `libs.gcs.analytics.core` 引用该库的模块（`iceberg-gcp` 的 `compileOnly` 和 `iceberg-gcp-bundle` 的 `implementation`）会自动解析到新版本。

此次升级为 patch 级别（1.2.x → 1.2.y），属于向后兼容的缺陷修复版本，不涉及 API 变更，因此不需要修改任何使用该库的代码（`GCSInputFile`、`GcsInputStreamWrapper`、`PrefixedStorage` 等）。

## 总结

本提交是 `gcs-analytics-core` 依赖的常规 patch 版本升级（1.2.1 → 1.2.3），通过 Gradle version catalog 机制仅需一处改动即可全局生效。该库为 Iceberg GCS 读取路径提供分析型优化能力，升级后用户可获得最新的稳定性修复和改进，无需修改任何配置或代码。
