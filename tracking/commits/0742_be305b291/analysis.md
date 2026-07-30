# 提交 0742：Build: Bump org.xerial:sqlite-jdbc from 3.45.2.0 to 3.45.3.0 (#10194)

## 提交信息

- **序号**：0742 / 4088
- **哈希**：be305b2910848194160073009859b8f600ba5ed3
- **短哈希**：be305b291
- **日期**：2024-05-03 12:27:50 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.45.2.0 to 3.45.3.0 (#10194)
- **PR/Issue**：#10194

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 依赖的 `org.xerial:sqlite-jdbc`（SQLite JDBC 驱动）从 `3.45.2.0` 升级到 `3.45.3.0`，属于 semver-patch（补丁版本号）级别的依赖更新。

`sqlite-jdbc` 是 Xerial 维护的纯 Java SQLite JDBC 驱动，Iceberg 在测试中使用它来支持基于 SQLite 的 catalog / JDBC 集成测试场景。补丁版本升级通常包含 bug 修复与小的兼容性改进，不引入破坏性变更。Dependabot 在 PR 描述中提供了 [release notes](https://github.com/xerial/sqlite-jdbc/releases)、[changelog](https://github.com/xerial/sqlite-jdbc/blob/master/CHANGELOG) 与 [commits 对比](https://github.com/xerial/sqlite-jdbc/compare/3.45.2.0...3.45.3.0) 供维护者审阅。Iceberg 维护者合并此 PR 即表示认可升级在 Iceberg 使用范围内是兼容的。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `sqlite-jdbc = "3.45.2.0"` 改为 `sqlite-jdbc = "3.45.3.0"`。所有引用该版本常量的模块会自动解析到新版本，无需逐个修改各模块的构建脚本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `org.xerial:sqlite-jdbc` 的版本从 3.45.2.0 升级到 3.45.3.0。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区，原行 `sqlite-jdbc = "3.45.2.0"` 被改为 `sqlite-jdbc = "3.45.3.0"`。该版本常量控制 Iceberg 构建中引用的 SQLite JDBC 驱动 artifact 版本，升级后相关测试会使用新版本的驱动。

## 小结

- **成效**：将 SQLite JDBC 驱动从 3.45.2.0 升级到 3.45.3.0，获取补丁版本的 bug 修复与改进。
- **影响范围**：仅构建依赖版本，不涉及任何源代码变更。影响范围限于使用 sqlite-jdbc 的测试场景。
- **回迁注意事项**：无技术风险。补丁版本升级向后兼容，回迁到 1.4.x 只需同步 `gradle/libs.versions.toml` 中的版本行即可。若 1.4.x 已有不同版本的 sqlite-jdbc，以较高版本为准即可。
