# 提交 2679：Build: Bump com.google.errorprone:error_prone_annotations (#14132)

## 提交信息

- **序号**：2679 / 4088
- **哈希**：1ed0a755f7097b8b70375a45b9ad5b4447a7f6a6
- **短哈希**：1ed0a755f
- **日期**：2025-09-24 00:30:27 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#14132)
- **PR/Issue**：#14132

## 总体目的

本提交由 Dependabot 自动生成，将 Google Error Prone 的注解库 `error_prone_annotations` 从 2.41.0 升级到 2.42.0。这是一次 minor 版本升级（2.41 → 2.42），属于 `version-update:semver-minor` 类型。

Error Prone 是 Google 开发的 Java 编译时静态分析工具，用于捕获常见编程错误。`error_prone_annotations` 是其注解库，提供如 `@FormatMethod`、`@FormatString`、`@CanIgnoreReturnValue` 等注解，被 Iceberg 用于标注代码以配合 Error Prone 检查器或在其他工具中传达意图。该库作为 `direct:production` 依赖被引入，意味着它出现在生产 classpath 中。保持其版本最新有助于获取新的注解定义和 bug 修复。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `errorprone-annotations` 的版本声明，从 `2.41.0` 改为 `2.42.0`。所有引用该版本的模块会自动应用新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 error_prone_annotations 依赖版本。

**工作逻辑**：将 `errorprone-annotations = "2.41.0"` 一行修改为 `errorprone-annotations = "2.42.0"`。其余条目保持不变。这是 Dependabot 自动化依赖升级的标准单点修改。

## 总结

这是一次常规的依赖版本升级，将 Error Prone 注解库从 2.41.0 升级到 2.42.0（minor 版本）。修改仅涉及一行版本目录配置，风险极低。Iceberg 项目通过 Dependabot 持续保持构建依赖的最新状态。
