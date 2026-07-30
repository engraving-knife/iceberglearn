# 提交 2422：Build: Bump com.google.errorprone:error_prone_annotations (#13683)

## 提交信息

- **序号**：2422 / 4088
- **哈希**：7e9a1bc9d3e06aed8ebfd4ac98ffa8c807189fdb
- **短哈希**：7e9a1bc9d
- **日期**：2025-07-28 09:03:27 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#13683)
- **PR/Issue**：#13683

## 总体目的

本提交由 Dependabot 自动生成，将 `com.google.errorprone:error_prone_annotations` 从 2.40.0 升级到 2.41.0。

Error Prone 是 Google 开发的 Java 编译时静态分析工具，用于捕获常见的 Java 编程错误。`error_prone_annotations` 是其注解库，提供如 `@CanIgnoreReturnValue`、`@CompatibleWith` 等注解，用于在代码中标注预期行为以辅助静态分析。

Iceberg 项目使用 Error Prone 注解来提高代码质量和安全性。此次升级为 semver minor 版本升级（2.40.0 → 2.41.0），可能包含新的注解、检查规则和改进。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中 `error_prone_annotations` 的版本定义，将其更新为新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 error_prone_annotations 版本。

**工作逻辑**：将版本目录中 `error_prone_annotations` 的版本号从 `2.40.0` 更新为 `2.41.0`。

## 总结

这是一个常规的依赖升级提交，将 Error Prone 注解库从 2.40.0 升级到 2.41.0，获取最新的注解支持和静态分析改进。
