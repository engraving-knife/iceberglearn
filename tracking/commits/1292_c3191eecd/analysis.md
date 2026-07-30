# 提交 1292：Build: Bump net.snowflake:snowflake-jdbc from 3.19.0 to 3.19.1 (#11406)

## 提交信息

- **序号**：1292 / 4088
- **哈希**：c3191eecd90cd0b33bb11b77ea2ebd4c35f5e751
- **短哈希**：c3191eecd
- **日期**：2024-10-28（Mon Oct 28 14:15:42 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.19.0 to 3.19.1 (#11406)
- **PR/Issue**：#11406

## 总体目的

Iceberg 仓库通过 Dependabot 自动管理依赖版本。本次提交将 Snowflake JDBC 驱动（`net.snowflake:snowflake-jdbc`）从 3.19.0 升级到 3.19.1，属于 semver-patch 级别的补丁更新。Snowflake JDBC 驱动用于 Snowflake 目录（SnowflakeCatalog）和 Snowflake 表的读写操作，升级到最新补丁版本可以获取 bug 修复和小的功能改进，保持依赖的最新状态。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中 `snowflake-jdbc` 版本声明存在较旧的 3.19.0，自动将其更新为 3.19.1。该文件是 Iceberg 使用的 Gradle 版本目录（Version Catalog），所有子模块通过引用 `libs.snowflake-jdbc` 来统一引用该版本号，因此只需修改这一处即可全局生效。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Snowflake JDBC 驱动版本从 3.19.0 升级到 3.19.1。

**工作逻辑**：在版本目录的 `[versions]` 区块中，将：

```toml
snowflake-jdbc = "3.19.0"
```

修改为：

```toml
snowflake-jdbc = "3.19.1"
```

该版本号位于 `slf4j` 和 `spark-hive33` 之间，属于生产依赖（direct:production）。所有引用 `libs.snowflake.jdbc` 的模块（主要是 `iceberg-snowflake` 子项目）会自动使用新版本。

## 小结

- **成效**：Snowflake JDBC 驱动升级到 3.19.1，获取上游补丁修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是依赖版本升级，属于低风险的补丁级更新。1.4.x 维护分支若包含 Snowflake 模块且使用 3.19.0，可以考虑回迁以获取 bug 修复。但需验证 3.19.1 与 1.4.x 所用的其他依赖（如 Jackson 等）的兼容性。如果 1.4.x 当前未发现 Snowflake JDBC 相关问题，也可选择不回迁，保持分支稳定。
