# 提交 2285：Build: Bump com.google.errorprone:error_prone_annotations (#13423)

## 提交信息

- **序号**：2285 / 4088
- **哈希**：d4bb2a185c6ef27461b9d7116cb7036f46b71bbe
- **短哈希**：d4bb2a185
- **日期**：2025-06-30 07:45:12 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#13423)
- **PR/Issue**：#13423

## 总体目的

本提交由 Dependabot 自动生成，将 `com.google.errorprone:error_prone_annotations` 依赖从 2.38.0 升级到 2.39.0。Error Prone Annotations 是 Google Error Prone 工具的注解库，提供 `@CheckReturnValue`、`@CanIgnoreReturnValue`、`@InlineMe` 等注解，Iceberg 使用这些注解辅助静态分析和编译期检查。

这是一次补丁/次版本升级，通常包含新注解、改进和缺陷修复。定期升级有助于获取上游改进并保持与 Error Prone 编译器插件的兼容性（提交 2279 修复了 Error Prone 警告，此处升级注解库版本与之相关）。

## 如何达成设计目的

- 修改 `gradle/libs.versions.toml` 中 `errorprone-annotations` 版本变量，从 `2.38.0` 改为 `2.39.0`。
- 通过版本目录集中管理，所有引用该变量的模块自动应用新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 errorprone-annotations 版本号。

**工作逻辑**：将 `errorprone-annotations = "2.38.0"` 改为 `errorprone-annotations = "2.39.0"`。所有通过 `libs.errorprone.annotations` 引用该坐标的依赖会自动解析为新版本。

## 总结

本提交是常规的 Dependabot 依赖升级，将 Error Prone 注解库从 2.38.0 升级到 2.39.0，仅需一行版本目录修改。这与项目使用 Error Prone 进行静态分析的质量保障工作相配合，保持依赖时效性。
