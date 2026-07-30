# 提交 2320：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.7 to 8.3.8 (#13476)

## 提交信息

- **序号**：2320 / 4088
- **哈希**：e0b62e622e8ffa0c0adc2056a6d59a624289e554
- **短哈希**：e0b62e622
- **日期**：2025-07-07 09:52:41 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.7 to 8.3.8 (#13476)
- **PR/Issue**：#13476

## 总体目的

这是一个由 dependabot 自动生成的依赖升级提交，将 Shadow Gradle 插件从版本 8.3.7 升级到 8.3.8。

Shadow 是一个 Gradle 插件，用于创建 fat/uber JAR 文件（将依赖打包到单个 JAR 中）。Iceberg 项目使用它来构建分发包。这是一个 semver-patch 级别升级，包含 bug 修复和改进。

## 如何达成设计目的

通过修改 `build.gradle` 文件中的 `buildscript` 依赖，将 shadow-gradle-plugin 的版本号从 8.3.7 改为 8.3.8。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 Shadow Gradle 插件版本。

**工作逻辑**：在 `buildscript.dependencies` 块中，将 `classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.7'` 改为 `classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.8'`。

## 总结

这是一个常规的构建插件依赖升级提交，通过 dependabot 自动升级 Shadow 插件以获取最新的 bug 修复。变更仅涉及版本号修改。
