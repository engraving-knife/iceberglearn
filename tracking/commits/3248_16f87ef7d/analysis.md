# 提交 3248：Build: Bump org.xerial:sqlite-jdbc from 3.51.1.0 to 3.51.2.0 (#15320)

## 提交信息

- **序号**：3248 / 4088
- **哈希**：16f87ef7dd2c80fe55765e937c990fd227a69b4c
- **短哈希**：16f87ef7d
- **日期**：2026-02-14
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.51.1.0 to 3.51.2.0 (#15320)
- **PR/Issue**：#15320

## 总体目的

`org.xerial:sqlite-jdbc` 是 Xerial 维护的 SQLite JDBC 驱动，在 Iceberg 项目中用于测试场景——典型用途是在单元测试和集成测试中提供基于 SQLite 的内存或文件型数据库，例如模拟 Hive Metastore 的关系型存储后端或测试 SQL catalog 功能。该依赖属于 `direct:production` 类型（直接生产依赖）。

本次提交由 Dependabot 自动生成，将 `sqlite-jdbc` 从 `3.51.1.0` 升级到 `3.51.2.0`。根据语义版本规范，这是一个 patch 级别升级（`version-update:semver-patch`），即第三个版本号段从 1 变为 2。patch 升级通常只包含 bug 修复和小的改进，不引入破坏性 API 变更，因此预期对 Iceberg 的测试套件不产生行为影响。

## 如何达成设计目的

仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `sqlite-jdbc` 的版本声明，从 `3.51.1.0` 改为 `3.51.2.0`。所有引用该版本坐标的模块会自动获取新版本，无需修改其他构建文件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 sqlite-jdbc 依赖版本从 3.51.1.0 升级至 3.51.2.0。

**工作逻辑**：
在版本目录的第 91 行，将 `sqlite-jdbc = "3.51.1.0"` 修改为 `sqlite-jdbc = "3.51.2.0"`。该版本变量被项目中所有需要 SQLite JDBC 驱动的模块（主要在测试 scope）引用，修改后这些模块将自动拉取新版本。作为 patch 级别升级，预期包含 SQLite 驱动的 bug 修复和原生库更新，不涉及 API 兼容性变更。

## 总结

本次提交是 Dependabot 自动执行的 sqlite-jdbc 依赖 patch 升级（3.51.1.0 → 3.51.2.0），保持 Iceberg 测试所用的 SQLite JDBC 驱动处于最新补丁版本，获取 bug 修复和稳定性改进，对项目功能无影响。
