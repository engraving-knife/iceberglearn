# 提交 0986：Build: Bump nessie from 0.93.1 to 0.94.2 (#10798)

## 提交信息

- **序号**：0986 / 4088
- **哈希**：31d52c0edb9010b051e6c1ee75cb84e6b94c8929
- **短哈希**：31d52c0ed
- **日期**：2024-07-29 09:11:02 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.93.1 to 0.94.2 (#10798)
- **PR/Issue**：#10798

## 总体目的

Iceberg 提供 Nessie catalog 集成（`iceberg-nessie` 模块），用于把 Iceberg 表元数据存放在 Nessie 版本化存储中。该集成同时使用四个 Nessie 制品：`nessie-client`（生产客户端）、`nessie-jaxrs-testextension`（测试用 JAX-RS 服务扩展）、`nessie-versioned-storage-inmemory-tests` 与 `nessie-versioned-storage-testextension`（基于内存的版本化存储测试扩展）。

Dependabot 自动检测到 Nessie 从 0.93.1 升到 0.94.2，这是一次 minor 级别升级（minor 0.93→0.94，再加两个 patch），通常包含新特性、bugfix 与可能的 API 调整。本提交统一升级这四个制品，确保 Iceberg 与最新 Nessie 兼容。

## 如何达成设计目的

与其它依赖升级一致，Iceberg 用 `gradle/libs.versions.toml` 把 Nessie 的版本号统一为 `nessie` 这个别名。`iceberg-nessie` 模块的 `build.gradle` 用 `version.ref = "nessie"` 把四个制品都绑定到该别名。因此升级只需在 catalog 中改一行 `nessie` 的值，四个制品会同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `nessie` 版本别名从 0.93.1 升级到 0.94.2。

**工作逻辑**：仅修改一行：

```diff
-nessie = "0.93.1"
+nessie = "0.94.2"
```

四个制品 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 通过 `version.ref = "nessie"` 全部跟随升级。

## 小结

- **成效**：把 Iceberg 的 Nessie 集成从 0.93.1 升到 0.94.2，跨过一个 minor 版本与两个 patch，确保与最新 Nessie 兼容并跟随上游修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行；间接影响 `iceberg-nessie` 模块的生产代码与测试代码。
- **回迁到 1.4.x 的注意事项**：minor 升级可能存在 API 兼容性变化（Nessie 0.x 系列尚未承诺稳定 API），回迁前需先在 1.4.x 上跑 `iceberg-nessie` 模块的完整测试（包括与 Nessie 集成的集成测试）。若 1.4.x 当前 Nessie 版本与 0.93.1 差距较大，建议直接升级到与 main 一致的 0.94.2 或更新版本，避免维护过老的兼容代码。回迁时确认 catalog key 与依赖引用方式与 main 一致。
