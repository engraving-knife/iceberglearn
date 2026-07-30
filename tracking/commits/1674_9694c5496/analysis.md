# 提交 1674：Build: Bump net.snowflake:snowflake-jdbc from 3.21.0 to 3.22.0 (#12155)

## 提交信息

- **序号**：1674 / 4088
- **哈希**：9694c549622adaf3addc94133265580bd37fa839
- **短哈希**：9694c5496
- **日期**：2025-02-02（Sun Feb 2 09:39:58 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.21.0 to 3.22.0 (#12155)
- **PR/Issue**：#12155

## 总体目的

Dependabot 自动升级，把 Snowflake JDBC 驱动从 `3.21.0` 升到 `3.22.0`。`3.21.0 → 3.22.0` 是 minor 版本升级，可能包含新特性、SQL 行为调整、连接参数变更以及 bug 修复。

Snowflake JDBC 驱动是 `iceberg-snowflake` 模块的核心依赖——`SnowflakeCatalog` 通过该驱动连接 Snowflake，执行 `SHOW`/`SELECT` 命令发现 Iceberg 表、调用 `SYSTEM$GET_ICEBERG_TABLE_INFORMATION` 获取表元数据位置。驱动也用于 Snowflake Catalog 的集成测试（连接真实或模拟的 Snowflake 实例）。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `snowflake-jdbc` 的版本号。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

```diff
-snowflake-jdbc = "3.21.0"
+snowflake-jdbc = "3.22.0"
```

该条目被 `iceberg-snowflake` 模块通过 `libs.snowflake.jdbc` 引用，驱动版本随之更新。

## 小结

- **成效**：Snowflake JDBC 驱动从 3.21.0 升到 3.22.0，获取上游 minor 版本改进。
- **影响范围**：`iceberg-snowflake` 模块及其集成测试。由于是 minor 升级，可能存在少量行为变化（如 SQL 解析、错误码、连接参数等），但通常不破坏 JDBC 标准 API。
- **回迁到 1.4.x 的注意事项**：可 cherry-pick，但需谨慎。Snowflake JDBC minor 升级偶有错误码变更或 `SYSTEM$` 函数行为调整，可能影响 `JdbcSnowflakeClient` 中的错误码映射（`DATABASE_NOT_FOUND_ERROR_CODES`、`SCHEMA_NOT_FOUND_ERROR_CODES`、`TABLE_NOT_FOUND_ERROR_CODES`）。建议回迁后回归 `iceberg-snowflake` 的集成测试（若有 Snowflake 测试环境）。若 1.4.x 的 Snowflake Catalog 测试主要依赖 mock 而非真实 Snowflake 连接，则风险较低。确认 1.4.x 的 `gradle/libs.versions.toml` 中 `snowflake-jdbc` 命名一致。
