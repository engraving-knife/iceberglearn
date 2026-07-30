# 提交 2167：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#13144)

## 提交信息

- **序号**：2167 / 4088
- **哈希**：d8d9753eb69bbefeb6cd4ac4d085ee2f4285a345
- **短哈希**：d8d9753eb
- **日期**：2025-05-27 14:26:12 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#13144)
- **PR/Issue**：#13144

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 `com.palantir.gradle.gitversion:gradle-git-version` 插件从 3.2.0 升级到 3.3.0。该插件用于在 Gradle 构建中基于 Git 信息生成版本号。此次升级为 semver-minor 版本升级（3.2.0 -> 3.3.0），可能包含新功能和改进。Dependabot 自动跟踪依赖的最新版本并创建 PR，保持依赖的及时更新有助于获取新功能、bug 修复和安全补丁。

## 如何达成设计目的

- 在 `build.gradle` 的 `buildscript` 依赖中，将 `gradle-git-version` 插件的版本号从 `3.2.0` 修改为 `3.3.0`。

## 修改详情

### `build.gradle` (修改, +1/-1 lines)

**修改目的**：升级 gradle-git-version 插件版本。

**工作逻辑**：在 `buildscript.dependencies` 中，将 `classpath 'com.palantir.gradle.gitversion:gradle-git-version:3.2.0'` 修改为 `classpath 'com.palantir.gradle.gitversion:gradle-git-version:3.3.0'`。

## 总结

这是一个 Dependabot 自动生成的依赖升级提交，将 gradle-git-version 插件从 3.2.0 升级到 3.3.0，属于常规的依赖维护工作。
