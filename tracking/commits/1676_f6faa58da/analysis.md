# 提交 1676：Build: Bump com.azure:azure-sdk-bom from 1.2.30 to 1.2.31 (#12154)

## 提交信息

- **序号**：1676 / 4088
- **哈希**：f6faa58dac57e03be6e02a43937ac7c15c770225
- **短哈希**：f6faa58da
- **日期**：2025-02-02（Sun Feb 2 20:25:46 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.30 to 1.2.31 (#12154)
- **PR/Issue**：#12154

## 总体目的

Dependabot 自动升级，把 Azure SDK for Java 的 BOM（Bill of Materials）从 `1.2.30` 升到 `1.2.31`。`1.2.30 → 1.2.31` 是 patch 升级，仅含 bug 修复与服务端 API 更新，无破坏性 API 变更。

Azure SDK for Java 是 Iceberg Azure 集成模块（`iceberg-azure`）的核心依赖，用于 Azure Blob Storage / Data Lake Storage（ADLS Gen2）的数据文件读写。`azuresdk-bom` 作为 BOM 统一管理 `azure-storage-blob`、`azure-storage-file-datalake` 等子模块版本，确保彼此兼容。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `azuresdk-bom` 的版本号。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

```diff
-azuresdk-bom = "1.2.30"
+azuresdk-bom = "1.2.31"
```

## 小结

- **成效**：Azure SDK for Java 全家桶从 1.2.30 升到 1.2.31，获取上游 patch 修复（通常含 Blob Storage / ADLS 的 bug 修复与 Azure 区域端点更新）。
- **影响范围**：`iceberg-azure` 模块及依赖该模块的 Spark/Flink 集成。由于是 patch 升级，无 API 变更风险。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick。需确认 1.4.x 的 `gradle/libs.versions.toml` 中 `azuresdk-bom` 命名一致。建议回迁后回归 Azure Blob / ADLS 读写路径的测试（如 `TestAzureBlobFileSystem`、`TestADLSLocation` 等，若有对应测试环境）。Azure SDK patch 升级通常无破坏。
