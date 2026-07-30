# 提交 3502：Build: bump shadow-gradle-plugin to 9.4.1 (#15835)

## 提交信息

- **序号**：3502 / 4088
- **哈希**：9a939d68358de9dac2c6ba9b236b675ebe477490
- **短哈希**：9a939d6835
- **日期**：2026-04-03 16:15:34 +0200
- **作者**：Maksim Konstantinov
- **提交说明**：Build: bump shadow-gradle-plugin to 9.4.1 (#15835)
- **PR/Issue**：#15835

## 总体目的

将 `shadow-gradle-plugin` 从 8.3.10 升级到 9.4.1。这是一个跨大版本升级，shadow 插件 9.x 版本对 shadow JAR 的组件发布方式做了重大改变，需要同步更新 `deploy.gradle` 中的发布配置。

## 如何达成设计目的

1. 在 `build.gradle` 的 buildscript 依赖中升级 shadow 插件版本。
2. 在 `deploy.gradle` 中将 `project.shadow.component(it)` 改为 `from components.shadow`，适配 shadow 插件 9.x 的新 API。

## 修改详情

### `build.gradle` (+1/-1 line)

**修改目的**：升级 shadow 插件版本。

**工作逻辑**：`com.gradleup.shadow:shadow-gradle-plugin:8.3.10` → `com.gradleup.shadow:shadow-gradle-plugin:9.4.1`。

### `deploy.gradle` (+1/-1 line)

**修改目的**：适配 shadow 插件 9.x 的组件发布 API。

**工作逻辑**：`project.shadow.component(it)` → `from components.shadow`。这是 shadow 插件 9.x 的新推荐方式，直接从 shadow 组件发布。

## 总结

构建工具升级提交，将 shadow-gradle-plugin 从 8.3.10 升级到 9.4.1。跨大版本升级需要同步修改 deploy.gradle 中的组件发布方式，从 `project.shadow.component(it)` 改为 `from components.shadow`。
