# 提交 0091：Build: Bump com.google.errorprone:error_prone_annotations (#8897)

## 提交信息

- **序号**：0091 / 4088
- **哈希**：9ff517c02dce25bb0b985283a2c3504a0b179367
- **短哈希**：9ff517c02
- **日期**：2023-10-25 14:00:57 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#8897)
- **PR/Issue**：#8897

## 总体目的

这是一个由 Dependabot 自动生成的依赖版本升级提交，将 `com.google.errorprone:error_prone_annotations` 从 2.22.0 升级到 2.23.0，属于语义化版本中的次版本（minor）升级。

`error_prone_annotations` 是 Google [error-prone](https://github.com/google/error-prone) 项目提供的注解构件（`@DoNotCall`、`@CompatibleWith`、`@FormatMethod` 等），Iceberg 在源码中使用这些注解来标注 API 的使用约束与可捕获的编程错误。该构件本身不携带运行时逻辑，仅在编译期由 error-prone 编译器插件或静态分析工具消费。Iceberg 在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中以 `errorprone-annotations` 的版本引用直接声明该依赖。

本次升级是 Iceberg 项目持续进行的依赖维护工作的一部分。Dependabot 会定期扫描依赖并自动提交 PR，由维护者评审后合入，以保持依赖库的版本新鲜度，获取上游的 bug 修复、新增注解能力以及与新版 JDK/error-prone 工具链的兼容性。这类小步快跑的依赖升级对降低安全风险、避免依赖滞后堆积成大升级有重要意义。

## 如何达成设计目的

改动极为简单：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中将 `errorprone-annotations` 的版本字符串从 `"2.22.0"` 改为 `"2.23.0"`。由于该依赖在版本目录中以 `version.ref` 形式被各模块引用，仅需修改一处版本常量即可让所有引用该依赖的模块统一升级，无需逐模块改动。

## 修改详情

### [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml)

**修改目的**：将 `errorprone-annotations` 依赖版本从 2.22.0 升级到 2.23.0。

**工作逻辑**：版本目录的 `[versions]` 段中只改了一行：

```toml
-errorprone-annotations = "2.22.0"
+errorprone-annotations = "2.23.0"
```

该版本常量在 `[libraries]` 段被 `errorprone-annotations = { module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }` 引用。改完后，所有通过 `libs.errorprone.annotations` 引入该依赖的子模块都会自动解析到 2.23.0。升级属于 2.22 → 2.23 的次版本跳跃，按 error-prone 项目的兼容性约定，注解构件在次版本升级中保持二进制兼容，不会破坏 Iceberg 现有编译产物。

## 小结

该提交通过 Dependabot 将 error-prone 注解依赖从 2.22.0 升到 2.23.0，是 Iceberg 持续依赖维护工作中的一次常规次版本升级，保持编译期静态分析注解与上游同步。
