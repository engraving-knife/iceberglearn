# 提交 3165：Build: Bump spotless gradle plugin to 8.2.0 (#15156)

## 提交信息

- **序号**：3165 / 4088
- **哈希**：0e37d50fe8b9f3aa2581bdba6e43711cded8ce52
- **短哈希**：0e37d50fe
- **日期**：2026-01-27 09:15:38 -0800
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Bump spotless gradle plugin to 8.2.0
- **PR/Issue**：#15156

## 总体目的

本提交将 Iceberg 项目使用的 Spotless Gradle 插件（`com.diffplug.spotless:spotless-plugin-gradle`）从 `6.25.0` 升级到 `8.2.0`。Spotless 是 Iceberg 构建链中的代码格式化工具，在 `baseline.gradle` 中通过 `apply plugin: 'com.diffplug.spotless'` 启用，配置了：Java 代码使用 `googleJavaFormat("1.22.0")` 格式化、移除未使用 import、添加版权头（`licenseHeaderFile`），以及 Spark 模块的 Scala 代码用 `scalafmt("3.9.7")` 格式化并加版权头。`./gradlew spotlessCheck`/`spotlessApply` 是开发者日常与 CI 中强制执行的代码风格守门任务，插件版本直接影响格式化行为与构建兼容性。

本次为**主版本（major）升级**（6.25.0 → 8.2.0），跨过了 Spotless 7.x/8.x 两个大版本。Spotless 8.x 通常要求较新的 Gradle 与 JDK 基线，并可能调整默认格式化行为或插件 API。值得关注的是仓库已先行将 Gradle Wrapper 升级到 8.14.x（参见 #15143），为主版本插件升级铺平了 Gradle 兼容性前提。该升级由人工发起（非 dependabot），说明是有意为之的工具链现代化举措。

## 如何达成设计目的

直接在根 `build.gradle` 的 `buildscript.dependencies` classpath 声明中将插件坐标版本从 `6.25.0` 改为 `8.2.0`，所有子项目通过 `baseline.gradle` 中 `apply plugin: 'com.diffplug.spotless'` 应用该插件时自动使用新版本。由于 `baseline.gradle` 中的 spotless 配置块（`googleJavaFormat`、`scalafmt`、`removeUnusedImports`、`licenseHeaderFile`）使用的 API 在 8.x 仍兼容，无需同步修改配置代码。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 Spotless Gradle 插件 classpath 依赖从 6.25.0 升级到 8.2.0。

**工作逻辑**：在 `buildscript.dependencies` 块中将 `classpath 'com.diffplug.spotless:spotless-plugin-gradle:6.25.0'` 修改为 `classpath 'com.diffplug.spotless:spotless-plugin-gradle:8.2.0'`。该 classpath 声明决定了全局解析到的 Spotless 插件版本，`baseline.gradle` 中 `apply plugin: 'com.diffplug.spotless'` 及其配置块（`googleJavaFormat("1.22.0")`、`removeUnusedImports()`、`licenseHeaderFile`、Spark 模块的 `scalafmt("3.9.7")`）随后都基于 8.2.0 运行。作为主版本升级，需确保项目当前 Gradle（8.14.x）与 JDK 基线满足 Spotless 8.x 要求；预期格式化输出可能与 6.x 存在细微差异，需通过 `./gradlew spotlessApply` 重新格式化代码以适配新版本。

## 总结

本提交通过将 Spotless Gradle 插件从 6.25.0 主版本升级到 8.2.0，推动 Iceberg 代码格式化工具链现代化，使其与项目已升级的 Gradle 8.14.x 基线对齐并获取 8.x 的改进与修复；作为 major 升级，可能引入格式化行为的细微变化，需配合 `spotlessApply` 适配，但对现有 spotless 配置 DSL 保持兼容，无需改动 `baseline.gradle`。
