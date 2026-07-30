# 提交 2328：Add BigQuery Dependencies for Iceberg GCP Bundle (#13111)

## 提交信息

- **序号**：2328 / 4088
- **哈希**：5d7d8c13c07a3f591058f5208826bfe79d39f1fa
- **短哈希**：5d7d8c13c
- **日期**：2025-07-08 17:54:55 -0700
- **作者**：Talat UYARER
- **提交说明**：Add BigQuery Dependencies for Iceberg GCP Bundle (#13111)
- **PR/Issue**：#13111

## 总体目的

这个提交为 Iceberg 的 GCP Bundle（Google Cloud Platform 打包模块）添加了 BigQuery 相关的依赖。GCP Bundle 是 Iceberg 提供的便利性打包，将所有 GCP 相关的依赖打包到一个 fat JAR 中，方便用户在 GCP 环境中使用 Iceberg。

此前 GCP Bundle 仅包含 Google Cloud Storage 依赖。随着 Iceberg 对 BigQuery 支持的增强（如 BigQuery Catalog、BigQuery 表操作等），需要将 BigQuery 相关依赖也纳入 Bundle 中。此次添加了 `google-cloud-bigquery` 和 `google-cloud-core` 两个依赖。

同时，提交还更新了 `LICENSE` 和 `NOTICE` 文件，反映了新增依赖带来的许可证和版权声明变更。

## 如何达成设计目的

在 `gcp-bundle/build.gradle` 的依赖声明中添加 BigQuery 和 core 依赖，并更新许可证文件以反映新的传递依赖。

## 修改详情

### `gcp-bundle/build.gradle` (+2/-0 lines)

**修改目的**：添加 BigQuery 依赖到 GCP Bundle。

**工作逻辑**：在 `dependencies` 块中新增：
- `implementation "com.google.cloud:google-cloud-bigquery"` — BigQuery 客户端库
- `implementation "com.google.cloud:google-cloud-core"` — Google Cloud 核心库

这些依赖会通过 Shadow 插件打包到最终的 fat JAR 中。

### `gcp-bundle/LICENSE` (+730/-18 lines)

**修改目的**：更新许可证文件以包含新依赖及其传递依赖的许可证声明。

**工作逻辑**：添加了 BigQuery 及其传递依赖（如 gRPC、Protobuf、Google Auth 库等）的许可证文本。这是一个大的文本变更，主要原因是 BigQuery 客户端库引入了大量传递依赖，每个依赖都有各自的许可证需要声明。

### `gcp-bundle/NOTICE` (+54/-0 lines)

**修改目的**：更新版权声明文件以包含新依赖的版权信息。

**工作逻辑**：添加 BigQuery 相关依赖的版权和归属声明。

## 总结

这个提交为 GCP Bundle 添加了 BigQuery 依赖支持，为 Iceberg 在 GCP 环境中使用 BigQuery 功能提供了便利的打包方案。虽然代码变更量小（仅 2 行 build.gradle），但由于许可证合规要求，LICENSE 和 NOTICE 文件的更新量较大（共约 800 行）。
