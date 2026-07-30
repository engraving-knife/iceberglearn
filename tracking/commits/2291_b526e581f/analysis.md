# 提交 2291：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.6 to 8.3.7 (#13414)

## 提交信息

- **序号**：2291 / 4088
- **哈希**：b526e581f37f8f8b357557a34f831509264f46fa
- **短哈希**：b526e581f
- **日期**：2025-06-30 10:17:21 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.6 to 8.3.7 (#13414)
- **PR/Issue**：#13414

## 总体目的

本提交由 Dependabot 自动生成，将 Shadow Gradle 插件从 8.3.6 升级到 8.3.7。Shadow 是一个 Gradle 插件，用于创建 fat/uber JAR 文件，将依赖项打包到单个 JAR 中。在 Iceberg 项目中，Shadow 插件用于构建包含所有依赖的发布包。

此次升级属于 semver-patch（补丁版本）更新，主要包含 bug 修复和小改进，不涉及破坏性变更。

## 如何达成设计目的

Dependabot 检测到 `build.gradle` 中声明的 Shadow 插件版本有新的补丁版本可用，自动创建 PR 进行升级。版本号从 `8.3.6` 修改为 `8.3.7`。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 Shadow Gradle 插件版本从 8.3.6 升级到 8.3.7。

**工作逻辑**：在 `build.gradle` 文件的插件声明部分，将 `com.gradleup.shadow:shadow-gradle-plugin` 的版本号从 `8.3.6` 更新为 `8.3.7`。这是一个补丁版本升级，不涉及配置变更。

## 总结

这是一个常规的依赖补丁版本升级提交，通过 Dependabot 自动完成。Shadow 插件是 Iceberg 构建系统中的重要组成部分，此次升级确保 fat JAR 打包功能使用最新的修复版本。
