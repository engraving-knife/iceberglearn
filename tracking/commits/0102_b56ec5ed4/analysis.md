# 提交 0102：Build: Bump nessie from 0.72.1 to 0.73.0 (#8941)

## 提交信息

- **序号**：0102 / 4088
- **哈希**：b56ec5ed421cb82d0c87c4fdd2a579db40f0e036
- **短哈希**：b56ec5ed4
- **日期**：2023-10-30 08:22:45 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.72.1 to 0.73.0 (#8941)
- **PR/Issue**：#8941

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 Nessie 的版本从 0.72.1 升级到 0.73.0。Nessie（Project Nessie）是一个提供 Git 风格数据版本控制的目录服务，Iceberg 通过 `nessie` 模块支持以 Nessie 作为 Catalog 后端。本次升级一次性更新了四个 Nessie 制品：

- `org.projectnessie.nessie:nessie-client`（生产环境直接使用的客户端）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（测试用 JAX-RS 扩展）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory`（内存版存储实现，测试用）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（存储测试扩展）

这四个制品在 `gradle/libs.versions.toml` 中共享同一个版本变量 `nessie`，因此一次版本号修改即可同步升级全部。Nessie 仍在快速演进期（0.x 版本），其 API 和协议可能在小版本间发生变化；保持与上游同步可让 Iceberg 的 Nessie Catalog 实现持续兼容最新的 Nessie 服务端，并获取上游的缺陷修复。

该升级对 Iceberg 演进的意义在于：维护 Iceberg 与多版本控制型 Catalog（Nessie）生态的兼容性，避免因 Nessie 客户端版本过旧而无法对接新版本 Nessie 服务端，同时确保集成测试所使用的内存 Nessie 实例与真实部署版本行为一致。

## 如何达成设计目的

设计思路同样是单点版本修改：Dependabot 仅修改 `gradle/libs.versions.toml` 中 `nessie` 这一个版本变量，从 `0.72.1` 改为 `0.73.0`。由于四个 Nessie 制品在依赖目录中都通过 `version.ref = "nessie"` 引用同一版本变量，一次修改即可同步全部四个制品。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 全家桶四个制品的统一版本号从 0.72.1 提升到 0.73.0。

**工作逻辑**：仅修改第 67 行附近的版本常量，`nessie = "0.72.1"` 改为 `nessie = "0.73.0"`。该变量被依赖目录中的 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension` 四个库条目通过 `version.ref` 引用。其中 `nessie-client` 是 `iceberg-nessie` 模块对 Nessie 服务端进行 REST 调用的运行时依赖，其余三个用于在集成测试中启动一个嵌入式 Nessie 服务（in-memory 存储后端），从而在不依赖外部 Nessie 部署的前提下验证 Iceberg 的 Catalog 操作（建表、提交、分支合并等）。提交说明的 `updated-dependencies` 元数据将四个制品都标记为 `direct:production`、`version-update:semver-minor`。

## 小结

这是 Dependabot 自动化依赖维护的一次例行升级，通过修改 Nessie 的统一版本变量，让 Iceberg 的 Nessie Catalog 集成与测试基础设施同步升级到 0.73.0。
