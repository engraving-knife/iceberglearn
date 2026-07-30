# 提交 1775：Build: Bump org.xerial:sqlite-jdbc from 3.49.0.0 to 3.49.1.0 (#12385)

## 提交信息

- **序号**：1775 / 4088
- **哈希**：e4833dd0d516e76ff95054740e7d2b4f6cb40d18
- **短哈希**：e4833dd0d
- **日期**：2025-02-24 09:05:45 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.49.0.0 to 3.49.1.0 (#12385)
- **PR/Issue**：#12385

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 SQLite JDBC 驱动从 3.49.0.0 版本升级到 3.49.1.0 版本。sqlite-jdbc 是 Iceberg 项目中用于测试和某些 JDBC 目录场景的 SQLite 数据库驱动。此次升级为补丁版本升级（semver-patch），获取 SQLite 驱动的 bug 修复和改进。

## 如何达成设计目的

提交通过更新 `gradle/libs.versions.toml` 文件中 sqlite-jdbc 的版本号来完成升级。该文件是 Gradle 版本目录（Version Catalog），统一管理项目的所有依赖版本。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 sqlite-jdbc 依赖版本。

**工作逻辑**：将 `sqlite-jdbc = "3.49.0.0"` 修改为 `sqlite-jdbc = "3.49.1.0"`。

## 小结

- **成效**：将 SQLite JDBC 驱动升级到 3.49.1.0 补丁版本。
- **影响范围**：影响使用 SQLite JDBC 的测试和 JDBC 目录相关代码。补丁版本升级通常无破坏性变更。
- **回迁到 1.4.x 的注意事项**：低优先级回迁。补丁版本升级风险低，可根据需要回迁。无前置依赖。
