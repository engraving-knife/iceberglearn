# 提交 2851：Build: Bump com.google.errorprone:error_prone_annotations (#14538)

## 提交信息

- **序号**：2851 / 4088
- **哈希**：72c4d9a021be8058440c6ee9222835bcfe307317
- **短哈希**：72c4d9a02
- **日期**：2025-11-08 22:18:04 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#14538)
- **PR/Issue**：#14538

## 总体目的

这是一个由 dependabot 自动生成的依赖升级提交，将 Google Error Prone 的注解库（error_prone_annotations）从 2.43.0 升级到 2.44.0。

Error Prone 是 Google 开发的 Java 静态分析工具，用于在编译时捕获常见的 Java 编程错误。`error_prone_annotations` 是其提供的注解库，用于在代码中标注以抑制或配置特定的错误检查。该库通常作为传递依赖被引入，但在 Iceberg 项目中作为直接生产依赖进行版本管理。

版本从 2.43.0 升级到 2.44.0，属于次版本号（minor）升级，意味着可能包含新的检查规则注解和问题修复，同时保持向后兼容。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `errorprone-annotations` 版本声明来完成升级。Iceberg 项目使用集中式版本管理，所有依赖版本都在该文件中声明，修改一处即可在所有模块中生效。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Error Prone 注解库版本声明。

**工作逻辑**：将 `errorprone-annotations` 变量从 `2.43.0` 修改为 `2.44.0`。该变量在版本目录中定义后，可被各模块的 build.gradle 引用，确保整个项目使用统一的依赖版本。

## 总结

这是一个常规的依赖升级提交，将 error_prone_annotations 从 2.43.0 升级到 2.44.0。修改仅涉及版本目录文件的一行改动，保持了项目依赖的及时更新。
