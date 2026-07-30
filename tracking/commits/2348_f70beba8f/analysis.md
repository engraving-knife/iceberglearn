# 提交 2348：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#13544)

## 提交信息

- **序号**：2348 / 4088
- **哈希**：f70beba8f69be052747359b3cedb1134f78e401e
- **短哈希**：f70beba8f
- **日期**：2025-07-14 09:21:16 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#13544)
- **PR/Issue**：#13544

## 总体目的

本提交由 Dependabot 自动生成，将 Gradle 插件 `com.gorylenko.gradle-git-properties:gradle-git-properties` 从 2.5.0 升级到 2.5.2。这是一个补丁版本升级。

`gradle-git-properties` 是一个 Gradle 插件，用于在构建时生成 `git.properties` 文件，其中包含当前构建对应的 Git 提交信息（如提交哈希、分支、提交时间等）。这个文件通常被打包到应用中，便于在运行时查询当前构建对应的代码版本，用于版本追踪和问题排查。

Iceberg 项目在 `build.gradle` 的 `buildscript` 依赖中声明了这个插件。从 2.5.0 到 2.5.2 是补丁升级，包含 bug 修复。

## 如何达成设计目的

在 `build.gradle` 的 `buildscript` classpath 依赖中更新插件版本号。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 gradle-git-properties 插件版本号。

**工作逻辑**：将 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.0'` 改为 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.2'`，更新 buildscript 中的插件版本。注意此依赖直接在 `build.gradle` 中声明，而非通过版本目录管理。

## 总结

本提交是 Dependabot 自动生成的依赖升级，将 `gradle-git-properties` 插件从 2.5.0 升级到 2.5.2（补丁版本），获取最新的 bug 修复。该插件用于生成构建版本信息文件 `git.properties`。
