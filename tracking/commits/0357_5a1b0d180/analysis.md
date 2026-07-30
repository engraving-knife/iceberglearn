# 提交 0357：Build: Bump nessie from 0.76.0 to 0.76.2 (#9467)

## 提交信息

- **序号**：0357
- **哈希**：5a1b0d1802e17f92df7c7e98e1e4e7c1486bd37c
- **短哈希**：5a1b0d180
- **日期**：2024-01-15 09:15:17 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.76.0 to 0.76.2 (#9467)
- **PR/Issue**：#9467

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目对 Nessie（ProjectNessie）的依赖版本从 `0.76.0` 升级到 `0.76.2`，属于一次 patch 级别的依赖维护（`update-type: version-update:semver-patch`）。Nessie 是 Iceberg 支持的几种 catalog 实现之一（位于 `nessie/` 模块），通过 `NessieCatalog` / `NessieIcebergClient` 与 Nessie 服务端交互，提供事务化的多表/多分支元数据管理（类似 Git 的数据湖版本控制）。本次升级一次性更新四个 Nessie 模块：`org.projectnessie.nessie:nessie-client`（生产客户端）、`org.projectnessie.nessie:nessie-jaxrs-testextension`（JAX-RS 测试扩展）、`org.projectnessie.nessie:nessie-versioned-storage-inmemory`（内存存储后端，测试用）、`org.projectnessie.nessie:nessie-versioned-storage-testextension`（存储测试扩展），均从 `0.76.0` 升到 `0.76.2`。

之所以四个模块同步升级，是因为 Iceberg 的 `gradle/libs.versions.toml` 用一个 `nessie` 版本变量统管这四个模块的版本（避免版本错配）。Nessie 的 patch 版本升级（0.76.0 → 0.76.2）通常包含缺陷修复与小改进，例如 Nessie 客户端在 API v2 下的请求重试逻辑、`ContentKey` 序列化兼容性、`IcebergContent` unwrap 行为、`nessie-jaxrs-testextension` 在 JUnit 测试中启动内存 Nessie 服务的稳定性等。这类升级对 Iceberg 的 `NessieCatalog` 用户意味着更稳定的 Nessie 集成体验，同时不会引入 breaking change（patch 版本严格向后兼容）。

本次升级紧随同一天（2024-01-15）的 AWS SDK BOM 升级（提交 0356），属于 Iceberg 项目日常依赖维护节奏的一部分。Dependabot 的 PR 描述列出了所有四个被更新的依赖及其 `dependency-type: direct:production` 与 `update-type: version-update:semver-patch` 标签，确认这是一次纯 patch 升级，预期无需任何代码改动。

## 如何达成设计目的

实现路径极简：在 `gradle/libs.versions.toml` 这个 Gradle Version Catalog 中，把 `nessie` 这一项的版本字符串从 `"0.76.0"` 改为 `"0.76.2"`。该版本变量被 `[libraries]` 段中所有四个 Nessie 模块引用（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`），改一处即同步升级四个模块，是 Version Catalog 集中治理依赖版本的标准模式。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 依赖版本从 `0.76.0` 升级到 `0.76.2`，作为本次依赖升级的唯一改动点。

**工作逻辑**：在版本目录文件的 `[versions]` 段中，把 `nessie = "0.76.0"` 改为 `nessie = "0.76.2"`。该 key 在 `[libraries]` 段被四个 Nessie 模块共享引用（通过 `version.ref = "nessie"` 形式），改一处即让 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension` 四个依赖同步升级到 `0.76.2`。Nessie 0.76.x 系列 patch 升级严格向后兼容，CI 流水线预期直接通过。这是 Dependabot 升级 `direct:production` 依赖的标准最小改动模式，与同日提交 0356（AWS SDK BOM 升级）属于同一批 Dependabot 自动维护 PR。

## 小结

本次提交是 Dependabot 对 Nessie 依赖的一次 patch 升级（0.76.0 → 0.76.2），仅修改 `gradle/libs.versions.toml` 中 `nessie` 一项版本字符串，通过 Version Catalog 的 `version.ref` 机制自动同步到四个 Nessie 模块（client、jaxrs-testextension、versioned-storage-inmemory、versioned-storage-testextension）。Nessie 的 SemVer patch 承诺保证了向后兼容，本次升级为 Iceberg 的 Nessie 集成带来上游缺陷修复，无需任何代码改动。这是项目日常依赖维护的一部分，与同日的 AWS SDK BOM 升级共同维护了 Iceberg 依赖树的健康度。
