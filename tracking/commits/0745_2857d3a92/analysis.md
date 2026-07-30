# 提交 0745：Build: Bump nessie from 0.80.0 to 0.81.1 (#10267)

## 提交信息

- **序号**：0745 / 4088
- **哈希**：2857d3a927527e8116871ef4abe6cfed15340188
- **短哈希**：2857d3a92
- **日期**：2024-05-06 10:42:01 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.80.0 to 0.81.1 (#10267)
- **PR/Issue**：#10267

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 依赖的 Nessie（Projectnessie）从 `0.80.0` 升级到 `0.81.1`，属于 semver-minor（次版本号）级别的依赖更新。

Nessie 是一个提供 Git 风格版本化数据目录（versioned catalog）的服务，Iceberg 通过 Nessie catalog 支持对数据表的分支、标签、事务隔离等高级版本管理能力。本次升级涉及的 Nessie artifact 共有 4 个，它们共享同一版本号 `nessie`，因此只需在版本目录中改一处即可同时升级全部 4 个 artifact：

- `org.projectnessie.nessie:nessie-client`：Nessie 客户端，Iceberg 用于连接 Nessie 服务进行 catalog 操作（生产依赖）。
- `org.projectnessie.nessie:nessie-jaxrs-testextension`：JAX-RS 测试扩展，用于在测试中启动内嵌 Nessie 服务。
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`：内存存储测试支持。
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`：版本化存储测试扩展。

次版本号（minor）升级按 Dependabot 分类标准意味着包含兼容性新增特性，不含破坏性变更。升级通常带来新功能、bug 修复与 API 兼容性改进。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `nessie = "0.80.0"` 改为 `nessie = "0.81.1"`。由于 4 个 Nessie artifact 在版本目录中统一引用 `nessie` 版本常量，改这一处即可同时升级全部 4 个 artifact，无需逐个修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 相关 4 个 artifact 的版本从 0.80.0 升级到 0.81.1。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区，原行 `nessie = "0.80.0"` 被改为 `nessie = "0.81.1"`。该版本常量被以下 4 个 Nessie artifact 引用：`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`。其中 `nessie-client` 是生产依赖（用于 Nessie catalog 连接），其余 3 个为测试依赖。升级后，所有引用这些 artifact 的模块会自动解析到 0.81.1 版本。

## 小结

- **成效**：将 Nessie 从 0.80.0 升级到 0.81.1，同时升级 4 个 Nessie artifact（1 个生产依赖 + 3 个测试依赖），获取次版本级别的功能新增、bug 修复与兼容性改进。
- **影响范围**：构建依赖版本变更，不涉及任何源代码变更。生产影响限于 Nessie catalog 功能（`nessie-client`），测试影响限于 Nessie 相关测试（3 个测试扩展 artifact）。
- **回迁注意事项**：次版本（minor）升级通常向后兼容，但相比补丁版本风险略高。回迁到 1.4.x 时需确认 1.4.x 的 Nessie catalog 代码与 Nessie 0.81.1 客户端 API 兼容（Nessie 客户端 API 在 minor 版本间一般保持兼容，但建议回迁后跑一遍 Nessie catalog 相关测试套件验证）。只需同步 `gradle/libs.versions.toml` 中的 `nessie` 版本行即可。
