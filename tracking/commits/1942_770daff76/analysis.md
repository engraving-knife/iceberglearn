# 提交 1942：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#12687)

## 提交信息

- **序号**：1942 / 4088
- **哈希**：770daff764672ccdf9240404c9026fdbd0476c54
- **短哈希**：770daff76
- **日期**：2025-04-01 09:05:36 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#12687)
- **PR/Issue**：#12687

## 总体目的

这是由 Dependabot 自动生成的 Gradle 插件依赖升级提交，将 `com.palantir.gradle.gitversion:gradle-git-version` 从 3.1.0 升级到 3.2.0。该 Gradle 插件用于从 Git 仓库状态推导版本号（如基于 git tag 计算语义版本），Iceberg 构建脚本用它来生成构建产物的版本字符串。

从 3.1.0 到 3.2.0 是一个 semver-minor（次版本）升级，可能包含新功能与改进，按 semver 约定应保持向后兼容。升级目的是保持构建插件处于最新版本，获取改进与修复。

## 如何达成设计目的

在根 `build.gradle` 的 `buildscript.dependencies` classpath 中将插件版本从 `3.1.0` 修改为 `3.2.0`，Gradle 会按此版本解析并加载插件。

## 修改详情

### `build.gradle` (修改, +1/-1 lines)

**修改目的**：升级 gradle-git-version 插件版本。

**工作逻辑**：在 `buildscript { dependencies { classpath ... } }` 中将 `classpath 'com.palantir.gradle.gitversion:gradle-git-version:3.1.0'` 修改为 `classpath 'com.palantir.gradle.gitversion:gradle-git-version:3.2.0'`。该插件在构建时基于 git 状态生成版本号。

## 总结

本次提交为 Dependabot 自动执行的 Gradle 插件升级（gradle-git-version 3.1.0 → 3.2.0），仅修改根 `build.gradle` 的 buildscript classpath 版本声明。属于例行的构建依赖维护。
