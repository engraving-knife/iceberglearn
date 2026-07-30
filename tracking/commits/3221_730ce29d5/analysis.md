# 提交 3221：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#15263)

## 提交信息

- **序号**：3221 / 4088
- **哈希**：730ce29d5cd722b1751a1984d9eabb68542eba39
- **短哈希**：730ce29d5
- **日期**：2026-02-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#15263)
- **PR/Issue**：#15263

## 总体目的

这是一次 dependabot 发起的依赖版本升级，针对 Gradle 插件 `com.gorylenko.gradle-git-properties:gradle-git-properties`。该插件用于在构建时生成 `git.properties` 资源文件（包含 git 提交 hash、分支、提交时间等元信息），供运行时读取以暴露构建来源信息。在 Iceberg 的根 `build.gradle` 中，该插件被加在 `buildscript` 的 `classpath` 中，并通过 `apply plugin: 'com.gorylenko.gradle-git-properties'` 与 `gitProperties { ... }` 配置块定制生成名为 `iceberg-build.properties` 的资源，输出到 `${rootDir}/build` 目录，用于把构建所对应的 git 状态写入产物。

本次把该插件从 `2.5.4` 提升到 `2.5.5`，属于 `semver-patch`（修订号）升级。Gradle 插件的 patch 升级通常为缺陷修复与小改进，不改变插件 DSL 与任务行为。升级动机是跟进上游修复、保持构建插件新鲜。

## 如何达成设计目的

作为 dependabot 自动化升级，整体思路是在根 `build.gradle` 的 `buildscript.dependencies.classpath` 中把插件版本字符串从 `2.5.4` 改为 `2.5.5`。该插件仅在 `buildscript` classpath 中声明版本，单点修改即生效，不涉及 `gitProperties` 配置块本身。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 `gradle-git-properties` 插件版本从 2.5.4 升到 2.5.5。

**工作逻辑**：
在根 `build.gradle` 的 `buildscript { dependencies { classpath ... } }` 块中，将 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.4'` 修改为 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.5'`。该 classpath 声明决定了构建脚本可用的插件版本；下方的 `apply plugin` 与 `gitProperties { gitPropertiesName = 'iceberg-build.properties'; ... }` 配置均不变。修订号升级预期不改变插件任务行为，对生成的 `iceberg-build.properties` 内容与构建流程无影响，仅获得 2.5.5 自带的修复。

## 总结

本提交由 dependabot 将根 `build.gradle` 中 `buildscript` classpath 上的 `gradle-git-properties` 插件版本从 2.5.4 升级到 2.5.5（修订号升级，插件 DSL 兼容），用于跟进上游修复、保持构建插件新鲜；改动为单点版本号替换，不影响 `gitProperties` 配置或生成的构建元信息内容。
