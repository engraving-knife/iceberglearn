# 提交 3838：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16706)

## 提交信息

- **序号**：3838 / 4088
- **哈希**：457854fb3d05887c7bd263703cb99bcd97c13429
- **短哈希**：457854fb3
- **日期**：2026-06-07 09:42:30 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16706)
- **PR/Issue**：#16706

## 总体目的

这是 Dependabot 自动发起的依赖升级提交，用于将 Gradle 构建脚本中使用的 `com.diffplug.spotless:spotless-plugin-gradle` 从 8.5.1 版本升级到 8.6.0 版本。

Spotless 是 DiffPlug 维护的代码格式化 Gradle 插件，用于在构建过程中统一代码风格（如 Java、Scala、Python 等的格式化）。Iceberg 项目使用 Spotless 来保证整个代码库的代码风格一致性，所有提交的代码都会经过 Spotless 检查。本次升级是 minor 版本更新（8.5.1 → 8.6.0），可能包含新的格式化规则或改进。

## 如何达成设计目的

通过修改根 `build.gradle` 文件中 `buildscript` 块的 `dependencies` 中该插件的版本号实现升级。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 spotless-plugin-gradle 版本。

**工作逻辑**：
在 `buildscript` 依赖块中将 Spotless 插件版本从 `8.5.1` 升级到 `8.6.0`：
```gradle
dependencies {
    classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.11'
    classpath 'com.palantir.baseline:gradle-baseline-java:6.90.0'
    classpath 'com.diffplug.spotless:spotless-plugin-gradle:8.6.0'
    ...
}
```

## 总结

这是一次例行的 Gradle 插件 minor 版本升级，将 `spotless-plugin-gradle` 从 8.5.1 升级到 8.6.0。Spotless 用于代码格式化，影响整个项目的代码风格检查。作为 minor 版本更新，通常向后兼容，但需要注意新版本可能引入新的格式化规则导致部分代码需要重新格式化。属于常规依赖维护工作。
