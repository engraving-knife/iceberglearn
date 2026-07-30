# 提交 2937：Build: Bump com.google.errorprone:error_prone_annotations (#14715)

## 提交信息

- **序号**：2937 / 4088
- **哈希**：46d766ae48597d09cfdc26d3f502b2bbfaf6edcc
- **短哈希**：46d766ae4
- **日期**：2025-11-29
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#14715)
- **PR/Issue**：#14715

## 总体目的

这是由 Dependabot 自动发起的依赖升级，目标是将 Google 出品的 `com.google.errorprone:error_prone_annotations` 从 2.44.0 升级到 2.45.0。

`error_prone_annotations` 是 Google [error-prone](https://github.com/google/error-prone) 项目的注解包，提供诸如 `@CheckReturnValue`、`@CanIgnoreReturnValue`、`@FormatMethod` 等编译期静态检查注解。这些注解会在编译阶段配合 error-prone 插件或 IDE 的检查能力使用，帮助捕获诸如忽略返回值、错误使用格式化字符串等常见 bug。在 Iceberg 项目里，这个依赖以 `compileOnly` 形式引入 `iceberg-api` 模块（见 `build.gradle` 第 297 行：`compileOnly libs.errorprone.annotations`），也就是说它不参与最终打包，仅用于编译期 API 模块中使用到的注解符号解析。

从语义版本看，本次为 `semver-minor` 升级，按 Google error-prone 团队的版本约定，2.45.0 一般只是新增检测器或注解能力、修复既有 bug，不破坏既有 API 表面。Dependabot 元数据也将其标注为 `version-update:semver-minor`、`direct:production`，与上述判断一致。

## 如何达成设计目的

改动只涉及 Gradle 版本目录（version catalog）`gradle/libs.versions.toml` 中的版本别名声明，不动任何引用方代码。因为下游引用都通过 `version.ref` 间接指向别名，所以一处抬升即可让所有依赖 `errorprone-annotations` 的模块同步使用新版本，无需逐处修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将版本目录中 `errorprone-annotations` 别名的固定版本从 `2.44.0` 修改为 `2.45.0`。

**工作逻辑**：
该文件是 Gradle 的集中依赖版本目录，第 20 行定义了别名版本，第 88 行定义了依赖坐标：

```toml
errorprone-annotations = "2.45.0"   # 第 20 行
errorprone-annotations = { module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }  # 第 88 行
```

本次仅改了第 20 行的版本号字符串。下游 `build.gradle` 中 `compileOnly libs.errorprone.annotations` 等引用通过 `version.ref` 自动获得新版本，因此只需这一行变更即可统一驱动所有模块升级。

## 总结

本次提交是常规的依赖维护，把 `iceberg-api` 模块编译期使用的 error-prone 注解包从 2.44.0 抬升到 2.45.0 一个 minor 版本，保持静态检查注解依赖与上游同步。由于是 `compileOnly` 依赖且为 minor 升级，对运行时无影响，风险极低。
