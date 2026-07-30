# 提交 2272：Build: Bump calcite from 1.39.0 to 1.40.0 (#13203)

## 提交信息

- **序号**：2272 / 4088
- **哈希**：ff5d1a1bc64215984f2ac451c9ad1fd314742eb2
- **短哈希**：ff5d1a1bc
- **日期**：2025-06-25 15:45:03 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump calcite from 1.39.0 to 1.40.0 (#13203)
- **PR/Issue**：#13203

## 总体目的

本提交由 Dependabot 自动生成，将 Apache Calcite 依赖版本从 1.39.0 升级到 1.40.0。Apache Calcite 是一个动态数据管理框架，Iceberg 项目使用其 `calcite-core` 和 `calcite-druid` 组件（主要用于 Spark 等 module 的 SQL 解析与优化）。

这是一次次要版本（semver-minor）升级，按照语义化版本约定，1.40.0 相对 1.39.0 应当保持向后兼容，主要包含新功能、改进和缺陷修复。定期升级依赖有助于获取上游的 bug 修复、安全补丁和性能改进，同时避免技术债务积累。Dependabot 自动化此过程，降低维护成本。

## 如何达成设计目的

- 修改 `gradle/libs.versions.toml` 中 calcite 版本目录条目，将 `calcite = "1.39.0"` 改为 `calcite = "1.40.0"`。
- 由于 Iceberg 使用 Gradle 版本目录（Version Catalog）统一管理依赖版本，所有引用 `${libs.calcite}` 的模块会自动应用新版本，无需逐模块修改。
- 通过版本目录集中管理，一次修改即覆盖 `calcite-core` 和 `calcite-druid` 等所有 calcite 子组件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将版本目录中 calcite 的版本号从 1.39.0 升级到 1.40.0。

**工作逻辑**：`libs.versions.toml` 是 Gradle 版本目录文件，其中 `calcite = "1.39.0"` 这一行定义了 calcite 版本变量。改为 `1.40.0` 后，所有通过 `libs.calcite.core`、`libs.calcite.druid` 等引用该变量的依赖坐标都会自动解析为新版本。版本目录机制确保项目中 calcite 版本的一致性，避免不同模块使用不同版本导致的冲突。

## 总结

本提交是一次常规的依赖版本升级，通过 Dependabot 自动化完成，将 Apache Calcite 从 1.39.0 升级到 1.40.0。利用 Gradle 版本目录机制，仅需一行修改即可完成全项目范围的版本更新。这类升级有助于保持依赖的时效性，获取上游改进。
