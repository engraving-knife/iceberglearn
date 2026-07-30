# 提交 0813：Build: Clean up Jackson dependency usages (#10448)

## 提交信息
- **序号**：0813 / 4088
- **哈希**：a642a9350e06dc244ec167372985564372896850
- **短哈希**：a642a9350
- **日期**：2024-06-05 17:22:22 +0200
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build: Clean up Jackson dependency usages (#10448)
- **PR/Issue**：#10448

## 总体目的

这是一个构建脚本维护性提交，目的是清理和统一 Iceberg 各模块对 Jackson 依赖的声明方式。

Iceberg 使用 Gradle 的版本目录（version catalog）机制，在 `gradle/libs.versions.toml` 中集中声明所有依赖的坐标与版本。版本目录中已经声明了 `jackson-core`、`jackson-databind`、`jackson-bom` 等依赖别名，本应被各 `build.gradle` 引用。但在实际代码中，多个模块（`iceberg-core`、`iceberg-aws`、`iceberg-delta-lake`、`iceberg-nessie`、`iceberg-snowflake`、`iceberg-kafka-connect`、`iceberg-mr`）仍然直接以字符串字面量 `"com.fasterxml.jackson.core:jackson-core"`、`"com.fasterxml.jackson.core:jackson-databind"`、`"com.fasterxml.jackson.core:jackson-annotations"` 的形式引用 Jackson 依赖，绕过了版本目录。这导致依赖声明风格不一致、版本管理分散，违背了版本目录机制的初衷。

提交的总体目的就是：把这些散落在各 `build.gradle` 中的 Jackson 字符串字面量全部改为版本目录别名引用（`libs.jackson.core`、`libs.jackson.databind`、`libs.jackson.annotations`），统一依赖声明方式；同时修正版本目录中 `jackson-core`、`jackson-databind` 的 Maven 坐标（之前误写为 `com.fasterxml.jackson:jackson-core`，应为 `com.fasterxml.jackson.core:jackson-core`），并新增 `jackson-annotations` 别名以供 `mr` 模块使用。

## 如何达成设计目的

提交通过两个层面的修改达成目的：

1. **修正版本目录坐标并新增别名**：在 `gradle/libs.versions.toml` 中，把 `jackson-core` 和 `jackson-databind` 的 `module` 坐标从错误的 `com.fasterxml.jackson:jackson-core` / `com.fasterxml.jackson:jackson-databind` 修正为正确的 `com.fasterxml.jackson.core:jackson-core` / `com.fasterxml.jackson.core:jackson-databind`（注意 Jackson core/databind/annotations 这三个包的 groupId 是 `com.fasterxml.jackson.core`，而 `jackson-bom` 的 groupId 是 `com.fasterxml.jackson`）。同时新增 `jackson-annotations = { module = "com.fasterxml.jackson.core:jackson-annotations", version.ref = "jackson-bom" }`，统一这三个 Jackson 子模块的版本引用（都指向 `jackson-bom` 这个版本引用）。

2. **替换各模块 build.gradle 中的字符串字面量**：在 `build.gradle`（根项目脚本，包含 `iceberg-core`、`iceberg-aws`、`iceberg-delta-lake`、`iceberg-nessie`、`iceberg-snowflake` 等子项目配置）、`kafka-connect/build.gradle`、`mr/build.gradle` 中，把所有 `"com.fasterxml.jackson.core:jackson-core"`、`"com.fasterxml.jackson.core:jackson-databind"`、`"com.fasterxml.jackson.core:jackson-annotations"` 字面量分别替换为 `libs.jackson.core`、`libs.jackson.databind`、`libs.jackson.annotations`。这些模块原本都使用了 `implementation platform(libs.jackson.bom)` 引入 BOM 来对齐版本，替换后仍然由 BOM 控制版本，依赖行为保持不变。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：修正 `jackson-core`/`jackson-databind` 的 Maven 坐标，新增 `jackson-annotations` 别名。
**工作逻辑**：把 `jackson-core` 的 `module` 由 `com.fasterxml.jackson:jackson-core` 改为 `com.fasterxml.jackson.core:jackson-core`；把 `jackson-databind` 的 `module` 由 `com.fasterxml.jackson:jackson-databind` 改为 `com.fasterxml.jackson.core:jackson-databind`；新增 `jackson-annotations = { module = "com.fasterxml.jackson.core:jackson-annotations", version.ref = "jackson-bom" }`。三者的 `version.ref` 都指向 `jackson-bom`，与 BOM 一起保证版本一致。

### `build.gradle`
**修改目的**：将 `iceberg-core`、`iceberg-aws`、`iceberg-delta-lake`、`iceberg-nessie`、`iceberg-snowflake` 子项目中的 Jackson 字符串字面量替换为版本目录别名。
**工作逻辑**：在各子项目的 `dependencies` 块中，把 `implementation "com.fasterxml.jackson.core:jackson-core"` 改为 `implementation libs.jackson.core`，把 `implementation "com.fasterxml.jackson.core:jackson-databind"` 改为 `implementation libs.jackson.databind`。这些子项目原本都通过 `implementation platform(libs.jackson.bom)` 引入了 Jackson BOM，替换后版本仍由 BOM 统一管理，依赖解析结果不变。

### `kafka-connect/build.gradle`
**修改目的**：将 `iceberg-kafka-connect` 子项目中的 Jackson 字符串字面量替换为版本目录别名。
**工作逻辑**：把 `implementation "com.fasterxml.jackson.core:jackson-core"` 改为 `implementation libs.jackson.core`，把 `implementation "com.fasterxml.jackson.core:jackson-databind"` 改为 `implementation libs.jackson.databind`。同样保留了 `implementation platform(libs.jackson.bom)`。

### `mr/build.gradle`
**修改目的**：将 `iceberg-mr` 子项目中的 Jackson annotations 字符串字面量替换为版本目录别名。
**工作逻辑**：把 `testImplementation "com.fasterxml.jackson.core:jackson-annotations"` 改为 `testImplementation libs.jackson.annotations`，依赖 BOM 控制版本。

## 小结
- **成效**：统一了 Jackson 依赖的声明方式，所有模块都通过版本目录别名引用 Jackson 依赖；修正了版本目录中 `jackson-core`/`jackson-databind` 错误的 Maven 坐标；新增 `jackson-annotations` 别名。版本管理更集中、风格更一致，便于后续升级 Jackson 版本。
- **影响范围**：仅影响构建脚本（`gradle/libs.versions.toml`、`build.gradle`、`kafka-connect/build.gradle`、`mr/build.gradle`），不涉及任何 Java/源代码改动；由于保留了 BOM 引入且版本引用未变，依赖解析结果与之前等价，运行时行为零影响。
- **回迁注意事项**：1.4.x 分支可直接回迁。需要确认 1.4.x 分支上 `gradle/libs.versions.toml` 中 `jackson-core`/`jackson-databind` 的坐标是否也是错误的（`com.fasterxml.jackson:jackson-core`），如果是错误的，本提交中的坐标修正非常重要（错误的坐标会导致 Gradle 无法正确解析依赖或解析到不同的 artifact）；如果 1.4.x 上坐标已经是正确的，则只需应用别名替换部分。同时要确认 1.4.x 上各 `build.gradle` 是否仍有 Jackson 字符串字面量需要替换。
