# 提交 3192：Build: Bump com.google.cloud:libraries-bom from 26.74.0 to 26.75.0 (#15202)

## 提交信息

- **序号**：3192 / 4088
- **哈希**：e7bfab6c54a96319a4e5aac2a0c7de57b1d0b47b
- **短哈希**：e7bfab6c5
- **日期**：2026-01-31
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.74.0 to 26.75.0 (#15202)
- **PR/Issue**：#15202

## 总体目的

这是 Dependabot 自动发起的依赖升级，将 Google Cloud Libraries BOM 从 `26.74.0` 升至 `26.75.0`。`com.google.cloud:libraries-bom` 是一个 BOM（Bill of Materials），用于统一管理 Google Cloud 各客户端库（如 GCS、BigQuery、Pub/Sub 等）的版本。在 Iceberg 中，它主要用于 GCS（Google Cloud Storage）`FileIO` 集成——通过 BOM 锁定 Google Cloud Storage 客户端及其传递依赖的版本，保证 GCS 读写链路稳定。该 BOM 属生产依赖。

本次为语义化版本的 **minor** 升级（`26.74.0` → `26.75.0`）。BOM 的 minor 升级通常会把其管理的若干 Google Cloud 客户端库同步到较新版本，可能引入新功能与缺陷修复，但 BOM 设计上保证版本组合经过兼容性测试。预期效果是让 Iceberg 的 GCS 集成跟进 Google Cloud 客户端库的最新兼容组合，获得稳定性与安全修复，而不需要逐个升级 GCS 相关制品。

## 如何达成设计目的

Dependabot 仅修改版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 版本变量。该变量经 `[libraries]` 段的 `google-libraries-bom = { module = "com.google.cloud:libraries-bom", version.ref = "google-libraries-bom" }` 引用，并以 `platform`/`enforcedPlatform` 方式导入到 GCS 等模块的依赖中，Gradle 解析时自动用新 BOM 管理传递依赖版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Google Cloud Libraries BOM 版本从 `26.74.0` 提升至 `26.75.0`。

**工作逻辑**：
将 `[versions]` 段中的 `google-libraries-bom = "26.74.0"` 改为 `google-libraries-bom = "26.75.0"`。由于 `[libraries]` 段对应条目通过 `version.ref` 引用该变量，且该 BOM 以平台依赖形式参与依赖解析，这一处改动即可让 Google Cloud 客户端库集合整体升级到 `26.75.0` 锁定的兼容版本组合。

## 总结

本次提交通过单点修改 BOM 版本变量，将 Google Cloud 客户端库集合 minor 升级到 `26.75.0`，使 Iceberg 的 GCS 集成跟进上游兼容组合与修复，属常规依赖维护。
