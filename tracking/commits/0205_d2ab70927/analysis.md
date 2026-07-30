# 提交 0205：Build: Bump nessie from 0.73.0 to 0.74.0 (#9153)

## 提交信息

- **序号**：0205 / 4088
- **哈希**：d2ab70927c9509b10b858845bfac8c4ebb9acfec
- **短哈希**：d2ab70927
- **日期**：2023-11-29 00:14:28 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.73.0 to 0.74.0 (#9153)
- **PR/Issue**：#9153

## 总体目的

这是一次由 GitHub Dependabot 自动生成的依赖版本升级提交，把 Iceberg 项目使用的 [Nessie](https://github.com/projectnessie/nessie) 相关工件从 `0.73.0` 升到 `0.74.0`（一个 minor 版本升级）。

Nessie 是一个面向数据湖的"事务型目录"服务（transactional catalog），通过 Git-like 的分支与提交模型为 Iceberg 表提供版本化的元数据存储。Iceberg 通过 `nessie-client` 把 Nessie 作为一类 `Catalog` 实现来支持（即 `NessieCatalog`）。受这次升级影响的 4 个工件是：
- `org.projectnessie.nessie:nessie-client`：生产用客户端，`NessieCatalog` 直接依赖它连接 Nessie 服务；
- `org.projectnessie.nessie:nessie-jaxrs-testextension`：测试用，在 JUnit 测试里拉起一个内存 Nessie HTTP 服务；
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory`：测试用，Nessie 的内存版存储后端；
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`：测试用，把内存存储接到 JUnit。

Dependabot 把这 4 个工件全部标记为 `dependency-type: direct:production`、`update-type: version-update:semver-minor`。Nessie 0.73 → 0.74 是 minor 版本，按 Nessie 的版本约定通常包含新 API、bug 修复和协议改进，但保持向后兼容（Nessie 客户端与服务端有版本协商机制，新客户端可以连旧服务端，反之一般也兼容一定范围）。保持 Nessie 客户端紧跟上游对 Iceberg 的意义在于：拿到最新的 API 能力（如新的 reference 类型、内容缓存改进）、修复已知的客户端 bug、并与最新版 Nessie 服务端无缝协作——这对在生产中用 Nessie 作为 Iceberg catalog 的用户是重要保障。对 Iceberg 自身的 Nessie 集成测试（用 in-memory Nessie 起服务跑端到端测试）而言，升级测试侧工件能确保测试覆盖到最新协议路径。

## 如何达成设计目的

通过修改 Gradle 版本目录 [`gradle/libs.versions.toml`](gradle/libs.versions.toml) 中 `nessie` 这一个版本变量，把 `0.73.0` 改为 `0.74.0`。由于项目里所有 4 个 nessie 工件坐标（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension`）都以 `version.ref = "nessie"` 形式引用这个变量，一处改动即可联动升级全部 nessie 工件。改动共 1 个文件、1 行新增、1 行删除，无任何代码或测试逻辑变化。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把版本目录中 nessie 的版本变量从 `0.73.0` 升级到 `0.74.0`。

**工作逻辑**：在 `[versions]` 段把
```toml
nessie = "0.73.0"
```
改为
```toml
nessie = "0.74.0"
```
该变量随后被 `[libraries]` 段中 4 个 nessie 工件坐标以 `version.ref = "nessie"` 引用：
```toml
nessie-client = { module = "org.projectnessie.nessie:nessie-client", version.ref = "nessie" }
nessie-jaxrs-testextension = { module = "org.projectnessie.nessie:nessie-jaxrs-testextension", version.ref = "nessie" }
nessie-versioned-storage-inmemory = { module = "org.projectnessie.nessie:nessie-versioned-storage-inmemory", version.ref = "nessie" }
nessie-versioned-storage-testextension = { module = "org.projectnessie.nessie:nessie-versioned-storage-testextension", version.ref = "nessie" }
```
因此一次升版即可让 `nessie` 模块（生产客户端）和 nessie 集成测试模块的 4 个工件同时升到 0.74.0。

## 小结

这是一次由 Dependabot 生成的常规 minor 版本依赖升级，把 Iceberg 的 Nessie 客户端与测试侧工件从 0.73.0 提升到 0.74.0，无代码逻辑变化，目的是保持 Nessie 集成栈与上游最新稳定版同步。
