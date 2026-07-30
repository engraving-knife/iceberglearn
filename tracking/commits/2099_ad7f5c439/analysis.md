# 提交 2099：Spark: Spark 4.0 initial support (#12494)

## 提交信息

- **序号**：2099 / 4088
- **哈希**：ad7f5c439b8392bd13d6376050e21330c2dfeced
- **短哈希**：ad7f5c439
- **日期**：2025-05-07 23:18:25 -0600
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark: Spark 4.0 initial support (#12494)
- **PR/Issue**：#12494

> **注意**：本提交在两天后被 #12998 之前的提交 2105（`a5bcacd97`）整体回退，二者需关联阅读。

## 总体目的

Apache Spark 4.0 是 Spark 4.x 系列的首个版本，相对 3.x 有大量 API 变更：Scala 默认版本提升到 2.13（不再支持 2.12）、ANTLR 升级到 4.13.1、Jackson 升级到 2.15、TableCatalog/View 相关 API 调整、Java 21 支持等。Iceberg 需要新增 `spark/v4.0/` 模块以提供对 Spark 4.0 的初始集成支持，使 Spark 4.0 用户能够使用 Iceberg 的 source、SQL 扩展、procedures、actions 等能力。

本次提交是"初始支持"：把 `spark/v3.5/` 的代码大规模复制到 `spark/v4.0/`，并针对 Spark 4.0 的 API 差异做必要调整（包名、签名、Scala 2.13 语法、ANTLR 4.13 语法等）。共新增约 580 个文件、13.2 万行，是本批提交中规模最大的一个。由于改动量大、稳定性尚未充分验证，随后被回退（见 2105）。

## 如何达成设计目的

1. **构建配置**：
   - `gradle.properties`：把 `defaultSparkVersions` 改为 `4.0`，`knownSparkVersions` 加入 `4.0`。
   - `gradle/libs.versions.toml`：新增 `spark40 = "4.0.0"`、`antlr413 = "4.13.1"` 及对应依赖别名（Spark 4.0 用 ANTLR 4.13，而 3.x 用 4.9.3）。
   - `settings.gradle`：当 `sparkVersions` 含 `4.0` 时，include 三个子项目 `iceberg-spark-4.0_2.13`、`iceberg-spark-extensions-4.0_2.13`、`iceberg-spark-runtime-4.0_2.13`，目录指向 `spark/v4.0/{spark,spark-extensions,spark-runtime}`，**固定 Scala 2.13**。
   - `spark/build.gradle`：`sparkVersions.contains("4.0")` 时 apply `v4.0/build.gradle`。
   - `spark/v4.0/build.gradle`（337 行）：定义三个子项目的依赖、sourceSets（scala+java 合并）、ANTLR 插件配置（用 `antlr413`、`-visitor -package`）、Jackson 2.15 强制版本、runtime shaded jar 排除 antlr 等。
   - `build.gradle`：全项目仓库新增 Apache Spark 临时仓库 `orgapachespark-1480`（Spark 4.0.0 候选构建所在）。
   - `jmh.gradle`：把 Spark 4.0 的 spark 与 extensions 项目加入 JMH 基准项目列表。
   - CI 工作流（`spark-ci.yml`、`java-ci.yml`、`jmh-benchmarks.yml`、`publish-snapshot.yml`、`recurring-jmh-benchmarks.yml`）：矩阵加入 `spark: '4.0'`，排除 `spark 4.0 + scala 2.12` 与 `spark 4.0 + jvm 11` 组合。
   - `.gitignore`：加入 `spark/v4.0/spark/benchmark/*` 等。

2. **核心小改动**：`core/src/main/java/org/apache/iceberg/MetadataColumns.java` 把 `_spec_id` 列从 `NestedField.required(...)` 改为 `NestedField.optional(...)`，以适配 Spark 4.0 对元数据列可空性的更严格处理。

3. **新增 `spark/v4.0/` 模块**：从 `spark/v3.5/` 复制并适配，包含：
   - `spark-extensions`：ANTLR 4 语法 `IcebergSqlExtensions.g4`（适配 ANTLR 4.13）、`IcebergSparkSessionExtensions`、catalyst analysis/logical plans/execution 一整套 Scala 类（`ResolveProcedures`、`ResolveViews`、`CreateOrReplaceBranchExec`、`CallExec` 等）。
   - `spark`：Java 主源码（`SparkCatalog`、`SparkTable`、`SparkSession` 工具、`actions`（`RewriteDataFilesSparkAction` 等）、`source`（`SparkBatch`、`SparkWrite`、`IcebergSource`）、`data` 等），以及 Scala 源码（`spark/Spark3Util.scala` 等）。
   - `spark-runtime`：聚合 shaded jar。
   - `benchmark`：JMH 基准（`PlanningBenchmark`、`DeleteFileIndexBenchmark` 等）。
   - 大量测试（`TestAggregatePushDown`、`TestAlterTable`、`TestCreateTable`、`TestSelect`、`TestStoragePartitionedJoins`、`TestFilterPushDown` 等数百个测试类），从 v3.5 复制并适配 API 差异。
   - `hive` 测试基础设施小调整（`TestHiveMetastore` 加一个字段）。

4. **Scala 2.13 适配**：Spark 4.0 仅支持 Scala 2.13，因此 v4.0 模块固定 2.13，源码中需要调整 2.12 与 2.13 不兼容的集合 API、隐式转换等。

## 修改详情

由于本提交新增约 580 个文件、13.2 万行，绝大多数是从 `spark/v3.5/` 复制并适配的源码与测试，下面仅列出构建与基础设施层面的关键文件，源码级文件不逐个展开。

### `gradle.properties` (修改, +2/-2 lines)

**修改目的**：把 Spark 4.0 纳入已知版本并设为默认。

**工作逻辑**：`defaultSparkVersions=4.0`、`knownSparkVersions=3.4,3.5,4.0`。

### `gradle/libs.versions.toml` (修改, +4/-0 lines)

**修改目的**：登记 Spark 4.0.0 与 ANTLR 4.13.1 版本及依赖别名。

**工作逻辑**：新增 `antlr413 = "4.13.1"`、`spark40 = "4.0.0"`；新增 `antlr-antlr413`、`antlr-runtime413` 两个依赖别名。

### `settings.gradle` (修改, +12/-0 lines)

**修改目的**：注册 Spark 4.0 的三个子项目。

**工作逻辑**：`sparkVersions.contains("4.0")` 时 include `spark-4.0_2.13`、`spark-extensions-4.0_2.13`、`spark-runtime-4.0_2.13`，分别指向 `spark/v4.0/{spark,spark-extensions,spark-runtime}` 目录。

### `spark/build.gradle` (修改, +4/-0 lines)

**修改目的**：在 Spark 根 build 中应用 v4.0 子构建。

**工作逻辑**：`if (sparkVersions.contains("4.0")) { apply from: file("$projectDir/v4.0/build.gradle") }`。

### `spark/v4.0/build.gradle` (新增, +337/-0 lines)

**修改目的**：定义 Spark 4.0 三个子项目的完整构建逻辑。

**工作逻辑**：
- 固定 `sparkMajorVersion='4.0'`、`scalaVersion='2.13'`。
- 三个子项目统一强制 Jackson 2.15 版本。
- `iceberg-spark-4.0_2.13`：scala+java 合并 sourceSets，依赖 iceberg 各子项目 + `spark-hive_2.13:4.0.0`（compileOnly）。
- `iceberg-spark-extensions-4.0_2.13`：应用 antlr 插件，用 `antlr413`（4.13.1）生成 SQL 扩展 parser，`-visitor -package org.apache.spark.sql.catalyst.parser.extensions`；compileOnly spark 与 spark 主项目。
- `iceberg-spark-runtime-4.0_2.13`：shaded runtime jar，排除 antlr，聚合 spark + extensions。
- 集成测试 sourceSet 用 `spark-hive_2.13:4.0.0`。

### `build.gradle` (修改, +3/-0 lines)

**修改目的**：添加 Apache Spark 临时 Maven 仓库以拉取 Spark 4.0.0 候选构建。

**工作逻辑**：全项目 `repositories` 新增 `maven { url "https://repository.apache.org/content/repositories/orgapachespark-1480/" }`。

### `jmh.gradle` (修改, +5/-0 lines)

**修改目的**：把 Spark 4.0 项目纳入 JMH 基准。

**工作逻辑**：`sparkVersions.contains("4.0")` 时把 `iceberg-spark-4.0_2.13` 与 `iceberg-spark-extensions-4.0_2.13` 加入 `jmhProjects`。

### `.github/workflows/spark-ci.yml` (修改, +4/-2 lines)

**修改目的**：CI 矩阵加入 Spark 4.0。

**工作逻辑**：矩阵 `spark` 增加 `'4.0'`；排除 `jvm:11 + spark:4.0`（Spark 4 不再支持 Java 11）与 `spark:4.0 + scala:2.12`（Spark 4 不再支持 2.12）。

### 其它 CI 工作流 (`java-ci.yml`、`jmh-benchmarks.yml`、`publish-snapshot.yml`、`recurring-jmh-benchmarks.yml`)

**修改目的**：同步 Spark 4.0 到各 CI 流水线。

### `core/src/main/java/org/apache/iceberg/MetadataColumns.java` (修改, +1/-1 lines)

**修改目的**：把 `_spec_id` 元数据列改为 optional。

**工作逻辑**：`NestedField.required(...)` → `NestedField.optional(...)`，适配 Spark 4.0 对元数据列可空性的处理。

### `hive/src/test/java/org/apache/iceberg/hive/TestHiveMetastore.java` (修改, +1/-0 lines)

**修改目的**：测试基础设施小调整以适配 Spark 4.0 集成测试。

### `spark/v4.0/**` (新增, 约 580 文件 / +132207 lines)

**修改目的**：Spark 4.0 集成的全部源码、扩展、actions、source、data、测试与基准。

**工作逻辑**：从 `spark/v3.5/` 复制并针对 Spark 4.0 API 差异适配，包括：
- SQL 扩展语法与 parser（ANTLR 4.13）。
- catalyst analysis/logical plans/execution Scala 类（branch/tag/procedure/view 等）。
- `SparkCatalog`/`SparkTable`/`SparkSession` 等 catalog 与 table 实现。
- `actions`（`RewriteDataFilesSparkAction` 等，与新 Planner/Runner API 对齐）。
- `source`（`SparkBatch`/`SparkWrite`/`IcebergSource`/`SparkMicroBatchStream` 等）。
- `data`（向读写、Avro/Parquet/ORC 转换）。
- 大量测试（`TestSelect`、`TestCreateTable`、`TestAlterTable`、`TestAggregatePushDown`、`TestStoragePartitionedJoins`、`TestFilterPushDown` 等）。
- JMH 基准（`PlanningBenchmark`、`DeleteFileIndexBenchmark`、`MergeCardinalityCheckBenchmark`、`TaskGroupPlanningBenchmark`、`UpdateProjectionBenchmark`）。

## 总结

本次提交为 Iceberg 引入 Spark 4.0 初始支持：新增 `spark/v4.0/` 模块（固定 Scala 2.13、ANTLR 4.13、Spark 4.0.0、Jackson 2.15），从 v3.5 大规模复制并适配源码与测试，调整构建配置（`settings.gradle`/`build.gradle`/`libs.versions.toml`/`gradle.properties`/CI），并把 `MetadataColumns._spec_id` 改为 optional。规模庞大（580 文件、13.2 万行），是 Iceberg 跟进 Spark 4.0 的第一步。但由于改动量大且尚未充分稳定，两天后被提交 2105（`a5bcacd97`，Revert "Spark: Spark 4.0 initial support"）整体回退，待后续重新以更稳妥的方式引入。
