# 提交 0056：Build: Bump nessie from 0.71.1 to 0.72.0 (#8835)

## 提交信息

- **序号**：0056 / 4088
- **哈希**：49b1ceb337d58a50a975839ed62daa798376965f
- **短哈希**：49b1ceb33
- **日期**：2023-10-16 07:51:03 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.71.1 to 0.72.0 (#8835)
- **PR/Issue**：#8835

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 Iceberg 项目所依赖的 Nessie 从 0.71.1 升级到 0.72.0。Nessie（Project Nessie）是一个提供事务化目录（catalog）服务的开源项目，Iceberg 通过 `nessie-client` 等模块与之集成，把 Nessie 作为 Iceberg 表的一种目录实现（即 NessieCatalog），用于跨多个表/分支的原子性提交与版本化数据湖管理。

本次升级属于 semver-minor（次版本号）升级，受影响的制品共有四个：生产用途的 `org.projectnessie.nessie:nessie-client`，以及测试用途的 `org.projectnessie.nessie:nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`。这些测试扩展用于在内存中启动 Nessie 服务，对 Iceberg 的 NessieCatalog 进行端到端集成测试。

从 Iceberg 演进角度看，持续跟进 Nessie 上游版本有两个意义：一是确保 Iceberg 的 NessieCatalog 集成能够使用上游最新的 API 与 bug 修复，二是避免因 Nessie 客户端与新版 Nessie 服务端协议不匹配而出现兼容性问题。值得注意的是，Iceberg 此前在 PR #8607 中刚做过一次"Nessie: Bump Nessie to 0.71.0, adjust to Nessie client-builder updates"的升级，说明 Nessie 客户端 API 在 0.71.x 系列发生过 builder 相关调整，因此持续小幅跟进版本是必要的维护工作。

## 如何达成设计目的

Dependabot 通过集中式版本目录（Gradle Version Catalog）来达成升级。Iceberg 在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中以单一变量 `nessie = "..."` 统一管理所有 Nessie 制品的版本，下游各模块通过 `version.ref = "nessie"` 引用。因此只需修改这一行版本号，四个制品（nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory、nessie-versioned-storage-testextension）便会同时升级到 0.72.0，无需改动任何构建脚本或源代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 版本变量从 0.71.1 提升到 0.72.0，使所有引用该变量的 Nessie 制品一并升级。

**工作逻辑**：在 `[versions]` 段中，将 `nessie = "0.71.1"` 改为 `nessie = "0.72.0"`。由于该文件中 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension` 四个库声明均使用 `version.ref = "nessie"`，这一处修改会级联生效到全部四个制品。本次升级为 minor 版本升级（0.71.x → 0.72.x），按 semver 约定可能引入新功能但不破坏既有 API，风险相对较低；但仍需依赖 CI 中的 Nessie 集成测试来验证 NessieCatalog 的读写、分支操作等行为没有回归。

## 小结

通过集中式版本目录的一行改动，把 Iceberg 对 Nessie 目录服务的依赖从 0.71.1 平滑升级到 0.72.0，保持了与上游的同步，属于低风险的常规依赖维护。
