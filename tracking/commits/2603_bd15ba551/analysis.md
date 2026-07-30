# 提交 2603：Build: Bump com.azure:azure-sdk-bom from 1.2.37 to 1.2.38 (#14010)

## 提交信息

- **序号**：2603 / 4088
- **哈希**：bd15ba551f821aba22265d31ad541d9d1ed7b222
- **短哈希**：bd15ba551
- **日期**：2025-09-07 20:44:28 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.37 to 1.2.38 (#14010)
- **PR/Issue**：#14010

## 总体目的

本次提交由 Dependabot 自动生成，将 `com.azure:azure-sdk-bom` 从 1.2.37 升级到 1.2.38。

Azure SDK BOM（Bill of Materials）是 Azure SDK 的依赖管理 POM，通过导入该 BOM 可以统一管理所有 Azure SDK 组件的版本，避免版本冲突。Iceberg 的 Azure 集成模块（azure-bundle）使用此 BOM 管理对 Azure 存储服务（如 Azure Blob Storage、Data Lake Storage 等）的依赖。

此次升级为 patch 级别更新（1.2.37 → 1.2.38），按照语义化版本规范，只包含 bug 修复和向后兼容的改进。

## 如何达成设计目的

在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `azuresdk-bom` 的版本号从 `1.2.37` 修改为 `1.2.38`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Azure SDK BOM 版本。

**工作逻辑**：将 `azuresdk-bom = "1.2.37"` 改为 `azuresdk-bom = "1.2.38"`。该 BOM 管理所有 Azure SDK 组件的版本，升级后所有 Azure 相关依赖将统一升至 BOM 中指定的对应版本。

**潜在影响**：作为 BOM 的 patch 级升级，预期包含各 Azure SDK 组件的 bug 修复和稳定性改进。由于是 BOM 更新，实际影响的组件版本取决于 BOM 内部的版本定义。这类升级有助于修复 Azure 集成中的已知问题并保持与最新 Azure 服务的兼容性。

## 总结

这是 Dependabot 自动生成的依赖升级提交，将 Azure SDK BOM 从 1.2.37 升至 1.2.38。作为 BOM patch 级升级，风险较低，包含 Azure SDK 各组件的 bug 修复。保持 Azure SDK 最新有助于确保 Iceberg 的 Azure 集成模块稳定运行。
