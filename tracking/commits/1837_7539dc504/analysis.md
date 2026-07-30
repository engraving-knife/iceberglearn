# 提交 1837：Build: Rename versions.spark.hive3* to versions.spark3* (#12489)

## 提交信息

- **序号**：1837 / 4088
- **哈希**：7539dc504cd61dc6e04e9b12f2f467f1b9e6aee5
- **短哈希**：7539dc504
- **日期**：2025-03-10 11:01:29 +0100
- **作者**：Cheng Pan
- **提交说明**：Build: Rename versions.spark.hive3* to versions.spark3* (#12489)
- **PR/Issue**：#12489

## 总体目的

本提交将 Gradle version catalog 中 Spark 版本变量的命名从 `spark-hive34`/`spark-hive35` 重命名为 `spark34`/`spark35`，并同步更新所有引用处。在此之前，Spark 版本变量以 `spark-hive3x` 命名，这是因为 Iceberg 的 Spark 集成依赖的是 `spark-hive` artifact（而非纯 `spark` artifact）。然而，版本号本身描述的是 Spark 的版本（3.4.x / 3.5.x），而非 Hive 的版本，`hive` 前缀在版本变量名中容易造成混淆。

重命名后，变量名 `spark34`/`spark35` 更清晰地表达"这是 Spark 3.4 / 3.5 的版本号"这一语义，提高了构建脚本的可读性和可维护性。这是一个纯重构（rename）操作，不改变任何依赖的实际版本号或行为。

## 如何达成设计目的

整体思路是"改声明 + 改引用"两步走。首先在 `gradle/libs.versions.toml` 中将版本变量 `spark-hive34`/`spark-hive35` 重命名为 `spark34`/`spark35`（值不变，仍为 3.4.4 / 3.5.5）；然后在所有引用 `${libs.versions.spark.hive34.get()}` 和 `${libs.versions.spark.hive35.get()}` 的 build.gradle 文件中，将引用改为 `${libs.versions.spark34.get()}` 和 `${libs.versions.spark35.get()}`。注意 Gradle version catalog 的命名规则：toml 中的 `spark34` 在 build.gradle 中通过 `libs.versions.spark34.get()` 引用（点号分隔转换为驼峰）。

## 修改详情

### `gradle/libs.versions.toml` (修改, 2 lines)

**修改目的**：重命名 Spark 版本变量。

**工作逻辑**：将 `spark-hive34 = "3.4.4"` 改为 `spark34 = "3.4.4"`，将 `spark-hive35 = "3.5.5"` 改为 `spark35 = "3.5.5"`。版本值不变，仅改变量名。在 Gradle version catalog 中，带连字符的变量名 `spark-hive34` 在 Groovy DSL 中通过 `libs.versions.spark.hive34.get()` 引用（连字符转为属性访问层级），而 `spark34` 则通过 `libs.versions.spark34.get()` 引用。

### `build.gradle` (修改, 1 line)

**修改目的**：更新 iceberg-delta-lake 模块中的 Spark 版本引用。

**工作逻辑**：在 `iceberg-delta-lake` 项目的 integrationImplementation 依赖中，将 `${libs.versions.spark.hive35.get()}` 改为 `${libs.versions.spark35.get()}`。此处引用的是 `spark-hive` artifact，版本号使用重命名后的变量。

### `spark/v3.4/build.gradle` (修改, 3 lines)

**修改目的**：更新 Spark 3.4 模块中的 Spark 版本引用。

**工作逻辑**：在三处 `spark-hive_${scalaVersion}` 依赖声明中（iceberg-spark 项目的 compileOnly、iceberg-spark-extensions 项目的 compileOnly、iceberg-spark-runtime 项目的 integrationImplementation），将 `${libs.versions.spark.hive34.get()}` 改为 `${libs.versions.spark34.get()}`。

### `spark/v3.5/build.gradle` (修改, 3 lines)

**修改目的**：更新 Spark 3.5 模块中的 Spark 版本引用。

**工作逻辑**：与 spark/v3.4 类似，在三处 `spark-hive_${scalaVersion}` 依赖声明中，将 `${libs.versions.spark.hive35.get()}` 改为 `${libs.versions.spark35.get()}`。

## 小结

本提交是构建脚本的重构性重命名，将 `spark-hive34`/`spark-hive35` 重命名为 `spark34`/`spark35`，使变量名更准确地表达 Spark 版本语义。改动涉及 4 个文件、9 处引用，不改变任何实际依赖版本或构建行为。回迁到 1.4.x 时需确保所有引用点同步修改，否则构建会因找不到变量而失败。由于是纯重命名，回迁风险低。
