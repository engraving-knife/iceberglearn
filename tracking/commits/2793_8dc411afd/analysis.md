# 提交 2793：Build: Bump nessie from 0.105.5 to 0.105.6 (#14419)

## 提交信息

- **序号**：2793 / 4088
- **哈希**：8dc411afdd3f8d6bc526941c9984d0a5f636d21f
- **短哈希**：8dc411afd
- **日期**：2025-10-25 23:35:36 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.105.5 to 0.105.6 (#14419)
- **PR/Issue**：#14419

## 总体目的

本提交由 dependabot 自动生成，将 Nessie 相关依赖从 0.105.5 升级到 0.105.6。

Nessie 是一个提供 Git 式数据版本控制的工具，用于管理数据湖中的表/视图状态。Iceberg 项目与 Nessie 集成，支持将 Nessie 作为 catalog 后端。本次升级涉及以下 Nessie 组件：

- `org.projectnessie.nessie:nessie-client`
- `org.projectnessie.nessie:nessie-jaxrs-testextension`
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`

这是一个 patch 级别升级（0.105.5 → 0.105.6），通常包含 bug 修复和小改进。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `nessie` 的版本号从 `0.105.5` 更新为 `0.105.6`。由于所有 Nessie 组件共用同一版本变量，一次修改即可同步升级所有相关组件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 依赖版本。

**工作逻辑**：将版本目录中 `nessie = "0.105.5"` 修改为 `nessie = "0.105.6"`。所有引用 `nessie` 版本变量的 Nessie 组件（client、jaxrs-testextension、versioned-storage-inmemory-tests、versioned-storage-testextension）将同步升级到 0.105.6。

## 总结

本提交是依赖升级，将 Nessie 相关组件从 0.105.5 升级到 0.105.6。作为 patch 级别升级，预期包含 bug 修复和稳定性改进，保持 Iceberg 与 Nessie 集成的最新兼容性。
