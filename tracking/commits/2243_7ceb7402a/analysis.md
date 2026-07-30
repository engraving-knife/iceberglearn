# 提交 2243：Build: Bump net.snowflake:snowflake-jdbc from 3.24.0 to 3.24.2

## 提交信息

- **序号**：2243 / 4088
- **哈希**：7ceb7402ad076b671131ad0e757d7c9a5420ea84
- **短哈希**：7ceb7402a
- **日期**：2025-06-16 11:18:30 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.24.0 to 3.24.2
- **PR/Issue**：#13200

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Snowflake JDBC 驱动从 3.24.0 升级到 3.24.2。Snowflake JDBC 驱动用于 Iceberg 与 Snowflake 数据仓库的集成，支持 Snowflake catalog 和 Snowflake 表操作。此次升级为 patch 级别更新，包含 bug 修复和稳定性改进。

## 如何达成设计目的

- 在 Gradle 版本目录文件中修改 Snowflake JDBC 的版本号。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Snowflake JDBC 驱动版本。

**工作逻辑**：将 `snowflake-jdbc = "3.24.0"` 修改为 `snowflake-jdbc = "3.24.2"`。

## 总结

常规依赖升级提交，将 Snowflake JDBC 驱动从 3.24.0 升级到 3.24.2，获取最新的 bug 修复和稳定性改进。
