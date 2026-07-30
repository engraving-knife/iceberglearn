# 提交 0038：Build: Bump com.google.errorprone:error_prone_annotations (#8776)

## 提交信息

- **序号**：0038 / 4088
- **哈希**：9210a10598f4d1c4a44ae3959fae275b8a49ebb6
- **短哈希**：9210a1059
- **日期**：2023-10-11 10:11:35 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#8776)
- **PR/Issue**：#8776

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 `com.google.errorprone:error_prone_annotations` 从 `2.3.3` 升级到 `2.22.0`，属于 `version-update:semver-minor`（次版本号升级）。虽然 Dependabot 归类为 minor，但实际跨越了近 19 个次版本（2.3.3 → 2.22.0），跨度较大，是本批升级中版本跳跃最显著的一笔。

需要区分的是，`error_prone_annotations` 只是 [Error Prone](https://github.com/google/error-prone) 项目的**注解 artifact**，仅含编译期注解（如 `@FormatMethod`、`@FormatString`、`@CompatibleWith`、`@CanIgnoreReturnValue` 等），不包含 Error Prone 编译器插件本身。注解 artifact 的特点是体积小、API 高度向后兼容——注解只在编译期用于静态检查或给 IDE/工具提供元信息，运行期通常保留但不影响逻辑。因此即便跨越近 19 个次版本，运行期风险仍很低。

在 Iceberg 中，`error_prone_annotations` 主要被 `iceberg-api` 模块使用：`api/src/main/java/org/apache/iceberg/exceptions/` 下的几乎每个异常类（如 [`CommitFailedException`](../../../../api/src/main/java/org/apache/iceberg/exceptions/CommitFailedException.java)、[`ValidationException`](../../../../api/src/main/java/org/apache/iceberg/exceptions/ValidationException.java)、[`NotFoundException`](../../../../api/src/main/java/org/apache/iceberg/exceptions/NotFoundException.java)、[`AlreadyExistsException`](../../../../api/src/main/java/org/apache/iceberg/exceptions/AlreadyExistsException.java)、`BadRequestException`、`ForbiddenException`、`RESTException`、`RuntimeIOException` 等十余个）都 `import com.google.errorprone.annotations.FormatMethod` 并在构造方法上标注 `@FormatMethod`，用于校验 `String.format` 风格的格式化字符串参数与可变参数的类型匹配，避免运行期 `IllegalFormatException`。Spark 模块（v3.2/v3.3/v3.4/v3.5）也以 `compileOnly` 方式引用该注解，并在运行时 shadow jar 中通过 `relocate 'com.google.errorprone', 'org.apache.iceberg.shaded.com.google.errorprone'`（见 [`spark/v3.3/build.gradle`](../../../../spark/v3.3/build.gradle)）重命名包以隔离。

## 如何达成设计目的

改动极简：仅在 Gradle 版本目录 [`gradle/libs.versions.toml`](../../../../gradle/libs.versions.toml) 中把 `errorprone-annotations = "2.3.3"` 改为 `errorprone-annotations = "2.22.0"`。所有引用 `libs.errorprone.annotations` 的模块会自动解析到新版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `com.google.errorprone:error_prone_annotations` 的版本从 2.3.3 升级到 2.22.0。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（约第 20 行），原行 `errorprone-annotations = "2.3.3"` 被改为 `errorprone-annotations = "2.22.0"`。该版本常量在 Iceberg 构建中被以下位置引用：

- `iceberg-api`（[`build.gradle:297`](../../../../build.gradle) `compileOnly libs.errorprone.annotations`）：为异常类提供 `@FormatMethod` 注解，以 `compileOnly` 方式声明，运行期由 `iceberg-bundled-guava` 的传递依赖或直接引入。
- `iceberg-spark` 各版本的 spark 与 spark-extensions 模块（如 [`spark/v3.3/build.gradle:63`](../../../../spark/v3.3/build.gradle)、`:155` 等 `compileOnly libs.errorprone.annotations`）：同样以 `compileOnly` 引用，并在 shadow jar 中 relocate。

由于注解 artifact API 高度稳定（主要是新增注解类型，极少删除或破坏既有注解），2.3.3 到 2.22.0 的升级主要带来新增注解（如 `@MustBeClosed`、`@InlineMe` 等更丰富的静态检查能力）和对新 JDK 版本的兼容。Iceberg 现有代码仅使用 `@FormatMethod`，该注解在 2.x 全程稳定，因此本次升级对运行期行为无影响。

## 小结

该提交由 Dependabot 将 error_prone_annotations 从 2.3.3 升级到 2.22.0，使 Iceberg 的异常类格式化注解跟上 Error Prone 项目的最新版本，因注解 artifact API 高度向后兼容且仅编译期使用，运行期风险很低。
