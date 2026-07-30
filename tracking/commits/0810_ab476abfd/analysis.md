# 提交 0810：Build: Bump com.google.errorprone:error_prone_annotations (#10418)

## 提交信息

- **序号**：0810 / 4088
- **哈希**：ab476abfdbdb7fbfc07751c4fc1ba4f32870a4e7
- **短哈希**：ab476abfd
- **日期**：2024-06-04 08:23:04 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#10418)
- **PR/Issue**：#10418（Dependabot 自动创建）

## 总体目的

本提交由 Dependabot 自动生成，目的是将 Google Error Prone 的注解包 `com.google.errorprone:error_prone_annotations` 从 `2.27.0` 升级到 `2.28.0`。这是一次 semver-minor 级别的依赖升级（update-type: version-update:semver-minor），用于获取 Error Prone 在 2.28.0 版本中新增的注解与检查规则改进，并保持依赖更新。

需要区分两个相关但不同的产物：
- **`error_prone_annotations`**（本提交升级的对象）：纯注解包，提供 `@FormatMethod`、`@FormatString`、`@Immutable`、`@InlineMe`、`@CanIgnoreReturnValue`、`@CheckReturnValue`、`@Keep` 等注解。这些注解在编译时被 Error Prone 静态分析工具消费，运行时仅作为元数据保留。Iceberg 将其作为 `compileOnly` 依赖引入，用于标注代码以获得更好的静态检查。
- **`error_prone_core`**：Error Prone 编译器插件本身，仅在构建时通过 `gradle-errorprone-plugin` / `palantir-baseline-error-prone` 插件运行静态检查，不随产品发布。

Iceberg 代码中实际使用了 `error_prone_annotations` 的三种注解：
- `@FormatMethod` / `@FormatString`：标记异常类（如 `ValidationException`、`CommitFailedException` 等几乎所有 `org.apache.iceberg.exceptions` 下的异常）和工具类（如 `UncheckedInterruptedException`）的格式化字符串构造方法，让 Error Prone 在编译期校验格式串与参数类型/数量匹配，避免运行时 `IllegalFormatException`。
- `@Immutable`：标记 `Dates`、`Timestamps` 等 transform 类为不可变，让 Error Prone 验证其所有字段确实不可变。

升级这些注解包版本不会改变 Iceberg 运行时行为，仅影响编译期静态检查能力与字节码中保留的注解元数据。

## 如何达成设计目的

Dependabot 扫描到 `gradle/libs.versions.toml` 中 `errorprone-annotations` 版本落后，自动提交 PR 将版本字符串从 `2.27.0` 改为 `2.28.0`。由于项目使用 Gradle Version Catalog 集中管理版本，`errorprone-annotations` 通过 `version.ref` 被 `com.google.errorprone:error_prone_annotations` 库引用，只需修改这一处版本号即可。

升级内容（2.27.0 → 2.28.0）：Error Prone 的 minor 版本迭代，按其语义化版本策略可能新增注解类型与检查规则，保持已有 API 兼容。Iceberg 使用的 `@FormatMethod`、`@FormatString`、`@Immutable` 均为长期稳定的核心注解，不受 minor 升级影响。

影响路径（基于 `libs.errorprone.annotations` 与 `com.google.errorprone` 包名的引用）：
- `build.gradle`（根项目）：`compileOnly libs.errorprone.annotations`（约 line 303），以及一处 `exclude group: 'com.google.errorprone'`（line 265，排除某传递依赖中的旧版 errorprone）。
- `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 的 `build.gradle`：各两处 `compileOnly libs.errorprone.annotations`。
- `flink/v1.17`、`flink/v1.18`、`flink/v1.19`、`spark/v3.3`~`v3.5`、`hive-runtime`、`gcp-bundle` 的 `build.gradle`：shadow jar 中 `relocate 'com.google.errorprone', 'org.apache.iceberg*.shaded.com.google.errorprone'`，把 errorprone 注解包重定位到 Iceberg 自有命名空间，避免与下游用户依赖冲突。
- `baseline.gradle` / `jmh.gradle`：构建时通过 `palantir-baseline-error-prone` 插件运行 Error Prone 静态检查（jmh 模块关闭该检查）。这部分使用的是 `error_prone_core`（构建工具链），与本次升级的注解包版本无直接绑定，但版本对齐有利于检查规则与注解语义一致。
- 源码使用：`api` 模块（异常类、`Dates`、`Timestamps`）、`core` 模块（`UncheckedInterruptedException` 等）等 Java 源文件 import 并使用 `@FormatMethod`/`@FormatString`/`@Immutable`。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Error Prone 注解包版本从 2.27.0 升级到 2.28.0。

**工作逻辑**：

```toml
# 修改前
errorprone-annotations = "2.27.0"

# 修改后
errorprone-annotations = "2.28.0"
```

该版本号通过 `version.ref = "errorprone-annotations"` 被 `errorprone-annotations = { module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }` 引用。所有以 `libs.errorprone.annotations` 引入该依赖的 `compileOnly` 配置（根 build.gradle、spark 各版本 build.gradle）会自动使用新版本。这是 Version Catalog 的单点修改，无需改动任何 build.gradle。

## 小结

- **成效**：将 Error Prone 注解包升级到 2.28.0，获取 minor 版本的新增注解与检查规则改进，保持依赖更新，使编译期静态检查能力与上游同步。
- **影响范围**：影响所有使用 `@FormatMethod`/`@FormatString`/`@Immutable` 注解的源码编译（主要是 `api`、`core` 模块的异常类与 transform 类），以及 spark、flink、hive-runtime、gcp-bundle 等模块 shadow jar 中的 errorprone 包重定位。由于注解包仅提供编译期元数据，运行时行为不受影响。
- **兼容性**：semver-minor 升级，API 向后兼容。Iceberg 使用的三种注解（`@FormatMethod`、`@FormatString`、`@Immutable`）均为 Error Prone 长期稳定的核心注解，2.28.0 完整保留，源码无需任何适配。注解在字节码中按 `@Retention`（多为 `CLASS` 或 `RUNTIME`）保留，下游消费方只要 errorprone 版本 ≥ Iceberg 编译版本即可正常读取。
- **回迁注意事项**：(1) 依赖升级类提交，回迁到 1.4.x 安全且推荐，可保持 1.4.x 与 main 的依赖版本一致；(2) 回迁时需确认 1.4.x 的 `gradle/libs.versions.toml` 中 `errorprone-annotations` 当前版本（若已通过其它 backport 升级到 ≥2.28.0，则无需回迁）；(3) 由于 Iceberg 通过 shadow jar `relocate` 了 `com.google.errorprone` 包，下游用户使用 Iceberg 发布物时不会暴露原始 errorprone 依赖，本升级对下游二进制兼容性影响极小；(4) 若 1.4.x 构建时启用 Error Prone 静态检查（baseline-error-prone 插件），升级后可能因新增/强化的检查规则暴露新的警告，需关注构建日志，但通常不会阻断发布（检查多配置为非致命）；(5) 回迁后建议运行 `api`、`core` 模块编译与相关单元测试验证无回归。
