# 提交 1997：Build: Bump junit to 5.12.2

## 提交信息

- **序号**：1997 / 4088
- **哈希**：0aeb574ed35983a1f667b37f475453f9d8f472dc
- **短哈希**：0aeb574ed
- **日期**：2025-04-15 12:31:47 +0200
- **作者**：iProdigy
- **提交说明**：Build: Bump junit to 5.12.2 (#12391)
- **PR/Issue**：#12391

## 总体目的

本提交将 JUnit 5 从 5.11.4 升级到 5.12.2，同时将 JUnit Platform 从 1.11.4 升级到 1.12.2。此外还新增了 `junit-platform-launcher` 依赖，这是 JUnit 5.12+ 的一个重要变更——从此版本开始，`junit-platform-launcher` 需要作为显式依赖添加，以确保测试发现和启动机制正常工作。

JUnit 5.12 是一个较大的版本升级（从 5.11.x 到 5.12.x），包含多个新特性和改进。其中最关键的变更是 `junit-platform-launcher` 的分离，如果不同步添加此依赖，可能导致 IDE 或构建工具无法正确发现和执行测试。

## 如何达成设计目的

通过以下三处修改完成升级：
1. 在版本目录中更新 JUnit 和 JUnit Platform 的版本号
2. 在版本目录中新增 `junit-platform-launcher` 制品声明
3. 在根构建脚本和 Spark 3.5 构建脚本中添加该依赖

## 修改详情

### `gradle/libs.versions.toml` (修改, +5/-2 lines)

**修改目的**：更新 JUnit 版本号并新增 launcher 制品声明。

**工作逻辑**：
- 将 `junit` 版本从 `"5.11.4"` 更新为 `"5.12.2"`
- 将 `junit-platform` 版本从 `"1.11.4"` 更新为 `"1.12.2"`
- 新增制品声明：`junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junit-platform" }`

### `build.gradle` (修改, +1/-0 lines)

**修改目的**：为所有子项目添加 junit-platform-launcher 测试依赖。

**工作逻辑**：
在 `subprojects` 块中添加 `testImplementation libs.junit.platform.launcher`，确保所有子项目都能使用 JUnit Platform Launcher 来发现和执行测试。

### `spark/v3.5/build.gradle` (修改, +1/-0 lines)

**修改目的**：为 Spark 3.5 集成测试添加 junit-platform-launcher 依赖。

**工作逻辑**：
在集成测试依赖中添加 `integrationImplementation libs.junit.platform.launcher`，确保 Spark 3.5 模块的集成测试也能正确使用 JUnit Platform Launcher。

## 总结

本提交将 JUnit 从 5.11.4 升级到 5.12.2（JUnit Platform 从 1.11.4 升级到 1.12.2），并新增了 `junit-platform-launcher` 显式依赖，以适配 JUnit 5.12+ 的变更。这是支撑 Iceberg 项目全面迁移到 JUnit 5 的基础构建升级。
