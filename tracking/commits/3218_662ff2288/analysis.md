# 提交 3218：Build: Bump nessie from 0.107.0 to 0.107.1 (#15259)

## 提交信息

- **序号**：3218 / 4088
- **哈希**：662ff2288ae89ab7aab29272eeb15b69ffa07403
- **短哈希**：662ff2288
- **日期**：2026-02-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.107.0 to 0.107.1 (#15259)
- **PR/Issue**：#15259

## 总体目的

这是一次 dependabot 发起的依赖版本升级，针对的是 Nessie 相关的一组制品。Nessie 是面向数据湖的版本化目录服务（Project Nessie），Iceberg 通过 `nessie-client` 与之对接，把 Nessie 作为可选的 Catalog 实现（NessieCatalog），并使用 `nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 等测试扩展在内存/测试环境中运行 Nessie 服务以进行集成测试。这四个制品共享同一个版本号 `nessie`，定义在 `gradle/libs.versions.toml` 中作为一个统一的版本引用（version reference）。

本次把 `nessie` 从 `0.107.0` 提升到 `0.107.1`，属于 `semver-patch`（修订号）升级。按 Nessie 的版本约定，patch 升级主要为缺陷修复与小改进，保持 API/ABI 兼容。升级动机是跟进上游修复，避免 Nessie 集成与测试链路因已知问题而受阻。

## 如何达成设计目的

作为 dependabot 自动化升级，整体思路是在集中式版本目录 `gradle/libs.versions.toml` 中把 `nessie` 版本变量从 `0.107.0` 改为 `0.107.1`。由于四个 nessie 制品都通过 `version.ref = "nessie"` 引用该变量，单点修改即可同时更新全部四个制品版本，无需改动各自的具体声明。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将统一管理的 `nessie` 版本变量从 0.107.0 升到 0.107.1。

**工作逻辑**：
在 `[versions]` 段中，将 `nessie = "0.107.0"` 修改为 `nessie = "0.107.1"`。该变量被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 四个制品的 `version.ref` 共同引用，因此一次修改即可将生产侧的 Nessie 客户端与测试侧的测试扩展同步升级到 0.107.1，保证客户端与测试用 Nessie 服务版本一致。修订号升级预期不引入破坏性 API 变更，对 `NessieCatalog` 的运行时行为与相关集成测试应为透明或仅带来修复收益。

## 总结

本提交由 dependabot 将集中式版本目录中的 `nessie` 版本变量从 0.107.0 升级到 0.107.1（修订号升级，兼容），同步更新了 Nessie 客户端及三套 Nessie 测试扩展制品，用于跟进上游修复并保持 Nessie Catalog 集成与测试链路的新鲜度；改动为单点版本号替换，不涉及代码逻辑。
