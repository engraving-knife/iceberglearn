# 提交 2170：Build: Bump com.google.cloud:libraries-bom from 26.60.0 to 26.61.0 (#13146)

## 提交信息

- **序号**：2170 / 4088
- **哈希**：d46acf9147b239a91d03e221fb5f6d44c1c70c49
- **短哈希**：d46acf914
- **日期**：2025-05-27 14:28:08 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.60.0 to 26.61.0 (#13146)
- **PR/Issue**：#13146

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交。目的是将 Google Cloud Libraries BOM（Bill of Materials）从 26.60.0 版本升级到 26.61.0 版本。Google Cloud Libraries BOM 是一个依赖管理清单，用于统一管理 Google Cloud 相关 Java 库的版本。Iceberg 的 GCP 模块使用该 BOM 来管理 Google Cloud Storage 等服务的客户端库版本。升级有助于获取最新的功能和安全修复。

## 如何达成设计目的

- 通过 Dependabot 自动检测到 libraries-bom 有新版本发布
- 在 `gradle/libs.versions.toml` 中将版本号从 26.60.0 更新为 26.61.0
- 这是一个 semver-minor 级别的升级，通常向后兼容

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：更新 Google Cloud Libraries BOM 的版本号。

**工作逻辑**：将 libraries-bom 的版本声明从 26.60.0 改为 26.61.0，使项目中所有依赖 Google Cloud 库的模块在构建时使用统一的新版本。

## 总结

这是一个常规的依赖升级提交，将 Google Cloud Libraries BOM 从 26.60.0 升级到 26.61.0，属于次要版本升级。该 BOM 用于管理 Iceberg GCP 模块相关依赖的版本一致性。
