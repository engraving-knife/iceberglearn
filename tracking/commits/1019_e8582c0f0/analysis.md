# 提交 1019：Build: Bump org.xerial:sqlite-jdbc from 3.46.0.0 to 3.46.0.1 (#10871)

## 提交信息

- **序号**：1019 / 4088
- **哈希**：e8582c0f00159638f822c831ddeccd47b01d7f9f
- **短哈希**：e8582c0f0
- **日期**：2024-08-05 09:02:39 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.46.0.0 to 3.46.0.1 (#10871)
- **PR/Issue**：#10871

## 总体目的

这是 Dependabot 自动生成的依赖升级 PR。`org.xerial:sqlite-jdbc` 是 SQLite 的 JDBC 驱动，Iceberg 在 JDBC catalog（用关系型数据库存储 catalog 元数据的实现）中支持 SQLite 作为后端，测试中用其做内存/临时数据库。Dependabot 检测到 3.46.0.0 升级到 3.46.0.1（patch 升级），属于直接生产依赖。本提交的目的是跟进 SQLite JDBC 驱动上游 patch 修复（通常包含 SQLite 引擎小版本修复或驱动 bug 修复）。

## 如何达成设计目的

实现方式是单行版本号替换：在 `gradle/libs.versions.toml` 的版本目录中，把 `sqlite-jdbc = "3.46.0.0"` 改为 `sqlite-jdbc = "3.46.0.1"`。引用该版本变量的库会自动同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 sqlite-jdbc 版本变量从 3.46.0.0 升级到 3.46.0.1。

**工作逻辑**：在 `[versions]` 段中：
```
-sqlite-jdbc = "3.46.0.0"
+sqlite-jdbc = "3.46.0.1"
```
该变量被 `[libraries]` 段中 `sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }` 引用，Gradle 解析时所有依赖 sqlite-jdbc 的模块（主要是 JDBC catalog 测试）会统一使用 3.46.0.1。相邻行（spark-hive34/35、spring-boot、spring-web、testcontainers、tez 等）保持不变。

## 小结

- **成效**：将 SQLite JDBC 驱动从 3.46.0.0 升级到 3.46.0.1，跟进上游 patch 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动。影响 JDBC catalog（含 SQLite 后端）的运行时与测试，不涉及其它 catalog 实现或核心功能。
- **回迁到 1.4.x 的注意事项**：本提交是依赖版本升级，回迁风险极低。需注意：(1) 1.4.x 分支的 `libs.versions.toml` 中 `sqlite-jdbc` 版本可能与 main 不同，cherry-pick 时直接同步版本号即可；(2) 3.46.0.1 是 patch 升级，API/行为兼容，回迁后建议跑一遍 JDBC catalog 测试确认；(3) 若 1.4.x 已有等价或更高版本，可跳过。整体可选回迁。
