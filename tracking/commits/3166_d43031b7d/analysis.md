# 提交 3166：Build: Bump gradle-git-version to 4.2.0 (#15157)

## 提交信息

- **序号**：3166 / 4088
- **哈希**：d43031b7d704670386624bd87cafd0ca5bfea592
- **短哈希**：d43031b7d
- **日期**：2026-01-27
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Bump gradle-git-version to 4.2.0 (#15157)
- **PR/Issue**：#15157

## 总体目的

这是一次纯依赖升级提交，由 Eduard Tudenhoefner 通过 PR #15157 合入。`gradle-git-version` 是 Palantir 提供的一个 Gradle 插件（`com.palantir.gradle.gitversion:gradle-git-version`），Iceberg 在构建脚本 `build.gradle` 的 `buildscript` 依赖中引入它，用于在构建期从 git 仓库状态（如 git describe、提交计数、分支名等）推导出项目的版本号字符串。Iceberg 的发布流程依赖该插件为构建产物打上与 git 标签一致的版本。

本次提交将该插件从 `3.4.0` 升级到 `4.2.0`，跨越了一个大版本（3.x → 4.x）。Palantir 的 gradle-git-version 在 4.x 系列对其内部实现和对外 API 做了较大调整（例如对 git describe 的处理、ref 解析逻辑等），因此这属于一次主版本升级，理论上可能带来行为变化，但 Iceberg 此处仅更新了 classpath 上的版本声明，并未调整任何调用代码或构建逻辑，说明升级是兼容的，仓库现有构建配置在新版本下仍可正常工作。

升级动机通常包括：获取上游 bug 修复与性能改进、保持与最新 Gradle 版本的兼容性、跟随依赖治理策略（Iceberg 社区有定期升级构建工具链的惯例，例如同一天还提交了 gradle-baseline-java 的升级）。这类构建依赖的持续更新有助于避免技术债积累，并降低未来升级到更高版本时的难度。

## 如何达成设计目的

改动极为聚焦：仅在 `build.gradle` 的 `buildscript.dependencies` 依赖块中将 `gradle-git-version` 的版本字符串从 `3.4.0` 改为 `4.2.0`。无需改动任何使用该插件的代码，插件的应用方式与版本号推导逻辑保持不变。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 gradle-git-version 插件版本。

**工作逻辑**：
在 `buildscript` 依赖声明中，`classpath 'com.palantir.gradle.gitversion:gradle-git-version:3.4.0'` 被替换为 `classpath 'com.palantir.gradle.gitversion:gradle-git-version:4.2.0'`。该依赖加载于构建脚本类路径，供后续 `apply plugin` 使用。语义版本上属于从 3.x 到 4.x 的主版本跨越，但由于 Iceberg 仅消费该插件的标准版本推导能力，未触及任何被废弃或移除的 API，因此升级是平滑的。

## 总结

本次提交将 Iceberg 构建脚本依赖的 `gradle-git-version` 插件从 3.4.0 升级至 4.2.0，属于构建工具链的例行维护。升级为构建过程带来上游修复与改进，同时确保版本号推导能力与最新的 git/Gradle 环境保持兼容，对 Iceberg 的功能代码无任何影响。
