# 提交 0080：Build: Bump net.snowflake:snowflake-jdbc from 3.13.30 to 3.14.2 (#8790)

## 提交信息

- **序号**：0080 / 4088
- **哈希**：7a60b00cd671bd28c3ade5ca55f75ae027c312db
- **短哈希**：7a60b00cd
- **日期**：2023-10-20 09:06:16 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.13.30 to 3.14.2 (#8790)
- **PR/Issue**：#8790

## 总体目的

这个提交由 GitHub Dependabot 自动生成，将 Iceberg 项目所依赖的 Snowflake JDBC 驱动（`net.snowflake:snowflake-jdbc`）从 `3.13.30` 升级到 `3.14.2`。Snowflake JDBC 是 Iceberg 与 Snowflake 交互（例如通过 `iceberg-snowflake` 模块读写 Snowflake 目录/表）所必需的客户端驱动，其版本由 Gradle 版本目录 `gradle/libs.versions.toml` 统一管理。

Dependabot 提交信息将此次升级归类为 `version-update:semver-minor`，即 SemVer 语义上的次版本（minor）升级：从 `3.13.x` 系列跨到 `3.14.x` 系列。按照 SemVer 约定，次版本升级应当保持向后兼容，通常包含新特性、缺陷修复与可能的性能改进，不应引入破坏性 API 变更。Iceberg 接受此次升级意味着项目希望跟踪 Snowflake JDBC 的最新稳定版，以获得上游修复与新能力，同时降低因滞留旧版本而积累的安全/兼容性风险。这是项目依赖治理中的常规一环，Dependabot 会持续扫描 `libs.versions.toml` 并对每个直接生产依赖（`dependency-type: direct:production`）发起 PR，由维护者评审合并。

需要说明的是，本次只改了版本目录中的版本号字符串，并未触碰任何业务代码或构建脚本中的引用点——所有引用 `libs.snowflake.jdbc`（或类似别名）的模块会自动继承新版本，这是版本目录（Version Catalog）带来的集中化管理优势。

## 如何达成设计目的

整体改动只有一行：在 `gradle/libs.versions.toml` 中将 `snowflake-jdbc` 的版本常量从 `3.13.30` 改为 `3.14.2`。由于该常量是项目内唯一声明 Snowflake JDBC 版本的位置，所有通过版本目录引用它的子项目（如 `iceberg-snowflake`）会在下次构建时自动解析到新版本，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Snowflake JDBC 驱动版本从 `3.13.30` 升级到 `3.14.2`。

**工作逻辑**：在版本目录的 `[versions]` 段（按字母序位于 `slf4j` 之后、`spark-hive32` 之前）将 `snowflake-jdbc = "3.13.30"` 改为 `snowflake-jdbc = "3.14.2"`。该常量在目录中通过 `snowflake-jdbc = { module = "net.snowflake:snowflake-jdbc", version.ref = "snowflake-jdbc" }`（或等价别名）被引用，所有 `testImplementation`/`implementation` 等配置处使用 `libs.snowflake.jdbc` 的模块都会在构建解析时拿到 `3.14.2`。

## 小结

本提交由 Dependabot 自动将 Snowflake JDBC 从 3.13.30 升级到 3.14.2（SemVer 次版本升级），通过版本目录集中改一行即完成全项目依赖更新，是 Iceberg 依赖治理的常规例行维护。
