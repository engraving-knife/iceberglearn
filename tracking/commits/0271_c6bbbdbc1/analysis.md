# 提交 0271：Spark: Remove support for Spark 3.2 (#9295)

## 提交信息

- **序号**：0271 / 4088
- **哈希**：c6bbbdbc11d42713c86dd52185f577b81d946963
- **短哈希**：c6bbbdbc1
- **日期**：2023-12-14
- **作者**：Ajantha Bhat
- **提交说明**：Spark: Remove support for Spark 3.2 (#9295)
- **PR/Issue**：#9295

## 总体目的

Apache Iceberg 通过为每个不兼容的引擎版本维护独立的集成代码库来支持多个 Spark 版本（例如 `spark/v3.2`、`spark/v3.3`、`spark/v3.4`、`spark/v3.5`）。这种"每版本一套源码"的模式让 Iceberg 可以针对新版引擎的新特性进行开发，而不会破坏对旧引擎的兼容性，但代价是需要维护多份并行的代码库。Spark 3.2 是 Spark 3.x 系列中较早的版本，随着 Spark 3.3、3.4、3.5 的发布与广泛采用，继续维护 Spark 3.2 的支持代码成本越来越高。

本提交的目的是正式移除对 Spark 3.2 的支持。Spark 3.2 自 2021 年 10 月发布后已有较长的生命周期，社区在讨论后决定在 Iceberg 1.5.0 版本中停止维护 Spark 3.2，以便集中精力在更新版本（3.3、3.4、3.5）上。移除 Spark 3.2 的支持后，可以显著减小仓库的代码体积（本提交删除了约 9.4 万行代码与 455 个文件），降低 CI 的运行成本（少跑一个 Spark 版本的矩阵），并简化后续的代码演进工作——很多新特性无需再为 3.2 单独写一份兼容实现或维护条件分支。

从动机上看，这是一次典型的"弃用并下线"操作：先在若干版本前停止新功能适配，再通过一次大清理把整套源码、构建脚本、CI 配置、文档示例与发布脚本中涉及 Spark 3.2 的内容一次性清掉，使仓库保持精简。

## 如何达成设计目的

整体设计思路是"按层下线"：先在构建层从 Gradle 的版本目录（`gradle/libs.versions.toml`）、构建属性（`gradle.properties`）、模块注册（`settings.gradle`、`spark/build.gradle`、`jmh.gradle`）中删除 `3.2` 相关条目，让构建系统不再识别该版本；接着在发布与 CI 层（`dev/stage-binaries.sh`、`.github/workflows/spark-ci.yml`、`.github/workflows/publish-snapshot.yml`）去掉 3.2 的矩阵与发布任务；然后把整个 `spark/v3.2/` 目录连同其下约 455 个 Scala/Java 源文件与测试文件全部删除；最后在文档层（`docs/` 与 `site/docs/` 下多个文档）把示例里引用的 `3.2_2.12` 统一替换为 `3.5_2.12`，并删除针对 Spark 3.2 之前版本的兼容性说明段落。这样既保证构建与发布产物不再包含 Spark 3.2 artifact，也让文档示例反映当前最低支持版本。

## 修改详情

### `.github/workflows/publish-snapshot.yml`

**修改目的**：从快照发布流水线中移除 Spark 3.2。

**工作逻辑**：
将发布命令中的 `-DsparkVersions=3.2,3.3,3.4,3.5` 改为 `-DsparkVersions=3.3,3.4,3.5`，这样后续快照构建不再产出 `iceberg-spark-3.2_*` 系列 artifact。

### `.github/workflows/spark-ci.yml`

**修改目的**：从 CI 矩阵中移除 Spark 3.2。

**工作逻辑**：
两处构建矩阵（基础构建与扩展构建）的 `spark` 项从 `['3.2', '3.3', '3.4', '3.5']` 改为 `['3.3', '3.4', '3.5']`，减少一个版本的 CI 任务，节省运行时间。

### `.gitignore`

**修改目的**：移除针对 `spark/v3.2/spark/benchmark/*` 的忽略规则。

**工作逻辑**：
删除一行 `spark/v3.2/spark/benchmark/*`，因为该目录已不存在。

### `dev/stage-binaries.sh`

**修改目的**：从发布脚本中移除 Spark 3.2 的发布逻辑。

**工作逻辑**：
- `SPARK_VERSIONS` 从 `3.2,3.3,3.4,3.5` 改为 `3.3,3.4,3.5`；
- 删除专门为 Scala 2.13 发布 Spark 3.2 artifact 的一行 `./gradlew ... -DsparkVersions=3.2 :iceberg-spark:iceberg-spark-3.2_2.13:...`。

### `gradle.properties`

**修改目的**：从已知 Spark 版本列表中移除 3.2。

**工作逻辑**：
`systemProp.knownSparkVersions` 从 `3.2,3.3,3.4,3.5` 改为 `3.3,3.4,3.5`，默认版本仍为 3.5。

### `gradle/libs.versions.toml`

**修改目的**：从版本目录移除 Spark 3.2 依赖定义。

**工作逻辑**：
删除 `spark-hive32 = "3.2.2"` 一行，这是构建 Spark 3.2 模块所用的 `spark-hive` 依赖版本。

### `jmh.gradle`

**修改目的**：从 JMH 基准测试项目列表中移除 Spark 3.2 模块。

**工作逻辑**：
删除判断 `sparkVersions.contains("3.2")` 的代码块，该块原本会把 `:iceberg-spark:iceberg-spark-3.2_${scalaVersion}` 加入 JMH 项目集合。

### `settings.gradle`

**修改目的**：从 Gradle 模块注册中移除 Spark 3.2 的三个子模块。

**工作逻辑**：
删除一段 12 行的 `if (sparkVersions.contains("3.2"))` 块，该块原本 `include` 了 `:iceberg-spark:spark-3.2_${scalaVersion}`、`:iceberg-spark:spark-extensions-3.2_${scalaVersion}`、`:iceberg-spark:spark-runtime-3.2_${scalaVersion}`，并把它们的 `projectDir` 指向 `spark/v3.2/` 下的对应目录、把 artifact 名重命名为 `iceberg-spark-3.2_*` 等。

### `spark/build.gradle`

**修改目的**：不再 apply Spark 3.2 的子构建脚本。

**工作逻辑**：
删除 `if (sparkVersions.contains("3.2")) { apply from: file("$projectDir/v3.2/build.gradle") }` 块。

### `spark/v3.2/build.gradle` 及整个 `spark/v3.2/` 目录（约 455 个文件）

**修改目的**：彻底删除 Spark 3.2 集成模块的全部源码、扩展、运行时 jar 与测试。

**工作逻辑**：
该目录是 Iceberg 为 Spark 3.2 维护的整套独立集成代码，包括：
- 构建脚本 `spark/v3.2/build.gradle`（约 295 行）；
- SQL 扩展语法解析器与 AST 构建器（如 `IcebergSqlExtensions.g4`、`IcebergSparkSessionExtensions.scala`、`IcebergSparkSqlExtensionsParser.scala`、`IcebergSqlExtensionsAstBuilder.scala`）；
- Catalyst 分析器与重写规则（如 `RewriteMergeIntoTable`、`RewriteUpdateTable`、`RewriteDeleteFromTable`、`ResolveProcedures`、`ProcedureArgumentCoercion`、`AlignRowLevelCommandAssignments` 等）；
- 执行算子（如 `ExtendedDataSourceV2Strategy`、`MergeRowsExec`、`ReplaceDataExec`、`CallExec`、`CreateOrReplaceBranchExec`、`DropTagExec` 等）；
- 逻辑计划节点（`ReplaceData`、`MergeRows`、`UpdateIcebergTable`、`WriteDelta` 等）；
- connector 相关类（`ExtendedLogicalWriteInfoImpl`、`RowLevelOperationTable` 等）；
- 大量测试类（`TestSparkCatalog`、`TestIcebergSourceTablesBase`、`TestRewriteDataFilesAction`、`TestExpireSnapshotsAction`、`RandomData`、`TestHelpers` 等）。

一次性删除这批文件是本次提交的核心：从此仓库不再保留任何针对 Spark 3.2 的源码与测试。

### `docs/spark-getting-started.md`、`docs/spark-procedures.md`、`docs/spark-queries.md`、`docs/dell.md`、`docs/jdbc.md`

**修改目的**：更新用户文档中的示例与版本说明。

**工作逻辑**：
- `spark-getting-started.md`、`dell.md`、`jdbc.md`：把示例里的 `iceberg-spark-runtime-3.2_2.12` 改为 `iceberg-spark-runtime-3.5_2.12`，并将 dell 文档示例里的 `ICEBERG_VERSION`/`SPARK_VERSION` 同步更新到 `1.4.2`/`3.5_2.12`；
- `spark-procedures.md`：删除 `sort_order` 参数说明里 "(Supported in Spark 3.2 and Above)" 的限定语；
- `spark-queries.md`：删除针对 Spark 3.2 之前版本 session catalog 不支持多段标识符的提示块。

### `site/docs/contribute.md`、`site/docs/multi-engine-support.md`、`site/docs/spark-quickstart.md`

**修改目的**：同步更新主站文档。

**工作逻辑**：
- `contribute.md`：示例 `-DsparkVersions=3.2,3.3` 改为 `-DsparkVersions=3.4,3.5`；
- `multi-engine-support.md`：把"多版本支持"说明里的示例从 Spark 3.1/3.2 改为 3.4/3.5，运行时 jar 示例从 3.2 改为 3.5；
- `spark-quickstart.md`：多处 `--packages`、`spark.jars.packages`、jar 下载链接中的 `3.2_2.12` 改为 `3.5_2.12`。

### `site/docs/docs/nightly/docs/*`（dell、jdbc、spark-getting-started、spark-procedures、spark-queries）

**修改目的**：同步更新 nightly 站点文档。

**工作逻辑**：与上面 `docs/` 同名文件改动一致，把 3.2 相关示例改为 3.5，并删除 Spark 3.2 之前的兼容性提示。nightly 站点使用 mkdocs 风格语法（`!!! info`），与 `docs/` 下的 hugo 风格（`{{< hint info >}}`）对应。

## 小结

该提交通过一次性删除整个 `spark/v3.2/` 源码目录（约 455 个文件、9.4 万行）并同步清理 Gradle 构建、CI 矩阵、发布脚本与各级文档中的 Spark 3.2 引用，正式下线了 Iceberg 对 Spark 3.2 的支持，从而简化了多版本 Spark 集成的维护负担、降低 CI 成本，并让仓库聚焦于 Spark 3.3/3.4/3.5 三个仍然活跃的版本。
