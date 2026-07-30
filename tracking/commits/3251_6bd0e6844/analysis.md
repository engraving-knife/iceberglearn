# 提交 3251：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#15322)

## 提交信息

- **序号**：3251 / 4088
- **哈希**：6bd0e6844cccad307fb974fd59525cd6b6f4b6ef
- **短哈希**：6bd0e6844
- **日期**：2026-02-14
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#15322)
- **PR/Issue**：#15322

## 总体目的

`com.gorylenko.gradle-git-properties:gradle-git-properties` 是一个 Gradle 插件，用于在构建过程中生成 `git.properties` 文件，该文件包含当前构建对应的 Git 提交信息（如 commit hash、分支名、提交时间等）。在 Iceberg 项目中，该插件配置在根 `build.gradle` 的 `buildscript` classpath 中，用于在构建产物中嵌入版本溯源信息，便于运行时追踪发布包对应的代码版本。该依赖属于 `direct:production` 类型。

本次提交由 Dependabot 自动生成，将该插件从 `2.5.5` 升级到 `2.5.7`。根据语义版本规范，这是一个 patch 级别升级（`version-update:semver-patch`），即修订号从 5 增至 7（跳过 2.5.6）。patch 升级通常只包含 bug 修复和小改进，不引入破坏性变更，预期对构建产物的 `git.properties` 生成逻辑无影响。

## 如何达成设计目的

仅修改根构建文件 `build.gradle` 中 `buildscript` classpath 的插件版本声明，从 `2.5.5` 改为 `2.5.7`。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 gradle-git-properties 插件版本从 2.5.5 升级至 2.5.7。

**工作逻辑**：
在 `build.gradle` 的 `buildscript` 块第 37 行，将 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.5'` 修改为 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.7'`。该插件在构建时生成 git.properties 资源文件，升级后获得 bug 修复。作为 patch 级别升级，不涉及插件 API 变更。值得注意的是该依赖在 `build.gradle` 中直接声明，而非使用版本目录（`libs.versions.toml`），因此修改位置不同其他依赖升级。

## 总结

本次提交是 Dependabot 自动执行的 gradle-git-properties 插件 patch 升级（2.5.5 → 2.5.7），保持构建时 Git 信息生成插件处于最新补丁版本，确保 `git.properties` 文件正确生成版本溯源信息，对项目功能无影响。
