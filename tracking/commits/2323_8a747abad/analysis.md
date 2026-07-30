# 提交 2323：Build: Bump com.azure:azure-sdk-bom from 1.2.35 to 1.2.36 (#13474)

## 提交信息

- **序号**：2323 / 4088
- **哈希**：8a747abade0f1892afd0dc5662f61bf1dfd68da3
- **短哈希**：8a747aba
- **日期**：2025-07-07 11:09:56 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.35 to 1.2.36 (#13474)
- **PR/Issue**：#13474

## 总体目的

这是一个由 dependabot 自动生成的依赖升级提交，将 Azure SDK for Java 的 BOM 从版本 1.2.35 升级到 1.2.36。Azure SDK BOM 用于管理 Iceberg 项目中 ADLS（Azure Data Lake Storage）等 Azure 服务的客户端依赖版本。这是一个 semver-patch 级别升级。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `libs.versions.toml`，将 `azuresdk-bom` 的版本号从 `1.2.35` 更新为 `1.2.36`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Azure SDK BOM 版本号。

**工作逻辑**：将 `azuresdk-bom = "1.2.35"` 修改为 `azuresdk-bom = "1.2.36"`。

## 总结

这是一个常规的依赖维护提交，通过 dependabot 自动升级 Azure SDK 版本以获取最新的 bug 修复和安全补丁。变更仅涉及版本号修改。
