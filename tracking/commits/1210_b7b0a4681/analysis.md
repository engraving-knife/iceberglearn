# 提交 1210：Build: Bump org.xerial:sqlite-jdbc from 3.46.1.0 to 3.46.1.3 (#11231)

## 提交信息

- **序号**：1210 / 4088
- **哈希**：b7b0a4681949bf8a285b78f8ba4b29e12748e66c
- **短哈希**：b7b0a4681
- **日期**：2024-10-03（Thu Oct 3 04:52:59 2024 -0700）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.46.1.0 to 3.46.1.3
- **PR/Issue**：#11231

## 总体目的

Dependabot 自动生成的依赖升级提交，把 SQLite JDBC 驱动从 `3.46.1.0` 升级到 `3.46.1.3`（semver-patch 补丁版本升级）。

`org.xerial:sqlite-jdbc` 是 SQLite 数据库的纯 Java JDBC 驱动，Iceberg 在 `JdbcCatalog`（`core` 模块的 JDBC Catalog 实现）中使用它来进行 catalog 元数据的持久化（支持用 SQLite 作为轻量级 catalog 存储后端，主要用于测试/演示场景）。升级动机是跟进上游 3.46.1.3 补丁版本的 bug 修复与 SQLite 引擎更新，保持依赖最新。

## 如何达成设计目的

Iceberg 的依赖版本统一集中在 `gradle/libs.versions.toml` 中维护，`sqlite-jdbc` 共享一个版本变量，只需把版本号从 `3.46.1.0` 改为 `3.46.1.3` 即可。属于 patch 版本升级（同一 3.46.1 系列内的 3 个补丁），理论上不包含破坏性 API 变更。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：升级 sqlite-jdbc 版本。

**工作逻辑**：

```diff
-sqlite-jdbc = "3.46.1.0"
+sqlite-jdbc = "3.46.1.3"
```

该版本变量被 `sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }` 引用，所有使用该 library 的模块（主要是使用 `JdbcCatalog` 的测试）会同步升级。

## 小结

- **成效**：把 SQLite JDBC 驱动从 3.46.1.0 升级到 3.46.1.3，跟进上游 patch 修复。仅修改 1 行 1 个文件，无源代码改动。
- **影响范围**：仅影响使用 `JdbcCatalog` + SQLite 后端的测试/演示场景，不影响生产环境（生产环境通常用 PostgreSQL/MySQL 作为 JDBC Catalog 后端）。
- **回迁到 1.4.x 的注意事项**：纯依赖版本升级，回迁零风险。1.4.x 分支的 `gradle/libs.versions.toml` 可直接 cherry-pick。需确认 1.4.x 上 `JdbcCatalog` 的测试用例与 3.46.1.3 兼容（patch 版本通常兼容）。
