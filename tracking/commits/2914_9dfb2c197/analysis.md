# 提交 2914：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#14664)

## 提交信息

- **序号**：2914 / 4088
- **哈希**：9dfb2c197197c7cd9928f8ef4a4c5d663ba2c846
- **短哈希**：9dfb2c197
- **日期**：2025-11-22 23:26:14 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties from 2.5.2 to 2.5.4
- **PR/Issue**：#14664

## 总体目的

这是 dependabot 自动生成的依赖升级提交。`gradle-git-properties` 是一个 Gradle 插件，用于在构建产物中生成 `git.properties` 文件，包含当前构建对应的 Git 提交信息（如 commit hash、分支、提交时间等）。Iceberg 项目使用此插件在构建产物中嵌入 Git 元数据，便于追踪构建来源。此次从 2.5.2 升级到 2.5.4，属于 semver-patch 级别更新，包含 bug 修复。

## 如何达成设计目的

通过修改项目根 `build.gradle` 文件中 buildscript classpath 的插件版本声明完成升级。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 gradle-git-properties 插件从 2.5.2 升级到 2.5.4。

**工作逻辑**：将 buildscript dependencies 中的 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.2'` 修改为 `2.5.4`，patch 级别升级，向后兼容。

## 总结

本提交是 gradle-git-properties 插件的 patch 版本升级（2.5.2 到 2.5.4），预期向后兼容，包含 bug 修复。该插件用于生成构建产物的 Git 元数据，对构建可追溯性有辅助作用。
