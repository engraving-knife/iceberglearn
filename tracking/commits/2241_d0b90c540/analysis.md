# 提交 2241：Build: Bump com.google.cloud:libraries-bom from 26.61.0 to 26.62.0

## 提交信息

- **序号**：2241 / 4088
- **哈希**：d0b90c540d64112eff7cc856fddaccdd184bd91a
- **短哈希**：d0b90c540
- **日期**：2025-06-16 09:30:08 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.61.0 to 26.62.0
- **PR/Issue**：#13317

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Google Cloud Libraries BOM 从 26.61.0 升级到 26.62.0。Google Cloud Libraries BOM 统一管理 Google Cloud Java 客户端库的版本，Iceberg 使用 Google Cloud SDK 进行 GCS（Google Cloud Storage）等云存储服务的交互。此次升级为 minor 级别更新，可能包含新功能和改进。

## 如何达成设计目的

- 在 Gradle 版本目录文件中修改 Google Cloud Libraries BOM 的版本号。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.61.0"` 修改为 `google-libraries-bom = "26.62.0"`，所有引用该 BOM 的 Google Cloud 模块将自动使用新版本。

## 总结

常规依赖升级提交，将 Google Cloud Libraries BOM 从 26.61.0 升级到 26.62.0，获取最新的功能改进和 bug 修复。
