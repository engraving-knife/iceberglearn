# 提交 2584：Build: Bump net.snowflake:snowflake-jdbc from 3.26.0 to 3.26.1 (#13950)

## 提交信息

- **序号**：2584 / 4088
- **哈希**：01774476510d5317a82079978346fdd2530635ae
- **短哈希**：017744765
- **日期**：2025-09-01 05:12:07 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.26.0 to 3.26.1 (#13950)
- **PR/Issue**：#13950

## 总体目的

此次提交由 Dependabot 自动生成，将 Snowflake JDBC 驱动版本从 3.26.0 升级到 3.26.1。Snowflake JDBC 驱动用于 Iceberg 的 Snowflake Catalog 集成（如 `SnowflakeCatalog`），通过该驱动连接 Snowflake 服务获取表元数据和目录信息。

此次升级为 semver-patch 版本更新（3.26.0 -> 3.26.1），属于常规依赖维护，用于获取 Snowflake JDBC 驱动的最新 bug 修复和改进。Dependabot 自动检测到新版本发布并提交 PR，CI 通过后合并。

## 如何达成设计目的

- 在 `gradle/libs.versions.toml` 版本目录中将 `snowflake-jdbc` 的版本值从 `3.26.0` 改为 `3.26.1`。
- 所有引用该版本变量的模块依赖会自动跟随升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1)

**修改目的**：升级 Snowflake JDBC 驱动版本。

**工作逻辑**：将 `snowflake-jdbc = "3.26.0"` 改为 `snowflake-jdbc = "3.26.1"`，引用该变量的 Snowflake Catalog 相关模块依赖随之升级。

## 总结

一次 Dependabot 自动依赖升级提交，将 Snowflake JDBC 驱动从 3.26.0 升级到 3.26.1（semver-patch 更新），通过修改版本目录 `libs.versions.toml` 中的一行完成。属于常规依赖维护，获取 Snowflake JDBC 驱动的最新修复。
