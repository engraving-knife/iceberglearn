# 提交 0034：Build: Bump org.xerial:sqlite-jdbc from 3.42.0.0 to 3.43.0.0 (#8775)

## 提交信息

- **序号**：0034 / 4088
- **哈希**：4a08140ada653a625ffedf782e73a25963d52e21
- **短哈希**：4a08140ad
- **日期**：2023-10-11 09:02:40 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.42.0.0 to 3.43.0.0 (#8775)
- **PR/Issue**：#8775

## 总体目的

这个提交由 dependabot 自动生成，把 Iceberg 依赖的 Xerial SQLite JDBC 驱动从 3.42.0.0 升级到 3.43.0.0，更新类型为 `version-update:semver-minor`（minor 版本升级）。

`org.xerial:sqlite-jdbc` 是 Iceberg 在测试场景下使用的轻量级 JDBC 驱动，Iceberg 通过它提供基于 SQLite 的 JDBC catalog（`SqliteCatalog`，用于本地测试与 demo），以及 JDBC 模块的测试（`JdbcCatalog` 实现的测试套件中用 SQLite 作为嵌入式数据库后端，无需启动外部服务）。3.43.x 系列对应 SQLite 引擎 3.43.0，相比 3.42.0.0 通常带来 SQLite 上游的若干 SQL 语法/函数增强（如新的内置函数、JSON 函数改进、性能与正确性修复）以及对 JDBC 驱动自身 native 库的更新与 bugfix。对于 Iceberg 而言，由于 SQLite 主要用于测试与本地 catalog，升级风险较低，但能确保测试在较新的 SQLite 引擎下运行，减少因 SQLite 旧 bug 误报测试失败的概率。

作为 minor 版本升级，Xerial 在 3.43.0.0 保持 API 兼容，Iceberg 的 JDBC catalog 与测试代码无需任何改动即可升级。

## 如何达成设计目的

通过单行版本号变更完成升级：在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中把 `sqlite-jdbc` 版本引用从 `3.42.0.0` 改为 `3.43.0.0`，所有通过 `version.ref = "sqlite-jdbc"` 引用该版本的 library 坐标会一并升级。

## 修改详情

### [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml)

**修改目的**：把 Xerial SQLite JDBC 驱动版本从 3.42.0.0 升级到 3.43.0.0。

**工作逻辑**：在 `[versions]` 段把 `sqlite-jdbc = "3.42.0.0"` 改为 `sqlite-jdbc = "3.43.0.0"`。该版本号被 `[libraries]` 段的 `sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }` 引用，从而统一升级 `SqliteCatalog` 与相关 JDBC 测试所用驱动版本。无其他源码或配置改动。

## 小结

本提交是 dependabot 自动把 Xerial SQLite JDBC 驱动从 3.42.0.0 升级到 3.43.0.0 的单行依赖升级，影响范围是 Iceberg 基于 SQLite 的 JDBC catalog 与相关测试，属低风险 minor 版本升级，使测试用嵌入式数据库与上游 SQLite 引擎保持同步。
