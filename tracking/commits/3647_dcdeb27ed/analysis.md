# 提交 3647：Flink: Define Joda Time in libs.versions.toml file (#16191)

## 提交信息

- **序号**：3647 / 4088
- **哈希**：dcdeb27edcfaedfccd1e96d3a7c1f1b4e10e898b
- **短哈希**：dcdeb27ed
- **日期**：2026-05-06 07:57:19 +0200
- **作者**：Talat UYARER
- **提交说明**：Flink: Define Joda Time in libs.versions.toml file (#16191)
- **PR/Issue**：#16191

## 总体目的

这个提交将 Joda-Time 依赖从 Flink 2.1 的 `build.gradle` 中硬编码的版本声明迁移到统一的 `gradle/libs.versions.toml` 版本目录中管理。

Iceberg 项目使用 Gradle 的版本目录（version catalog）机制在 `libs.versions.toml` 中集中管理所有依赖的版本号，便于统一维护和升级。此前 Flink 2.1 模块的 `build.gradle` 中直接硬编码了 `joda-time:joda-time:2.8.1`，没有纳入版本目录管理，违反了项目的依赖管理规范。本提交将其迁移到 `libs.versions.toml`，并统一使用 `libs.joda.time` 引用。

需要注意的是，迁移后版本号从 2.8.1 变为 2.5（版本目录中定义的版本），这是一个版本回退，可能是为了与项目中其他模块使用的 Joda-Time 版本保持一致。

## 如何达成设计目的

1. 在 `gradle/libs.versions.toml` 的 `[versions]` 段新增 `joda = "2.5"`。
2. 在 `[libraries]` 段新增 `joda-time = { module = "joda-time:joda-time", version.ref = "joda" }`。
3. 在 `flink/v2.1/build.gradle` 中将 `compileOnly 'joda-time:joda-time:2.8.1'` 改为 `compileOnly libs.joda.time`。

## 修改详情

### `gradle/libs.versions.toml` (+2 lines)

**修改目的**：在版本目录中注册 Joda-Time 依赖。

**工作逻辑**：
```toml
[versions]
joda = "2.5"

[libraries]
joda-time = { module = "joda-time:joda-time", version.ref = "joda" }
```

### `flink/v2.1/build.gradle` (+1/-1 line)

**修改目的**：使用版本目录引用替代硬编码版本。

**工作逻辑**：
```gradle
// 旧
compileOnly 'joda-time:joda-time:2.8.1'
// 新
compileOnly libs.joda.time
```

## 总结

这个提交是一个依赖管理的规范化改进，将 Flink 2.1 模块中硬编码的 Joda-Time 依赖迁移到项目的统一版本目录 `libs.versions.toml` 中管理，符合项目的依赖管理规范。需要注意的是版本号从 2.8.1 变为 2.5，建议关注是否有其他模块需要保持版本一致。Joda-Time 在 Flink 模块中作为 compileOnly 依赖使用。
