# 提交 3962：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16990)

## 提交信息

- **序号**：3962 / 4088
- **哈希**：839b22647ce5aa08e27220bbe85cde1292129fea
- **短哈希**：839b22647
- **日期**：2026-06-27 23:49:07 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16990)
- **PR/Issue**：#16990

## 总体目的

这是 Dependabot 自动升级 Gradle 的 Spotless 代码格式化插件从 8.6.0 到 8.7.0 的提交。Spotless 是一个用于统一代码风格的工具，在构建时自动格式化代码。升级类型为 `version-update:semver-minor`（次版本升级），包含功能改进。

## 如何达成设计目的

通过修改根 `build.gradle` 中的 buildscript 依赖，将 Spotless 插件版本从 8.6.0 升级到 8.7.0。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 Spotless 插件版本。

**工作逻辑**：
```gradle
dependencies {
  // ...
  classpath 'com.diffplug.spotless:spotless-plugin-gradle:8.7.0'  // 原为 8.6.0
  // ...
}
```

## 总结

常规构建工具升级，将 Spotless 代码格式化插件从 8.6.0 升级到 8.7.0。新版本可能包含格式化规则改进或 bug 修复，但不影响项目代码逻辑。
