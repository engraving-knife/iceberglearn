# 提交 0040：Build: Bump com.azure:azure-sdk-bom from 1.2.16 to 1.2.17 (#8794)

## 提交信息

- **序号**：0040 / 4088
- **哈希**：b5ea0d5a7f55e5b8d9eec8e764bbcc35f8301db3
- **短哈希**：b5ea0d5a7
- **日期**：2023-10-11 10:50:24 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.16 to 1.2.17 (#8794)
- **PR/Issue**：#8794

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 [Azure SDK for Java BOM](https://github.com/azure/azure-sdk-for-java)（Bill of Materials）从 `1.2.16` 升级到 `1.2.17`，属于 `version-update:semver-patch`（修订号升级），风险等级低。

`azure-sdk-bom` 是 Azure 官方维护的 BOM，统一管理 Azure SDK 全家桶各客户端库（如 `azure-storage-file-datalake`、`azure-identity`、`azure-core`、`azure-storage-blob` 等）及其传递依赖的版本，避免手工维护多个互相耦合的版本号。Iceberg 的 `iceberg-azure` 模块（ADLS Gen2 文件 IO 集成）通过 `platform(libs.azuresdk.bom)` 引入该 BOM，对齐 Azure 相关 artifact 的版本。`iceberg-azure-bundle` 模块（运行时重打包的 shaded jar）同样引入该 BOM。

`iceberg-azure` 模块是 Iceberg 的**可插拔云存储后端**之一，为 Iceberg 提供基于 Azure Data Lake Storage (ADLS Gen2) 的 `FileIO` 实现，把 `InputFile`/`OutputFile`/`FileIO` 抽象适配到 Azure Blob/ADLS 的对象存储语义。核心类包括 `ADLSFileIO`、`ADLSInputFile`、`ADLSOutputFile`、`ADLSInputStream`、`ADLSOutputStream`、`AzureProperties` 等（见 [`azure/src/main/java/org/apache/iceberg/azure/`](../../../../azure/src/main/java/org/apache/iceberg/azure/)）。BOM 升级后，`azure-storage-file-datalake` 与 `azure-identity` 等会按 1.2.17 BOM 解析到对应的较新版本，获取 bug 修复与改进。

1.2.16 到 1.2.17 之间是 patch 级别差异，按 Azure SDK 的发布惯例通常只包含 bug 修复与小改进，不含破坏性 API 变更。

## 如何达成设计目的

改动极简：仅在 Gradle 版本目录 [`gradle/libs.versions.toml`](../../../../gradle/libs.versions.toml) 中把 `azuresdk-bom = "1.2.16"` 改为 `azuresdk-bom = "1.2.17"`。所有通过 `platform(libs.azuresdk.bom)` 引入该 BOM 的模块会自动解析到新版本下的各 artifact 版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `com.azure:azure-sdk-bom` 的版本从 1.2.16 升级到 1.2.17。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（约第 14 行），原行 `azuresdk-bom = "1.2.16"` 被改为 `azuresdk-bom = "1.2.17"`。该版本常量在 Iceberg 构建中被以下位置以 `platform` BOM 方式引用：

- `iceberg-azure`（[`build.gradle:534`](../../../../build.gradle) `compileOnly platform(libs.azuresdk.bom)`）：以 `compileOnly` 引入 BOM 并声明 `compileOnly "com.azure:azure-storage-file-datalake"` 与 `compileOnly "com.azure:azure-identity"`，运行时由调用方或 `iceberg-azure-bundle` 提供。
- `iceberg-azure-bundle`（[`azure-bundle/build.gradle:27`](../../../../azure-bundle/build.gradle) `implementation platform(libs.azuresdk.bom)`）：运行时重打包模块，把 `azure-storage-file-datalake` 与 `azure-identity` 打入 shaded jar 并 relocate（如 `relocate 'io.netty', 'org.apache.iceberg.azure.shaded.io.netty'`），供用户作为单一 jar 引入。

升级后，BOM 内各 artifact（`azure-storage-file-datalake`、`azure-identity`、`azure-core` 等）会按 1.2.17 解析到对应的较新 patch 版本，修复的 bug 会随之进入 `iceberg-azure` 与 `iceberg-azure-bundle` 的测试与产物。Dependabot 在 PR 描述中给出了 [release notes](https://github.com/azure/azure-sdk-for-java/releases) 和 [commits 对比](https://github.com/azure/azure-sdk-for-java/compare/azure-sdk-bom_1.2.16...azure-sdk-bom_1.2.17) 供维护者审阅。

## 小结

该提交由 Dependabot 将 Azure SDK BOM 从 1.2.16 patch 升级到 1.2.17，使 Iceberg 的 ADLS Gen2 存储集成（iceberg-azure 与 iceberg-azure-bundle）跟进 Azure SDK 的最新 bug 修复版本，因属 patch 升级风险极低。
