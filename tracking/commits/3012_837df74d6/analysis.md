# 提交 3012：Build: Bump org.immutables:value from 2.11.7 to 2.12.0 (#14844)

## 提交信息

- **序号**：3012 / 4088
- **哈希**：837df74d6b88561a5a8d761e7883e081fab44b72
- **短哈希**：837df74d6
- **日期**：2025-12-14 06:57:52 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.11.7 to 2.12.0 (#14844)
- **PR/Issue**：#14844

## 总体目的

`org.immutables:value` 是 Immutables 项目的注解处理器，提供 `@Value.Immutable`、`@Value.Modifiable` 等注解，在编译期生成不可变值类（含 builder、`of` 工厂、`equals`/`hashCode`/`toString` 等）。Iceberg 在多处用 Immutables 生成不可变的数据/配置值对象（例如 metrics、加密相关、REST 请求/响应模型等需要不可变且可序列化的值类），以减少手写样板代码并保证线程安全。其版本由 `gradle/libs.versions.toml` 中 `immutables-value` 集中管理，各模块以 `annotationProcessor`/`compileOnly` 形式引入。

本提交是 dependabot 触发的依赖升级，把 `org.immutables:value` 从 `2.11.7` 升到 `2.12.0`，跨一个 minor 版本。Immutables 在 minor 升级中通常包含生成器改进、对新 JDK 版本的兼容性增强与 bug 修复，不破坏既有注解契约。升级动机是保持注解处理器最新、获取生成器改进与修复，避免与较新 JDK 编译环境产生兼容性问题。

## 如何达成设计目的

改动极小：仅在 `gradle/libs.versions.toml` 中把 `immutables-value = "2.11.7"` 改为 `immutables-value = "2.12.0"`。由于各模块通过版本目录引用该键，单点修改即让所有用到 Immutables 注解处理器的模块同步升级。dependabot 元数据标注 `update-type: version-update:semver-minor`，属向后兼容的 minor 升级；生成代码在编译期产生，升级后重新编译即可，无需改动手写源码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Immutables value 注解处理器版本号。

**工作逻辑**：
`immutables-value = "2.11.7"` 改为 `immutables-value = "2.12.0"`。该键被各模块以 `annotationProcessor(libs.immutables.value)`（及 `compileOnly`）形式引用，Gradle 据此解析 `org.immutables:value` 的版本。minor 升级预期保持 `@Value.Immutable` 等注解契约不变，主要带来生成器改进与 JDK 兼容性增强，生成的不可变值类行为与既有用法一致。

## 总结

该提交是一次 dependabot 驱动的 Immutables value 注解处理器 minor 升级（2.11.7 → 2.12.0），单点修改版本目录即可让 Iceberg 各模块用到的 `@Value.Immutable` 注解处理器同步更新。属于低风险、向后兼容的常规维护，主要获取生成器改进与 JDK 兼容性修复，不影响既有注解用法与生成代码的运行时行为。
