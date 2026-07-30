# 提交 1302：Build: Bump com.azure:azure-sdk-bom from 1.2.25 to 1.2.28 (#11267)

## 提交信息

- **序号**：1302 / 4088
- **哈希**：7ac617a5a8b0dedbaaa6e19caedfd846968c7cac
- **短哈希**：7ac617a5a
- **日期**：2024-10-28（Mon Oct 28 20:55:22 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.25 to 1.2.28 (#11267)
- **PR/Issue**：#11267

## 总体目的

本提交由 Dependabot 自动生成，用于将 Azure SDK for Java 的 BOM（Bill of Materials）依赖版本从 `1.2.25` 升级到 `1.2.28`。Iceberg 的 `azure` 模块（iceberg-azure）通过该 BOM 统一管理 Azure 相关 SDK（如 `azure-storage-file-datalake`、`azure-identity` 等）的版本，升级可获取最近 3 个 patch 版本中累积的 bug 修复、安全补丁与小幅改进，保持依赖栈与社区同步、降低潜在安全风险。

属于 `version-update:semver-patch`（patch 级版本升级），API 兼容，理论上无破坏性变更。

## 如何达成设计目的

直接修改仓库根目录的 Gradle 版本目录文件 `gradle/libs.versions.toml`，把 `azuresdk-bom` 这一条目的版本号由 `1.2.25` 改为 `1.2.28`。Gradle 在构建时会用 BOM 内声明的版本对 azure 模块传递依赖进行统一仲裁，无需在多个地方分别修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Azure SDK BOM 版本。

**工作逻辑**：将第 33 行附近的版本字面量由

```toml
azuresdk-bom = "1.2.25"
```

改为

```toml
azuresdk-bom = "1.2.28"
```

只此一行变更。该常量在 `build.gradle` 的 `iceberg-azure` 模块中通过 `platform("com.azure:azure-sdk-bom:${libs.versions.azuresdk.bom.get()}")` 形式被引用，作为整个 azure 模块依赖版本的统一仲裁来源。

## 小结

- **成效**：azure 模块所依赖的 Azure SDK 版本统一升至 1.2.28，与上游保持同步，获得 1.2.26 ~ 1.2.28 之间的修复。
- **影响范围**：仅 1 个文件、1 行改动；属纯构建依赖升级，无源代码或测试逻辑变更。
- **回迁到 1.4.x 的注意事项**：1.4.x 作为维护分支通常也应保持依赖栈得到必要的安全补丁。如果 1.4.x 的 azure 模块仍在使用 1.2.25 且未发现兼容性问题，可以按需回迁该 patch 升级以获取安全修复；若 1.4.x 已 freeze 依赖，则不强制回迁。回迁时只须同步该一行 toml 改动并验证 iceberg-azure 模块测试通过即可。注意 BOM 是直接生产依赖（`direct:production`），升级后应确认 `azure-storage-file-datalake` 等传递依赖版本变化不会与 1.4.x 中其他依赖（如 Hadoop、Jetty）产生冲突。
