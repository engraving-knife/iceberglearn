# 提交 1816：Build: Bump net.snowflake:snowflake-jdbc from 3.22.0 to 3.23.0 (#12437)

## 提交信息

- **序号**：1816 / 4088
- **哈希**：6323aa9405f23e3992f243b1134cbafdbb24d73c
- **短哈希**：6323aa940
- **日期**：2025-03-03 16:30:22 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.22.0 to 3.23.0 (#12437)
- **PR/Issue**：#12437

## 总体目的

这是一个由 dependabot 自动生成的依赖版本升级提交。该提交将 `net.snowflake:snowflake-jdbc` 从 3.22.0 升级到 3.23.0。

Snowflake JDBC 驱动用于连接 Snowflake 数据仓库，在 Iceberg 项目中主要用于 `snowflake` 模块，该模块支持将 Iceberg 表与 Snowflake 集成（例如通过 Snowflake Catalog 或 Snowflake 的 Iceberg 表支持）。保持 Snowflake JDBC 驱动处于最新版本有助于获得连接稳定性修复、新特性支持以及安全补丁。

此次升级属于 semver-minor 级别（次要版本升级），按照语义化版本约定，3.23.0 相对于 3.22.0 引入了向后兼容的新功能，通常不会破坏现有构建。但次要版本升级需关注 Snowflake JDBC 驱动的发行说明，确认无影响 Iceberg 集成行为的变更。

## 如何达成设计目的

dependabot 通过修改 `gradle/libs.versions.toml` 版本目录文件中的版本变量声明，将 `snowflake-jdbc` 从 `3.22.0` 改为 `3.23.0`。Iceberg 使用 Gradle 版本目录集中管理依赖版本，修改该变量后 snowflake 模块引用 snowflake-jdbc 的依赖会自动使用新版本。本次未涉及 LICENSE/NOTICE 文件更新，因为 snowflake 模块未打包进 bundle 制品的法务清单。

## 修改详情

### gradle/libs.versions.toml (修改, 1 line)

修改了版本目录中的 `snowflake-jdbc = "3.22.0"` 为 `snowflake-jdbc = "3.23.0"`。该变量位于版本目录的 `[versions]` 块中，控制 snowflake 模块使用的 JDBC 驱动版本。这是该提交的唯一实质性变更。

## 小结

这是一个低风险的依赖次要版本升级，仅修改版本目录一行。回迁到 1.4.x 分支时，若该分支包含 snowflake 模块且 libs.versions.toml 中存在 snowflake-jdbc 变量，则可直接 cherry-pick。由于是次要版本升级，建议回迁后运行 snowflake 模块的集成测试以确认兼容性。注意完整哈希对应的短哈希为 `6323aa940`（取前9位）。
