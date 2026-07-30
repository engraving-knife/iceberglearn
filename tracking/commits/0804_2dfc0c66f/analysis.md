# 提交 0804：Bump org.xerial:sqlite-jdbc from 3.45.3.0 to 3.46.0.0

## 提交信息

| 字段 | 值 |
|------|------|
| 序号 | 0804 |
| 完整哈希 | 2dfc0c66fa9e64344bc21d3d820d80a99f7ffd9c |
| 短哈希 | 2dfc0c66f |
| 日期 | 2024-06-03 08:09:39 +0200 |
| 作者 | dependabot[bot] |
| 提交说明 | Build: Bump org.xerial:sqlite-jdbc from 3.45.3.0 to 3.46.0.0 (#10415) |
| PR/Issue | #10415 |
| 修改文件数 | 1 |
| 增/删行数 | +1 / -1 |

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，用于将 SQLite JDBC 驱动 `org.xerial:sqlite-jdbc` 从 3.45.3.0 升级到 3.46.0.0。sqlite-jdbc 是 Iceberg 项目中用于测试与本地开发的嵌入式关系型数据库 JDBC 驱动（Iceberg 的 `iceberg-sqlite` 等模块在测试或本地 Catalog 场景中使用 SQLite 作为轻量级后端）。本次升级属于 minor 版本升级，目的是跟进上游 SQLite 引擎与 JDBC 驱动的新版本，获取新特性与缺陷修复。

## 如何达成设计目的

Iceberg 使用 Gradle 版本目录（Version Catalog）集中管理依赖版本，所有版本声明在 `gradle/libs.versions.toml` 中。Dependabot 识别到 `sqlite-jdbc` 别名对应的版本 `3.45.3.0` 存在更新的 `3.46.0.0`，于是直接修改版本目录中的版本字符串。各模块通过 `libs.sqlite.jdbc`（或类似别名引用）引入该驱动，因此一处修改即可全局生效。

sqlite-jdbc 3.46.0.0 对应上游 SQLite 3.46.x 引擎版本，通常包含 SQLite 引擎本身的查询优化与修复，以及 JDBC 驱动层的改进。由于 Iceberg 主要在测试与本地场景使用 SQLite，且 minor 升级一般保持 JDBC API 兼容，因此对 Iceberg 现有代码影响有限。提交信息中标注依赖类型为 `direct:production`、更新类型为 `version-update:semver-minor`。

## 修改详情

### `gradle/libs.versions.toml`

Gradle 版本目录文件，集中声明项目所有第三方依赖的版本。本次修改仅一行：

```diff
-sqlite-jdbc = "3.45.3.0"
+sqlite-jdbc = "3.46.0.0"
```

将 `sqlite-jdbc` 版本别名从 `3.45.3.0` 升级到 `3.46.0.0`。该别名在版本目录的 `[libraries]` 段被引用为 `sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }`（或类似形式），相关模块通过 `testImplementation` 或 `implementation` 引入，修改后自动应用新版本。

## 小结

- **成效**：SQLite JDBC 驱动升级到 3.46.0.0，获得上游 SQLite 引擎与驱动层的改进，提升测试与本地场景的稳定性与功能。
- **影响范围**：主要影响使用 SQLite 的测试模块与本地 Catalog 场景，不影响 Iceberg 主产物的核心表格式逻辑与生产部署行为。
- **回迁到 1.4.x 分支的注意事项**：回迁风险较低，但需稍谨慎。需确认 1.4.x 分支的 `gradle/libs.versions.toml` 中 `sqlite-jdbc` 当前版本；sqlite-jdbc 3.46.0.0 要求 JDK 8+，与 Iceberg 1.4.x 的 JDK 基线兼容。由于是 minor 升级且涉及嵌入式数据库引擎版本，回迁后建议运行涉及 SQLite 的测试用例验证无回归（如 SQL 行为差异、驱动初始化差异等）。通常可直接 cherry-pick。
