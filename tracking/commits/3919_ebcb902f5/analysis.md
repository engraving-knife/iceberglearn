# 提交 3919：Build: Bump com.google.errorprone:error_prone_annotations (#16900)

## 提交信息

- **序号**：3919 / 4088
- **哈希**：ebcb902f58f4fd00a541e668cbbba416cc1ba030
- **短哈希**：ebcb902f5
- **日期**：2026-06-21 00:06:59 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#16900)
- **PR/Issue**：#16900

## 总体目的

这是由 Dependabot 发起的依赖升级，将 Google Error Prone 的注解库（`error_prone_annotations`）从 2.49.0 升级到 2.50.0。Error Prone 是 Google 开发的 Java 静态分析工具，用于在编译期捕获常见的编程错误；其注解库提供了如 `@CanIgnoreReturnValue`、`@CheckReturnValue`、`@CompatibleWith` 等注解，被许多 Google 系库（如 Guava、gRPC 等）作为编译期依赖使用。

Iceberg 项目在版本目录中显式声明该依赖版本，以便在编译时控制被传递性引入的 error_prone_annotations 版本，避免因不同传递性依赖引入的版本冲突。此次升级为 semver-minor 更新，属于常规维护。

## 如何达成设计目的

通过修改版本目录 `gradle/libs.versions.toml` 中 `errorprone-annotations` 的版本条目，将其从 `2.49.0` 更新为 `2.50.0`。版本目录作为集中式版本管理入口，所有子项目通过别名引用该版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Error Prone 注解库版本。

**工作逻辑**：
将 `errorprone-annotations = "2.49.0"` 改为 `errorprone-annotations = "2.50.0"`。该注解库通常作为编译期依赖被引入，用于支持 Google 系库的注解约定。

## 总结

这是一次 Error Prone 注解库的次要版本升级，通过版本目录统一升级到 2.50.0。作为注解库的 minor 版本更新，预期向后兼容，主要获取上游的新注解支持与可能的错误检测改进。
