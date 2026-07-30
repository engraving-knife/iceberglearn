# 提交 4052：Build: Bump com.google.cloud:libraries-bom from 26.84.0 to 26.85.0 (#17237)

## 提交信息

- **序号**：4052 / 4088
- **哈希**：b4deba5e791db23a175bfc9bc586d3aaab5f005e
- **短哈希**：b4deba5e7
- **日期**：2026-07-16 10:43:14 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.84.0 to 26.85.0 (#17237)
- **PR/Issue**：#17237

## 总体目的

Dependabot 自动升级提交，将 Google Cloud Java 库的 BOM（`com.google.cloud:libraries-bom`）从 26.84.0 升级到 26.85.0（semver minor 版本升级）。该 BOM 统一管理 Iceberg 与 Google Cloud 服务（GCS、BigQuery、KMS 等）交互所用的各 Google Cloud 模块版本。

与 AWS SDK 升级类似，本次升级也涉及大量 Google Cloud 子模块的版本对齐（如 grpc、proto、gax 等模块的 minor 版本提升），因此同步更新了 `gcp-bundle` 和 `kafka-connect-runtime` 的运行时依赖清单。minor 版本升级通常包含新功能、API 改进和 bug 修复。

## 如何达成设计目的

通过三处同步更新：版本目录 BOM 变量更新 + `gcp-bundle/runtime-deps.txt` 中所有 Google Cloud 模块版本提升 + `kafka-connect-runtime/runtime-deps.txt` 同步，确保构建依赖与打包清单一致。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud libraries-bom 版本变量。

**工作逻辑**：将 BOM 版本从 `26.84.0` 改为 `26.85.0`。

### `gcp-bundle/runtime-deps.txt` (+31/-31 lines)

**修改目的**：同步更新 GCP bundle 的运行时依赖清单。

**工作逻辑**：将清单中 Google Cloud 模块版本批量提升，例如：
- `com.google.api.grpc:gapic-google-cloud-storage-v2:2.69` → `2.70`
- `com.google.api.grpc:grpc-google-cloud-bigquerystorage-v1:3.29` → `3.30`
- `com.google.api.grpc:proto-google-cloud-kms-v1:2.96` → `2.97`（等）
涵盖 gRPC、proto、gax、auth、api-common 等数十个模块的版本对齐。

### `kafka-connect-runtime/runtime-deps.txt` (+29/-29 lines)

**修改目的**：同步更新 Kafka Connect 运行时包的 Google Cloud 依赖清单。

**工作逻辑**：与 GCP bundle 同步更新所有 Google Cloud 模块版本，确保 Kafka Connect 发行版打包的 GCP 依赖与主构建一致。

## 总结

常规的 Google Cloud 库 minor 版本升级，涉及 BOM 变量和两个打包清单的同步更新，保持构建、GCP bundle 和 Kafka Connect 发行版的 Google Cloud 依赖版本一致。minor 级别升级通常向后兼容。
