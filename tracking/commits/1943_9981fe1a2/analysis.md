# 提交 1943：Build: Bump com.google.cloud:libraries-bom from 26.55.0 to 26.58.0 (#12688)

## 提交信息

- **序号**：1943 / 4088
- **哈希**：9981fe1a282d7d18dc6b361e764d1fb4f13313b5
- **短哈希**：9981fe1a2
- **日期**：2025-04-01 09:10:34 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.55.0 to 26.58.0 (#12688)
- **PR/Issue**：#12688

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Google Cloud Libraries BOM（`com.google.cloud:libraries-bom`）从 26.55.0 升级到 26.58.0。该 BOM 用于统一管理 Google Cloud Java 客户端库（如 GCS、BigQuery、Pub/Sub 等）的版本，Iceberg 的 `gcp` 模块通过引用此 BOM 来管理 Google Cloud 依赖版本。

从 26.55.0 到 26.58.0 跨越了三个次版本，属于 semver-minor 升级，通常会更新 BOM 内包含的多个 Google Cloud 客户端库版本（如 google-cloud-storage、google-cloud-core 等），获取新功能、bug 修复与安全补丁。升级目的是保持 Google Cloud 依赖处于较新版本。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中将 `google-libraries-bom` 版本字符串从 `26.55.0` 修改为 `26.58.0`，所有引用该 BOM 的 Google Cloud 制品版本会随之更新。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `[versions]` 区块中的 `google-libraries-bom = "26.55.0"` 修改为 `google-libraries-bom = "26.58.0"`。该 BOM 控制 gcp 模块下所有 `com.google.cloud:*` 制品的版本。

## 总结

本次提交为 Dependabot 自动执行的 Google Cloud Libraries BOM 升级（26.55.0 → 26.58.0），仅修改 `gradle/libs.versions.toml` 中的 BOM 版本声明。属于例行的依赖维护，更新 gcp 模块所用的 Google Cloud 客户端库版本。
