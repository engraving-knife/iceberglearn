# 提交 0339：Build: Bump com.google.errorprone:error_prone_annotations (#9429)

## 提交信息

- **序号**：0339
- **哈希**：be155d70ce1707cc9e70717373986620c2df5345
- **短哈希**：be155d70c
- **日期**：2024-01-08 04:19:53 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#9429)
- **PR/Issue**：#9429

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 Google Error Prone 注解库 `com.google.errorprone:error_prone_annotations` 从 `2.24.0` 升级到 `2.24.1`，属于 `version-update:semver-patch` 级别的依赖更新（仅补丁版本号变更，是本次五个提交中唯一的 patch 级升级）。

`error_prone_annotations` 是 Google Error Prone 项目的轻量注解 artifact（与 `error_prone_core`、`error_prone_check_api` 等区分），只包含一组用于在源码中标注意图的 Java 注解类型，本身不含任何静态分析逻辑——Error Prone 的实际检查器（bug pattern detector）运行在编译期由 `error_prone_core` 提供，而 `error_prone_annotations` 则是运行时/编译期被引用的"语义契约标记"。Iceberg 在 `api` 模块（`iceberg-api`）中通过 `compileOnly libs.errorprone.annotations` 引入该 artifact，把它提供的两类注解用于关键代码：(1) `@FormatMethod`（位于 `com.google.errorprone.annotations.FormatMethod`）大量标注在异常类构造器上——如 `ValidationException`、`NoSuchTableException`、`CommitFailedException` 等几乎所有 `org.apache.iceberg.exceptions` 包下的异常类，声明该构造器接受 `String format, Object... args` 并按 `String.format` 规则格式化，让 Error Prone 在编译期能静态校验调用方传入的格式串与参数类型/数量匹配（避免运行时 `IllegalFormatException`）；(2) `@Immutable`（位于 `com.google.errorprone.annotations.Immutable`）标注在 `org.apache.iceberg.transforms.Timestamps`、`Dates` 等变换类上，声明该类是不可变的，让 Error Prone 校验类所有字段都是 final 且类型本身可传递地不可变。由于这些注解是 `compileOnly`（不传递到下游运行时 classpath，但会保留在编译期 classpath 让编译器/静态分析器可见），升级 `error_prone_annotations` 主要影响 Iceberg 自身的编译期静态检查能力，不影响最终 jar 的运行时行为（注解在 class 文件中保留为 `RUNTIME` 或 `CLASS` 保留策略，但运行时无逻辑消费它们）。

`error_prone_annotations` 2.24.1 是 2.24.x 系列的第一个补丁版本，主要修复 2.24.0 引入的若干回归问题（2.24.0 是一个较大的 minor 升级，引入了对 JDK 21 的更好支持、新增若干 bug pattern、改进了对 record/sealed class 的处理）。由于是 patch 级升级，按 semver 严格保持 API 兼容，Dependabot 归类为 `version-update:semver-patch`，风险极低。Iceberg 维护者合并此 PR 即表示认可升级在 Iceberg 编译流程中的兼容性，让 Iceberg 编译期静态分析工具链跟进 Error Prone 最新稳定补丁版本，避免 2.24.0 已知的回归 bug 影响编译或误报。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `errorprone-annotations = "2.24.0"` 改为 `errorprone-annotations = "2.24.1"`。该版本常量通过 `libs.errorprone.annotations`（在 `libraries` 段定义为 `{ module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }`）被 `iceberg-api` 模块的 `build.gradle` 以 `compileOnly` 方式引用。升级后所有依赖该常量的模块会自动解析到 2.24.1，无需逐个修改模块的 `build.gradle`。这是 Gradle 版本目录带来的核心价值——单一来源（single source of truth）管理依赖版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `com.google.errorprone:error_prone_annotations` 的版本从 2.24.0 升级到 2.24.1。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于 `[versions]` 段的字母序位置（约第 38 行附近，处于 `esotericsoftware-kryo = "4.0.2"` 与 `findbugs-jsr305 = "3.0.2"` 之间），原行 `errorprone-annotations = "2.24.0"` 被改为 `errorprone-annotations = "2.24.1"`。该版本常量被 `[libraries]` 段的 `errorprone-annotations = { module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }` 通过 `version.ref` 引用，最终被 `iceberg-api` 模块的 `build.gradle` 以 `compileOnly libs.errorprone.annotations` 方式引入——`compileOnly` 意味着该 artifact 只在编译期可见、不会打包进最终 jar 传递给下游用户。升级后，`iceberg-api` 编译时解析到的 `error_prone_annotations` jar 切换到 2.24.1，源码中 `@FormatMethod`、`@Immutable` 等注解的类型定义由 2.24.1 提供；若项目在 CI 中启用了 Error Prone 编译器插件（`error_prone_core`），2.24.1 修复的回归 bug 也会让静态检查结果更准确。该升级仅影响 Iceberg 编译期注解处理与静态分析，不影响运行时行为（注解本身不含逻辑代码）。

## 小结

该提交由 Dependabot 自动将 `com.google.errorprone:error_prone_annotations` 从 2.24.0 升级到 2.24.1，使 Iceberg 编译期静态分析工具链跟进 Error Prone 2.24.x 系列的最新补丁版本，获取 2.24.0 引入的回归 bug 修复。`error_prone_annotations` 在 Iceberg `api` 模块中通过 `compileOnly` 引入，提供 `@FormatMethod`（异常构造器格式串校验）与 `@Immutable`（变换类不可变性契约）等注解，仅在编译期被 Error Prone 静态检查器消费，不影响运行时行为。改动仅一处版本常量，依赖 Gradle 版本目录机制自动传递到 `iceberg-api` 模块，属于低风险、低成本的编译期依赖维护工作。
