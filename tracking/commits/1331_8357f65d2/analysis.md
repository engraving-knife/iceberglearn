# 提交 1331：Build: Bump net.snowflake:snowflake-jdbc from 3.19.1 to 3.20.0 (#11447)

## 提交信息

- **序号**：1331 / 4088
- **哈希**：8357f65d23b8d3cc87f89b1aa818fcf4b5eb6d5d
- **短哈希**：8357f65d2
- **日期**：2024-11-04（Mon Nov 4 15:13:37 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.19.1 to 3.20.0 (#11447)
- **PR/Issue**：#11447

## 总体目的

由 Dependabot 自动发起的依赖版本升级：将 Snowflake JDBC 驱动（`net.snowflake:snowflake-jdbc`）从 `3.19.1` 升级到 `3.20.0`，属 minor 版本升级。Iceberg 在 Snowflake 目录集成（`snowflake` 模块，用于 Snowflake Catalog）中使用该 JDBC 驱动连接 Snowflake。升级目的是获取 3.20.0 中新功能与 bug 修复。

Dependabot 标注 `update-type: version-update:semver-minor`，按语义化版本约定属向后兼容的功能性升级，风险高于 patch 但低于 major。

## 如何达成设计目的

只修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `snowflake-jdbc` 这一个版本键的值。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Snowflake JDBC 驱动版本号。

**工作逻辑**：将第 83 行附近的版本声明由

```toml
snowflake-jdbc = "3.19.1"
```

改为

```toml
snowflake-jdbc = "3.20.0"
```

其他版本键（如 `slf4j`、`spark-hive33` 等）保持不变。所有引用 `snowflake-jdbc` 的模块（如 `snowflake/build.gradle`）在依赖解析时使用 3.20.0 版本。

## 小结

- **成效**：Snowflake JDBC 驱动升级至 3.20.0，获取 minor 版本中的新功能与修复。属依赖维护性升级，主要影响 Snowflake Catalog 集成模块的依赖解析。
- **影响范围**：仅 1 个文件、1 行版本号变更。运行时影响取决于 3.19.1→3.20.0 之间 Snowflake JDBC 的具体改动；minor 升级理论上向后兼容，但 JDBC 驱动涉及与 Snowflake 服务端的协议交互，需验证 Snowflake Catalog 集成测试通过。
- **回迁到 1.4.x 的注意事项**：**视情况可选回迁**。1.4.x 同样使用 `libs.versions.toml` 管理 Snowflake JDBC 版本。若 1.4.x 当前 Snowflake JDBC 版本为 3.19.1 或更低，且回迁能获取相关修复或与新版 Snowflake 服务端的兼容性改进，则可回迁。回迁前应确认 1.4.x 当前版本是否已高于 3.20.0，并验证 Snowflake Catalog 集成测试通过。需特别注意 minor 升级可能引入与新版 Snowflake 服务端的协议变化，回迁后应针对目标 Snowflake 服务端版本做兼容性验证。若 1.4.x Snowflake 集成稳定且无相关 bug，可不必回迁。
