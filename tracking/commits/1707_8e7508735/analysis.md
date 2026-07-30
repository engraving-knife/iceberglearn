# 提交 1707：Build: Bump com.google.cloud:libraries-bom from 26.53.0 to 26.54.0 (#12207)

## 提交信息

- **序号**：1707 / 4088
- **哈希**：8e7508735ad0376ffc3fdc16746618f6a0c96aa7
- **短哈希**：8e7508735
- **日期**：2025-02-10（Mon Feb 10 07:38:30 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.53.0 to 26.54.0 (#12207)
- **PR/Issue**：#12207

## 总体目的

Dependabot 自动升级提交。`com.google.cloud:libraries-bom` 是 Google Cloud Libraries 的 BOM，用于统一管理 Google Cloud SDK（如 GCS Storage）的版本。Iceberg 的 `gcp` 模块（GCSFileIO 等）依赖此 BOM。本提交把 BOM 版本从 `26.53.0` 升级到 `26.54.0`（minor 级），获取 Google Cloud SDK 的最新改进与 bug 修复。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中把 `google-libraries-bom = "26.53.0"` 改为 `google-libraries-bom = "26.54.0"`。

## 修改详情

### `gradle/libs.versions.toml`（修改，+1/-1 行）

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：修改版本变量定义，所有通过 `platform(libs.google.libraries.bom)` 引用 Google Cloud SDK 的模块自动使用新版本。

## 小结

- **成效**：升级 Google Cloud SDK 到 26.54.0，获取改进与 bug 修复。
- **影响范围**：仅构建配置，无源代码变更。影响 `gcp` 模块及所有使用 GCSFileIO 的场景。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯版本号升级。minor 级 BOM 升级通常向后兼容，但建议回迁后验证 GCS 相关功能测试通过。
