# 提交 1966：Build: Bump net.snowflake:snowflake-jdbc from 3.23.0 to 3.23.2 (#12732)

## 提交信息

- **序号**：1966 / 4088
- **哈希**：88b01239997bbee74b4a575c7bb28615365063df
- **短哈希**：88b012399
- **日期**：2025-04-07 07:47:19 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.23.0 to 3.23.2 (#12732)
- **PR/Issue**：#12732

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，将 Snowflake JDBC 驱动（`net.snowflake:snowflake-jdbc`）从 3.23.0 升级到 3.23.2。Snowflake JDBC 用于 Iceberg 与 Snowflake 相关的集成/测试场景，本次为 patch 版本升级，通常包含 bug 修复与小的改进。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 snowflake-jdbc 的版本声明来完成升级。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 snowflake-jdbc 版本声明。

**工作逻辑**：将 `net.snowflake:snowflake-jdbc` 的版本从 `3.23.0` 改为 `3.23.2`。

## 总结

本提交是 Dependabot 发起的依赖升级，将 `net.snowflake:snowflake-jdbc` 从 3.23.0 升级到 3.23.2，仅修改 `gradle/libs.versions.toml` 一行版本声明。
