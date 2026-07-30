# 提交 2697：Build: Bump nessie from 0.104.5 to 0.105.3 (#14204)

## 提交信息

- **序号**：2697 / 4088
- **哈希**：1f72ec14da28a5ed59e4e805323558bfc923c281
- **短哈希**：1f72ec14d
- **日期**：2025-09-29 09:00:36 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.104.5 to 0.105.3 (#14204)
- **PR/Issue**：#14204

## 总体目的

Dependabot 自动将 Nessie 相关依赖从 `0.104.5` 升级到 `0.105.3`。Nessie 是 ProjectNessie 提供的"事务型数据目录"（类似 Git 的数据版本控制层），可作为 Iceberg 的 catalog 后端。本次升级涉及 4 个 Nessie 制品：

- `org.projectnessie.nessie:nessie-client`（客户端）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（JAX-RS 测试扩展）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`（内存存储测试）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（版本化存储测试扩展）

Dependabot 元数据显示为 `version-update:semver-minor`（次版本升级），从 0.104.x 跳到 0.105.x，可能引入新功能与潜在的 API 调整，需要测试覆盖以验证兼容性。Iceberg 的 `nessie` 模块在集成测试中依赖这些制品模拟 Nessie 服务，升级保证与最新 Nessie 服务端协议兼容。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `nessie` 别名的版本号，从 `0.104.5` 改为 `0.105.3`。该别名为所有 Nessie 相关制品的统一版本引用点，单一改动即可让上述 4 个制品同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 相关依赖版本。

**工作逻辑**：将 `nessie = "0.104.5"` 改为 `nessie = "0.105.3"`。所有引用该别名的 Nessie 制品（client、testextension、storage 测试扩展等）同步升级到 0.105.3，保持版本一致。

## 总结

Dependabot 自动将 Nessie 依赖从 0.104.5 升级到 0.105.3 的次版本更新，涉及 4 个制品。属常规依赖维护，但因为是 semver-minor 升级，相比 patch 升级需更多关注 API 兼容性。改动仅一行版本号，通过 Gradle 版本目录集中生效，旨在与最新 Nessie 保持协议与功能对齐。
