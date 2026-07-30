# 提交 3440：Build: Bump org.xerial:sqlite-jdbc from 3.51.2.0 to 3.51.3.0 (#15721)

## 提交信息

- **序号**：3440 / 4088
- **哈希**：77f65e1462d5d59cac7a61e94664254af20c4fcc
- **短哈希**：77f65e1462
- **日期**：2026-03-22 09:20:13 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.51.2.0 to 3.51.3.0 (#15721)
- **PR/Issue**：#15721

## 总体目的

这是一个由 Dependabot 自动生成的依赖版本升级提交。将 SQLite JDBC 驱动从 3.51.2.0 升级到 3.51.3.0，这是一个补丁版本升级。

SQLite JDBC 驱动提供 Java 应用程序连接 SQLite 数据库的能力。在 Iceberg 项目中，SQLite 可能被用于测试环境或作为本地目录存储后端。

## 如何达成设计目的

- Dependabot 自动检测到 sqlite-jdbc 有新版本发布
- 在 `gradle/libs.versions.toml` 版本目录文件中更新版本号
- 通过 CI 测试验证升级兼容性

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 sqlite-jdbc 依赖版本号。

**工作逻辑**：
- 将 `org.xerial:sqlite-jdbc` 的版本号从 `3.51.2.0` 修改为 `3.51.3.0`
- 该文件是 Gradle 版本目录，集中管理项目所有依赖版本

## 总结

这是 Dependabot 自动生成的常规依赖升级提交，将 SQLite JDBC 驱动从 3.51.2.0 升级到 3.51.3.0，属于补丁版本升级，包含 bug 修复和改进，风险较低。
