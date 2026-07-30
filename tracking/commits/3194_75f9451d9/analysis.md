# 提交 3194：Build: Bump com.azure:azure-sdk-bom from 1.3.3 to 1.3.4  (#15204)

## 提交信息

- **序号**：3194 / 4088
- **哈希**：75f9451d9e82500588d3ea075e301810b302a0ff
- **短哈希**：75f9451d9
- **日期**：2026-02-01
- **作者**：Prashant Singh
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.3.3 to 1.3.4  (#15204)
- **PR/Issue**：#15204

## 总体目的

这是 Dependabot 发起、由 Prashant Singh 协同补充的依赖升级，将 Azure SDK BOM 从 `1.3.3` 升至 `1.3.4`。`com.azure:azure-sdk-bom` 是 Azure SDK for Java 的 BOM，统一管理 Azure 各客户端库版本；在 Iceberg 中主要用于 ADLS Gen2（Azure Data Lake Storage Gen2）/ Blob 的 `FileIO` 集成，即 Azure 模块通过 BOM 锁定 Azure Storage 与 Identity 客户端版本，属生产依赖。

本次为语义化版本的 **patch** 升级（`1.3.3` → `1.3.4`），但此次升级带来一个兼容性副作用：BOM `1.3.4` 中包含的 Azure SDK 客户端会使用 API 版本 `2026-02-06`，而 Iceberg 集成测试所用的 Azurite（Azure Storage 的本地模拟器）`3.35.0` 仅支持到 API 版本 `2025-11-05`。这会导致 Azurite 在收到高于其支持版本的请求时拒绝请求、使集成测试失败。因此 PR 包含第二个 commit：在 Azurite 测试容器启动命令上加 `--skipApiVersionCheck` 标志，跳过 API 版本校验，使 Azurite 接受新版 SDK 发出的请求。

综上，本提交既要完成 patch 级依赖升级，又要修补由升级引发的测试基础设施兼容性问题，二者配套缺一不可。

## 如何达成设计目的

改动分两处：在 `gradle/libs.versions.toml` 将 `azuresdk-bom` 版本变量升至 `1.3.4`；在 Azure 集成测试的 `AzuriteContainer` 构造器中新增一行 `withCommand` 显式启动命令，同时指定 `--blobHost 0.0.0.0`（覆盖镜像默认入口、保证 blob 服务监听在所有网卡以保持主机可达）与 `--skipApiVersionCheck`。前者升级 SDK，后者让本地模拟器容忍新版 SDK 的更高 API 版本，从而让依赖升级不破坏集成测试。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Azure SDK BOM 版本从 `1.3.3` 提升至 `1.3.4`。

**工作逻辑**：
将 `[versions]` 段中的 `azuresdk-bom = "1.3.3"` 改为 `azuresdk-bom = "1.3.4"`。由于 `[libraries]` 段的 `azuresdk-bom = { module = "com.azure:azure-sdk-bom", version.ref = "azuresdk-bom" }` 通过 `version.ref` 引用该变量，且该 BOM 以平台依赖形式参与 Azure 模块解析，这一处改动即让全部 Azure 客户端库升级到 `1.3.4` 锁定的版本组合（含使用 API 版本 `2026-02-06` 的客户端）。

### `azure/src/integration/java/org/apache/iceberg/azure/adlsv2/AzuriteContainer.java` (+1/-0 lines)

**修改目的**：为 Azurite 测试容器设置显式启动命令，跳过 API 版本校验以兼容 Azure SDK `1.3.4` 的更高 API 版本。

**工作逻辑**：
原构造器未调用 `withCommand`，依赖 Azurite 镜像的默认入口启动。本提交在 `this.addEnv("AZURITE_ACCOUNTS", ...)` 之后新增一行 `this.withCommand("azurite", "--blobHost", "0.0.0.0", "--skipApiVersionCheck")`。由于 `withCommand` 会整体替换镜像默认命令，必须显式带上 `--blobHost 0.0.0.0` 才能让 blob 服务监听在所有网卡上，从而保持通过 Testcontainers 端口映射从主机可达；而 `--skipApiVersionCheck` 是 Azurite 的命令行开关，使其不校验请求所声明的 API 版本是否在支持范围内。这样即便 Azure SDK `1.3.4` 发出 `2026-02-06` 的请求（超出 Azurite `3.35.0` 支持的 `2025-11-05` 上限），Azurite 仍会正常处理，从而保持集成测试可运行。

## 总结

本次提交把 Azure SDK BOM patch 升级到 `1.3.4`，并通过为 Azurite 测试容器新增显式启动命令（含 `--blobHost 0.0.0.0` 与 `--skipApiVersionCheck`），修补了因新 SDK 使用更高 API 版本而导致的本地模拟器兼容性问题。两处改动配套，使依赖升级得以安全落地、集成测试不被破坏，体现了依赖升级与测试基础设施维护需协同推进的实践。
