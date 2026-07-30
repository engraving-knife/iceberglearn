# 提交 1965：Build: Bump io.delta:delta-standalone_2.12 from 3.3.0 to 3.3.1 (#12731)

## 提交信息

- **序号**：1965 / 4088
- **哈希**：6cfc21b744b4187d4aea33b01158acf036fc219e
- **短哈希**：6cfc21b74
- **日期**：2025-04-07 07:46:24 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.delta:delta-standalone_2.12 from 3.3.0 to 3.3.1 (#12731)
- **PR/Issue**：#12731

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，将 Delta Lake Standalone 库（`io.delta:delta-standalone_2.12`）从 3.3.0 升级到 3.3.1。Delta Standalone 用于 Iceberg 的 Delta 兼容/迁移相关功能（如将 Delta 表迁移到 Iceberg），本次为 patch 版本升级，包含 bug 修复。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 delta-standalone 的版本声明来完成升级。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 delta-standalone 版本声明。

**工作逻辑**：将 `io.delta:delta-standalone_2.12` 的版本从 `3.3.0` 改为 `3.3.1`。

## 总结

本提交是 Dependabot 发起的依赖升级，将 `io.delta:delta-standalone_2.12` 从 3.3.0 升级到 3.3.1，仅修改 `gradle/libs.versions.toml` 一行版本声明。
