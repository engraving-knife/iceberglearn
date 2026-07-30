# 提交 3353：Build: Bump com.azure:azure-sdk-bom from 1.3.4 to 1.3.5 (#15539)

## 提交信息

- **序号**：3353 / 4088
- **哈希**：663bbf5af32a030ecfbc72f6b70844623589d9eb
- **短哈希**：663bbf5af
- **日期**：2026-03-07 23:16:07 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.3.4 to 1.3.5 (#15539)
- **PR/Issue**：#15539

## 总体目的

`com.azure:azure-sdk-bom` 是 Azure 官方发布的 Java BOM（Bill of Materials），统一管理 Azure SDK 各模块（如 azure-storage、azure-identity、azure-core 等）的版本，避免模块间版本不兼容。Iceberg 通过该 BOM 提供对 Azure Blob / ADLS（Gen2）存储的支持，是 `azure` 模块及云存储 IO 实现的依赖基础。

本次 dependabot 把 BOM 版本从 `1.3.4` 升到 `1.3.5`（语义版本 semver-patch 升级）。BOM 的 patch 升级通常只更新所管辖模块的 patch 版本（bug 修复、安全补丁），不引入新 API 或破坏性变更，目的是让 Iceberg 使用的 Azure SDK 组件获得最新修复、保持与上游兼容。

## 如何达成设计目的

Iceberg 把版本统一在 Gradle version catalog `gradle/libs.versions.toml` 中（键 `azuresdk-bom`），各模块通过 catalog 引用 BOM，因此一行改动即可全局生效，无需修改任何业务代码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：把 azure-sdk-bom 版本从 1.3.4 升到 1.3.5。

**工作逻辑**：`azuresdk-bom = "1.3.4"` 改为 `azuresdk-bom = "1.3.5"`。该键在 catalog 中被 `azure-bom` 依赖声明引用，进而导入 `azure-storage-blob`、`azure-identity`、`azure-storage-file-datalake` 等模块。1.3.5 是 patch 级升级，预期只带来 bug 修复与安全补丁，无 API 破坏性变更。

## 总结

本提交把 Azure SDK BOM 从 1.3.4 升到 1.3.5（semver-patch），让 Iceberg Azure 存储集成的依赖组件获得最新修复。改动仅一行 version catalog 版本值，属低风险依赖维护。
