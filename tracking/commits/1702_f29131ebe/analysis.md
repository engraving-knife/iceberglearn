# 提交 1702：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.5 to 8.3.6 (#12210)

## 提交信息

- **序号**：1702 / 4088
- **哈希**：f29131ebe2a35a267716ab5a1326dd8cf4164f25
- **短哈希**：f29131ebe
- **日期**：2025-02-09（Sun Feb 9 08:37:25 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.5 to 8.3.6 (#12210)
- **PR/Issue**：#12210

## 总体目的

Dependabot 自动升级提交。`shadow-gradle-plugin`（来自 GradleUp/shadow）是 Gradle 的"胖包"打包插件，用于创建包含所有依赖的 uber jar（如 `iceberg-bundled-guava`、`azure-bundle`、`kafka-connect-runtime` 等模块使用）。本提交把该插件从 `8.3.5` 升级到 `8.3.6`（patch 级），获取最新的 bug 修复。

## 如何达成设计目的

在 `build.gradle` 的 `buildscript.dependencies` 中把 `classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.5'` 改为 `8.3.6`。

## 修改详情

### `build.gradle`（修改，+1/-1 行）

**修改目的**：升级 shadow 插件版本。

**工作逻辑**：修改 `buildscript` 块中的 classpath 依赖版本号，所有应用 shadow 插件的子项目自动使用新版本。

## 小结

- **成效**：升级 shadow 插件到 8.3.6，获取 bug 修复。
- **影响范围**：仅构建脚本，无源代码变更。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯构建工具版本升级。需确认 1.4.x 使用的 shadow 插件版本，直接升级即可。
