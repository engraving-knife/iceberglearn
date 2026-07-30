# 提交 0093：Build: Bump org.xerial:sqlite-jdbc from 3.43.2.0 to 3.43.2.1 (#8893)

## 提交信息

- **序号**：0093 / 4088
- **哈希**：3bced93376d51511f5cb3254484223743f723f6b
- **短哈希**：3bced9337
- **日期**：2023-10-25 14:01:35 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.43.2.0 to 3.43.2.1 (#8893)
- **PR/Issue**：#8893

## 总体目的

这是一个由 Dependabot 自动生成的依赖版本升级提交，将 [Xerial SQLite JDBC 驱动](https://github.com/xerial/sqlite-jdbc) `org.xerial:sqlite-jdbc` 从 3.43.2.0 升级到 3.43.2.1，属于语义化版本中的补丁版本（patch）升级。

`sqlite-jdbc` 是 Iceberg 在测试场景中使用的纯 Java SQLite JDBC 驱动，主要用于 Iceberg JDBC Catalog（`org.apache.iceberg.jdbc.JdbcCatalog`）相关的单元测试与集成测试。SQLite 是一个嵌入式文件型数据库，无需独立服务进程，非常适合在 CI 环境中作为 JDBC Catalog 的轻量后端进行测试，避免引入外部数据库依赖。该驱动内置了针对各平台的原生 SQLite 库，在测试时按当前 OS/架构加载对应二进制。

本次升级是 3.43.2.0 → 3.43.2.1 的最小步进，按 Xerial 的发布惯例，第三位版本号变更通常只包含 bug 修复（如针对特定平台的 native 库问题、JDBC 兼容性修复、资源泄漏修复等），不引入 API 变更。对 Iceberg 而言，升级该驱动有助于修复测试中可能遇到的驱动层面问题，同时保持依赖新鲜度。

与前两个提交一样，这是 Iceberg 持续依赖维护工作的一部分，由 Dependabot 自动发起。

## 如何达成设计目的

改动只动一处：在 Gradle 版本目录 `gradle/libs.versions.toml` 中将 `sqlite-jdbc` 版本字符串从 `"3.43.2.0"` 改为 `"3.43.2.1"`。该版本常量被测试相关模块通过 `version.ref` 引用，改一处即可全局生效。

## 修改详情

### [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml)

**修改目的**：将 SQLite JDBC 驱动版本从 3.43.2.0 升级到 3.43.2.1。

**工作逻辑**：版本目录的 `[versions]` 段中只改了一行：

```toml
-sqlite-jdbc = "3.43.2.0"
+sqlite-jdbc = "3.43.2.1"
```

该版本常量在 `[libraries]` 段被 `sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }` 引用，主要供 JDBC Catalog 测试模块使用。改完后所有引用 `libs.sqlite.jdbc` 的测试都会自动解析到 3.43.2.1。这是 patch 级别升级，不改变 JDBC API 行为，对 Iceberg 测试逻辑无影响。

## 小结

该提交通过 Dependabot 将 SQLite JDBC 驱动从 3.43.2.0 升到 3.43.2.1，是 Iceberg JDBC Catalog 测试依赖的一次常规 patch 升级，保持测试驱动与上游修复同步。
