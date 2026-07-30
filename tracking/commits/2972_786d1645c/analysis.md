# 提交 2972：Build: Bump org.xerial:sqlite-jdbc from 3.51.0.0 to 3.51.1.0 (#14786)

## 提交信息

- **序号**：2972 / 4088
- **哈希**：786d1645c78f13fbd627e1d8a9785c6960d44798
- **短哈希**：786d1645c
- **日期**：2025-12-06
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.51.0.0 to 3.51.1.0 (#14786)
- **PR/Issue**：#14786

## 总体目的

这是 Dependabot 自动生成的依赖升级。`org.xerial:sqlite-jdbc` 是 SQLite 的纯 Java JDBC 驱动，Iceberg 仓库将其作为测试依赖使用（在根 `build.gradle` 与多个 Spark 版本的 `build.gradle` 中以 `testImplementation libs.sqlite.jdbc` 引入），主要用于以嵌入式 SQLite 数据库作为 JDBC catalog 的测试后端，验证 Iceberg 的 JDBC catalog 集成。Dependabot 将该依赖从 `3.51.0.0` 升级到 `3.51.1.0`。本次升级属于 `semver-patch`（补丁版本）升级，预期只包含 SQLite 驱动的缺陷修复、原生库版本小幅更新或小改进，不引入破坏性 API 变更，对测试行为无影响。

## 如何达成设计目的

Dependabot 仅修改 `gradle/libs.versions.toml` 中 `sqlite-jdbc` 版本变量的值，所有引用该变量的位置自动获得新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 `sqlite-jdbc` 版本变量从 `3.51.0.0` 升级到 `3.51.1.0`。

**工作逻辑**：
在 `[versions]` 区段将 `sqlite-jdbc = "3.51.0.0"` 改为 `sqlite-jdbc = "3.51.1.0"`。该变量被根 `build.gradle`（第 361、493 行附近）以及 `spark/v3.2`~`spark/v3.5`、`spark/v4.0` 等 build 脚本中的 `libs.sqlite.jdbc` 引用，统一作为 `testImplementation` 使用。升级后，所有以 SQLite 为后端的 JDBC catalog 测试将自动使用 3.51.1.0 版本的驱动。

## 总结

本提交是 Dependabot 对 `org.xerial:sqlite-jdbc` 的补丁版本升级（3.51.0.0 → 3.51.1.0），该依赖在 Iceberg 中作为测试依赖用于 JDBC catalog 的 SQLite 测试后端。属于低风险补丁升级，预期仅带来驱动修复与小改进，不影响测试逻辑。
