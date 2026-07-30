# 提交 0022：Build: Bump org.immutables:value from 2.9.2 to 2.10.0 (#8736)

## 提交信息

- **序号**：0022 / 4088
- **哈希**：2504c5810821f83061ec5688c2b6aee082085ecc
- **短哈希**：2504c5810
- **日期**：2023-10-09 12:32:39 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.9.2 to 2.10.0 (#8736)
- **PR/Issue**：#8736

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 Iceberg 使用的 `org.immutables:value`（即 Immutables 注解处理库）从 `2.9.2` 升级到 `2.10.0`，属于 semver-minor 级别的版本提升。

Immutables 是 Iceberg 核心大量使用的关键依赖：Iceberg 通过 `@Value.Immutable`、`@Value.Style` 等注解在编译期生成不可变值类（如各种 `Base*` 接口的 `Immutable*` 实现、view/actions 模块的 DTO 等）。`2.10.0` 版本相对于 `2.9.2` 的一个关键变化是修复了上游 issue [immutables/immutables#1474](https://github.com/immutables/immutables/pull/1474)——允许通过字符串形式（`visibilityString` / `builderVisibilityString`）指定 `@Value.Style` 的可见性，而不是必须引用 `ImplementationVisibility` / `BuilderVisibility` 枚举常量。这一点直接催生了紧随其后的提交 0024（Core: Use visibility string instead of enum），本提交为它提供了版本前提。

此外，例行升级还能获得 `2.9.2 → 2.10.0` 之间的常规 bug 修复与稳定性改进。Dependabot 元数据将其标记为 `direct:production`、`version-update:semver-minor`，影响范围是全仓库所有使用 Immutables 生成代码的模块（core、各 catalog/actions 等），但由于是注解处理器在编译期工作，运行时行为变化极小，升级风险低。

## 如何达成设计目的

改动集中在唯一的版本目录文件 `gradle/libs.versions.toml`：将 `immutables-value` 这个版本别名从 `"2.9.2"` 改为 `"2.10.0"`。该别名在 Gradle 构建脚本中被引用（如 `immutables-value` 坐标），升级后所有依赖该别名的模块会统一拉取新版本，无需逐模块修改。这是一行版本号变更，设计上依赖 Gradle 版本目录（Version Catalog）的集中式依赖管理机制。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Immutables 库的版本别名从 `2.9.2` 提升到 `2.10.0`，使全仓库所有使用 Immutables 注解处理的模块统一升级到新版本。

**工作逻辑**：原行 `immutables-value = "2.9.2"` 改为 `immutables-value = "2.10.0"`。该别名位于 `libs.versions.toml` 的 `[versions]` 段，配合同文件 `[libraries]` 段中的 `immutables-value = { module = "org.immutables:value", version.ref = "immutables-value" }`（Iceberg 既有的引用约定）使用，下游模块通过 `libs.immutables.value` 引入依赖。升级后，编译期生成的不可变类将基于 Immutables 2.10.0 的代码生成器，为后续采用 `visibilityString` 字符串风格（提交 0024）扫清版本障碍。

## 小结

通过 Dependabot 将 Immutables 从 2.9.2 升级到 2.10.0，既获取上游修复与改进，也为后续用字符串形式配置 `@Value.Style` 可见性（消除消费方编译告警）提供了版本前提。
