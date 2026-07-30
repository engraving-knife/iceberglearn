# 提交 3591：Build: Bump org.xerial:sqlite-jdbc from 3.51.3.0 to 3.53.0.0 (#16120)

## 提交信息

- **序号**：3591 / 4088
- **哈希**：180e399e195f0a264d6ce7298685ad3ecd13338a
- **短哈希**：180e399e1
- **日期**：2026-04-25 23:48:38 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.51.3.0 to 3.53.0.0 (#16120)
- **PR/Issue**：#16120

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 SQLite JDBC 驱动 `org.xerial:sqlite-jdbc` 从版本 3.51.3.0 升级到 3.53.0.0。SQLite JDBC 驱动用于 Iceberg 测试中的嵌入式 SQL 数据库（如 JDBC Catalog 测试）。这是一个 semver-minor（次版本）升级，跨越了两个次版本（3.51 到 3.53）。

## 如何达成设计目的

Dependabot 自动检测到版本目录中 `sqlite-jdbc` 版本有新版本可用，自动创建 PR 升级版本声明。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 sqlite-jdbc 版本声明。

**工作逻辑**：
```toml
-sqlite-jdbc = "3.51.3.0"
+sqlite-jdbc = "3.53.0.0"
```

## 总结

这是一个常规的依赖维护提交，通过次版本升级保持 SQLite JDBC 驱动的最新状态，获取 3.53.0.0 版本中的改进和 bug 修复。
