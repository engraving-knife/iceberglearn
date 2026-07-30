# 提交 0942：Build: Bump net.snowflake:snowflake-jdbc from 3.16.1 to 3.17.0 (#10696)

## 提交信息

- **序号**：0942 / 4088
- **哈希**：cc1a1e495548d1e1ae7e0c0922824d6f2e35e926
- **短哈希**：cc1a1e495
- **日期**：2024-07-17 09:08:01 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.16.1 to 3.17.0 (#10696)
- **PR/Issue**：#10696

## 总体目的

本提交由 Dependabot 自动生成，目的是将 Iceberg 项目所依赖的 Snowflake JDBC 驱动 `net.snowflake:snowflake-jdbc` 从 3.16.1 升级到 3.17.0。这是一次 semver-minor（次要版本）升级，属于项目维护类工作中"依赖版本巡检与升级"的一环。

Snowflake JDBC 是 Iceberg 与 Snowflake 数据仓库交互（例如通过 Snowflake catalog 读写 Iceberg 表）所必需的驱动。保持驱动版本与上游同步有助于获取新特性、缺陷修复以及安全补丁，避免长期滞后后难以一次性升级。Dependabot 通过扫描 `gradle/libs.versions.toml` 中声明的版本，发现 3.17.0 已发布，因此发起本 PR 将版本号推进。

## 如何达成设计目的

实现方式非常直接：仅修改 Iceberg 集中管理依赖版本的 Gradle 版本目录文件 `gradle/libs.versions.toml`，将其中 `snowflake-jdbc` 的版本字符串从 `3.16.1` 改为 `3.17.0`。所有引用该版本键的模块（如 snowflake 模块、相关测试模块）都会在下次构建时自动解析到新版本，无需逐模块修改 `build.gradle`。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Snowflake JDBC 驱动版本由 3.16.1 升级到 3.17.0。

**工作逻辑**：Gradle 版本目录（Version Catalog）采用单一 TOML 文件集中声明所有依赖版本，键值形如 `snowflake-jdbc = "3.16.1"`。改动仅替换该行字符串：

```diff
-snowflake-jdbc = "3.16.1"
+snowflake-jdbc = "3.17.0"
```

下游模块通过 `libs.snowflake.jdbc` 引用此键，构建系统在依赖解析阶段会统一使用 3.17.0。

## 小结

- **成效**：完成 Snowflake JDBC 驱动从 3.16.1 到 3.17.0 的版本升级，与上游发布保持同步。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，无源代码、构建脚本结构或文档变更。
- **回迁到 1.4.x 的注意事项**：属于低风险依赖升级，原则上可回迁到 1.4.x；但回迁前应确认 1.4.x 分支当前所用的 Snowflake JDBC 基线版本以及该分支是否仍在使用 3.16.x，并验证 3.17.0 与 1.4.x 测试套件（特别是 Snowflake 相关集成测试）的兼容性。若 1.4.x 已单独维护依赖基线，应按 1.4.x 的依赖升级策略决定是否合并。
