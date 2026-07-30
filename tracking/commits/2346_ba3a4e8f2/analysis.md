# 提交 2346：Build: Bump com.google.errorprone:error_prone_annotations (#13542)

## 提交信息

- **序号**：2346 / 4088
- **哈希**：ba3a4e8f21d4b967c48e9ce519e63abb49563dbf
- **短哈希**：ba3a4e8f2
- **日期**：2025-07-14 09:20:42 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#13542)
- **PR/Issue**：#13542

## 总体目的

本提交由 Dependabot 自动生成，将 `com.google.errorprone:error_prone_annotations` 依赖从 2.39.0 升级到 2.40.0。这是一个次版本（minor version）升级。

Error Prone 是 Google 开发的 Java 编译时静态分析工具，用于捕获常见的编程错误。`error_prone_annotations` 是其注解库，提供如 `@CanIgnoreReturnValue`、`@InlineMe`、`@SuppressWarnings` 等注解，被 Iceberg 代码广泛使用来标注代码意图和抑制特定的静态分析警告。

从 2.39.0 到 2.40.0 是次版本升级，可能新增注解或改进现有注解的行为，但不引入破坏性变更。

## 如何达成设计目的

在 Gradle 版本目录中更新 Error Prone Annotations 的版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Error Prone Annotations 版本号。

**工作逻辑**：将 `errorprone-annotations = "2.39.0"` 改为 `errorprone-annotations = "2.40.0"`。版本目录中定义的变量被所有引用该依赖的模块共享。

## 总结

本提交是 Dependabot 自动生成的依赖升级，将 `error_prone_annotations` 从 2.39.0 升级到 2.40.0（次版本升级），获取 Error Prone 注解库的最新改进。
