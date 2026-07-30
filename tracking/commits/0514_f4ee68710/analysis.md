# 提交 0514：Build: Bump org.immutables:value from 2.10.0 to 2.10.1

## 提交信息

- **序号**：0514 / 4088
- **哈希**：f4ee68710dbd16f67b28b35a7db4041ca338fd1e
- **短哈希**：f4ee68710
- **日期**：2024-02-19（Mon Feb 19 10:24:19 2024 +0100）
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.10.0 to 2.10.1 (#9749)
- **PR/Issue**：#9749

## 总体目的

这是一次由 Dependabot 自动发起的依赖升级，把 `org.immutables:value` 从 2.10.0 升到 2.10.1（语义化版本中的 patch 升级）。Dependabot 在 PR 描述中标注了 `dependency-type: direct:production`、`update-type: version-update:semver-patch`，意味着这是一个直接生产依赖的小版本升级。

`org.immutables:value` 是 Iceberg 使用的**编译期注解处理器**（annotation processor），通过 `@Value.Immutable`、`@Value.Style` 等注解在编译时生成不可变值类（`ImmutableXxx`）与对应的 Builder。它不是运行时依赖——生成的代码在编译后被静态链接进各模块 jar，运行时不需要 `immutables-value` jar 在 classpath 上。

Iceberg 的提交规范是“Build: Bump ...”这类纯依赖升级由 Dependabot 提交、社区 reviewer 直接合入，不需要额外设计讨论。本次升级的目标是跟进 Immutables 上游的维护版本，获取 bug 修复与编译兼容性改进，避免长期停留在旧版本。

## 如何达成设计目的

升级方式极简：只改一个文件 `gradle/libs.versions.toml`，把版本常量 `immutables-value` 从 `"2.10.0"` 改为 `"2.10.1"`。该常量通过 `version.ref` 被 `immutables-value = { module = "org.immutables:value", version.ref = "immutables-value" }` 引用，而后者又被各模块 `build.gradle` 中的 `annotationProcessor libs.immutables.value` / `compileOnly libs.immutables.value` 引用——一改全改，无需逐模块调整。

**Immutables 2.10.1 上游变更**（来自 GitHub release notes）：
- 维护版本（maintenance release），无 API 破坏。
- 修复 #1502：`Fix 'yield outside of switch expression'`——这是与新版本 Java 编译器（switch 表达式相关）的兼容性修复，避免在生成的代码里出现 `yield` 在 switch 表达式之外的非法用法。
- 修复 #1496：`Allows NaturalOrdering for Comparable type hierarchies`——允许在实现了 `Comparable` 接口的类型层级上使用 `NaturalOrdering`，扩大了可使用的排序场景。

由于 Iceberg 只使用 Immutables 生成不可变值类（详见下文消费链路），这两个修复都不会改变 Iceberg 生成类的 API 形态，只是让生成过程更稳健、兼容更新的 JDK。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 `immutables-value` 版本常量。

**工作逻辑**：
```toml
-immutables-value = "2.10.0"
+immutables-value = "2.10.1"
```
单行改动。该常量位于 `[versions]` 段（约第 32~50 行），与 `jackson-bom`、`httpcomponents-httpclient5`、`hive2/hive3` 等版本常量并列。下游 `immutables-value = { module = "org.immutables:value", version.ref = "immutables-value" }` 在 `[libraries]` 段（第 125 行）通过 `version.ref` 引用，Gradle 会把所有依赖此 library 的位置统一升到 2.10.1。

## 消费链路：Immutables 在 Iceberg 中的角色

为了让回迁评估更完整，下面说明 `org.immutables:value` 在 Iceberg 中的具体使用方式（基于 1.4.x 当前代码库搜索）：

1. **build.gradle 配置**：在四个模块（core、api、aws/aliyun 等共 4 处 `annotationProcessor` + `compileOnly`）中以 annotationProcessor + compileOnly 的形式引入：
   ```groovy
   annotationProcessor libs.immutables.value
   compileOnly libs.immutables.value
   ```
   - `annotationProcessor`：在编译时让 Immutables 的注解处理器扫描源码中的 `@Value.Immutable` 接口/抽象类，生成 `ImmutableXxx` 实现类与 Builder，落到 `build/generated/sources/annotationProcessor/` 下。
   - `compileOnly`：让源码能引用 `@Value.Immutable`、`@Value.Style` 等注解，但不会把 immutables jar 打进运行时 classpath（因为生成的 `ImmutableXxx` 类已包含全部所需逻辑，运行时不再依赖注解处理器）。

2. **代码中的使用**：搜索结果显示有约 31 个文件使用了 `@Value.Immutable`，主要分布在：
   - `core/src/main/java/org/apache/iceberg/metrics/`：`CommitMetrics`、`CommitMetricsResult`、`ScanMetrics`、`ScanMetricsResult`、`CommitReport`、`TimerResult`、`CounterResult`、`ScanReport` 等指标与报告类。
   - `core/src/main/java/org/apache/iceberg/TableScanContext.java`：扫描上下文。
   - `core/src/main/java/org/apache/iceberg/catalog/TableCommit.java`：表提交模型。
   - `core/src/main/java/org/apache/iceberg/actions/`：`BaseRewriteManifests`、`BaseRewritePositionalDeleteFiles`、`BaseMigrateTable` 等 actions，并在这些类上同时使用 `@Value.Style` 控制生成类的可见性与 builder 命名风格。
   - 这些类通过 Immutables 生成 `ImmutableCommitMetrics`、`ImmutableScanReport` 等（可以在 `core/build/generated/sources/annotationProcessor/` 与 `core/bin/generated-sources/annotations/` 中看到生成产物）。

3. **运行时**：Iceberg 的发布 jar 不包含 immutables-value 依赖，运行时只有生成代码，因此升级版本只影响**编译期生成的代码**，不影响运行时 classpath。这意味着升级风险主要落在“重新编译后生成代码是否仍然合法”这一环节。

## 小结

本提交是一次低风险的依赖维护升级：把编译期注解处理器 `org.immutables:value` 从 2.10.0 升到 2.10.1，跟进上游两个修复（switch 表达式 yield 兼容性、Comparable 层级 NaturalOrdering 支持）。改动只有一行版本号，无源码改动，无 API 破坏。

**影响范围**：
- 仅影响编译期：重新构建时使用新版 Immutables 生成 `Immutable*` 类，运行时 classpath 不变。
- 不影响用户配置、不影响运行时行为。
- 对 Iceberg 自身代码：因为 Iceberg 只用到 `@Value.Immutable`/`@Value.Style` 基础特性，2.10.1 的两个修复不会改变生成类 API，理论上重新编译即可，无需调整源码。

**回迁到 1.4.x 注意事项**：
1. 1.4.x 当前 `immutables-value` 版本为 **2.9.2**（比 main 还落后一个 minor 版本），即 1.4.x 实际上是 2.9.2 → 2.10.1，跨了一个 minor。本提交只覆盖 2.10.0 → 2.10.1 这一步；若 1.4.x 想完整追平 main，需要先做 2.9.2 → 2.10.0 的升级，再做本提交的 2.10.0 → 2.10.1。
2. 跨 minor（2.9.x → 2.10.x）通常会有少量生成代码变化或新增功能，需要重新编译各模块并跑一遍单元测试，确认生成代码与下游用法（特别是 `@Value.Style` 自定义）兼容。
3. 由于 Immutables 是 annotationProcessor，IDE（IntelliJ）需要在升级后重新运行“Reload Gradle Project”并触发一次构建，让 `build/generated/sources/annotationProcessor/` 下的生成类被重新生成，避免 IDE 报红。
4. 升级后产物 jar 不会变化（运行时无 immutables 依赖），不会影响下游用户。
