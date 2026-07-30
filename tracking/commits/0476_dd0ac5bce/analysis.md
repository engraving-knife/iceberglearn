# 提交 0476：Build: Bump org.xerial:sqlite-jdbc from 3.44.0.0 to 3.45.1.0 (#9634)

## 提交信息

- **序号**：0476
- **哈希**：dd0ac5bce409fd02c0b04039a3ba13f9404b6f5b
- **短哈希**：dd0ac5bce
- **日期**：2024-02-06 19:56:10 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.44.0.0 to 3.45.1.0
- **PR/Issue**：#9634

## 总体目的

本提交由 Dependabot 自动生成，将 `org.xerial:sqlite-jdbc` 从 `3.44.0.0` 升级到 `3.45.1.0`，属于一次语义化版本中的 minor 版本更新（`version-update:semver-minor`）。sqlite-jdbc 是 Xerial 维护的纯 Java SQLite JDBC 驱动，它将原生 SQLite 引擎与 JDBC 接口绑定，允许 JVM 程序以嵌入式（无服务端）方式使用 SQLite 数据库。该依赖被 Dependabot 标记为 `direct:production`（直接生产依赖）。

在 Iceberg 中，sqlite-jdbc 主要服务于 `JdbcCatalog`（位于 `core` 模块）这一 Catalog 实现。`JdbcCatalog` 将 Iceberg 表的元数据（命名空间、表指针、Catalog 属性等）持久化到关系型数据库中，支持 PostgreSQL、MySQL、SQL Server 等多种后端，而 SQLite 则是其中最轻量的后端选择——无需独立部署数据库服务，适合本地开发、单元测试与集成测试场景。具体而言，`build.gradle` 中在 `:iceberg-core`（第 361 行）和 `:iceberg-aws`（第 493 行）两个模块下以 `testImplementation` 引入该依赖，对应的测试类包括 `TestJdbcCatalog`、`TestJdbcTableConcurrency`、`TestRESTCatalog` 以及 `TestS3FileIO`，它们通过 SQLite 验证 JdbcCatalog 的并发与一致性语义。

将版本从 3.44.0.0 提升到 3.45.1.0，目的在于跟进上游驱动的缺陷修复与功能改进，同时保持与 Iceberg 测试栈中使用的 SQLite 行为一致。3.45.x 系列包含了 SQLite 引擎本身的更新（SQL 语法、性能与稳定性修复）以及 JDBC 层的改进，及时升级可避免在测试中命中已修复的旧 bug，并降低未来跨版本升级的累积风险。由于该依赖在 Iceberg 中仅用于测试，本升级对生产运行时无直接影响，但能提升测试的可靠性与可维护性。

## 如何达成设计目的

Dependabot 通过扫描 `gradle/libs.versions.toml` 中的版本声明，识别出 `sqlite-jdbc = "3.44.0.0"` 这一可升级项，并自动生成单一文件的版本号替换，将引用值改为 `3.45.1.0`。由于该版本号在 `libs.versions.toml` 中以 `version.ref` 形式集中声明，并在 `build.gradle` 中通过 `libs.sqlite.jdbc` 访问器引用，因此只需修改一处版本常量即可让所有依赖该库的模块（core、aws）同步升级，无需逐模块改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 sqlite-jdbc 版本引用从 `3.44.0.0` 提升到 `3.45.1.0`。

**工作逻辑**：

文件第 87 行附近（在 `spark-hive35`、`spring-boot`、`spring-web` 等版本声明相邻处）将：

```
sqlite-jdbc = "3.44.0.0"
```

改为：

```
sqlite-jdbc = "3.45.1.0"
```

该声明对应的库坐标定义在第 195 行 `sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }`，`version.ref` 指向被修改的常量。所有通过 `libs.sqlite.jdbc` 引用该依赖的 `build.gradle` 配置（core 模块第 361 行、aws 模块第 493 行，均为 `testImplementation`）会自动解析到新版本，从而完成整个测试栈的 sqlite-jdbc 升级。

## 小结

本提交是一次由 Dependabot 驱动的常规依赖升级，仅修改 `gradle/libs.versions.toml` 中 `sqlite-jdbc` 的版本号（3.44.0.0 → 3.45.1.0），文件改动量为 1 行增、1 行删。sqlite-jdbc 在 Iceberg 中作为 JdbcCatalog 的测试后端使用，本次 minor 版本升级旨在跟进上游修复与改进，提升测试可靠性。由于版本号采用集中式 `version.ref` 声明，单点修改即可让 core 与 aws 两个模块的测试依赖同步生效。
