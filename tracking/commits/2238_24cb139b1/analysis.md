# 提交 2238：Build: Bump org.xerial:sqlite-jdbc from 3.49.1.0 to 3.50.1.0

## 提交信息

- **序号**：2238 / 4088
- **哈希**：24cb139b15e509dbd0864c8c5f90a77b1cea8503
- **短哈希**：24cb139b1
- **日期**：2025-06-15 09:12:13 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.49.1.0 to 3.50.1.0
- **PR/Issue**：#13318

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 SQLite JDBC 驱动从 3.49.1.0 升级到 3.50.1.0。SQLite JDBC 驱动用于 Iceberg 的测试场景中（如使用 SQLite 作为 JDBC catalog 的后端数据库）。此次升级为 minor 级别更新，包含 SQLite 引擎升级和 bug 修复。

## 如何达成设计目的

- 在 Gradle 版本目录文件中修改 SQLite JDBC 的版本号。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 SQLite JDBC 驱动版本。

**工作逻辑**：将 `sqlite-jdbc = "3.49.1.0"` 修改为 `sqlite-jdbc = "3.50.1.0"`。

## 总结

常规依赖升级提交，将 SQLite JDBC 驱动从 3.49.1.0 升级到 3.50.1.0，获取最新的 SQLite 引擎和 bug 修复。
