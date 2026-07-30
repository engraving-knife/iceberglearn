# 提交 0977：Build: Support building with Java 21 (#10474)

## 提交信息

- **序号**：0977 / 4088
- **哈希**：7bced33132088857e790cf22b857ab8840dd1408
- **短哈希**：7bced3313
- **日期**：2024-07-25 14:45:48 +0200
- **作者**：Piotr Findeisen
- **提交说明**：Build: Support building with Java 21 (#10474)
- **PR/Issue**：#10474

## 总体目的

Java 21 是最新的 LTS（长期支持）版本，社区和用户逐渐将构建与运行环境迁移到 Java 21。此前 Iceberg 的构建与 CI 仅官方支持 JDK 8、11、17，使用 JDK 21 构建会直接抛出 `GradleException`（build.gradle 中显式校验 JDK 版本）。本提交的目的是让 Iceberg 能够在 JDK 21 环境下成功构建与测试，并将 JDK 21 纳入官方支持矩阵与 CI 矩阵。

需要解决的具体障碍包括：

1. `build.gradle` 中显式拒绝非 8/11/17 的 JDK，需放行 21。
2. `baseline.gradle` 中应用的 Spotless 插件（依赖 Google Java Format）在 JDK 21 上无法正常工作（Google Java Format 需升级到 1.17.0+ 才支持 JDK 21，但升级会要求放弃 JDK 8 支持），需在 JDK 21 下临时禁用 Spotless。
3. Spark 3.3/3.4 在 Java 21 上会失败（SPARK-42369），Spark 3.5 是首个不失败的版本，完整 Java 21 支持要等 Spark 4（SPARK-43831），因此 CI 矩阵需排除 Spark 3.3/3.4 与 Java 21 的组合。
4. Flink 1.17 不支持 Java 21（且本身已不支持 Java 17），需在 Flink CI 矩阵中排除该组合。
5. Spark 模块在 Scala 2.12 下，传递依赖 `scala-collection-compat_2.12` 会拉入 Scala 2.12.17，而 Java 21 需要 Scala 2.12.18，需显式强制升级 scala-library 版本。

## 如何达成设计目的

整体策略是"放行 + 适配 + 排除"：

1. **放行 JDK 21**：在 `build.gradle`、`jmh.gradle` 的版本校验中加入 JDK 21；将 JDK 17 与 21 合并到同一处理分支，复用 `--add-opens` 参数，并动态使用 `JavaVersion.current().getMajorVersion()` 作为 `jdkVersion`，避免硬编码。
2. **临时禁用 Spotless**：在 `baseline.gradle` 中判断当前是否为 JDK 21，若是则不应用 Spotless 插件，并注册一个会抛异常的 `spotlessApply` 任务提示用户切换 JDK；否则正常应用。
3. **CI 矩阵扩展**：在 `java-ci`、`spark-ci`、`flink-ci`、`hive-ci`、`delta-conversion-ci` 五个 workflow 的 JVM 矩阵中加入 `21`，并在 `spark-ci`、`flink-ci` 中通过 `exclude` 排除不兼容的组合。
4. **Scala 版本修正**：在 Spark 3.3/3.4/3.5 三个模块的 `build.gradle` 中，当 `scalaVersion == '2.12'` 时显式声明 `org.scala-lang:scala-library:2.12.18`，覆盖传递依赖拉入的 2.12.17。
5. **文档更新**：在 `README.md`、`site/docs/contribute.md` 中将构建说明从 "Java 8, 11, or 17" 更新为 "Java 8, 11, 17, or 21"。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml`

**修改目的**：在 Delta 转换模块 CI 的 JVM 矩阵中加入 Java 21。

**工作逻辑**：将该文件中两个 job 的 `matrix.jvm` 从 `[8, 11, 17]` 改为 `[8, 11, 17, 21]`。共两处改动。

### `.github/workflows/flink-ci.yml`

**修改目的**：在 Flink CI 矩阵中加入 Java 21，并排除不兼容的 Flink 1.17 + Java 21 组合。

**工作逻辑**：将 `matrix.jvm` 改为 `[8, 11, 17, 21]`，并在 `exclude` 列表中新增一条排除项：`jvm: 21` 与 `flink: '1.17'`（注释说明 Flink 1.17 不支持 Java 21，与既有的 Flink 1.17 不支持 Java 17 排除项并列）。

### `.github/workflows/hive-ci.yml`

**修改目的**：在 Hive CI 的两个 job 的 JVM 矩阵中加入 Java 21。

**工作逻辑**：两处 `matrix.jvm` 由 `[8, 11, 17]` 改为 `[8, 11, 17, 21]`。

### `.github/workflows/java-ci.yml`

**修改目的**：在核心 Java CI 的三个 job 的 JVM 矩阵中加入 Java 21。

**工作逻辑**：三处 `matrix.jvm` 由 `[8, 11, 17]` 改为 `[8, 11, 17, 21]`。

### `.github/workflows/spark-ci.yml`

**修改目的**：在 Spark CI 矩阵中加入 Java 21，并排除 Spark 3.3/3.4 与 Java 21 的不兼容组合。

**工作逻辑**：将 `matrix.jvm` 改为 `[8, 11, 17, 21]`，新增 `exclude` 列表，排除 `jvm: 21` + `spark: '3.3'` 和 `jvm: 21` + `spark: '3.4'` 两组。注释引用 SPARK-42369（Spark 3.5 是首个不失败的版本）和 SPARK-43831（完整 Java 21 支持要等 Spark 4）。

### `README.md`

**修改目的**：更新构建说明中的 Java 版本列表。

**工作逻辑**：将 "Iceberg is built using Gradle with Java 8, 11, or 17." 改为 "Iceberg is built using Gradle with Java 8, 11, 17, or 21."。

### `baseline.gradle`

**修改目的**：在 JDK 21 下临时禁用 Spotless 插件，避免构建失败。

**工作逻辑**：原先无条件 `apply plugin: 'com.diffplug.spotless'`。改为条件分支：若当前 JDK 为 21，则注册一个 `spotlessApply` 任务，其 `doLast` 抛出 `GradleException`，提示用户 Spotless 在 JDK 21 下已禁用（直到放弃 JDK 8 支持），需切换到其他 JDK 版本运行 spotlessApply；否则（JDK 8/11/17）正常应用 Spotless 插件。注释说明：升级 Google Java Format 到 1.17.0+ 可在 JDK 21 上运行 Spotless，但那会要求放弃 JDK 8 支持，故暂走禁用路线。

### `build.gradle`

**修改目的**：放行 JDK 21 构建，复用 JDK 17 的 JVM 参数配置。

**工作逻辑**：两处改动：

1. 将 JDK 17 的判断分支 `else if (JavaVersion.current() == JavaVersion.VERSION_17)` 改为 `else if (JavaVersion.current() == JavaVersion.VERSION_17 || JavaVersion.current() == JavaVersion.VERSION_21)`，并将硬编码的 `project.ext.jdkVersion = '17'` 改为动态 `project.ext.jdkVersion = JavaVersion.current().getMajorVersion().toString()`（在 JDK 21 下返回 "21"）。该分支的 `--add-opens` 参数列表对 JDK 21 同样适用。
2. 将最终 `else` 分支的错误信息由 "must be run with JDK 8 or 11 or 17" 改为 "must be run with JDK 8 or 11 or 17 or 21"。

### `jmh.gradle`

**修改目的**：允许 JMH 基准测试在 JDK 21 下运行。

**工作逻辑**：将版本校验条件加入 `jdkVersion != '21'`，并将错误信息更新为 "must be run with JDK 8 or JDK 11 or JDK 17 or JDK 21"。

### `site/docs/contribute.md`

**修改目的**：同步贡献文档中的 Java 版本说明。

**工作逻辑**：将 "Iceberg is built using Gradle with Java 8, 11, or 17." 改为 "Iceberg is built using Gradle with Java 8, 11, 17, or 21."。

### `spark/v3.3/build.gradle`

**修改目的**：修正 Scala 2.12 下传递依赖的 Scala 版本，使其支持 Java 21。

**工作逻辑**：在该文件的 `iceberg-spark` 项目和 `iceberg-spark-extensions` 项目的 `dependencies` 块中各新增一段条件依赖：当 `scalaVersion == '2.12'` 时，显式声明 `implementation 'org.scala-lang:scala-library:2.12.18'`。注释说明 `scala-collection-compat_2.12` 会拉入 Scala 2.12.17，而 Java 21 支持需要 2.12.18。共两处新增。

### `spark/v3.4/build.gradle`

**修改目的**：同上，为 Spark 3.4 模块修正 Scala 2.12 版本。

**工作逻辑**：与 `spark/v3.3/build.gradle` 完全相同的两处条件依赖新增。

### `spark/v3.5/build.gradle`

**修改目的**：同上，为 Spark 3.5 模块修正 Scala 2.12 版本。

**工作逻辑**：与 `spark/v3.3/build.gradle` 完全相同的两处条件依赖新增。

## 小结

- **成效**：Iceberg 现可使用 JDK 21 进行构建与测试，JDK 21 被纳入官方支持矩阵；CI 矩阵在 java/spark/flink/hive/delta-conversion 五条流水线上均加入 Java 21，并正确排除了 Spark 3.3/3.4、Flink 1.17 与 Java 21 的不兼容组合；Scala 2.12 传递依赖问题已通过显式升级到 2.12.18 解决。
- **影响范围**：涉及 5 个 CI workflow、4 个 Gradle 构建脚本（build.gradle、baseline.gradle、jmh.gradle、3 个 spark build.gradle）、2 个文档文件，共 13 个文件。属构建/CI 层面改动，不改产品运行时行为。
- **回迁到 1.4.x 的注意事项**：该提交是构建工具链增强，原则上可回迁到 1.4.x 以便 1.4.x 也能用 JDK 21 构建。回迁时需注意：(1) 1.4.x 的 Spark 模块版本范围（可能仍含 3.2/3.3）与 main 不同，需核对排除矩阵是否适用；(2) `baseline.gradle` 中 Spotless 禁用逻辑、`build.gradle` 中 JDK 版本分支需与 1.4.x 既有结构对齐；(3) Scala 2.12.18 升级对 1.4.x 同样必要。整体回迁风险中等，主要是逐文件核对差异，无运行时兼容性问题。若 1.4.x 无需官方支持 Java 21，也可不回迁。
