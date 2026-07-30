# 提交 3187：Build: Bump nessie from 0.106.1 to 0.107.0 (#15197)

## 提交信息

- **序号**：3187 / 4088
- **哈希**：8f3328a765d93226e23e454ee363152de4f4fb82
- **短哈希**：8f3328a76
- **日期**：2026-01-31
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.106.1 to 0.107.0 (#15197)
- **PR/Issue**：#15197

## 总体目的

这是 Dependabot 自动发起的依赖升级，将 Project Nessie 的版本从 `0.106.1` 升至 `0.107.0`。Project Nessie 是一个面向数据湖的事务型目录服务（"数据湖的 Git"），Iceberg 通过 `nessie-client` 与 Nessie Catalog 集成，使表元数据的分支、提交、回滚等操作成为可能。

在 Iceberg 仓库中，`nessie` 版本号通过 `gradle/libs.versions.toml` 统一管理，并被以下四个制品引用：`org.projectnessie.nessie:nessie-client`（Nessie Catalog 客户端实现，属生产依赖）、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`（后三者用于 Nessie Catalog 的集成/单元测试）。因此本次升级同时影响生产客户端与测试扩展，四个制品版本必须保持一致以避免 API 不匹配。

本次为语义化版本的 **minor** 升级（`0.106.x` → `0.107.x`）。Nessie 在 `0.x` 阶段尚处于"次版本可能引入不兼容变更"的开发期，因此 minor 升级在语义上比 patch 更具风险，但项目已通过 CI 验证升级不会破坏现有 Nessie Catalog 测试。预期影响是获得 Nessie `0.107.0` 的缺陷修复与新特性，并跟进上游最新 API，避免目录集成在后续 Iceberg 版本中落后于 Nessie 的演进。

## 如何达成设计目的

Dependabot 仅修改 `gradle/libs.versions.toml` 中 `nessie` 这一个版本引用变量，由于四个 Nessie 制品都通过 `version.ref = "nessie"` 统一引用同一变量，单点修改即可让全部 Nessie 制品一致升级，Gradle 在解析时会自动把新版本应用到客户端与测试扩展。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Nessie 版本号从 `0.106.1` 提升至 `0.107.0`。

**工作逻辑**：
将 `[versions]` 段中的 `nessie = "0.106.1"` 改为 `nessie = "0.107.0"`。该变量被 `[libraries]` 段的 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension` 通过 `version.ref = "nessie"` 引用，因此这一处改动会联动更新全部四个 Nessie 制品版本，保证客户端与测试扩展版本对齐。

## 总结

本次提交通过单点修改版本变量，把 Iceberg 与 Project Nessie 集成所用的全部制品从 `0.106.1` 一致升级到 `0.107.0`，跟进上游 minor 版本以获取修复与新特性，并保持客户端与测试扩展的版本一致性，属于常规的依赖维护升级。
