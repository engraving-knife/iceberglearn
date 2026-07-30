# 提交 0099：Build: Bump me.champeau.jmh:jmh-gradle-plugin from 0.7.1 to 0.7.2 (#8942)

## 提交信息

- **序号**：0099 / 4088
- **哈希**：2f487f448f6bc43542cedde3e2b43470064c00ab
- **短哈希**：2f487f448
- **日期**：2023-10-30
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump me.champeau.jmh:jmh-gradle-plugin from 0.7.1 to 0.7.2 (#8942)
- **PR/Issue**：#8942

## 总体目的

本提交由 Dependabot 自动生成，将 JMH Gradle 插件 `me.champeau.jmh:jmh-gradle-plugin` 从 0.7.1 升级到 0.7.2（semver patch 级别升级）。

`jmh-gradle-plugin` 是 Iceberg 构建中用于集成 JMH（Java Microbenchmark Harness）的 Gradle 插件，用于运行项目的微基准测试（位于 `baseline`/性能测试相关模块）。Iceberg 在根 `build.gradle` 的 `buildscript` classpath 中声明该插件，以便各模块按需启用 JMH 基准任务。0.7.1 → 0.7.2 是该插件的补丁版本更新，通常包含 bug 修复与对更新 Gradle 版本的兼容性改进，不引入破坏性 API 变更。此类定期依赖升级是项目维护的一部分，旨在获取上游修复、保持构建工具链健康、降低安全/兼容性风险。

## 如何达成设计目的

改动单一：在 `build.gradle` 的 `buildscript.dependencies` classpath 声明中，把插件版本字符串从 `0.7.1` 改为 `0.7.2`。Dependabot 同时在 PR 描述中附带了 `updated-dependencies` 元数据，标明依赖类型为 `direct:production`、更新类型为 `version-update:semver-patch`，并由 bot 签名（`support@github.com`）。

## 修改详情

### `build.gradle`

**修改目的**：将 JMH Gradle 插件版本从 0.7.1 升级到 0.7.2。

**工作逻辑**：

修改位于根 `build.gradle` 第 38 行附近的 `buildscript` 依赖块：

```diff
-    classpath 'me.champeau.jmh:jmh-gradle-plugin:0.7.1'
+    classpath 'me.champeau.jmh:jmh-gradle-plugin:0.7.2'
```

该 classpath 声明使 `jmh` Gradle 插件在构建脚本类路径可用，各模块可通过 `apply plugin: 'me.champeau.jmh'`（或 plugins DSL）启用 JMH 基准测试能力。版本提升后，构建会拉取 0.7.2 的插件制品。

## 小结

本提交将 JMH Gradle 插件补丁版本从 0.7.1 升级到 0.7.2，是 Iceberg 构建依赖的常规维护升级，用于获取上游修复与兼容性改进。
