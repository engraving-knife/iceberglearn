# 提交 1290：Build: Bump org.xerial:sqlite-jdbc from 3.46.1.3 to 3.47.0.0 (#11407)

## 提交信息

- **序号**：1290 / 4088
- **哈希**：68a7102073d53e81a082cc56b6f5dac6d5f436ae
- **短哈希**：68a710207
- **日期**：2024-10-28（Mon Oct 28 12:02:34 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.46.1.3 to 3.47.0.0 (#11407)
- **PR/Issue**：#11407

## 总体目的

Iceberg 在测试中使用 SQLite JDBC（`org.xerial:sqlite-jdbc`）作为嵌入式数据库，验证 JDBC 相关能力（如 `JdbcCatalog`/`JdbcTable` 等基于 JDBC 的实现）。该依赖版本在 `gradle/libs.versions.toml` 中以 `sqlite-jdbc` 声明。dependabot 检测到 xerial sqlite-jdbc 从 3.46.1.3 升级到 3.47.0.0（minor 发布，包含 SQLite 引擎与原生库更新），本提交把版本号对齐，使测试用的 SQLite 基线跟上上游，获得修复与新版本 SQLite 引擎支持。

## 如何达成设计目的

在版本目录把 `sqlite-jdbc = "3.46.1.3"` 改为 `sqlite-jdbc = "3.47.0.0"`。引用 `libs.sqlite.jdbc` 的测试依赖随之整体升级。这是 dependabot 自动生成的单行 minor 升级，无代码逻辑变更。sqlite-jdbc 仅用于测试（非产物依赖），故只影响测试 classpath。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把测试用 SQLite JDBC 从 3.46.1.3 升到 3.47.0.0。

**工作逻辑**：

```toml
-sqlite-jdbc = "3.46.1.3"
+sqlite-jdbc = "3.47.0.0"
```

相邻的 `testcontainers = "1.20.2"`、`tez010`、`tez08` 等不受影响。

## 小结

- **成效**：测试用 SQLite JDBC 升级到 3.47.0.0（minor，含新版 SQLite 引擎与原生库），JDBC 相关测试基线跟上上游。
- **影响范围**：改动 1 个文件、1 行，仅测试依赖版本变更，无源代码变更。sqlite-jdbc 为测试依赖，不影响产物。
- **回迁到 1.4.x 的注意事项**：
  - **可选回迁**：纯测试依赖升级，不影响产物功能。1.4.x 回迁收益是让 JDBC 测试基线与 main 一致。
  - **风险**：低到中。sqlite-jdbc minor 升级会带来新版 SQLite 引擎（SQL 行为、类型推断可能微调），可能影响依赖 SQLite 具体行为的测试。回迁后建议跑 `JdbcCatalog`/`JdbcTable` 等 JDBC 相关测试确认无回归。
  - 若 1.4.x 不涉及 JDBC catalog 的测试维护，可不强求回迁。
