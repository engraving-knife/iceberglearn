# 提交 0829：Build: Bump Nessie to 0.90.4 (#10492)

## 提交信息
- **序号**：0829 / 4088
- **哈希**：74cf6977b9db46df2850f29d099764d76950b6a2
- **短哈希**：74cf6977b
- **日期**：2024-06-14
- **作者**：Alexandre Dutra
- **提交说明**：Build: Bump Nessie to 0.90.4 (#10492)
- **PR/Issue**：#10492

## 总体目的

将 Nessie 依赖从 0.83.2 升级到 0.90.4，跟进 Nessie 上游版本。Nessie 是 Iceberg 支持的一种「事务型目录」（catalog）实现，对应 `iceberg-nessie` 模块（位于 `nessie/` 目录，artifact 名为 `iceberg-nessie`）。该模块通过 Nessie 提供的 client API 与 Nessie 服务交互，把 Iceberg 的 catalog 操作（commit/branch/merge 等）映射到 Nessie 的事务模型。

本次升级跨度较大（从 0.83.2 跨越 7 个 minor 版本到 0.90.4），属于 Nessie 在 0.83 → 0.90 期间的常规迭代跟进。Nessie 在 0.9x 阶段持续打磨 API、修复 bug、提升与 Iceberg 的兼容性，因此 Iceberg 端需要定期升级以保持二者的协同。本次只动 version catalog 中一行版本号，没有任何源码改动，说明 0.83.2 → 0.90.4 之间 Nessie 的 client/test API 对 Iceberg 当前使用方式没有破坏性变更。

## 如何达成设计目的

Iceberg 用 Gradle version catalog（`gradle/libs.versions.toml`）集中声明 Nessie 版本，所有引用都通过 `version.ref = "nessie"` 间接指向：

```toml
nessie-client = { module = "org.projectnessie.nessie:nessie-client", version.ref = "nessie" }
nessie-jaxrs-testextension = { module = "org.projectnessie.nessie:nessie-jaxrs-testextension", version.ref = "nessie" }
nessie-versioned-storage-inmemory = { module = "org.projectnessie.nessie:nessie-versioned-storage-inmemory", version.ref = "nessie" }
nessie-versioned-storage-testextension = { module = "org.projectnessie.nessie:nessie-versioned-storage-testextension", version.ref = "nessie" }
```

在根 `build.gradle` 的 `:iceberg-nessie` 子项目里，`nessie-client` 作为 `implementation` 依赖，其余三个作为 `testImplementation`（用于启动 in-memory Nessie 服务跑集成测试）。改完 version catalog 一行后，所有四个依赖的版本号会自动同步到 0.90.4，无需逐个修改 build.gradle。

升级后的兼容性体现在：
- **客户端 API**：`nessie-client` 是 Iceberg 在 `nessie/src/main/java/org/apache/iceberg/nessie/` 下调用 Nessie 的主要入口（`NessieCatalog`、`NessieTableOperations` 等）。0.83→0.90 期间 Nessie 的 client API 保持兼容，因此 Iceberg 源码无需改动。
- **测试扩展**：`nessie-jaxrs-testextension` 与 `nessie-versioned-storage-testextension` 是 Iceberg 在测试中启动 Nessie 服务的机制。这两个扩展在 0.90.4 仍提供与 0.83.2 相同的 API，因此测试代码也无需调整。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 Nessie 版本号从 0.83.2 升级到 0.90.4。

**工作逻辑**：在 `[versions]` 段中将 `nessie = "0.83.2"` 改为 `nessie = "0.90.4"`。该值通过 `version.ref` 被 `[libraries]` 段中四个 Nessie 依赖（nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory、nessie-versioned-storage-testextension）引用，并被 `:iceberg-nessie` 子项目的 `build.gradle` 配置使用。一行修改即可同步升级生产依赖与测试依赖。

## 小结
- **成效**：将 `iceberg-nessie` 模块使用的 Nessie 依赖从 0.83.2 升级到 0.90.4，跟进上游 7 个 minor 版本的修复与改进；通过 version catalog 单点升级，无需任何源码改动。
- **影响范围**：仅影响 `iceberg-nessie` 模块（位于 `nessie/` 目录）的运行时与测试类路径；以及通过 `project(':iceberg-nessie')` 间接引用它的 hive-runtime、flink 1.15/1.16/1.17、spark v3.2/v3.3/v3.4/v3.5 等模块的运行时类路径（这些模块在 runtime 引入 iceberg-nessie 以支持 Nessie catalog）。不影响其他 catalog 实现（HiveCatalog、JdbcCatalog、RESTCatalog 等）。
- **回迁注意事项**：
  1. 1.4.x 分支当前的 `gradle/libs.versions.toml` 中 `nessie` 版本可能远低于 0.83.2（如 0.71.0），跨度更大。回迁前需先核对 1.4.x 实际版本与 0.90.4 之间是否有 Nessie client API 的破坏性变更（如 `NessieApiV1`、`Reference`、`Content`、`Operation` 等接口签名变化）。
  2. Nessie 在 0.7x → 0.9x 期间可能调整过 client 初始化方式、iceberg 集成相关 API、testextension 的启动参数。如果 1.4.x 还停留在 0.71.0，直接跳到 0.90.4 极可能编译失败或测试启动失败，需要同步调整 `NessieCatalog`、`NessieUtil`、测试基类等源码。
  3. 推荐做法：先在 1.4.x 上单独跑一遍 `:iceberg-nessie:test`，如果 Nessie client/testextension API 有 break，则把 main 分支上 0.71.0 → 0.83.2 → 0.90.4 之间的相关 Nessie 适配提交一并回迁（不只是这次单点版本号修改）。
  4. 还需确认 1.4.x 支持的 Spark/Flink/Hive 版本运行时是否与 Nessie 0.90.4 的传递依赖（如 Jackson、Jersey、gRPC 等）有冲突，必要时显式排除或锁定版本。
