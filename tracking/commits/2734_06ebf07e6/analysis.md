# 提交 2734：Build: Bump com.google.cloud:libraries-bom from 26.68.0 to 26.70.0

## 提交信息

- **序号**：2734 / 4088
- **哈希**：06ebf07e671c3ee2b0d0add1c4dc849ab9cbf630
- **短哈希**：06ebf07e6
- **日期**：2025-10-11 22:24:02 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.68.0 to 26.70.0
- **PR/Issue**：#14298

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。Google Cloud Libraries BOM 是 Google Cloud 客户端库的物料清单（Bill of Materials），用于统一管理 Google Cloud 各服务客户端（如 GCS、BigQuery 等）的版本。Iceberg 的 GCP 集成模块使用此 BOM 管理与 Google Cloud 交互的依赖。

本次升级将 libraries-bom 从 26.68.0 升级到 26.70.0，属于 semver-minor（次版本）升级，跨越两个次版本，可能包含新功能、改进以及 bug 修复。Dependabot 自动跟踪并升级此类 BOM 依赖，确保 Iceberg 与 Google Cloud 服务的兼容性。

## 如何达成设计目的

Dependabot 修改 Gradle 版本目录中的 BOM 版本声明。由于使用 BOM 管理方式，所有 Google Cloud 子模块的版本会自动跟随 BOM 统一升级，保证模块间兼容性。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.68.0"` 修改为 `google-libraries-bom = "26.70.0"`。项目中通过 BOM 引入的 Google Cloud 依赖会自动使用 26.70.0 对应的各子模块版本。

## 总结

这是常规的依赖维护升级，将 Google Cloud Libraries BOM 从 26.68.0 升级到 26.70.0。作为 semver-minor 升级，跨越两个次版本，可能引入新功能但应保持向后兼容。及时升级 GCP 相关依赖有助于保持 Iceberg 与 Google Cloud 服务的兼容性。
