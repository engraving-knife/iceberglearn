# 提交 0158：Build: Bump org.xerial:sqlite-jdbc from 3.43.2.1 to 3.44.0.0 (#9051)

## 提交信息

- **序号**：0158 / 4088
- **哈希**：fd00207cdddf179828e3fde788d3f68933fea061
- **短哈希**：fd00207cd
- **日期**：2023-11-13 14:26:25 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.43.2.1 to 3.44.0.0 (#9051)
- **PR/Issue**：#9051

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Xerial 的 SQLite JDBC 驱动从 3.43.2.1 升级到 3.44.0.0。本次为 `version-update:semver-minor` 次版本升级。

`org.xerial:sqlite-jdbc` 是 Iceberg 测试栈中使用的纯 Java SQLite JDBC 驱动。Iceberg 在测试中需要一个轻量、可嵌入、无需外部服务的关系型数据库来验证 JDBC Catalog 等行为，SQLite 因其零部署、单文件、进程内运行的特性非常适合这一角色。3.44.0.0 对应 SQLite 上游 3.44 版本的引擎，通常包含 SQL 语法增强（例如新的 PRAGMA、聚合函数、JSON 改进等）、性能修复以及 JNI 层的 native 库更新。

升级的意义在于：让测试中使用的 SQLite 引擎与上游最新稳定版保持一致，避免测试覆盖的 SQL 行为与生产环境中可能遇到的较新 SQLite 版本产生偏差。次版本升级虽然通常向后兼容，但 native 库更新会牵涉多平台二进制（sqlite-jdbc 打包了多平台的 native binary），dependabot 升级后 CI 会在各 OS 上跑测试以确认 native 加载正常。

## 如何达成设计目的

通过修改 Gradle 版本目录 [`gradle/libs.versions.toml`](../../../gradle/libs.versions.toml) 中 `sqlite-jdbc` 版本别名，从 `3.43.2.1` 改为 `3.44.0.0`。该别名在 `[libraries]` 段被 `sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }` 引用，供测试模块（如 JDBC 相关测试）以 `testImplementation` 形式引入。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Xerial SQLite JDBC 驱动从 3.43.2.1 升级到 3.44.0.0，更新测试用的嵌入式数据库引擎。

**工作逻辑**：在 `[versions]` 段将 `sqlite-jdbc = "3.43.2.1"` 修改为 `sqlite-jdbc = "3.44.0.0"`（位于 spring 系列版本之后、testcontainers 之前，约第 85 行）。Xerial 的 sqlite-jdbc 制品内嵌了对应 SQLite 引擎版本的 native 二进制库（覆盖 Linux/macOS/Windows 多架构），因此该版本号同时决定了测试中实际使用的 SQLite C 引擎版本。升级后，JDBC Catalog 等测试场景将基于 SQLite 3.44 引擎执行 SQL，覆盖更新的 SQL 行为。

## 小结

通过 dependabot 升级 Xerial SQLite JDBC 到 3.44.0.0，使 Iceberg 测试栈使用的嵌入式数据库引擎与上游最新稳定版本对齐，属于测试依赖的常规维护升级。
