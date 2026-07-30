# 提交 1472：Build: Bump com.azure:azure-sdk-bom from 1.2.29 to 1.2.30 (#11725)

## 提交信息

- **序号**：1472 / 4088
- **哈希**：0662373a676c40dabdfe66b8672b404cd53bb878
- **短哈希**：0662373a6
- **日期**：2024-12-09（Mon Dec 9 14:11:59 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.29 to 1.2.30 (#11725)
- **PR/Issue**：#11725

## 总体目的

Apache Iceberg 的 `azure` 模块依赖 Azure SDK for Java，通过 Gradle 版本目录（version catalog）统一管理 Azure SDK 各子组件的版本，由 `com.azure:azure-sdk-bom` 这个 BOM（Bill of Materials）来锁定所有 Azure SDK 子模块的版本。

Dependabot 是 GitHub 提供的自动化依赖更新机器人，它会定期检查依赖的最新版本，发现新版本后自动提交 PR。本提交就是 Dependabot 自动生成的依赖升级 PR：把 `azure-sdk-bom` 从 1.2.29 升级到 1.2.30（patch 版本升级）。

升级 patch 版本通常意味着 Azure SDK 内部的 bug 修复、小幅改进，不会引入 API 破坏性变更，对 Iceberg 而言风险很低。这类自动化升级能保证 Iceberg 使用的 Azure SDK 始终跟随上游修复，避免长期累积后被迫一次性升级带来的风险。

## 如何达成设计目的

直接修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `azuresdk-bom` 的版本字符串从 `"1.2.29"` 改为 `"1.2.30"`。无代码逻辑改动，所有引用该 BOM 的子模块（如 azure-storage-blob、azure-identity 等）的版本会自动跟随 BOM 升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Azure SDK BOM 版本。

**工作逻辑**：版本目录文件中第 33 行：

```toml
-azuresdk-bom = "1.2.29"
+azuresdk-bom = "1.2.30"
```

`azuresdk-bom` 是 Iceberg 内部对 `com.azure:azure-sdk-bom` 这个 Maven 坐标的别名，定义在 `libs.versions.toml` 的 `[versions]` 区块。后续在 `[libraries]` 区块中通过 `azure-sdk-bom = { module = "com.azure:azure-sdk-bom", version.ref = "azuresdk-bom" }` 引用，并由 `azure` 模块的 `build.gradle` 通过 `platform(libs.azure.sdk.bom)` 引入。因此只改一行字符串即可同步升级整个 Azure SDK 套件。

## 小结

- **成效**：Azure SDK BOM 从 1.2.29 升至 1.2.30，Iceberg 的 `azure` 模块随之获得 Azure SDK 上游的 bug 修复与小改进。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，单行改动，无业务代码变更。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支维护各自的依赖版本。若 1.4.x 仍使用较旧的 `azure-sdk-bom`，且希望获得 1.2.30 的修复，可考虑回迁；但若 1.4.x 上游 Azure SDK 已发布更高 patch 版本，应直接升级到更高版本。通常 1.4.x 不强制跟随 main 的每一次 Dependabot 升级，**可选回迁**，且必须验证 1.4.x 已有的 Azure 集成测试通过。
