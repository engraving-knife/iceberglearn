# 提交 0598：Build: Bump com.google.errorprone:error_prone_annotations

## 提交信息

- **序号**：0598 / 4088
- **哈希**：2dd30877885081de966b5d85edc3da31f55610ed
- **短哈希**：2dd308778
- **日期**：2024-03-18（Mon Mar 18 08:33:55 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#9972)
- **PR/Issue**：#9972（Dependabot 自动 PR）

Dependabot 元信息：
- 依赖：`com.google.errorprone:error_prone_annotations`
- 类型：`direct:production`（直接生产依赖）
- 升级类型：`version-update:semver-minor`（语义化版本的 minor 升级）
- 升级区间：2.24.1 → 2.26.1（跨 2.25.0、2.26.0、2.26.1 三个版本）

## 总体目的

这是 Dependabot 自动生成的依赖升级 PR，目标单一：把 `com.google.errorprone:error_prone_annotations` 这个编译期注解库从 2.24.1 升到 2.26.1，跟上上游 Google Error Prone 项目的最新发布，获取这两个 minor 版本里新增/修复的注解与 Bug 修复，同时维持 Iceberg 自身源码对该库的现有用法不变。

`error_prone_annotations` 是 Google Error Prone 工具的「注解 artifact」：它只包含注解类型定义（如 `@FormatMethod`、`@CanIgnoreReturnValue`、`@Immutable`、`@Var` 等），不含 Error Prone 静态分析器本体。Iceberg 把它作为 `compileOnly` 依赖引入，纯粹是为了在源码里用这些注解，让 Error Prone 在编译期做更精确的检查（例如校验格式化字符串参数、标记可忽略返回值的方法），并在生成 Javadoc 时也能保留这些语义信息。运行时不需要该库，因此 `compileOnly` 而非 `implementation`。

## 如何达成设计目的

Dependabot 采用了 Gradle 项目标准升级路径：

1. Iceberg 使用 Gradle 的 Version Catalog（`gradle/libs.versions.toml`）集中管理依赖版本，定义两行：
   - 版本变量：`errorprone-annotations = "2.24.1"`（第 20 行附近）
   - 模块坐标：`errorprone-annotations = { module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }`（第 88 行附近）
2. 升级只需改版本变量那一行：`"2.24.1"` → `"2.26.1"`。所有通过 `libs.errorprone.annotations` 引用该依赖的子模块（如 `iceberg-api` 的 `compileOnly libs.errorprone.annotations`）会自动继承新版本。
3. 由于这是 semver-minor 升级，按语义化版本承诺，2.24.1 → 2.26.1 不破坏 API 兼容性（无删除的注解、无改变语义的注解），Iceberg 现有源码里的 `@FormatMethod` / `@CanIgnoreReturnValue` / `@Immutable` / `@Var` / `@FormatString` 五种用法都继续有效，无需改动任何 `.java` 文件。
4. CI 会跑完整构建+测试来验证升级无回归，Dependabot 据此判定 PR 可合并。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `errorprone-annotations` 版本变量从 `2.24.1` 提升到 `2.26.1`。

**工作逻辑**：
```toml
-errorprone-annotations = "2.24.1"
+errorprone-annotations = "2.26.1"
```

仅此一行变更。该变量通过 `version.ref` 被 `errorprone-annotations = { module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }` 引用，再被各子模块的 `libs.errorprone.annotations` 消费。

### 该依赖在 Iceberg 中的角色与消费链路（背景说明）

为了让回迁评审者理解这一行改动的影响面，下面梳理 `error_prone_annotations` 在 Iceberg 中的完整消费链：

1. **角色**：纯编译期注解库，提供 5 类注解（Iceberg 实际使用的）：
   - `@FormatMethod` / `@FormatString`：标注「接收 printf 格式化字符串 + 可变参数」的方法（主要是各种 Exception 构造器，如 `ValidationException`、`NoSuchTableException`、`UncheckedSQLException` 等约 30+ 个异常类），让 Error Prone 在编译期校验格式串与参数类型/数量匹配，防止运行时 `IllegalFormatException`。
   - `@CanIgnoreReturnValue`：标注「调用方可以忽略返回值也不会出错」的方法，抑制 Error Prone 的 `ResultIgnored` 警告。在 Immutables 生成的 `Immutable*` Builder 类中大量出现（`core/bin/generated-sources/annotations/` 下的 `ImmutableCommitMetrics`、`ImmutableScanReport` 等）。
   - `@Immutable`：标注不可变类型，配合 Error Prone 的 `ImmutableChecker` 校验。
   - `@Var`：用于捕获可变闭包变量（较少使用，主要在 Immutables 生成代码中）。

2. **依赖声明位置**：
   - `gradle/libs.versions.toml`：版本与坐标定义（被本次提交修改）。
   - `build.gradle` 第 297 行（`project(':iceberg-api')` 块内）：`compileOnly libs.errorprone.annotations` —— 这是 Iceberg 唯一直接声明该依赖的子模块，且为 `compileOnly`，意味着只在编译 iceberg-api 时需要，不会进入运行时 classpath，不会被打进发布 jar。
   - `build.gradle` 第 259 行（`project(':iceberg-bundled-guava')` 块内）：在 shadow Guava 时 `exclude group: 'com.google.errorprone'`，避免 errorprone 注解被误打入 bundled-guava shaded jar。

3. **构建插件链路**（与本次升级无关但相关）：`build.gradle` 第 39 行 `classpath "net.ltgt.gradle:gradle-errorprone-plugin:3.1.0"` 引入了 Error Prone 的 Gradle 插件，用于在编译期运行 Error Prone 静态分析。该插件使用的是 `error_prone_core`（分析器本体），与 `error_prone_annotations`（注解 artifact）是同源不同 artifact，本次升级只动注解 artifact，不影响分析器本体版本。

4. **下游影响**：由于是 `compileOnly` 且只声明在 `iceberg-api`，下游用户（如 Spark/Flink 引擎集成 Iceberg）运行时不需要 errorprone-annotations，本次升级对最终用户运行时 classpath 零影响。唯一影响是 Iceberg 自身的编译期检查与 Javadoc 生成（注解会出现在 Javadoc 里）。

## 小结

- 这是 Dependabot 自动维护的纯依赖升级，1 行改动、0 业务代码变更、0 风险（semver-minor、`compileOnly` 依赖）。
- 升级区间 2.24.1 → 2.26.1 跨两个 minor 版本，主要获取上游 Bug 修复与小特性增强，不引入破坏性变更。
- 回迁到 1.4.x 的注意事项：
  - **版本基线差异**：1.4.x 当前的 `errorprone-annotations` 版本是 `2.3.3`（远低于 main 的 2.24.1）。直接 cherry-pick 本提交会因上下文不匹配（`2.24.1` vs `2.3.3`）产生冲突，需要手动把 1.4.x 的版本基线先对齐到 2.24.1，或直接把 1.4.x 的版本改成 2.26.1（跳过中间版本）。鉴于该依赖是 `compileOnly`，直接跳到 2.26.1 是安全的。
  - **配套升级**：若 1.4.x 上 Error Prone 插件版本（`gradle-errorprone-plugin`）也较旧，建议一并评估是否需要同步升级，但本提交本身不强制要求。
  - **验证**：回迁后跑一次 `./gradlew :iceberg-api:compileJava :iceberg-api:javadoc` 即可验证注解用法与新版本兼容。
