# 提交 2852：Build: Bump com.azure:azure-sdk-bom from 1.3.0 to 1.3.2 (#14539)

## 提交信息

- **序号**：2852 / 4088
- **哈希**：25bddb0a42364d4a2f4651af619adf5e3f1c0a2c
- **短哈希**：25bddb0a4
- **日期**：2025-11-08 22:18:46 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.3.0 to 1.3.2 (#14539)
- **PR/Issue**：#14539

## 总体目的

这是一个由 dependabot 自动生成的依赖升级提交，将 Azure SDK BOM（Bill of Materials）从 1.3.0 升级到 1.3.2。

Azure SDK BOM 是 Azure SDK for Java 的依赖管理清单，用于统一管理所有 Azure 相关依赖的版本。Iceberg 项目支持 Azure 作为存储后端（通过 `iceberg-azure` 模块），因此需要引入 Azure SDK 来与 Azure Blob Storage 等服务交互。使用 BOM 可以确保所有 Azure SDK 组件版本兼容。

版本从 1.3.0 升级到 1.3.2，属于补丁版本（patch）升级，通常包含 Bug 修复和小改进，风险较低，向后兼容性有保障。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `azuresdk-bom` 版本声明来完成升级。BOM 的版本更新后，所有通过该 BOM 管理的 Azure SDK 依赖会自动使用 BOM 中指定的兼容版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Azure SDK BOM 版本声明。

**工作逻辑**：将 `azuresdk-bom` 变量从 `1.3.0` 修改为 `1.3.2`。BOM 作为版本清单，被引入后会自动管理 Azure SDK 各组件的版本，确保它们之间的兼容性。此次补丁升级主要用于获取 Azure SDK 的最新修复。

## 总结

这是一个常规的依赖升级提交，将 Azure SDK BOM 从 1.3.0 升级到 1.3.2，属于低风险的补丁版本升级。修改仅涉及版本目录文件的一行改动，有助于保持 Azure 集成模块的稳定性和安全性。
