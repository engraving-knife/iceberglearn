# 提交 0803：Bump guava from 33.2.0-jre to 33.2.1-jre

## 提交信息

| 字段 | 值 |
|------|------|
| 序号 | 0803 |
| 完整哈希 | ee11de91c94f742e3c8e6414a152d753a36384ff |
| 短哈希 | ee11de91c |
| 日期 | 2024-06-03 08:09:13 +0200 |
| 作者 | dependabot[bot] |
| 提交说明 | Build: Bump guava from 33.2.0-jre to 33.2.1-jre (#10414) |
| PR/Issue | #10414 |
| 修改文件数 | 1 |
| 增/删行数 | +1 / -1 |

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，用于将 Google Guava 库从 33.2.0-jre 升级到 33.2.1-jre。Guava 是 Iceberg 项目核心代码中广泛使用的基础工具库（提供集合、缓存、并发、I/O、哈希等工具），同时其 `guava-testlib` 也用于测试。本次升级属于补丁版本（patch）升级，目的是获取上游的缺陷修复与小改进，保持核心依赖的稳定性与安全性。

## 如何达成设计目的

Iceberg 使用 Gradle 版本目录（Version Catalog）集中管理依赖版本，所有版本声明在 `gradle/libs.versions.toml` 中。Dependabot 识别到 `guava` 别名对应的版本 `33.2.0-jre` 存在更新的 `33.2.1-jre`，于是直接修改版本目录中的版本字符串。Guava 在版本目录中以单一别名 `guava = "33.2.0-jre"` 声明，并被 `[libraries]` 段中的 `guava` 与 `guava-testlib` 两个库条目通过 `version.ref = "guava"` 共同引用，因此一处修改即可同时升级主库与测试库。

提交信息明确指出本次更新涉及两个 artifact：`com.google.guava:guava` 与 `com.google.guava:guava-testlib`，二者均从 33.2.0-jre 升至 33.2.1-jre。由于是补丁升级，按照 Guava 的语义化版本约定，仅包含缺陷修复，不引入 API 破坏性变更，对 Iceberg 现有代码完全兼容。

## 修改详情

### `gradle/libs.versions.toml`

Gradle 版本目录文件，集中声明项目所有第三方依赖的版本。本次修改仅一行：

```diff
-guava = "33.2.0-jre"
+guava = "33.2.1-jre"
```

将 `guava` 版本别名从 `33.2.0-jre` 升级到 `33.2.1-jre`。由于 `guava` 与 `guava-testlib` 两个库条目均通过 `version.ref = "guava"` 引用同一版本别名，因此这一处修改会同时把运行时 Guava 与测试用 Guava testlib 升级到 33.2.1-jre。各模块的 `build.gradle` 无需改动，依赖解析会自动应用新版本。

## 小结

- **成效**：核心工具库 Guava 升级到 33.2.1-jre，获得上游补丁修复，提升核心代码运行时的稳定性与安全性。
- **影响范围**：Guava 是 Iceberg 核心代码的传递依赖，影响所有使用 Guava 工具类的主模块与测试模块；但由于是补丁升级，行为不变。
- **回迁到 1.4.x 分支的注意事项**：回迁风险较低。需确认 1.4.x 分支的 `gradle/libs.versions.toml` 中 `guava` 当前版本；Guava 33.2.1-jre 要求 JDK 8+，与 Iceberg 1.4.x 的 JDK 基线兼容。由于 Guava 在 Iceberg 中使用面极广，回迁后建议运行完整测试套件与集成测试验证无回归。补丁升级通常可直接 cherry-pick。
