# 提交 0602：Build: Bump nessie from 0.77.1 to 0.79.0

## 提交信息

- **序号**：0602 / 4088
- **哈希**：82137b9e7cf6ee243eb9895bc44c6b5d572e5283
- **短哈希**：82137b9e7
- **日期**：2024-03-18（Mon Mar 18 08:40:22 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.77.1 to 0.79.0 (#9976)

  完整提交说明（节选）：
  > Bumps `nessie` from 0.77.1 to 0.79.0.
  > Updates `org.projectnessie.nessie:nessie-client` from 0.77.1 to 0.79.0
  > Updates `org.projectnessie.nessie:nessie-jaxrs-testextension` from 0.77.1 to 0.79.0
  > Updates `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests` from 0.77.1 to 0.79.0
  > Updates `org.projectnessie.nessie:nessie-versioned-storage-testextension` from 0.77.1 to 0.79.0
  > update-type: version-update:semver-minor

- **PR/Issue**：#9976（Dependabot 自动 PR）

## 总体目的

本提交由 Dependabot 自动发起，将 Iceberg 项目所依赖的 Nessie 系列组件从 `0.77.1` 升级到 `0.79.0`（跨过 0.78.x，直接跳到当前最新），属于常规的次要版本（semver-minor）依赖跟进。

Nessie 在 Iceberg 项目中扮演"事务型目录服务（Transactional Catalog）"的角色，是 Iceberg 支持的多种 Catalog 实现之一（`NessieCatalog`）。Iceberg 通过 `iceberg-nessie` 模块与 Nessie 服务对接，允许用户把 Iceberg 表的元数据指针（pointer）托管在 Nessie 服务端，从而获得 Git 风格的分支/标签与事务隔离能力。Nessie 客户端 SDK 的升级能跟随上游 API 演进、修复缺陷、并获得新的服务端协议特性，但本身不改变 Iceberg 与 Nessie 的集成契约。

按 Dependabot 的分类，这次属于 `version-update:semver-minor`（次要版本升级），是 0601 提交配置生效后 Dependabot 仍会正常发起的 PR 类型（0601 只屏蔽 `semver-major`），符合依赖治理策略。

## 如何达成设计目的

Iceberg 使用 Gradle 的 version catalog 机制集中管理依赖版本，所有依赖版本声明统一放在 `gradle/libs.versions.toml` 中。Nessie 系列四个 artifact 共享同一个版本引用 `version.ref = "nessie"`，因此只需在 catalog 中修改一行 `nessie = "..."` 的值，就能同时把 `nessie-client`（生产代码）以及 `nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`（测试代码）四个 artifact 同步升到新版本。这种集中化版本管理正是 Dependabot 能用最小 diff（1 行修改）完成多 artifact 升级的原因。

升级的具体执行路径：

1. Dependabot 周期性扫描 Maven Central 仓库，发现 `org.projectnessie.nessie:nessie-client` 的最新版本为 `0.79.0`，高于当前 `0.77.1`。
2. Dependabot 识别出 catalog 中以 `nessie` 为 ref 的所有 artifact，生成单行版本号修改的 PR。
3. PR 触发 CI（包括 `iceberg-nessie` 模块的测试），若测试通过则可合并。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Nessie 系列四个 artifact 的版本号从 `0.77.1` 升级到 `0.79.0`。

**工作逻辑**：

文件第 68 行（升级前为第 65 行附近）的版本声明从：
```toml
nessie = "0.77.1"
```
改为：
```toml
nessie = "0.79.0"
```

该 `nessie` 版本变量被以下四个 catalog 别名通过 `version.ref = "nessie"` 引用（位于同一文件的 `[libraries]` 段）：

| Catalog 别名 | Maven 坐标 | 用途 | 消费位置 |
|---|---|---|---|
| `nessie-client` | `org.projectnessie.nessie:nessie-client` | 生产依赖，Nessie 客户端 SDK | `iceberg-nessie/build.gradle` 的 `implementation(libs.nessie.client)` |
| `nessie-jaxrs-testextension` | `org.projectnessie.nessie:nessie-jaxrs-testextension` | 测试依赖，JAX-RS 测试扩展，提供内嵌 Nessie REST 服务 | `iceberg-nessie/build.gradle` 的 `testImplementation libs.nessie.jaxrs.testextension` |
| `nessie-versioned-storage-inmemory-tests` | `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests` | 测试依赖，内存版版本化存储测试支持 | `iceberg-nessie/build.gradle` 的 `testImplementation libs.nessie.versioned.storage.inmemory.tests` |
| `nessie-versioned-storage-testextension` | `org.projectnessie.nessie:nessie-versioned-storage-testextension` | 测试依赖，版本化存储测试扩展 | `iceberg-nessie/build.gradle` 的 `testImplementation libs.nessie.versioned.storage.testextension` |

**消费链路**：

`iceberg-nessie` 模块（项目内目录名 `nessie/`）是 Nessie 集成的核心模块，其源码包为 `org.apache.iceberg.nessie`。生产代码主要包含四个类：
- `NessieCatalog`：实现 `Catalog` 与 `SupportsNamespaces` 接口，是与 Nessie 服务对接的入口，使用 `NessieClientBuilder` 构造客户端。
- `NessieTableOperations`：实现 `TableOperations`，负责通过 Nessie 客户端读写表元数据指针。
- `NessieIcebergClient`：封装与 Nessie API 的交互逻辑（branch/tag 操作、commit、获取 entry 等）。
- `UpdateableReference` / `NessieUtil`：辅助类，处理引用管理与工具方法。

测试代码通过 `nessie-jaxrs-testextension` 在测试 JVM 内拉起一个完整的 Nessie REST 服务，再用 `nessie-versioned-storage-inmemory-tests` + `nessie-versioned-storage-testextension` 提供内存版的版本化存储后端，从而无需外部 Nessie 服务即可端到端测试 `NessieCatalog` 的行为。这也是为什么 `iceberg-nessie` 模块对 Java 版本有要求——`build.gradle` 中显式判断 `JavaVersion.current().isJava11Compatible()`，只有 Java 11+ 才会启用测试，因为 Nessie 服务端组件要求 Java 11+。

**版本跨度说明**：

从 `0.77.1` 升级到 `0.79.0` 实际跨越了 0.78.x 整个版本线。这是因为 Dependabot 在生成 PR 时会取当前最新版本，而 Nessie 在 0.77.1 之后依次发布了 0.78.0、0.79.0，Dependabot 直接选取了 0.79.0。这属于正常的次要版本升级（0.77 → 0.79，主版本号仍为 0），未触发 0601 配置的 `semver-major` 屏蔽规则，因此 PR 被正常发起。Nessie 在 0.x 阶段虽然语义化版本约定比较宽松，但客户端 SDK 的 API 在 0.77 → 0.79 之间保持了向后兼容（CI 通过即可佐证）。

## 小结

本提交是一个由 Dependabot 自动生成的单行依赖升级，把 Nessie 系列四个 artifact 从 `0.77.1` 同步升到 `0.79.0`。改动极小（1 行），但影响 `iceberg-nessie` 模块的全部 4 个依赖（1 个生产 + 3 个测试）。

- **影响范围**：仅 `iceberg-nessie` 模块的构建与测试依赖。生产代码无任何源码改动，但因 Nessie 客户端 SDK 行为可能微调，理论上 `NessieCatalog` / `NessieIcebergClient` 等类的运行时行为会跟随 SDK 变化。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支当前的 Nessie 版本远低于 0.77.1（仓库工作树显示 1.4.x 上为 `0.71.0`），跨越多个次要版本直接升到 0.79.0 风险较大，需要确认 1.4.x 的 `iceberg-nessie` 模块代码与 0.79.0 SDK API 兼容。
  - 建议优先回迁 1.4.x 与 main 之间的所有 Nessie 升级 PR（0.71.0 → 0.71.1 → 0.72.0 → ... → 0.79.0），逐版本验证 CI；若想一步到位，至少要单独跑一次 `iceberg-nessie` 模块的全部测试。
  - 回迁时只需修改 `gradle/libs.versions.toml` 中的 `nessie = "..."` 一行，无需其他改动。
  - 注意 Java 版本约束：1.4.x 的 CI 矩阵中若仍有 Java 8，则 `iceberg-nessie` 测试会自动跳过，无法在 CI 中发现兼容问题，需手动用 Java 11+ 跑一次测试。
