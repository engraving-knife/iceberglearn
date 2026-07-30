# 提交 3780：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16552)

## 提交信息

- **序号**：3780 / 4088
- **哈希**：d9a12fa9c2525058fae9b2773b5480025c3441ef
- **短哈希**：d9a12fa9c
- **日期**：2026-05-24 10:30:09 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16552)
- **PR/Issue**：#16552

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Spotless Gradle 插件从 8.4.0 升级到 8.5.1。这是一个 semver-minor 级别的升级。Spotless 是代码格式化工具，Iceberg 使用它来保持代码风格一致性。

## 如何达成设计目的

在 `build.gradle` 的 buildscript 依赖中更新 Spotless 插件版本号。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 Spotless 代码格式化插件版本。

**工作逻辑**：将 `classpath 'com.diffplug.spotless:spotless-plugin-gradle:8.4.0'` 改为 `8.5.1`，升级一个 minor 版本。

## 总结

常规的构建工具依赖维护提交，将 Spotless Gradle 插件从 8.4.0 升级到 8.5.1，获取新功能和改进。
