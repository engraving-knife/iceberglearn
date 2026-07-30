# 提交 3613：Build: Bump com.google.cloud:libraries-bom from 26.79.0 to 26.80.0 (#16152)

## 提交信息

- **序号**：3613 / 4088
- **哈希**：8ac703067a2adfae1928748712dc1d47dbc3c22b
- **短哈希**：8ac703067
- **日期**：2026-04-28 22:36:21 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.79.0 to 26.80.0 (#16152)
- **PR/Issue**：#16152

## 总体目的

这个提交将 Google Cloud Libraries BOM 从版本 26.79.0 升级到 26.80.0，这是一个 minor 级别的版本更新。

Google Cloud Libraries BOM 用于统一管理所有 Google Cloud Java 客户端库的版本。升级到 26.80.0 可以获得最新的功能改进和 bug 修复。

## 如何达成设计目的

通过 Dependabot 自动生成的 PR，更新以下文件：
1. `gradle/libs.versions.toml` 中的 `google-libraries-bom` 版本号。
2. `gcp-bundle/runtime-deps.txt` 中所有 Google Cloud 依赖的版本号。
3. `kafka-connect/kafka-connect-runtime/runtime-deps.txt` 中所有 Google Cloud 依赖的版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Google Cloud Libraries BOM 版本声明。

**工作逻辑**：
```toml
google-libraries-bom = "26.80.0"  # 从 26.79.0 升级
```

### `gcp-bundle/runtime-deps.txt` (+53/-53 lines)

**修改目的**：同步更新 GCP bundle 中所有 Google Cloud 依赖的版本。

**工作逻辑**：
所有 Google Cloud 相关依赖（如 `com.google.cloud:*`、`com.google.api:*`、`io.grpc:*` 等）的版本号根据新的 BOM 进行了更新，涉及约 53 个依赖条目。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+45/-45 lines)

**修改目的**：同步更新 Kafka Connect runtime 中所有 Google Cloud 依赖的版本。

**工作逻辑**：
与 gcp-bundle 类似，所有 Google Cloud 相关依赖版本号根据新的 BOM 进行了更新。

## 总结

这是一个 Dependabot 自动依赖升级提交，将 Google Cloud Libraries BOM 从 26.79.0 升级到 26.80.0（minor 版本）。三个文件的修改确保了版本声明和依赖清单的一致性。作为 minor 级别升级，可能包含新功能和改进，但应保持向后兼容。
