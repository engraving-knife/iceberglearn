# 提交 1175：Build: Bump nessie from 0.95.0 to 0.97.1 (#11184)

## 提交信息

- **序号**：1175 / 4088
- **哈希**：fc6271ca245f3998d22a2b90d19103cb6bc737b3
- **短哈希**：fc6271ca2
- **日期**：2024-09-23（Mon Sep 23 14:43:30 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.95.0 to 0.97.1 (#11184)
- **PR/Issue**：#11184

## 总体目的

本提交由 Dependabot 自动生成，将 Projectnessie（`nessie`）从 0.95.0 升级到 0.97.1，属于 `version-update:semver-minor` 次版本升级，跨两个次版本（0.95 → 0.96 → 0.97）。一次性升级四个模块：
- `org.projectnessie.nessie:nessie-client`（生产依赖）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（测试）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`（测试）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（测试）

Nessie 是一个提供 Git-like 数据版本控制的 catalog，Iceberg 通过 `iceberg-nessie` 模块支持以 Nessie 作为 Catalog。`nessie-client` 是运行时与 Nessie 服务通信的客户端，其余三个 testextension 是用于本地启动内存版 Nessie 服务做集成测试。升级保证 Iceberg 与最新 Nessie 版本兼容，并获取 bug 修复与新特性。

## 如何达成设计目的

仅修改 `gradle/libs.versions.toml`，把 `nessie` 版本号从 `0.95.0` 改为 `0.97.1`。所有通过 `version.ref = "nessie"` 引用的库坐标会自动同步。无代码改动，说明 Nessie 0.95 → 0.97 的 API 变化未影响 Iceberg 已有调用。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 nessie 版本号。

**工作逻辑**：在 `[versions]` 段把：

```toml
nessie = "0.95.0"
```

改为：

```toml
nessie = "0.97.1"
```

该变量被 `[libraries]` 段多个条目通过 `version.ref = "nessie"` 引用，包括 `nessie-client`（运行时依赖，`iceberg-nessie` 模块）以及三个测试扩展（用于在测试中启动内存 Nessie 实例）。本提交只改版本变量，未改 `[libraries]` 条目。

**升级背景**：Nessie 0.96 与 0.97 各自引入新特性与 bug 修复。因为是次版本升级，理论上 API 向后兼容；Iceberg 现有 Nessie 集成代码无需调整即可在新版本上工作，这点已被 CI 通过验证。

## 小结

- **成效**：Nessie 升级到 0.97.1，`iceberg-nessie` 模块及其集成测试使用最新版本，保证兼容性与获取最新修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，无代码改动。运行时影响限于 `iceberg-nessie` 模块——用户使用 Nessie catalog 时会加载新版本的 `nessie-client`。
- **回迁到 1.4.x 的注意事项**：Nessie 0.95 → 0.97.1 跨两个次版本，跨度较大。1.4.x 回迁前需重点验证：(1) 1.4.x 的 `iceberg-nessie` 代码是否与 Nessie 0.97.1 的 API 兼容（main 可能已合入适配 0.96/0.97 API 变化的其他提交，1.4.x 未必有）；(2) 1.4.x 用户若依赖特定 Nessie 服务端版本，需确认兼容性。**建议谨慎回迁**，若 1.4.x 与 Nessie 0.95.0 工作正常且无安全/bug 修复需求，可不回迁；如确需回迁，必须单独跑 `iceberg-nessie` 全套集成测试。
