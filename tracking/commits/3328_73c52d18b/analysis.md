# 提交 3328：Build: Bump com.google.errorprone:error_prone_annotations (#15484)

## 提交信息

- **序号**：3328 / 4088
- **哈希**：73c52d18bc0d995f1acde6995de3f4b496f6bbe9
- **短哈希**：73c52d18b
- **日期**：2026-02-28 22:08:46 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#15484)
- **PR/Issue**：#15484

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 `com.google.errorprone:error_prone_annotations` 从 `2.47.0` 升级到 `2.48.0`。

Error Prone 是 Google 开发的 Java 静态分析工具，用于在编译期捕获常见的编程错误（如空指针、资源泄漏、并发误用、API 误用等）。它有两个相关产物：`error_prone_core`（编译期注解处理器，实际执行检查）和 `error_prone_annotations`（运行时注解库，提供 `@FormatMethod`、`@FormatString`、`@CanIgnoreReturnValue`、`@CheckReturnValue` 等注解）。Iceberg 引入的是后者 `error_prone_annotations`——一个仅包含注解定义的轻量库，这些注解会被 Error Prone 及其他工具（如 NullAway）识别，但注解本身在运行时存在。

在 Iceberg 项目中，`error_prone_annotations` 被用于在源码中以注解形式标注方法的契约（例如某方法返回值必须被使用、某参数是格式化字符串等），从而让静态分析工具能在编译期发现误用。该库出现在运行时 classpath 中（`direct:production`），因为编译产物中保留了这些注解。

本次升级属于语义化版本的 **minor（次版本）** 升级（`2.47.0` → `2.48.0`，`update-type: version-update:semver-minor`）。minor 升级通常会新增注解类型或检查规则，同时保持现有注解的二进制兼容。预期影响是获得 2.48 系列新增的注解与缺陷修复，对 Iceberg 现有代码的编译与运行行为无破坏性影响。

## 如何达成设计目的

改动仅修改版本目录文件 `gradle/libs.versions.toml` 中 `errorprone-annotations` 这一项的版本字符串。引用该版本变量的库坐标（`libs.errorprone.annotations`）会在构建时解析到新版本，无需改动任何模块代码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 error_prone_annotations 版本从 2.47.0 提升到 2.48.0。

**工作逻辑**：
在版本目录 `[versions]` 段中，将 `errorprone-annotations = "2.47.0"` 修改为 `errorprone-annotations = "2.48.0"`。该变量被 `errorprone-annotations` 库坐标引用，下游模块通过 `libs.errorprone.annotations` 引用此坐标时会解析到 `2.48.0`。这是一次纯版本号变更，不涉及代码逻辑。

## 总结

本次提交通过 Dependabot 将 error_prone_annotations 从 2.47.0 升级到 2.48.0（minor 级），以获取上游新增注解与缺陷修复。改动局限于版本目录单行，风险低，对 Iceberg 的运行时行为与编译产物无破坏性影响，属于日常静态分析相关依赖维护的一部分。
