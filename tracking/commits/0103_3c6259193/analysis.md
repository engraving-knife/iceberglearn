# 提交 0103：Build: Bump com.azure:azure-sdk-bom from 1.2.17 to 1.2.18 (#8939)

## 提交信息

- **序号**：0103 / 4088
- **哈希**：3c6259193790ad8a0685233b12b7a20c891e6cbf
- **短哈希**：3c6259193
- **日期**：2023-10-30 09:54:39 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.17 to 1.2.18 (#8939)
- **PR/Issue**：#8939

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 Azure SDK for Java 的物料清单（BOM）`com.azure:azure-sdk-bom` 从 1.2.17 升级到 1.2.18。该 BOM 用于统一管理 Azure 各客户端库（如 Azure Storage Blob、Data Lake、Cosmos 等）的版本，Iceberg 在 Azure 集成模块（`iceberg-azure`）中依赖它来引入 ADLS Gen2 相关的客户端组件以实现 `ADLSFileIO`。

Dependabot 将本次升级标记为 `version-update:semver-patch`（补丁版本升级），意味着按语义化版本约定仅包含缺陷修复、不引入新功能或破坏性变更，风险最低。这类小步快跑的补丁升级是保持云厂商 SDK 与云服务后端兼容性的常规手段。

该升级对 Iceberg 演进的意义在于：让 Azure 集成模块所依赖的客户端库跟上上游补丁发布节奏，及时获得 Azure SDK 团队修复的缺陷与稳定性改进，避免因 SDK 缺陷导致 Iceberg 在 ADLS 上的读写操作出现回归。

## 如何达成设计目的

设计思路与 libraries-bom 升级一致：Dependabot 仅修改 `gradle/libs.versions.toml` 中 `azuresdk-bom` 这一个版本变量，从 `1.2.17` 改为 `1.2.18`。由于 Iceberg 在 `iceberg-azure` 模块中以 platform BOM 方式引入 Azure SDK，所有由该 BOM 管理版本的 Azure 客户端传递依赖都会随之统一升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Azure SDK BOM 的版本号从 1.2.17 提升到 1.2.18。

**工作逻辑**：仅修改第 31 行附近的版本常量，`azuresdk-bom = "1.2.17"` 改为 `azuresdk-bom = "1.2.18"`。该变量在依赖目录中被引用为 `azuresdk-bom = { module = "com.azure:azure-sdk-bom", version.ref = "azuresdk-bom" }`，并在 `iceberg-azure/build.gradle` 中以 `platform(libs.azure.sdk.bom)` 的方式作为 BOM 平台依赖引入。这样 `AzureBlobStorageFileIO`、`AzureBlobStorageChecker`、ADLS Gen2 客户端等所需的 `azure-storage-blob`、`azure-storage-file-datalake` 等制品版本都由该 BOM 统一锁定，本次升级会让它们一并落到 1.2.18 BOM 所声明的版本上。提交说明的 `updated-dependencies` 元数据将此依赖标记为 `direct:production`、`version-update:semver-patch`。

## 小结

这是 Dependabot 自动化依赖维护的一次例行 semver-patch 升级，通过单行版本号变更让 Iceberg 的 Azure 集成模块获得 Azure SDK 上游补丁修复。
