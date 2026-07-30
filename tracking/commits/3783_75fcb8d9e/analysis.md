# 提交 3783：Build: Bump com.google.cloud:libraries-bom from 26.81.0 to 26.83.0 (#16551)

## 提交信息

- **序号**：3783 / 4088
- **哈希**：75fcb8d9e3378237a6b73b1b20d507641c37a392
- **短哈希**：75fcb8d9e
- **日期**：2026-05-24 10:31:02 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.81.0 to 26.83.0 (#16551)
- **PR/Issue**：#16551

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Google Cloud Libraries BOM 从 26.81.0 升级到 26.83.0。这是一个 semver-minor 级别的升级。Google Cloud Libraries BOM 用于统一管理 Google Cloud Java 客户端库的版本，Iceberg 使用它来支持 GCS（Google Cloud Storage）等存储后端。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中更新 `google-libraries-bom` 版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.81.0"` 改为 `google-libraries-bom = "26.83.0"`，升级 2 个 minor 版本。

## 总结

常规的依赖维护提交，将 Google Cloud Libraries BOM 从 26.81.0 升级到 26.83.0，获取 Google Cloud 客户端库的新功能和改进，影响 GCS 存储后端的依赖。
