# 提交 2294：Build: Bump com.azure:azure-sdk-bom from 1.2.31 to 1.2.35 (#13201)

## 提交信息

- **序号**：2294 / 4088
- **哈希**：e5ba73ec4b176e1e02a36ef4f64a8ea5cf0a1ff7
- **短哈希**：e5ba73ec4
- **日期**：2025-06-30 23:12:56 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.31 to 1.2.35 (#13201)
- **PR/Issue**：#13201

## 总体目的

本提交由 Dependabot 自动生成，将 Azure SDK BOM（Bill of Materials）从 1.2.31 升级到 1.2.35。Azure SDK BOM 是一个依赖管理清单，用于统一管理所有 Azure SDK Java 组件的版本。在 Iceberg 项目中，Azure SDK 用于 Azure Blob Storage 和 Azure Data Lake 等存储服务的集成。

此次升级属于 semver-patch（补丁版本）更新，跨越了 4 个补丁版本（1.2.31 → 1.2.32 → 1.2.33 → 1.2.34 → 1.2.35），主要包含 bug 修复和安全补丁。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中声明的 `azure-sdk-bom` 版本有新版本可用，自动创建 PR 将版本号从 `1.2.31` 修改为 `1.2.35`。由于使用 BOM 管理方式，所有 Azure SDK 子组件的版本会自动跟随 BOM 版本更新。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Azure SDK BOM 版本从 1.2.31 升级到 1.2.35。

**工作逻辑**：在 Gradle 版本目录文件中，将 `azure-sdk-bom` 的版本声明从 `1.2.31` 修改为 `1.2.35`。BOM 是一个 POM 文件，集中定义了所有 Azure SDK 组件的版本，通过升级 BOM 版本，所有依赖的 Azure SDK 组件版本会自动对齐到 BOM 中指定的版本。

## 总结

这是一个常规的依赖补丁版本升级提交，通过 Dependabot 自动完成。Azure SDK BOM 的升级确保 Iceberg 的 Azure 存储集成使用最新的修复版本，对 Azure 用户的安全性和稳定性有直接帮助。
