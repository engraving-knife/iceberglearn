# 提交 2971：Build: Bump nessie from 0.105.7 to 0.106.0 (#14785)

## 提交信息

- **序号**：2971 / 4088
- **哈希**：faabc46f955924667dbcb0cc43b8a0da3ec56169
- **短哈希**：faabc46f9
- **日期**：2025-12-06
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.105.7 to 0.106.0 (#14785)
- **PR/Issue**：#14785

## 总体目的

这是 Dependabot 自动生成的依赖升级。Nessie（Projectnessie）是一个提供 Git 风格版本化数据目录的服务，Iceberg 通过独立的 `nessie` 模块支持以 Nessie 作为 catalog 实现。仓库在 `gradle/libs.versions.toml` 中以统一的 `nessie` 版本变量驱动一组 Nessie 制品（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`），其中后两者主要用于测试环境（内存存储测试扩展）。Dependabot 将该版本变量从 `0.105.7` 升级到 `0.106.0`，使 Iceberg 的 Nessie 集成与测试依赖跟上上游最新发布。本次升级属于 `semver-minor`（次版本）升级，预期包含向后兼容的新功能与改进，可能伴随新 API/行为，但对 Iceberg 侧使用面（catalog 客户端与测试扩展）通常保持兼容。

## 如何达成设计目的

Dependabot 只修改 `gradle/libs.versions.toml` 中 `nessie` 版本变量的值，所有通过 `version.ref = "nessie"` 引用该变量的 Nessie 制品会一并升级到 0.106.0，无需逐个修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Nessie 版本变量从 `0.105.7` 升级到 `0.106.0`。

**工作逻辑**：
在 `[versions]` 区段将 `nessie = "0.105.7"` 改为 `nessie = "0.106.0"`。该变量被 `nessie-client`（生产依赖，Nessie catalog 客户端）、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`（测试依赖，用于在测试中起内存版本化存储与 JAX-RS 服务）四个制品引用，因此一次升级即把这组 Nessie 制品整体同步到 0.106.0，避免版本不一致。

## 总结

本提交是 Dependabot 对 Nessie 依赖组的次版本升级（0.105.7 → 0.106.0），通过单一版本变量驱动 `nessie-client` 及三个测试扩展制品同步升级，用于 Iceberg 的 Nessie catalog 集成与测试。属于次版本升级，预期带来向后兼容的新功能与修复，风险较低，保持 Iceberg 与 Nessie 上游发布的同步。
