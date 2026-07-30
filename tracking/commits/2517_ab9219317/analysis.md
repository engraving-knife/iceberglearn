# 提交 2517：Build: Bump net.snowflake:snowflake-jdbc from 3.25.1 to 3.26.0 (#13842)

## 提交信息

- **序号**：2517 / 4088
- **哈希**：ab9219317074b22634d3b16218950ecd48e5c924
- **短哈希**：ab9219317
- **日期**：2025-08-18 09:35:38 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.25.1 to 3.26.0 (#13842)
- **PR/Issue**：#13842

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 Snowflake JDBC 驱动从 3.25.1 升级到 3.26.0。

`net.snowflake:snowflake-jdbc` 是 Snowflake 数据云平台的官方 JDBC 驱动。Iceberg 项目支持 Snowflake 作为目录后端（SnowflakeCatalog），使用此 JDBC 驱动连接 Snowflake 数据库进行表元数据管理和数据操作。

此次升级属于次版本更新（semver-minor），可能包含新功能特性、性能改进和 bug 修复。

## 如何达成设计目的

Dependabot 自动检测到 Snowflake JDBC 驱动有新版本发布，在 `gradle/libs.versions.toml` 中将版本号从 3.25.1 更新为 3.26.0。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Snowflake JDBC 驱动版本号。

**工作逻辑**：将 Gradle 版本目录文件中 `snowflake-jdbc` 的版本号从 `3.25.1` 改为 `3.26.0`，使所有使用 Snowflake JDBC 的项目模块自动使用新版本。

## 总结

这是一个常规的依赖维护提交，将 Snowflake JDBC 驱动升级到新的次版本。对于使用 Snowflake 作为 Iceberg 目录后端的用户，保持 JDBC 驱动最新有助于确保与 Snowflake 平台的兼容性和获取最新功能。
