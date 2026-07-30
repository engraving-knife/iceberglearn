# 提交 0680：Build: Bump net.snowflake:snowflake-jdbc from 3.14.5 to 3.15.1

## 提交信息
- **序号**：0680 / 4088
- **哈希**：2400aa530727585e718ba58e921c6f7cd6277b2e
- **短哈希**：2400aa530
- **日期**：2024-04-14 07:16:47 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.14.5 to 3.15.1 (#10095)
- **PR/Issue**：#10095

## 总体目的

本提交由 Dependabot 自动生成，将 Snowflake JDBC 驱动（`net.snowflake:snowflake-jdbc`）从 3.14.5 升级到 3.15.1。这是一次 semver-minor 级别的依赖更新，目的是引入 Snowflake JDBC 3.15.x 的缺陷修复、功能改进与可能的性能优化，保持依赖的时效性。

Snowflake JDBC 驱动在 Iceberg 项目中用于 Snowflake catalog 相关的集成场景（如通过 Snowflake 操作 Iceberg 表）。保持该驱动为较新版本有助于获取上游修复、避免已知问题，并与 Snowflake 服务端保持兼容。

## 如何达成设计目的

采用 Iceberg 项目的依赖集中管理方式：所有依赖版本声明在 `gradle/libs.versions.toml` 中统一维护，本提交仅修改该文件中 `snowflake-jdbc` 版本号一行，由 Gradle 版本目录（version catalog）机制在构建时解析到所有引用该别名的模块。Dependabot 识别到该依赖有新版本，自动提交 PR 升级。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：升级 Snowflake JDBC 驱动版本。
**工作逻辑**：将 `snowflake-jdbc = "3.14.5"` 改为 `snowflake-jdbc = "3.15.1"`。该文件是 Gradle 版本目录，`snowflake-jdbc` 是版本别名，引用处（如 `libs.snowflake.jdbc`）会自动获得新版本，无需改动其他构建文件。

## 小结
- **成效**：成功达成目的，Snowflake JDBC 升级到 3.15.1。
- **影响范围**：仅构建依赖声明，影响所有使用 Snowflake JDBC 的模块（主要是 Snowflake catalog 相关集成）。运行时行为变化取决于 3.14.5→3.15.1 的上游变更。
- **回迁到 1.4.x 的注意事项**：回迁简单，直接同步该版本号即可。需注意 1.4.x 分支是否已有针对 Snowflake JDBC 的本地补丁或版本锁定；另外建议查阅 Snowflake JDBC 3.15.1 的 changelog 确认无破坏性变更（该版本为 minor 升级，通常向后兼容）。
