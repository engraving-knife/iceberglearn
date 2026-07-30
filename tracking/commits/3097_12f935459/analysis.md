# 提交 3097：Build: Bump nessie from 0.106.0 to 0.106.1 (#15019)

## 提交信息

- **序号**：3097 / 4088
- **哈希**：12f935459ce0801ea3460c81c75bfcceb1f2cb3a
- **短哈希**：12f935459
- **日期**：2026-01-11
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.106.0 to 0.106.1 (#15019)
- **PR/Issue**：#15019

## 总体目的

该提交由 Dependabot 自动生成，将 Nessie 相关依赖从 0.106.0 升级到 0.106.1。Nessie（Project Nessie）是一个提供 Git 风格版本化数据目录的服务，可作为 Iceberg 的 catalog 实现之一（`NessieCatalog` / `NessieTableOperations`）。Iceberg 通过 `nessie-client` 与 Nessie 服务交互来管理表元数据的分支与提交。本次升级同时更新四个 Nessie 工件：`org.projectnessie.nessie:nessie-client`（生产用客户端）、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests` 与 `nessie-versioned-storage-testextension`（后三者用于测试环境，提供内存型/可扩展的 Nessie 服务实例以跑集成测试）。

四个工件共享同一个版本变量 `nessie`，定义在 `gradle/libs.versions.toml` 中。版本号从 0.106.0 升级到 0.106.1，属于语义版本中的 patch 级别升级（`version-update:semver-patch`）。根据提交元数据，四个依赖均归类为 `direct:production`。patch 升级通常只包含 bug 修复与小的稳定性改进，不引入 API 破坏性变更。对 Iceberg 而言，预期影响是：Nessie 客户端行为与 catalog 交互保持一致，但可能修复了客户端或测试扩展在特定场景下的缺陷，使 Nessie catalog 的集成测试更稳定。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中的 `nessie` 版本变量，从 `0.106.0` 改为 `0.106.1`。由于四个 Nessie 工件都通过 `version.ref = "nessie"` 引用该变量，单点修改即可同步升级全部 Nessie 依赖。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 版本变量，同步更新四个 Nessie 工件。

**工作逻辑**：
将 `nessie = "0.106.0"` 修改为 `nessie = "0.106.1"`。该变量被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension` 四个 lib 坐标通过 `version.ref` 引用，Gradle 在解析时会统一替换为 0.106.1。`nessie-client` 作为 `NessieCatalog` 的运行时依赖参与生产构建，其余三个测试扩展仅在跑 Nessie catalog 集成测试时使用。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 Nessie 版本从 0.106.0 提升到 0.106.1（patch 级别），同步更新 `nessie-client` 及三个测试扩展。Nessie 是 Iceberg 支持的版本化 catalog 实现之一。作为 patch 升级，预期仅包含 bug 修复，对 Iceberg 的 Nessie catalog 集成与测试无破坏性影响。
