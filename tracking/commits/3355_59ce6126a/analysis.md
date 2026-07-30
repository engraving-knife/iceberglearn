# 提交 3355：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#15537)

## 提交信息

- **序号**：3355 / 4088
- **哈希**：59ce6126a1cf0887237546657187c3ead9218ef3
- **短哈希**：59ce6126a
- **日期**：2026-03-07 23:16:59 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#15537)
- **PR/Issue**：#15537

## 总体目的

`com.diffplug.spotless:spotless-plugin-gradle` 是 Spotless 代码格式化工具的 Gradle 插件，用于在构建过程中统一代码格式（如 import 排序、空白、换行规范等），保证 Iceberg 全仓库代码风格一致。Iceberg 在根 `build.gradle` 的 `buildscript` 块中以 classpath 依赖方式加载该插件（与 shadow、baseline、jmh 等插件并列），随后在整个构建中应用 `spotlessApply` / `spotlessCheck` 等任务。

本次 dependabot 把插件版本从 `8.2.1` 升到 `8.3.0`（语义版本 semver-minor 升级）。minor 升级通常引入新格式化规则或对构建工具链的新支持，但保持与现有配置的兼容，目的是获取上游修复与新特性、避免使用旧版本。由于只升级插件自身版本，Spotless 的格式化规则配置（在子项目中定义）保持不变。

## 如何达成设计目的

仅需把 `build.gradle` 中 `buildscript.dependencies` 的 classpath 依赖版本号更新，Gradle 在下次构建时即加载新版本插件。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：把 spotless-plugin-gradle 版本从 8.2.1 升到 8.3.0。

**工作逻辑**：`classpath 'com.diffplug.spotless:spotless-plugin-gradle:8.2.1'` 改为 `classpath 'com.diffplug.spotless:spotless-plugin-gradle:8.3.0'`。该依赖位于 `buildscript` 块，与 `com.gradleup.shadow:shadow-gradle-plugin`、`com.palantir.baseline:gradle-baseline-java` 等构建插件并列。8.3.0 是 minor 级升级，按 Spotless 的兼容性承诺，预期与现有格式化配置兼容，无破坏性变更。

## 总结

本提交把代码格式化插件 spotless-plugin-gradle 从 8.2.1 升到 8.3.0（semver-minor），让 Iceberg 构建所用的格式化工具获得新特性与修复。改动仅一行 classpath 依赖版本，属常规构建工具链维护。
