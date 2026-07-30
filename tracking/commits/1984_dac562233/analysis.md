# 提交 1984：Build: Bump com.google.cloud:libraries-bom from 26.58.0 to 26.59.0 (#12733)

## 提交信息

- **序号**：1984 / 4088
- **哈希**：dac5622338f024e9e5a565b8358d0b865edb4e2f
- **短哈希**：dac562233
- **日期**：2025-04-11 11:08:28 +0200
- **作者**：dependabot[bot]（Fokko 协同）
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.58.0 to 26.59.0 (#12733)
- **PR/Issue**：#12733

## 总体目的

本提交由 dependabot 自动生成，将 Google Cloud 库 BOM 依赖 `com.google.cloud:libraries-bom` 从 `26.58.0` 升级到 `26.59.0`（semver 次版本升级）。该 BOM 管理一系列 Google Cloud 客户端库的版本，升级后传递性依赖版本随之变化，因此同步更新 gcp-bundle 的 LICENSE / NOTICE 文件以保持许可证合规。

## 如何达成设计目的

1. 修改 Gradle 版本目录中 `google-libraries-bom` 的版本声明。
2. 由 Fokko 协同更新 gcp-bundle 的 LICENSE、NOTICE 中受影响依赖（如 jackson-core 等传递性依赖）的版本号。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 libraries-bom 版本。

**工作逻辑**：`google-libraries-bom = "26.58.0"` → `google-libraries-bom = "26.59.0"`。

### `gcp-bundle/LICENSE`, `gcp-bundle/NOTICE` (修改)

**修改目的**：同步 gcp-bundle 内打包依赖的版本声明。

**工作逻辑**：根据 BOM 升级带来的传递性依赖版本变化，更新 LICENSE/NOTICE 中相关组件（如 jackson-core 等）的版本号记录。

## 总结

依赖升级提交，将 `com.google.cloud:libraries-bom` 由 26.58.0 升至 26.59.0（次版本升级），并同步更新 gcp-bundle 的 LICENSE/NOTICE 中受影响依赖的版本声明以维持许可证合规。共 3 个文件、67 处增/73 处减（多为版本字符串调整）。
