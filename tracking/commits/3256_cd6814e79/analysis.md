# 提交 3256：Build: Bump nessie from 0.107.1 to 0.107.2 (#15323)

## 提交信息

- **序号**：3256 / 4088
- **哈希**：cd6814e795a5851c65f296d52059362370af0c6b
- **短哈希**：cd6814e79
- **日期**：2026-02-15
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.107.1 to 0.107.2 (#15323)
- **PR/Issue**：#15323

## 总体目的

这是一次由 GitHub Dependabot 自动生成的依赖版本升级提交。Iceberg 通过专门的 `nessie` 模块集成了 Project Nessie —— 一个面向数据湖的事务型目录（catalog）服务，使 Iceberg 能够以 Nessie 作为表的目录后端，并支持分支/标签等 Git 风格的数据版本管理能力。

仓库在 `gradle/libs.versions.toml` 中以版本变量 `nessie` 统一管理多个 Nessie 制品的版本，包括 `nessie-client`（客户端 API，用于连接 Nessie 服务）、`nessie-jaxrs-testextension`（JAX-RS 测试扩展，用于在测试中启动内嵌的 Nessie 服务）、`nessie-versioned-storage-inmemory`（内存版存储实现，主要用于测试）以及 `nessie-versioned-storage-testextension`（版本化存储测试扩展）。这些组件共同支撑了 Iceberg NessieCatalog 的功能实现与集成测试。

本次提交将这些制品从 `0.107.1` 升级到 `0.107.2`。根据提交说明中的 `update-type: version-update:semver-patch` 标记，这是一次语义化版本中的补丁级（patch）升级，按照约定仅包含向后兼容的缺陷修复和内部改进，不引入破坏性 API 变更，因此预期不会影响 Iceberg 现有的 NessieCatalog 功能与测试。

## 如何达成设计目的

改动极其精简，仅在 `gradle/libs.versions.toml` 中将 `nessie` 版本变量从 `0.107.1` 改为 `0.107.2`。由于所有 Nessie 制品均通过 `version.ref = "nessie"` 引用该变量，单点修改即可同步升级全部四个相关制品，无需改动各模块的 `build.gradle` 文件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Nessie 依赖版本从 0.107.1 升级到 0.107.2。

**工作逻辑**：
将版本目录（version catalog）中的 `nessie = "0.107.1"` 修改为 `nessie = "0.107.2"`。该变量被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension` 四个库定义通过 `version.ref = "nessie"` 引用，因此这一处改动会在构建时自动把上述所有制品解析到 0.107.2 版本。补丁版本升级通常只包含 bug 修复与小的稳定性改进，风险较低，Dependabot 也因此自动提交该 PR 而无需额外的人工评审。

## 总结

本次提交是 Dependabot 对 Nessie 依赖的一次常规补丁版本升级，通过单点修改版本变量将四个 Nessie 制品统一从 0.107.1 提升到 0.107.2，用以获取上游的最新缺陷修复，保持 Iceberg Nessie 目录集成与相关测试所依赖的客户端库处于较新且稳定的版本。
