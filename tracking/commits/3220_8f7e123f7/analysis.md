# 提交 3220：Build: Bump com.google.errorprone:error_prone_annotations (#15264)

## 提交信息

- **序号**：3220 / 4088
- **哈希**：8f7e123f727e85261a1b0d7e970aeb9f4add780e
- **短哈希**：8f7e123f7
- **日期**：2026-02-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#15264)
- **PR/Issue**：#15264

## 总体目的

这是一次 dependabot 发起的依赖版本升级，针对 Google Error Prone 的注解制品 `com.google.errorprone:error_prone_annotations`。Error Prone 是 Google 的 Java 静态分析工具，`error_prone_annotations` 制品仅包含其注解类型（如 `@CanIgnoreReturnValue`、`@CompatibleWith`、`@FormatMethod` 等），供库在源码中标注以表达编译期契约；很多传递依赖（如 Guava、gRPC 等）会把这个注解制品作为运行时/编译时依赖带进来。Iceberg 在 `gradle/libs.versions.toml` 中以 `errorprone-annotations` 变量显式声明其版本，便于在依赖解析时统一对齐。

本次把 `errorprone-annotations` 从 `2.46.0` 提升到 `2.47.0`，属于 `semver-minor`（次版本号）升级。Error Prone 的注解制品高度稳定，minor 升级通常仅新增注解或调整检测规则，注解本身保持二进制兼容。升级动机是跟进上游、避免传递依赖版本碎片化。

## 如何达成设计目的

作为 dependabot 自动化升级，整体思路是在集中式版本目录 `gradle/libs.versions.toml` 中把 `errorprone-annotations` 版本变量从 `2.46.0` 改为 `2.47.0`。该变量通过 `version.ref` 被对应制品引用，单点修改即生效。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将统一管理的 `errorprone-annotations` 版本变量从 2.46.0 升到 2.47.0。

**工作逻辑**：
在 `[versions]` 段中，将 `errorprone-annotations = "2.46.0"` 修改为 `errorprone-annotations = "2.47.0"`。该变量被 `[libraries]` 段中 `errorprone-annotations = { module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }` 引用。由于该制品只携带注解类型、API 高度稳定，次版本号升级预期对 Iceberg 源码与运行时行为透明，主要作用是把项目（及其传递依赖）使用的 Error Prone 注解版本统一对齐到 2.47.0，减少版本碎片。

## 总结

本提交由 dependabot 将集中式版本目录中的 `com.google.errorprone:error_prone_annotations` 版本变量从 2.46.0 升级到 2.47.0（次版本号升级，注解 API 兼容），用于跟进上游并统一对齐传递依赖中的 Error Prone 注解版本；改动为单点版本号替换，不涉及代码逻辑。
