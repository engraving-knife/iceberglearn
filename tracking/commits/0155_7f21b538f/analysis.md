# 提交 0155：Build: Bump net.snowflake:snowflake-jdbc from 3.14.2 to 3.14.3 (#9039)

## 提交信息

- **序号**：0155 / 4088
- **哈希**：7f21b538f30809e77fef32fce2138d0322fa73c8
- **短哈希**：7f21b538f
- **日期**：2023-11-13 10:00:52 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.14.2 to 3.14.3 (#9039)
- **PR/Issue**：#9039

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 Snowflake JDBC 驱动 `net.snowflake:snowflake-jdbc` 从 `3.14.2` 升级到 `3.14.3`，属于 `version-update:semver-patch` 级别的依赖更新。Dependabot 在 PR 描述中给出了 [release notes](https://github.com/snowflakedb/snowflake-jdbc/releases)、[changelog](https://github.com/snowflakedb/snowflake-jdbc/blob/master/CHANGELOG.rst) 和 [commits 对比](https://github.com/snowflakedb/snowflake-jdbc/compare/v3.14.2...v3.14.3) 供维护者审阅。

`net.snowflake:snowflake-jdbc` 是 Snowflake 官方维护的 JDBC 驱动，Iceberg 的 `iceberg-snowflake` 模块通过它以 `runtimeOnly` 依赖形式连接 Snowflake 数据库，用于在 Snowflake 中读写 Iceberg 表（Snowflake 作为 Iceberg 目录与查询引擎的集成场景）。JDBC 驱动版本升级通常会带来连接稳定性修复、认证/网络层改进、新特性支持以及对 Snowflake 服务端新功能的兼容性。

本次升级是 patch 级别（3.14.x 系列），按 Dependabot 分类属于 `version-update:semver-patch`，意味着只有兼容性补丁，不包含破坏性变更，风险较低。3.14.3 通常包含自 3.14.2 以来 Snowflake JDBC 驱动积累的 bug 修复与稳定性改进。Iceberg 维护者合并此 PR 即表示认可升级在 Iceberg 使用范围内是兼容的。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `snowflake-jdbc = "3.14.2"` 改为 `snowflake-jdbc = "3.14.3"`。所有通过 `libs.snowflake.jdbc` 引用该驱动的模块会自动解析到新版本，无需逐个修改各模块的 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `net.snowflake:snowflake-jdbc` 的版本从 3.14.2 升级到 3.14.3。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（第 78 行附近），原行 `snowflake-jdbc = "3.14.2"` 被改为 `snowflake-jdbc = "3.14.3"`。该版本常量在版本目录中通过 `snowflake-jdbc = { module = "net.snowflake:snowflake-jdbc", version.ref = "snowflake-jdbc" }`（第 150 行附近）绑定到具体 artifact。在根 `build.gradle` 的 `project(':iceberg-snowflake')` 配置中，该驱动以 `runtimeOnly libs.snowflake.jdbc` 形式引入——即只在运行时需要、编译期不可见，符合"JDBC 驱动作为运行时连接器"的惯例。升级后，`iceberg-snowflake` 模块在运行时会解析到 Snowflake JDBC 3.14.3，获取该版本的修复与改进。

## 小结

该提交由 Dependabot 自动将 Snowflake JDBC 驱动从 3.14.2 升级到 3.14.3，使 Iceberg 的 Snowflake 集成跟进 Snowflake JDBC 最新 patch 版本，获取 bug 修复与稳定性改进。
