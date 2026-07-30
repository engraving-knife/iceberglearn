# 提交 0290：Build: Bump nessie from 0.74.0 to 0.75.0 (#9313)

## 提交信息

- **序号**：0290 / 4088
- **哈希**：838787e296b502740470ce70f68bb27af4210121
- **短哈希**：838787e29
- **日期**：2023-12-19 12:53:04 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.74.0 to 0.75.0 (#9313)
- **PR/Issue**：#9313

## 总体目的

本提交由 GitHub Dependabot 自动生成，目的是把 Iceberg 项目依赖的 Nessie 版本从 `0.74.0` 升级到 `0.75.0`。Nessie 是 Iceberg 支持的多种 Catalog 实现之一（`NessieCatalog`），通过 `org.projectnessie.nessie` 这组工件为 Iceberg 提供基于 Git 风格版本控制的目录服务。Iceberg 在 `nessie` 模块中实现了与 Nessie 的集成，并在构建文件中通过版本目录（version catalog）统一管理 Nessie 相关依赖的版本。

Nessie 是一个活跃演进的项目，会定期发布包含 bug 修复、性能改进与新 API 的版本。保持依赖版本的新鲜度有助于让 Iceberg 的 Nessie 集成用上上游最新的修复与改进，同时降低未来一次性跨多个大版本升级时的兼容性风险。Dependabot 通过 `semver-minor` 类型的升级（即 `0.74.0 → 0.75.0`）发起本 PR，按照 SemVer 语义，minor 版本升级应保持向后兼容，不会引入破坏性变更。

本提交影响的 Nessie 工件共四个，按用途分为两类：`nessie-client` 是生产依赖（直接用于 `NessieCatalog` 的客户端通信）；`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension` 三个是测试依赖（用于在 Nessie 集成测试中模拟 Nessie 服务端与版本化存储后端）。由于四个工件都通过 `version.ref = "nessie"` 引用同一个版本变量，本次升级只需修改一行版本号即可同步升级全部工件。

## 如何达成设计目的

整体设计思路是利用 Gradle 版本目录（version catalog）的集中化版本管理能力。在 `gradle/libs.versions.toml` 中，`nessie` 被定义为一个版本变量（`nessie = "0.75.0"`），四个 Nessie 工件（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`）的库声明都通过 `version.ref = "nessie"` 引用该变量。因此只需把版本变量的值从 `0.74.0` 改为 `0.75.0`，四个工件的版本就会同步升级，无需逐个修改。

这种集中化版本管理方式是 Iceberg 项目处理多工件同源依赖（如 Nessie、Spark、Flink 等）的通用模式，可以避免版本不一致导致的运行时不兼容问题。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Nessie 依赖版本从 `0.74.0` 升级到 `0.75.0`。

**工作逻辑**：

在 `[versions]` 段中，把 `nessie = "0.74.0"` 改为 `nessie = "0.75.0"`。该版本变量被以下四个库声明通过 `version.ref = "nessie"` 引用，因此一行改动即同步升级全部 Nessie 工件：

- `nessie-client = { module = "org.projectnessie.nessie:nessie-client", version.ref = "nessie" }`（生产依赖，用于 `NessieCatalog` 与 Nessie 服务端通信）
- `nessie-jaxrs-testextension = { module = "org.projectnessie.nessie:nessie-jaxrs-testextension", version.ref = "nessie" }`（测试依赖，提供 JAX-RS 测试扩展）
- `nessie-versioned-storage-inmemory = { module = "org.projectnessie.nessie:nessie-versioned-storage-inmemory", version.ref = "nessie" }`（测试依赖，提供内存版版本化存储后端）
- `nessie-versioned-storage-testextension = { module = "org.projectnessie.nessie:nessie-versioned-storage-testextension", version.ref = "nessie" }`（测试依赖，提供版本化存储测试扩展）

升级后，Iceberg 的 `nessie` 模块及其测试将自动拉取 Nessie `0.75.0` 的客户端与测试扩展工件，获得上游在 `0.74.0 → 0.75.0` 之间发布的修复与改进。

## 小结

本提交通过 Dependabot 把 `gradle/libs.versions.toml` 中的 `nessie` 版本变量从 `0.74.0` 升级到 `0.75.0`，借助版本目录的 `version.ref` 机制一次性同步升级了 `nessie-client` 及三个 Nessie 测试扩展工件的版本，使 Iceberg 的 Nessie 集成保持与上游最新发布版本对齐，降低未来跨版本升级的兼容性风险。
