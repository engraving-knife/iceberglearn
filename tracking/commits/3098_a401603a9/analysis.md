# 提交 3098：Build: Bump com.google.errorprone:error_prone_annotations (#15020)

## 提交信息

- **序号**：3098 / 4088
- **哈希**：a401603a9cafe637baad27bf8291e5a595b020d0
- **短哈希**：a401603a9
- **日期**：2026-01-11
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#15020)
- **PR/Issue**：#15020

## 总体目的

该提交由 Dependabot 自动生成，将 `com.google.errorprone:error_prone_annotations` 从 2.45.0 升级到 2.46.0。Error Prone 是 Google 开发的 Java 静态分析工具，可在编译期捕获常见编程错误；`error_prone_annotations` 是其提供的纯注解工件（如 `@CanIgnoreReturnValue`、`@Immutable`、`@CheckReturnValue` 等），被库作者用于在 API 上声明契约，供 Error Prone 在使用方编译时校验。在 Iceberg 中，该工件作为生产依赖引入，代码中各类方法/类型会使用这些注解来表达语义约束（例如不可变类型、可忽略返回值），从而在编译期获得额外的正确性保障。

版本号从 2.45.0 升级到 2.46.0，属于语义版本中的 minor 级别升级（`version-update:semver-minor`）。根据提交元数据，该依赖归类为 `direct:production`。注解工件本身不携带运行时逻辑（仅保留注解定义），minor 升级可能新增少量注解类型或调整注解的保留策略/元数据，但不会改变既有注解的语义，因此对 Iceberg 的运行时行为无影响，主要是保持注解定义与上游同步、跟进新的静态检查能力。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `errorprone-annotations` 版本变量，从 `2.45.0` 改为 `2.46.0`。该变量被对应的 lib 坐标 `com.google.errorprone:error_prone_annotations` 通过 `version.ref` 引用，单点修改即生效。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 errorprone-annotations 版本变量。

**工作逻辑**：
将 `errorprone-annotations = "2.45.0"` 修改为 `errorprone-annotations = "2.46.0"`。该变量被 `errorprone-annotations` 坐标（`{ module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }`）引用。升级后，Iceberg 源码中已使用的 Error Prone 注解（如 `@CanIgnoreReturnValue` 等）会绑定到新版本的定义；由于注解工件仅含注解声明、无运行时行为，编译产物运行时不受影响。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 `error_prone_annotations` 从 2.45.0 提升到 2.46.0（minor 级别）。该工件为 Iceberg 提供 Error Prone 静态分析注解。由于注解工件无运行时逻辑，升级仅影响编译期注解定义，对运行时行为无影响。
