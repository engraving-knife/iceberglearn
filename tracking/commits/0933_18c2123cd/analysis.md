# 提交 0933：Build: Bump nessie from 0.92.0 to 0.92.1 (#10697)

## 提交信息

- **序号**：0933 / 4088
- **哈希**：18c2123cdeaebe60cb671dba8a976bbb1146faa6
- **短哈希**：18c2123cd
- **日期**：2024-07-15（Mon Jul 15 08:42:45 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.92.0 to 0.92.1 (#10697)
- **PR/Issue**：#10697

## 总体目的

这是由 Dependabot 自动生成的依赖升级 PR，将 Iceberg 项目依赖的 Nessie 版本从 `0.92.0` 升级到 `0.92.1`。

Nessie（Project Nessie）是一个提供 Git 风格版本化数据目录的服务，Iceberg 通过 `nessie` 模块支持将 Nessie 作为 catalog 实现。本次升级涉及的 Nessie 组件包括：

- `org.projectnessie.nessie:nessie-client`（生产用客户端）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（测试用 JAX-RS 扩展）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`（内存存储测试扩展）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（版本化存储测试扩展）

这些组件在 Iceberg 的 `nessie` 模块及对应测试中被引用。由于是 patch 版本升级（0.92.0 → 0.92.1），属于 bug 修复性质，API 兼容，风险较低。升级可获取 Nessie 0.92.1 的 bug 修复与稳定性改进。

## 如何达成设计目的

Nessie 的多个组件版本在版本目录中通过同一个版本别名 `nessie` 统一管理，因此只需修改 `gradle/libs.versions.toml` 中 `nessie` 这一项的版本字符串，所有四个 Nessie 组件（client、jaxrs-testextension、inmemory-tests、testextension）会通过 `version.ref = "nessie"` 自动统一升级到 0.92.1。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 组件版本别名从 0.92.0 升级到 0.92.1，统一升级所有 Nessie 相关依赖。

**工作逻辑**：

```diff
-nessie = "0.92.0"
+nessie = "0.92.1"
```

`nessie` 是版本目录中的版本别名，被 `[libraries]` 段中多个 Nessie 组件条目引用（通过 `version.ref = "nessie"`）。改这一行即可让 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 四个组件同时升级到 0.92.1，保持它们之间的版本一致性（Nessie 各组件需同版本以避免兼容性问题）。

## 小结

- **成效**：将 Nessie 4 个组件从 0.92.0 升级至 0.92.1，获取 patch 版本的 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动；影响 `nessie` 模块及其测试的 Nessie 依赖版本。
- **回迁到 1.4.x 的注意事项**：patch 版本升级、API 兼容，技术上适合回迁。需确认 1.4.x 分支当前 Nessie 版本（若仍为 0.92.0 或更早，可直接升级），并运行 `nessie` 模块测试验证。若 1.4.x 锁定更低版本（如 0.91.x），则需评估跨版本差异，不宜直接 cherry-pick 此单点升级。
