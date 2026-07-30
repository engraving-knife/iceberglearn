# 提交 0890：Build: Bump io.github.goooler.shadow:shadow-gradle-plugin (#10612)

## 提交信息

- **序号**：0890 / 4088
- **哈希**：3df3d29a6938f5999161b39bb219527b8cb67c80
- **短哈希**：3df3d29a6
- **日期**：2024-07-02（Tue Jul 2 09:47:23 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.github.goooler.shadow:shadow-gradle-plugin (#10612)
- **PR/Issue**：#10612

## 总体目的

本提交由 Dependabot 自动生成，目的是将 `io.github.goooler.shadow:shadow-gradle-plugin`（Shadow Gradle 插件的一个 fork 维护版本）从 8.1.7 升级到 8.1.8。Shadow 插件用于在 Gradle 构建中生成 fat/uber JAR（将依赖打包进单个 JAR），Iceberg 在多个模块（如 Flink 集成模块）的构建中使用该插件生成 shaded JAR，以避免依赖冲突。

8.1.7 → 8.1.8 是 semver-patch 升级，通常包含 bug 修复与小改进，属于低风险的例行构建依赖维护。

## 如何达成设计目的

该插件在项目根 `build.gradle` 的 `buildscript.dependencies` 中以 `classpath` 方式声明，直接指定版本号。只需将版本号字符串从 `8.1.7` 改为 `8.1.8` 即可完成升级。

## 修改详情

### `build.gradle`

**修改目的**：将 Shadow Gradle 插件版本从 8.1.7 升级到 8.1.8。

**工作逻辑**：将 `buildscript.dependencies` 中的 `classpath 'io.github.goooler.shadow:shadow-gradle-plugin:8.1.7'` 改为 `classpath 'io.github.goooler.shadow:shadow-gradle-plugin:8.1.8'`。该插件在构建脚本加载阶段通过 classpath 引入，升级后所有应用 Shadow 插件的子模块将自动使用新版本。

## 小结

- **成效**：完成 Shadow Gradle 插件的补丁版本升级（8.1.7 → 8.1.8），获取上游 bug 修复。
- **影响范围**：仅 `build.gradle` 一行改动，影响构建时 Shadow 插件版本；无代码改动，无运行时影响。
- **回迁到 1.4.x 的注意事项**：可按需回迁。属于构建依赖升级，风险低。需确认 1.4.x 分支是否使用相同版本的 Shadow 插件 fork（`goooler.shadow`），若版本基线一致则可直接回迁。若 1.4.x 构建稳定可不必回迁。
