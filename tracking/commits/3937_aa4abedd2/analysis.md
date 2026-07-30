# 提交 3937：Build: Bump com.google.cloud:libraries-bom from 26.83.0 to 26.84.0 (#16906)

## 提交信息

- **序号**：3937 / 4088
- **哈希**：aa4abedd2573b9fec5567213195ce208bd91609e
- **短哈希**：aa4abedd2
- **日期**：2026-06-23 22:12:45 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.83.0 to 26.84.0 (#16906)
- **PR/Issue**：#16906

## 总体目的

这是由 Dependabot 发起的依赖升级，将 Google Cloud Libraries BOM 从 26.83.0 升级到 26.84.0。Google Cloud Libraries BOM 用于统一管理所有 Google Cloud Java 客户端库（如 Cloud Storage、BigQuery、KMS 等）的版本。Iceberg 的 GCP 集成模块（`iceberg-gcp`）和 GCP bundle 依赖该 BOM。

此次升级为 semver-minor 更新，预期包含新功能和向后兼容的改进。由于该 BOM 管理大量传递性依赖，升级后需要同步更新 GCP bundle 和 Kafka Connect 运行时的 runtime-deps.txt 文件以反映新的传递性依赖版本。

## 如何达成设计目的

通过修改版本目录 `gradle/libs.versions.toml` 中 `google-libraries-bom` 的版本条目，并同步更新 `gcp-bundle/runtime-deps.txt` 和 `kafka-connect/kafka-connect-runtime/runtime-deps.txt` 中记录的传递性依赖版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.83.0"` 改为 `google-libraries-bom = "26.84.0"`。

### `gcp-bundle/runtime-deps.txt` (+14/-14 lines)

**修改目的**：同步 GCP bundle 运行时传递性依赖版本。

**工作逻辑**：更新多个 Google Cloud 相关依赖版本，包括 gapic-google-cloud-storage-v2 2.68→2.69、grpc-google-cloud-storage-v2 2.68→2.69、proto-google-cloud-storage-v2 2.68→2.69、proto-google-common-protos 2.71→2.72、proto-google-iam-v1 1.66→1.67、api-common 2.63→2.64、gax 系列 2.80→2.81、google-auth-library 1.47→1.48、google-cloud-core 系列 2.70→2.71、google-cloud-storage 2.68→2.69 等。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+36/-36 lines)

**修改目的**：同步 Kafka Connect 运行时传递性依赖版本。

**工作逻辑**：更新 Kafka Connect 运行时中 Google Cloud 相关传递性依赖的版本，与 GCP bundle 保持一致。

## 总结

这是一次 Google Cloud Libraries BOM 的次要版本升级，通过版本目录升级到 26.84.0 并同步更新运行时依赖清单。这确保了 GCP 和 Kafka Connect 模块使用一致的最新 Google Cloud 客户端库版本。
