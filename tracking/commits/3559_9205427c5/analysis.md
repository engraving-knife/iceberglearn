# 提交 3559：Build: Bump com.google.errorprone:error_prone_annotations (#16039)

## 提交信息

- **序号**：3559 / 4088
- **哈希**：9205427c5d02a58917eadafd7823e0d722049417
- **短哈希**：9205427c5
- **日期**：2026-04-19 07:16:15 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations from 2.48.0 to 2.49.0 (#16039)
- **PR/Issue**：#16039

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 Google Error Prone 的注解库 `com.google.errorprone:error_prone_annotations` 从版本 2.48.0 升级到 2.49.0。Error Prone 是 Google 开发的 Java 静态分析工具，用于在编译时捕获常见编程错误，其注解库提供相关注解（如 `@CanIgnoreReturnValue`、`@CheckReturnValue` 等）供代码使用。这是一个 semver-minor（次版本）升级。

## 如何达成设计目的

Dependabot 自动检测到版本目录中 error_prone_annotations 版本有更新，自动创建 PR 升级版本声明。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 error_prone_annotations 版本声明。

**工作逻辑**：
将 error_prone_annotations 版本从 `2.48.0` 升级到 `2.49.0`。该注解库被 Iceberg 代码用于标注方法的行为约定，帮助 Error Prone 在编译时进行静态检查。升级后可获取新版本中的新注解和检查规则改进。

## 总结

这是一个常规的依赖维护提交，通过次版本升级保持 Error Prone 注解库的最新状态，获取 2.49.0 版本的改进和新功能。
