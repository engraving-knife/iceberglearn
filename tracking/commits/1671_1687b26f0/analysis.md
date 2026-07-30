# 提交 1671：Build: Bump nessie from 0.102.2 to 0.102.4 (#12153)

## 提交信息

- **序号**：1671 / 4088
- **哈希**：1687b26f0f6fce647263e1e714895293ef3c2a27
- **短哈希**：1687b26f0
- **日期**：2025-02-02（Sun Feb 2 07:47:23 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.102.2 to 0.102.4 (#12153)
- **PR/Issue**：#12153

## 总体目的

Dependabot 自动升级提交。Nessie 是 Iceberg 支持的目录（catalog）实现之一（用于版本化表目录服务）。本提交把 Gradle 版本目录中 Nessie 的版本从 `0.102.2` 升级到 `0.102.4`（patch 级升级），涉及以下四个 artifact：

- `org.projectnessie.nessie:nessie-client`（生产依赖）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（测试依赖）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`（测试依赖）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（测试依赖）

patch 版本升级通常包含 bug 修复与小改进，不引入破坏性变更。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中把 `nessie = "0.102.2"` 改为 `nessie = "0.102.4"`。所有引用 `libs.versions.nessie` 的 artifact 会自动使用新版本。

## 修改详情

### `gradle/libs.versions.toml`（修改，+1/-1 行）

**修改目的**：把 Nessie 版本号从 `0.102.2` 升级到 `0.102.4`。

**工作逻辑**：仅修改 `nessie` 版本变量定义，所有通过 `libs.nessie.*` 引用的依赖自动跟随升级。

## 小结

- **成效**：把 Nessie 相关依赖升级到 0.102.4，获取最新的 bug 修复与改进。
- **影响范围**：仅构建配置，无源代码变更。影响 Nessie catalog 的生产客户端与测试扩展。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯版本号升级。需确认 1.4.x 分支的 Nessie 版本（若仍为 0.102.2 或更早则可直接升级），并验证 Nessie 0.102.4 与 1.4.x 的 Iceberg Nessie catalog 代码兼容。
