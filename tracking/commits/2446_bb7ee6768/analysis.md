# 提交 2446：Build: Bump com.google.cloud:libraries-bom from 26.64.0 to 26.65.0 (#13722)

## 提交信息

- **序号**：2446 / 4088
- **哈希**：bb7ee6768c8247505ec9b1b3cb6592f74fff83a0
- **短哈希**：bb7ee6768c
- **日期**：2025-08-04 18:04:16 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.64.0 to 26.65.0 (#13722)
- **PR/Issue**：#13722

## 总体目的

本提交由 dependabot 自动生成，将 Google Cloud Libraries BOM 从 26.64.0 升级到 26.65.0。Google Cloud Libraries BOM 用于统一管理 Iceberg 对 Google Cloud 服务（如 GCS 存储）客户端依赖的版本。

这是一次 minor 版本升级（26.64.0 → 26.65.0），按照语义化版本约定，minor 升级包含向后兼容的新功能或改进。dependabot 标注为 `update-type: version-update:semver-minor`。升级 Google Cloud Libraries 可以获得新的功能特性和 bug 修复，提升与 GCS 等 Google Cloud 服务交互的稳定性与兼容性。

## 如何达成设计目的

在版本目录文件中修改 `google-libraries-bom` 变量的值即可，所有引用该 BOM 的 Google Cloud 模块版本会自动统一。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.64.0"` 修改为 `google-libraries-bom = "26.65.0"`。该变量在 libraries 目录中作为 Google Cloud Libraries BOM 的版本引用，通过 Gradle 的 platform 机制统一管理相关 Google Cloud 模块的版本。

## 总结

这是一个由 dependabot 自动发起的依赖版本升级，将 Google Cloud Libraries BOM 从 26.64.0 升级到 26.65.0。作为 minor 版本升级，包含向后兼容的新功能和改进。改动仅涉及一行版本号配置。
