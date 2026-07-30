# 提交 0153：Build: Bump orc from 1.9.1 to 1.9.2 (#9045)

## 提交信息

- **序号**：0153 / 4088
- **哈希**：7ece5faaf839dfe8adb113b8b2fb1e94245f01cf
- **短哈希**：7ece5faaf
- **日期**：2023-11-13 10:00:17 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump orc from 1.9.1 to 1.9.2 (#9045)
- **PR/Issue**：#9045

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 Apache ORC 库从 `1.9.1` 升级到 `1.9.2`，属于 `version-update:semver-patch` 级别的依赖更新。Dependabot 在 PR 描述中说明本次一并更新两个共享 `orc` 版本引用的 artifact：`org.apache.orc:orc-core` 从 1.9.1 到 1.9.2，`org.apache.orc:orc-tools` 从 1.9.1 到 1.9.2。

`org.apache.orc:orc-core` 是 Apache ORC 列式文件格式的核心读写库，Iceberg 的 `iceberg-orc` 模块（ORC 格式读写器）和 `hive3` 模块（Hive 3 集成）都依赖它来读写 ORC 数据文件。`org.apache.orc:orc-tools` 是 ORC 的辅助工具库，在 Iceberg 中作为测试依赖使用。ORC 是 Iceberg 支持的三种主要开源列式格式之一（另两种是 Parquet 与 Avro），其版本升级直接影响 Iceberg 对 ORC 文件的读写正确性与性能。

本次升级是 patch 级别（1.9.x 系列），按 Dependabot 分类属于 `version-update:semver-patch`，意味着只有兼容性补丁，不包含破坏性变更，风险较低。1.9.2 通常包含自 1.9.1 以来 ORC 社区积累的 bug 修复、稳定性改进与潜在的安全补丁。Iceberg 维护者合并此 PR 即表示认可升级在 Iceberg 使用范围内是兼容的。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `orc = "1.9.1"` 改为 `orc = "1.9.2"`。由于 `orc-core` 和 `orc-tools` 两个 artifact 都通过 `version.ref = "orc"` 共享该版本常量，一次修改即可同步升级两个 artifact，无需逐个修改各模块的 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `org.apache.orc:orc-core` 与 `org.apache.orc:orc-tools` 的共享版本从 1.9.1 升级到 1.9.2。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（第 71 行附近），原行 `orc = "1.9.1"` 被改为 `orc = "1.9.2"`。该版本常量在版本目录中分别被 `orc-core = { module = "org.apache.orc:orc-core", version.ref = "orc" }`（第 141 行附近）和 `orc-tools = { module = "org.apache.orc:orc-tools", version.ref = "orc" }`（第 190 行附近）引用。在根 `build.gradle` 中，`iceberg-orc` 与 `hive3` 模块通过 `"${libs.orc.core.get().module}:${libs.versions.orc.get()}:nohive"` 形式引用 `orc-core` 的 `nohive` 变体（避免拉入 Hive 依赖），`orc-tools` 则作为测试依赖引入。升级后，这些模块会解析到 ORC 1.9.2，获取该版本的修复与改进。

## 小结

该提交由 Dependabot 自动将 Apache ORC（`orc-core` 与 `orc-tools`）从 1.9.1 升级到 1.9.2，使 Iceberg 的 ORC 读写器与 Hive3 集成跟进 ORC 最新 patch 版本，获取 bug 修复与稳定性改进。
