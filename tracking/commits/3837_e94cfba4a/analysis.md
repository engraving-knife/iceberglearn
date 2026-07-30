# 提交 3837：Build: Bump com.gradleup.shadow:shadow-gradle-plugin (#16705)

## 提交信息

- **序号**：3837 / 4088
- **哈希**：e94cfba4af8799a8bc84d041fe884dc5c0605853
- **短哈希**：e94cfba4a
- **日期**：2026-06-07 09:41:40 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gradleup.shadow:shadow-gradle-plugin (#16705)
- **PR/Issue**：#16705

## 总体目的

这是 Dependabot 自动发起的依赖升级提交，用于将 Gradle 构建脚本中使用的 `com.gradleup.shadow:shadow-gradle-plugin` 从 8.3.10 版本升级到 8.3.11 版本。

Shadow Gradle Plugin 是 GradleUp 维护的一个 Gradle 插件，用于生成包含所有依赖的 "fat jar"（uber jar），是 Iceberg 项目构建可发布 JAR 包时的重要工具。Iceberg 的一些模块（如 REST 服务、Flink/Spark 集成等）需要打包成包含依赖的可执行 JAR，因此依赖该插件。Dependabot 监测到上游发布了 patch 版本更新后自动发起了本次升级。

## 如何达成设计目的

通过修改根 `build.gradle` 文件中 `buildscript` 块的 `dependencies` 中该插件的版本号实现升级。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 shadow-gradle-plugin 版本。

**工作逻辑**：
在 `buildscript` 依赖块中将 shadow 插件版本从 `8.3.10` 升级到 `8.3.11`：
```gradle
dependencies {
    classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.11'
    ...
}
```
这是一个 patch 版本更新（8.3.10 → 8.3.11），通常只包含 bug 修复和小改进，不会引入破坏性变更。

## 总结

这是一次例行的 Gradle 插件 patch 版本升级，将 `shadow-gradle-plugin` 从 8.3.10 升级到 8.3.11。该插件用于生成 fat jar，影响 Iceberg 项目的打包构建过程，但作为 patch 版本更新，风险极低，属于常规依赖维护工作。
