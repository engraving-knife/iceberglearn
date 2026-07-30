# 提交 3516：Revert "Build: bump shadow-gradle-plugin to 9.4.1 (#15835)" (#15941)

## 提交信息

- **序号**：3516 / 4088
- **哈希**：9242be6ddbae41965afb5ed5f1fbf6be163e3700
- **短哈希**：9242be6dd
- **日期**：2026-04-11 03:08:35 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Revert "Build: bump shadow-gradle-plugin to 9.4.1 (#15835)" (#15941)
- **PR/Issue**：#15941（回退 #15835）

## 总体目的

这个提交是为了回退此前将 `shadow-gradle-plugin` 从 8.3.10 升级到 9.4.1 的改动（PR #15835）。显然那次升级引入了构建或发布流程的问题，因此维护者选择紧急回退到稳定的 8.3.10 版本，以保证构建链路的可靠性。

回退操作本身通常意味着新版本与现有构建脚本或发布插件存在不兼容。`shadow-gradle-plugin` 9.x 是一个主要版本升级，发布组件 API 发生了破坏性变更，回退是最稳妥的短期处理方式。

## 如何达成设计目的

提交者直接执行 `git revert`，将 `build.gradle` 中的插件版本从 9.4.1 改回 8.3.10，并将 `deploy.gradle` 中发布组件的写法从 `components.shadow` 改回 `project.shadow.component(it)`。这种写法差异正是两个版本 API 变化的体现。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 shadow 插件版本回退到 8.3.10。

**工作逻辑**：
```groovy
-    classpath 'com.gradleup.shadow:shadow-gradle-plugin:9.4.1'
+    classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.10'
```
直接在 `buildscript.dependencies` 中将插件 classpath 版本号还原。

### `deploy.gradle` (+1/-1 lines)

**修改目的**：还原 shadow 组件发布方式以适配 8.x 的 API。

**工作逻辑**：
```groovy
-              from components.shadow
+              project.shadow.component(it)
```
9.x 版本使用 `components.shadow` 直接引用组件，而 8.x 版本需要通过 `project.shadow.component(it)` 来注册 shadow 组件到发布配置中。回退后必须使用旧 API 才能正常工作。

## 总结

这是一个紧急回退提交，目的是撤销引入 shadow-gradle-plugin 9.4.1 的升级，因为它与现有发布脚本不兼容。通过同时还原插件版本号和 `deploy.gradle` 中的组件发布写法，恢复到 8.3.10 的稳定状态，保障构建与发布流程正常运作。
