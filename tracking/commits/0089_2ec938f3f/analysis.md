# 提交 0089：Build: Bump nessie from 0.72.0 to 0.72.1 (#8900)

## 提交信息

- **序号**：0089 / 4088
- **哈希**：2ec938f3f18427292bb970fdd049469140d8d1c4
- **短哈希**：2ec938f3f
- **日期**：2023-10-25 13:59:16 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.72.0 to 0.72.1 (#8900)
- **PR/Issue**：#8900

## 总体目的

这个提交要解决的是 Nessie 依赖版本滞后的依赖维护问题。Iceberg 通过 Gradle 版本目录 `gradle/libs.versions.toml` 统一管理依赖版本，其中 `nessie` 这一项被 4 个 artifact 共同引用：`org.projectnessie.nessie:nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`。这些 artifact 用于 Iceberg 的 Nessie catalog 集成及其测试。Nessie 上游发布了 0.72.1 补丁版本（0.72.0 → 0.72.1，semver patch 升级），本提交由 dependabot 自动发起，把版本目录里的 `nessie` 版本号从 `0.72.0` 升到 `0.72.1`，从而使上述 4 个 artifact 同步升级。

这是 Iceberg 持续进行依赖健康维护的一部分。对 Iceberg 演进的意义在于：及时跟进上游补丁版本，获取 bug 修复与小幅改进，避免依赖长期漂移后升级困难；同时通过 dependabot 自动化降低维护成本。由于是 patch 级升级，按 semver 约定不引入破坏性变更，风险较低。

## 如何达成设计目的

设计思路高度集中：由于 Iceberg 用 Gradle 版本目录集中管理 Nessie 版本号（`nessie = "0.72.0"`），所有 Nessie 相关 artifact 都通过 `${libs.nessie}` 形式引用同一个版本变量，因此只需把这一行版本号改成 `0.72.1`，4 个 artifact 的版本就同步升级。无需逐个 artifact 改动，体现了版本目录的维护优势。改动只有 1 行。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Nessie 依赖版本从 0.72.0 升级到 0.72.1，使 Nessie client 及 3 个测试扩展 artifact 同步升级到最新补丁版本。

**工作逻辑**：在 `[versions]` 段将 `nessie = "0.72.0"` 改为 `nessie = "0.72.1"`。根据 dependabot 在 commit message 中列出的清单，受影响的 4 个 direct:production 依赖为 `org.projectnessie.nessie:nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`，均属 `version-update:semver-patch` 类型升级。提交由 dependabot 签名（`Signed-off-by: dependabot[bot]`），并由 dependabot 与合入者共同署名（`Co-authored-by: dependabot[bot]`）。

## 小结

通过把 Gradle 版本目录中的 Nessie 版本号从 0.72.0 升到 0.72.1，本提交以一行改动同步升级了 4 个 Nessie artifact，是 Iceberg 依赖健康维护与 dependabot 自动化的一次例行补丁升级。
