# 提交 0987：Build: Bump net.snowflake:snowflake-jdbc from 3.17.0 to 3.18.0 (#10801)

## 提交信息

- **序号**：0987 / 4088
- **哈希**：349046889a1f18431132b00a8e94f064401b3e75
- **短哈希**：349046889
- **日期**：2024-07-29 09:30:36 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.17.0 to 3.18.0 (#10801)
- **PR/Issue**：#10801

## 总体目的

Iceberg 提供 `iceberg-snowflake` 模块（SnowflakeCatalog）用于与 Snowflake 数据库集成，该模块依赖官方 `net.snowflake:snowflake-jdbc` 驱动来连接 Snowflake。Dependabot 检测到 Snowflake JDBC 从 3.17.0 升到 3.18.0，这是一次 minor 级别升级。本提交把依赖升级到 3.18.0，以获取 Snowflake 官方在 3.18 系列中带来的新特性与 bugfix，保持与 Snowflake 服务端最新协议兼容。

## 如何达成设计目的

通过 Gradle version catalog 集中管理。`gradle/libs.versions.toml` 中 `snowflake-jdbc` 别名持有版本号，`iceberg-snowflake` 模块的 `build.gradle` 通过 `version.ref = "snowflake-jdbc"` 引用。升级只需改一行版本号。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `snowflake-jdbc` 版本别名从 3.17.0 升级到 3.18.0。

**工作逻辑**：仅修改一行：

```diff
-snowflake-jdbc = "3.17.0"
+snowflake-jdbc = "3.18.0"
```

`iceberg-snowflake` 模块自动应用新版本。

## 小结

- **成效**：把 Snowflake JDBC 驱动从 3.17.0 升到 3.18.0，获取 Snowflake 官方 3.18 系列的更新。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行；间接影响 `iceberg-snowflake` 模块的运行时与测试。
- **回迁到 1.4.x 的注意事项**：minor 升级可能引入驱动行为变化（如默认连接参数、SSL 配置、SQL 方言解析等），回迁前建议在 1.4.x 上跑 `iceberg-snowflake` 模块的集成测试，确认 catalog 操作（建表、提交、读取）正常。若 1.4.x 上没有对应集成测试环境，回迁需谨慎，可优先升级到当前 1.4.x 已验证的版本而非盲目跟随 main。
