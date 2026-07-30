# 提交 1778：Build: Bump com.google.cloud:libraries-bom from 26.54.0 to 26.55.0 (#12382)

## 提交信息

- **序号**：1778 / 4088
- **哈希**：ea3fd7fca53320635bf4d0a2db35067b0aa45799
- **短哈希**：ea3fd7fca
- **日期**：2025-02-24 12:18:42 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.54.0 to 26.55.0 (#12382)
- **PR/Issue**：#12382

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Google Cloud Libraries BOM 从 26.54.0 版本升级到 26.55.0 版本。Google Cloud Libraries BOM 是一个物料清单（Bill of Materials），用于统一管理 Google Cloud 各客户端库（如 GCS 存储、BigQuery 等）的版本。Iceberg 项目在 GCS 集成模块中使用 Google Cloud 客户端库。此次升级为次版本升级（semver-minor），获取 Google Cloud 库的新功能和改进。

## 如何达成设计目的

提交通过更新 `gradle/libs.versions.toml` 文件中 google-libraries-bom 的版本号来完成升级。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.54.0"` 修改为 `google-libraries-bom = "26.55.0"`。

## 小结

- **成效**：将 Google Cloud Libraries BOM 升级到 26.55.0 次版本。
- **影响范围**：影响 GCS（Google Cloud Storage）集成模块及使用 Google Cloud 客户端库的测试。BOM 升级通常向后兼容。
- **回迁到 1.4.x 的注意事项**：低优先级回迁。BOM 版本升级通常无破坏性变更。回迁时需确认 GCS 集成测试通过。无前置依赖。
