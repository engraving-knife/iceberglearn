# 提交 2084：Build: Bump net.snowflake:snowflake-jdbc from 3.23.2 to 3.24.0

## 提交信息

- **序号**：2084 / 4088
- **哈希**：47f8ae8d724955699c613fbffaf51857f0b08a7a
- **短哈希**：47f8ae8d7
- **日期**：2025-05-06 10:51:15 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.23.2 to 3.24.0 (#12965)
- **PR/Issue**：#12965

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Snowflake JDBC 驱动从 3.23.2 升级到 3.24.0。这是一个 minor 版本升级，可能包含新功能和改进。Snowflake JDBC 驱动用于 Iceberg 与 Snowflake 数据库的集成（如 SnowflakeCatalog），保持驱动为最新版本有助于获得新功能和 Bug 修复。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `snowflake-jdbc` 版本号定义，完成升级。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：将 Snowflake JDBC 版本号从 3.23.2 升级到 3.24.0。

**工作逻辑**：
将 `snowflake-jdbc = "3.23.2"` 修改为 `snowflake-jdbc = "3.24.0"`。

## 总结

本提交由 Dependabot 自动生成，将 Snowflake JDBC 驱动从 3.23.2 升级到 3.24.0（minor 版本升级），仅修改版本目录文件中一行版本号定义。
