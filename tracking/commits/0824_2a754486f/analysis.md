# 提交 0824：Build: Bump com.azure:azure-sdk-bom from 1.2.23 to 1.2.24 (#10420)

## 提交信息

- **序号**：0824 / 4088
- **哈希**：2a754486f7e1ff536d5c9b04742bcf379bc9c340
- **短哈希**：2a754486f
- **日期**：2024-06-11 03:36:47 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.23 to 1.2.24 (#10420)
- **PR/Issue**：#10420

## 总体目的

本提交由 Dependabot 自动生成，将 Azure SDK for Java 的 BOM（Bill of Materials，依赖版本清单）从 `1.2.23` 升级到 `1.2.24`。Azure SDK BOM 统一管理所有 Azure SDK 模块的版本，Iceberg 项目使用 Azure SDK 实现对 Azure Blob Storage 等云存储服务的访问（如 ADLSFileIO）。

此次升级属于 SemVer patch 级别升级（1.2.23 → 1.2.24），目的是获取 Azure SDK 在该版本区间的缺陷修复、安全补丁和服务模型更新。

## 如何达成设计目的

提交仅修改了 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的一行版本号声明：

1. **版本号声明**：将 `azuresdk-bom = "1.2.23"` 改为 `azuresdk-bom = "1.2.24"`。该变量通过 `version.ref` 被 `azuresdk-bom = { module = "com.azure:azure-sdk-bom", version.ref = "azuresdk-bom" }` 引用。

2. **BOM 传递机制**：与 AWS SDK BOM 类似，Azure SDK BOM 通过 Gradle platform 机制引入后，统一管理所有 `com.azure:*` 模块的版本。修改 BOM 版本号即可统一升级所有 Azure SDK 子模块。

3. **无需修改使用处**：所有 Azure SDK 模块依赖引用不带版本号（由 BOM 控制），升级 BOM 后自动生效。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Azure SDK BOM 版本从 1.2.23 升级到 1.2.24。

**工作逻辑**：
- 修改位于版本目录的 `[versions]` 段，第 32 行附近，紧随 `awssdk-bom` 声明之后。
- `azuresdk-bom` 版本变量被同文件的 `azuresdk-bom = { module = "com.azure:azure-sdk-bom", version.ref = "azuresdk-bom" }` 库声明引用（约第 83 行）。
- BOM 在 `build.gradle` 中通过 platform 机制引入，此后所有 Azure SDK 模块依赖的版本由该 BOM 决定。
- Iceberg 使用 Azure SDK 主要用于 ADLS（Azure Data Lake Storage）集成，支持以 Azure Blob Storage 作为表数据存储后端。
- 1.2.23 到 1.2.24 的 patch 升级通常包含：Azure 服务客户端稳定性修复、服务模型更新、已知缺陷修复，不涉及破坏性 API 变更。

## 小结

- **成效**：Azure SDK for Java 升级到 1.2.24，获取了 patch 版本的缺陷修复和改进，提升了 Azure 存储集成的稳定性。
- **影响范围**：影响使用 Azure SDK 的模块，主要是 ADLS/Azure Blob Storage 文件 IO 集成（ADLSFileIO 及相关 Azure 存储访问代码）。由于是 patch 级升级，API 兼容，运行时行为基本不变。
- **回迁注意事项**：回迁到 1.4.x 分支时需注意版本差异——当前 1.4.x 分支的 `azuresdk-bom` 版本为 `1.2.16`（低于 1.2.24），说明 1.4.x 分支与 main 分支存在版本差距。直接回迁此单个提交（1.2.23→1.2.24）不适用，因为 1.4.x 分支尚未升级到 1.2.23。建议在 1.4.x 分支上整体评估 Azure SDK 版本升级。Azure SDK BOM 1.2.x 内部 patch 升级向后兼容，回迁风险低。
