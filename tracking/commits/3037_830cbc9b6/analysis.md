# 提交 3037：Build: Bump net.snowflake:snowflake-jdbc from 3.27.1 to 3.28.0 (#14899)

## 提交信息

- **序号**：3037 / 4088
- **哈希**：830cbc9b6840d233889a64d09260913a315f9464
- **短哈希**：830cbc9b6
- **日期**：2025-12-20
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.27.1 to 3.28.0 (#14899)
- **PR/Issue**：#14899

## 总体目的

本提交是 Dependabot 自动生成的依赖版本升级，将 Snowflake JDBC 驱动 `net.snowflake:snowflake-jdbc` 从 `3.27.1` 升级到 `3.28.0`。Snowflake JDBC 驱动是 Iceberg 与 Snowflake 数据仓库交互的必备组件，用于 `iceberg-snowflake` 模块（Snowflake Catalog 集成）中通过 JDBC 连接 Snowflake 实例、读取 Iceberg 表的元数据与快照信息，并将 Iceberg 表注册到 Snowflake 的目录中。

在 Iceberg 项目中，`snowflake-jdbc` 通过 `gradle/libs.versions.toml` 的 `snowflake-jdbc` 条目定义，被 `iceberg-snowflake` 模块引用，主要用于 `SnowflakeCatalog`、`SnowflakeTableOperations` 等类在初始化时建立 JDBC 连接并执行元数据查询。Dependabot 元数据显示该依赖为 `direct:production` 类型，进入发布制品的运行时路径。本次升级属于语义版本的次版本升级（semver-minor，`3.27.1` → `3.28.0`），按语义化版本约定，次版本升级允许引入向后兼容的新功能，但不应破坏现有 API。

此次升级的动机是常规依赖维护：Snowflake JDBC 驱动随 Snowflake 服务端演进而持续发布新版本，包含连接稳定性改进、认证机制更新（如 OAuth/SCIM）、SQL 行为修正以及与新 Snowflake 特性的兼容性。保持驱动版本同步有助于 Iceberg 的 Snowflake 集成与最新服务端协同工作。

## 如何达成设计目的

改动仅涉及 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `snowflake-jdbc` 版本号的更新。该版本通过 `version.ref = "snowflake-jdbc"` 被 `iceberg-snowflake` 模块的 `build.gradle` 引用，单点修改即可让该模块在构建时拉取新版驱动。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 snowflake-jdbc 版本号。

**工作逻辑**：
将版本目录中 `snowflake-jdbc = "3.27.1"` 修改为 `snowflake-jdbc = "3.28.0"`。该版本通过 `snowflake-jdbc = { module = "net.snowflake:snowflake-jdbc", version.ref = "snowflake-jdbc" }` 定义，被 `iceberg-snowflake` 模块引用以在运行时建立到 Snowflake 的 JDBC 连接。修改后，使用 Snowflake Catalog 的用户将自动获得 3.28.0 驱动带来的改进。

## 总结

本提交是 Dependabot 自动维护的 Snowflake JDBC 驱动次版本升级（3.27.1 → 3.28.0），属于常规的生产依赖版本维护。Snowflake JDBC 驱动是 Iceberg Snowflake Catalog 集成的基础，次版本升级带来向后兼容的新功能与缺陷修复，确保 Iceberg 与 Snowflake 服务端的协同保持最新。
