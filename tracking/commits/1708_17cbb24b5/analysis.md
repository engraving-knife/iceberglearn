# 提交 1708：Build: Bump org.xerial:sqlite-jdbc from 3.48.0.0 to 3.49.0.0 (#12206)

## 提交信息

- **序号**：1708 / 4088
- **哈希**：17cbb24b5c6a45806aa0f3013d265f5a381a9c13
- **短哈希**：17cbb24b5
- **日期**：2025-02-10（Mon Feb 10 07:38:44 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.48.0.0 to 3.49.0.0 (#12206)
- **PR/Issue**：#12206

## 总体目的

Dependabot 自动升级提交。`org.xerial:sqlite-jdbc` 是 SQLite 的 JDBC 驱动，Iceberg 在测试中使用 SQLite 作为轻量级数据库（如 JDBC catalog 测试）。本提交把该驱动从 `3.48.0.0` 升级到 `3.49.0.0`（minor 级），获取 SQLite 引擎与 JDBC 驱动的改进。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中把 `sqlite-jdbc = "3.48.0.0"` 改为 `sqlite-jdbc = "3.49.0.0"`。

## 修改详情

### `gradle/libs.versions.toml`（修改，+1/-1 行）

**修改目的**：升级 SQLite JDBC 驱动版本。

**工作逻辑**：修改版本变量定义，所有通过 `libs.sqlite.jdbc` 引用 SQLite 驱动的模块（主要是测试）自动使用新版本。

## 小结

- **成效**：升级 SQLite JDBC 驱动到 3.49.0.0，获取 SQLite 引擎与驱动改进。
- **影响范围**：仅构建配置，无源代码变更。影响使用 SQLite 的测试场景。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯测试依赖版本升级。SQLite JDBC minor 升级通常向后兼容。
