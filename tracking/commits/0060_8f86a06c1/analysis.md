# 提交 0060：Build: Bump org.xerial:sqlite-jdbc from 3.43.0.0 to 3.43.2.0 (#8837)

## 提交信息

- **序号**：0060 / 4088
- **哈希**：8f86a06c1405b68fa2935c06a87d50a02ca400b3
- **短哈希**：8f86a06c1
- **日期**：2023-10-16 09:55:35 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.43.0.0 to 3.43.2.0 (#8837)
- **PR/Issue**：#8837

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 `org.xerial:sqlite-jdbc` 从 3.43.0.0 升级到 3.43.2.0。`sqlite-jdbc` 是 Xerial 提供的纯 Java SQLite JDBC 驱动，内嵌原生 SQLite 引擎，允许 Java 程序以 JDBC 方式访问内存或文件型 SQLite 数据库而无需部署外部数据库服务。

在 Iceberg 中，`sqlite-jdbc` 仅作为**测试依赖**（`testImplementation`）使用，不出现在生产运行时。它主要用于 Iceberg `JdbcCatalog` 实现的集成测试——SQLite 提供了一个零部署、进程内的 SQL 数据库，使得 `JdbcCatalog` 的建表/提交/查询等逻辑可以在 CI 中无需外部 PostgreSQL/MySQL 即可被覆盖。该依赖在 [build.gradle](file:///Users/fengxiaohang/trae/iceberglearn/build.gradle)（core 模块测试，第 360、492 行附近）以及 `spark/v3.2`、`spark/v3.3`、`spark/v3.4`、`spark/v3.5` 的 build.gradle 中以 `testImplementation libs.sqlite.jdbc` 形式引入。

本次升级属于 semver-patch（补丁号）升级（3.43.0.0 → 3.43.2.0），同属 3.43.x 系列，仅包含 bug 修复与小改进（如对 SQLite 引擎的小版本更新与 JDBC 驱动缺陷修复），不引入 API 不兼容变更，风险极低。对 Iceberg 演进的意义在于持续获得 SQLite 驱动的稳定性修复，保障 `JdbcCatalog` 测试在 CI 中的可靠性与可重复性。

## 如何达成设计目的

Dependabot 通过 Gradle 集中式版本目录完成升级。在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中，`sqlite-jdbc` 版本变量被同名库声明 `org.xerial:sqlite-jdbc` 通过 `version.ref = "sqlite-jdbc"` 引用，下游各模块的 `testImplementation libs.sqlite.jdbc` 都会自动解析到新版本，因此只需修改一行版本号即可，无需改动任何源代码或测试代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `sqlite-jdbc` 版本变量从 3.43.0.0 提升到 3.43.2.0，使测试用的 SQLite JDBC 驱动获得该补丁版本的修复。

**工作逻辑**：在 `[versions]` 段中，将 `sqlite-jdbc = "3.43.0.0"` 改为 `sqlite-jdbc = "3.43.2.0"`。由于该制品仅用于测试（`JdbcCatalog` 集成测试），且为同 3.43.x 系列内的 patch 升级，回归风险极低；CI 中运行 `JdbcCatalog` 相关测试（core 模块与 Spark 各版本模块的测试）即可验证升级无碍。值得注意，`sqlite-jdbc` 的版本号采用"SQLite 引擎版本.JDBC 驱动补丁"的四级编码（3.43.2.0 表示内嵌 SQLite 3.43.2、驱动补丁 0），因此本次升级实际上也把内嵌的 SQLite 引擎从 3.43.0 提升到了 3.43.2，测试中涉及 SQLite SQL 行为的细节（如新关键字、类型亲和性修复）理论上可能受影响，但概率很低。

## 小结

通过一行版本目录改动，将 Iceberg 仅用于测试的 sqlite-jdbc 从 3.43.0.0 升级到 3.43.2.0，是低风险的常规补丁维护，保障 `JdbcCatalog` 集成测试所依赖的嵌入式 SQLite 引擎与驱动持续获得稳定性修复。
