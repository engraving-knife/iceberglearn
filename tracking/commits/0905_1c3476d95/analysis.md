# 提交 0905：Build: Bump com.azure:azure-sdk-bom from 1.2.24 to 1.2.25 (#10652)

## 提交信息

- **序号**：0905 / 4088
- **哈希**：1c3476d953865c1953d54ce02be12b5b19dafe1c
- **短哈希**：1c3476d95
- **日期**：2024-07-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.24 to 1.2.25 (#10652)
- **PR/Issue**：#10652

## 总体目的

Iceberg 通过 Azure SDK BOM（`com.azure:azure-sdk-bom`）统一管理 Azure 相关依赖（如 `azure-storage-blob`、`azure-identity` 等）的版本。dependabot 定期检查该 BOM 的新版本并提交 PR 升级。本次将 BOM 从 `1.2.24` 升级到 `1.2.25`，引入 Azure SDK 在该版本区间内的 bug 修复和小幅改进，保持依赖栈最新。这是常规的依赖维护工作，不涉及 Iceberg 自身代码逻辑变更。

## 如何达成设计目的

采用 Gradle version catalog 统一管理依赖版本：所有版本号集中在 `gradle/libs.versions.toml` 中声明，业务代码和构建脚本通过 catalog alias 引用。升级时只需修改 toml 文件中 `azuresdk-bom` 这一行版本号，所有引用该 BOM 的依赖会自动同步到新版本，无需逐个修改 build.gradle 文件。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Azure SDK BOM 版本从 1.2.24 升级到 1.2.25。

**工作逻辑**：

```diff
-azuresdk-bom = "1.2.24"
+azuresdk-bom = "1.2.25"
```

该行位于 `[versions]` 段，定义 `azuresdk-bom` 的版本常量。Gradle 构建中通过 `platform("com.azure:azure-sdk-bom:${azuresdk-bom}")` 引入 BOM，BOM 内部统一管理各 Azure SDK 子模块的版本。升级 BOM 版本后，所有未显式指定版本的 Azure SDK 子模块会自动使用 1.2.25 BOM 中声明的版本。

## 小结

- **成效**：将 Azure SDK BOM 从 1.2.24 升级到 1.2.25，引入 Azure SDK 的最新 patch 版本修复。
- **影响范围**：1 个文件 `gradle/libs.versions.toml`，1 行改动，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：可以回迁但非必须。这是常规依赖版本升级，1.4.x 分支可按需升级到相同或更新的 Azure SDK BOM 版本。回迁时需确认 1.4.x 分支使用的 Azure SDK 子模块 API 在 1.2.25 BOM 管控的版本下无破坏性变更（Azure SDK BOM patch 版本升级通常向后兼容）。若 1.4.x 已有更新的 BOM 版本则无需回迁此提交。
