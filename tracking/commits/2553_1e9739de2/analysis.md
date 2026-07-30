# 提交 2553：Build: Bump nessie from 0.104.3 to 0.104.5 (#13910)

## 提交信息

- **序号**：2553 / 4088
- **哈希**：1e9739de255dcfc3a3c7b3f45ce0e46b09ea8ab5
- **短哈希**：1e9739de2
- **日期**：2025-08-24 09:18:20 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.104.3 to 0.104.5 (#13910)
- **PR/Issue**：#13910

## 总体目的

该提交由 Dependabot 自动生成，将 Iceberg 项目依赖的 Nessie 版本从 0.104.3 升级到 0.104.5。Nessie 是一个提供 Git 式数据版本控制的目录服务，Iceberg 通过 `nessie-client` 与 Nessie 集成，支持将 Nessie 作为 catalog 的后端。

此次升级是补丁版本（patch）升级（0.104.3 -> 0.104.5），属于向后兼容的维护性更新，通常包含 bug 修复和小幅改进。升级涉及以下 4 个 Nessie 组件：
- `org.projectnessie.nessie:nessie-client`（生产依赖）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（测试依赖）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`（测试依赖）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（测试依赖）

保持依赖版本最新有助于获取最新的 bug 修复和安全补丁，避免使用过时版本中已知的问题。

## 如何达成设计目的

- 在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中将 `nessie` 版本变量从 `0.104.3` 修改为 `0.104.5`，所有引用该变量的 Nessie 组件将自动使用新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1)

**修改目的**：升级 Nessie 版本号。

**工作逻辑**：将 `nessie = "0.104.3"` 修改为 `nessie = "0.104.5"`。由于所有 Nessie 组件的版本都通过此变量统一管理，修改一处即可完成所有组件的升级。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 Nessie 从 0.104.3 升级到 0.104.5（补丁版本升级），涉及 4 个组件，修改仅一行版本号配置。
