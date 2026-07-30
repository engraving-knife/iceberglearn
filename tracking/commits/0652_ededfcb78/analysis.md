# 提交 0652：Build: Bump com.azure:azure-sdk-bom from 1.2.21 to 1.2.22

## 提交信息
- **序号**：0652 / 4088
- **哈希**：ededfcb78f75940265cba24e61c5f9c218d45b95
- **短哈希**：ededfcb78
- **日期**：2024-04-02（Tue Apr 2 08:42:44 2024 +0200）
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.21 to 1.2.22 (#10071)。Bumps com.azure:azure-sdk-bom from 1.2.21 to 1.2.22。属于 version-update:semver-patch（补丁版本升级），direct:production 类型依赖。
- **PR/Issue**：#10071

## 总体目的

本提交由 GitHub Dependabot 自动生成，目的是将项目所依赖的 `com.azure:azure-sdk-bom`（Azure SDK for Java 的物料清单/BOM）从 1.2.21 版本升级到 1.2.22 版本。

Azure SDK BOM 是一个 POM 类型的依赖清单，用于统一管理 Azure SDK 全家桶（如 azure-storage、azure-identity、azure-cosmos 等）各模块的版本，使项目引入 Azure 相关组件时无需逐个指定版本号即可获得经过兼容性验证的版本组合。Iceberg 项目中存在 Azure 相关的集成模块（`azure` 模块，用于支持以 Azure Blob Storage / ADLS 作为底层存储），通过该 BOM 来约束 Azure SDK 各组件版本。

1.2.21 到 1.2.22 属于 1.2.x 主线上的补丁版本升级，依据语义化版本规范只包含缺陷修复与小幅改进，不引入破坏性 API 变更。Dependabot 定期扫描 `gradle/libs.versions.toml` 中声明的依赖版本，发现可升级的补丁版本后自动发起 PR，附带了 Azure SDK 仓库的 Release notes 与 Commits 对比链接。本次升级旨在获取该区间内的累积修复（如兼容性、稳定性或安全补丁），保持 Azure SDK 依赖处于较新的稳定状态。

## 如何达成设计目的

提交策略非常直接：Dependabot 仅修改集中管理依赖版本的 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `azuresdk-bom` 的版本字面量从 `"1.2.21"` 改为 `"1.2.22"`。由于该变量在版本目录中被统一定义，所有引用 `libs.azure.sdk.bom`（或等价别名）的模块构建时会自动解析到新版本，并通过 BOM 传递性地统一 Azure 各子模块版本。这是一次最小化的单行变更，符合 Dependabot 自动依赖升级的一贯做法。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 Azure SDK BOM 版本从 1.2.21 提升到 1.2.22。
**工作逻辑**：该文件是 Gradle 版本目录（Version Catalog），集中声明项目中所有第三方库的版本。原行 `azuresdk-bom = "1.2.21"` 被改为 `azuresdk-bom = "1.2.22"`。相邻上下文可见 `awssdk-bom = "2.25.21"`（AWS SDK BOM）、`awssdk-s3accessgrants = "2.0.0"`、`caffeine`、`calcite` 等其它依赖版本。修改后，所有通过版本目录引用 Azure SDK BOM 的模块（主要是 `azure` 模块）在下次构建时解析到 1.2.22，并通过 BOM 传递性地统一其引入的 Azure 子组件版本。

## 小结
- **成效**：成功达成目的，将 azure-sdk-bom 补丁版本从 1.2.21 升至 1.2.22，属于低风险补丁升级。
- **影响范围**：构建依赖管理（`gradle/libs.versions.toml`），间接影响 `azure` 模块及所有运行时依赖 Azure SDK 的集成代码。
- **回迁到 1.4.x 的注意事项**：无特殊注意点。该变更仅涉及一行版本字符串，回迁时直接应用即可。需确认 1.4.x 分支上 Azure 相关模块的代码与 1.2.22 兼容（补丁版本升级通常向后兼容）。若 1.4.x 已有其它依赖升级改动了同一版本目录行（如 awssdk-bom），注意合并冲突即可。
