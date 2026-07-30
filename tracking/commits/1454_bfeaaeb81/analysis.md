# 提交 1454：Build: Bump org.xerial:sqlite-jdbc from 3.47.0.0 to 3.47.1.0 (#11682)

## 提交信息

- **序号**：1454 / 4088
- **哈希**：bfeaaeb8181bf035367761a0c6c907055f4f00cf
- **短哈希**：bfeaaeb81
- **日期**：2024-12-02（Mon Dec 2 06:27:43 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.47.0.0 to 3.47.1.0 (#11682)
- **PR/Issue**：#11682

## 总体目的

Dependabot 自动生成的依赖升级，把 SQLite JDBC 驱动 `org.xerial:sqlite-jdbc` 从 `3.47.0.0` 升到 `3.47.1.0`。`3.47.0.0 → 3.47.1.0` 属于 patch 级升级（xerial 版本号约定：`主.次.修.附`，第三段 0 → 1 视为 patch）。

Iceberg 在测试场景中广泛使用 SQLite 作为轻量级嵌入式 JDBC 后端，用来验证 `JdbcCatalog`、`JdbcViewCatalog` 等 JDBC 类 catalog 实现——这些测试需要一个支持事务的关系库，而 SQLite（特别是内存模式或临时文件模式）启动快、零配置，是测试 JDBC catalog 行为的理想选择。xerial 维护的 `sqlite-jdbc` 是社区事实标准的 SQLite JDBC 驱动，每个版本通常包含对内置 native SQLite 引擎的升级、bug 修复与新 JDK 兼容性改进。本次升级属常规版本跟进，目的是保持测试依赖最新、获取上游修复，避免长期滞后造成后续升级跨度变大。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `sqlite-jdbc` 的版本字符串即可。由于该依赖在项目中通过版本目录统一管理，所有引用 `libs.sqlite.jdbc` 的模块（iceberg-core、iceberg-api、iceberg-bom 等测试依赖）会自动随 BOM 拉取新版本，无需在各 build.gradle 中分散修改。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：把 SQLite JDBC 驱动版本从 `3.47.0.0` 升级到 `3.47.1.0`。

**工作逻辑**：

```toml
-sqlite-jdbc = "3.47.0.0"
+sqlite-jdbc = "3.47.1.0"
```

该条目位于版本目录的依赖版本区（紧邻 `snowflake-jdbc`、`testcontainers` 等），下游 build 脚本以 `libs.sqlite.jdbc` 形式引用。该依赖主要进入测试 classpath（`testImplementation`），用于 `JdbcCatalog`、`JdbcViewCatalog` 等的单元/集成测试，不进入发布产物的运行时依赖。

## 小结

- **成效**：SQLite JDBC 测试依赖升级到 3.47.1.0，获取上游 patch 修复与 native SQLite 引擎更新，使 JDBC catalog 相关测试在新版驱动下持续可运行。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行，+1/-1。属于低风险测试依赖升级，不影响发布产物运行时行为。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支的 JDBC catalog 测试同样使用 SQLite 驱动。建议回迁以保持测试依赖与 main 一致，避免因驱动版本差异导致的测试 flakiness（SQLite 驱动升级偶有 SQL 解析行为微调）。回迁风险极低，仅改一处版本号；如 1.4.x 上有针对 SQLite 特定行为的测试断言，升级后应回归 `JdbcCatalogTest`、`JdbcViewCatalogTest` 等测试套件。
