# 提交 0331：Build: Bump com.google.errorprone:error_prone_annotations (#9369)

## 提交信息

- **序号**：0331 / 4088
- **哈希**：12b3ccdd008545dc6c9b3c4fc1a4ea15aaa385a8
- **短哈希**：12b3ccdd0
- **日期**：2024-01-05 12:59:07 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#9369)
- **PR/Issue**：#9369

## 总体目的

这是一次由 Dependabot 自动生成的依赖版本升级提交，目标是将 `com.google.errorprone:error_prone_annotations` 从 2.23.0 升级到 2.24.0。

Error Prone 是 Google 开源的一个 Java 编译时静态分析工具，它通过 hook 到 `javac` 编译器中，在编译期捕获常见的 Java 编程错误。`error_prone_annotations` 是其附带的注解 artifact（包含 `@CanIgnoreReturnValue`、`@CompatibleWith`、`@InlineMe`、`@InlineMeValidationDisabled` 等注解），被 Iceberg 作为直接生产依赖引用，主要用于在代码中标注方法的可忽略返回值、参数类型约束等，以配合 Error Prone 在编译期做检查，同时也供其他静态分析工具消费这些元数据。

Iceberg 项目通过 Gradle 版本目录（version catalog）统一管理依赖，Dependabot 会定期扫描 `gradle/libs.versions.toml` 中的依赖，发现上游有新版本发布时自动发起 PR 升级。本次升级属于 minor 版本升级（2.23.0 → 2.24.0），按照语义化版本约定通常只新增功能、修复问题，理论上不引入破坏性变更，因此可以低风险地合入。这类自动化升级的价值在于让项目持续跟进上游修复，避免长期积累后一次性升级带来的风险与工作量。

## 如何达成设计目的

提交通过修改 Gradle 版本目录文件中 `errorprone-annotations` 这一行版本声明，从 `2.23.0` 改为 `2.24.0`，即可在构建时让 Gradle 解析并拉取新版本，所有传递引用此依赖的模块都会自动跟随升级，无需逐模块修改 build 脚本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `errorprone-annotations` 依赖版本从 2.23.0 提升到 2.24.0。

**工作逻辑**：
Gradle 版本目录（version catalog）是 Gradle 7.x 引入的集中式依赖管理机制，文件中以 `key = "version"` 的形式声明依赖版本常量，再在 `[libraries]`、`[bundles]`、`[plugins]` 节通过引用这些常量定义具体依赖。本次修改只动了一行：

```toml
- errorprone-annotations = "2.23.0"
+ errorprone-annotations = "2.24.0"
```

文件第 35 行附近的版本声明区，将 `errorprone-annotations` 常量的值从 `"2.23.0"` 改为 `"2.24.0"`。由于后续 `[libraries]` 节中 `errorprone-annotations` 的依赖定义引用了此版本常量（形如 `module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations"`），所以只需改这一处常量，整个项目所有模块中引用 `libs.errorprone.annotations` 的位置都会自动使用新版本。这种集中式管理方式让依赖升级变得原子化、低风险。

## 小结

本次提交是 Iceberg 项目依赖治理流水线中的一环，由 Dependabot 自动将 `error_prone_annotations` 从 2.23.0 升级到 2.24.0（minor 升级），通过修改版本目录的单行声明完成，体现了项目对上游依赖的持续跟进能力和版本目录集中管理的便利性，为项目持续享受 Error Prone 注解生态的最新改进与修复提供了基础。
