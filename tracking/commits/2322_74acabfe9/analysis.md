# 提交 2322：Build: Bump org.xerial:sqlite-jdbc from 3.50.1.0 to 3.50.2.0 (#13468)

## 提交信息

- **序号**：2322 / 4088
- **哈希**：74acabfe9d240bbe60829fa1083ceba871ff31ea
- **短哈希**：74acabfe9
- **日期**：2025-07-07 09:55:35 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.50.1.0 to 3.50.2.0 (#13468)
- **PR/Issue**：#13468

## 总体目的

这是一个由 dependabot 自动生成的依赖升级提交，将 SQLite JDBC 驱动从版本 3.50.1.0 升级到 3.50.2.0。SQLite JDBC 驱动是 Iceberg 项目中用于测试和某些 Catalog 实现的嵌入式数据库驱动。这是一个 semver-patch 级别升级。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `libs.versions.toml`，将 `sqlite-jdbc` 的版本号从 `3.50.1.0` 更新为 `3.50.2.0`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 SQLite JDBC 驱动版本号。

**工作逻辑**：将 `sqlite-jdbc = "3.50.1.0"` 修改为 `sqlite-jdbc = "3.50.2.0"`。

## 总结

这是一个常规的依赖维护提交，通过 dependabot 自动升级 SQLite JDBC 驱动以获取最新的 bug 修复。变更仅涉及版本号修改。
