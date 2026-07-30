# 提交 3046：Spark: Initial support for 4.1.0

## 提交信息

- **序号**：3046 / 4088
- **哈希**：317968426afb0a254ee3b8cd930578c774ac4fdb
- **短哈希**：317968426
- **日期**：2025-12-22
- **作者**：manuzhang
- **提交说明**：Spark: Initial support for 4.1.0
- **PR/Issue**：无（提交说明未带编号；属主线功能提交）

## 总体目的

Apache Spark 4.1.0 即将发布，Iceberg 作为深度集成 Spark 的数据湖表格式需要同步跟上上游版本节奏，提前建立 `spark/v4.1` 模块并打通构建、CI、发布与测试链路。此提交是 Spark 4.1 的“初始支持（Initial support）”，即在已有 `spark/v4.0` 模块的基础上，将 4.1 目录从原先照搬 4.0 的占位状态改造为真正面向 Spark 4.1.0 的可构建子项目。

具体动机体现在几个方面：第一，Spark 4.1 在 Catalyst/SQL 层做了若干 API 调整（如 `Origin` case class 增加字段、`DataSourceV2Relation.create` 增加参数、`MemoryStream` 迁移包路径与构造签名、`QueryCompilationErrors` 方法重命名、`SubstituteUnresolvedOrdinals` 行为变化等），Iceberg 的扩展解析器与视图解析逻辑必须随之适配，否则编译或运行会失败。第二，Spark 4.1 引入了 Geography/Geometry 这两种新的空间类型（`GeographyVal`、`GeometryVal`），Iceberg 的 `StructInternalRow` 与 `SparkParquetReaders` 需要实现对应的 `getGeography`/`getGeometry` 访问器以维持 InternalRow 接口完整性。第三，Spark 4.1 在错误信息与行为上也有变化（如 `rewrite_manifests` 重复命名参数的错误码从 `UNRECOGNIZED_PARAMETER_NAME` 变为 `DUPLICATE_ROUTINE_PARAMETER_ASSIGNMENT`、不支持的 MERGE/UPDATE 报错文案改变、部分列 INSERT 在 4.1 已可用等），相关断言需同步更新。第四，需要把 4.1 纳入 CI 矩阵、发布脚本、默认构建版本与 JMH 基准工程，使新版本成为一等公民。

值得注意的是，此提交暂时保留了 datafusion-comet 对 4.0 的依赖（带 TODO 注释），说明 comet 尚未发布针对 Spark 4.1 的构件，这是后续待补的缺口。

## 如何达成设计目的

整体思路是“复制 4.0 模块结构 + 逐文件适配 4.1 API 差异 + 接入构建/CI/发布流水线”。改动覆盖三个层面：构建与基础设施（`settings.gradle`、`spark/build.gradle`、`gradle.properties`、`gradle/libs.versions.toml`、`dev/stage-binaries.sh`、`.github/workflows/spark-ci.yml`、`jmh.gradle`、`.gitignore`）、生产源码适配（`spark/v4.1` 下的 Java/Scala 源文件）、测试与基准适配（测试断言、benchmark 注释、JMH 工程注册）。`spark/v4.1/build.gradle` 中专门为 Scala 编译设置了 `-release:17`，用于解决 `ThetaSketchAgg.scala` 因 `java.lang.Record` 找不到而编译失败的问题。

## 修改详情

### `.github/workflows/spark-ci.yml` (+6/-1 lines)

**修改目的**：将 Spark 4.1 纳入 CI 测试矩阵。

**工作逻辑**：在 `matrix.spark` 列表中新增 `'4.1'`，并补充与 4.0 一致的排除规则——`jvm: 11` 不跑 4.1（4.x 要求 JDK 17/21）、`spark: '4.1'` 不与 `scala: '2.12'` 组合（4.x 仅支持 Scala 2.13）。这样 4.1 会在 JDK 17/21 × Scala 2.13 下被验证。

### `.gitignore` (+2/-0 lines)

**修改目的**：忽略 4.1 模块下 JMH 产生的 benchmark 输出目录。

**工作逻辑**：新增 `spark/v4.1/spark/benchmark/*` 与 `spark/v4.1/spark-extensions/benchmark/*` 两行，与 3.4/3.5/4.0 保持一致，避免基准结果被误提交。

### `dev/stage-binaries.sh` (+1/-1 lines)

**修改目的**：发布脚本纳入 Spark 4.1 制品。

**工作逻辑**：将 `SPARK_VERSIONS` 从 `3.4,3.5,4.0` 改为 `3.4,3.5,4.0,4.1`，使 Apache 发布流程一并构建并发布 4.1 的构件到 Maven 仓库。

### `gradle.properties` (+2/-2 lines)

**修改目的**：将默认与已知 Spark 版本集合升级到包含 4.1。

**工作逻辑**：`systemProp.defaultSparkVersions` 由 `4.0` 改为 `4.1`（即默认只构建 4.1），`systemProp.knownSparkVersions` 由 `3.4,3.5,4.0` 改为 `3.4,3.5,4.0,4.1`。这意味着开发者默认构建会聚焦 4.1，而完整构建仍覆盖历史版本。

### `gradle/libs.versions.toml` (+1/-0 lines)

**修改目的**：声明 Spark 4.1.0 版本号。

**工作逻辑**：新增 `spark41 = "4.1.0"` 版本目录条目，供 `spark/v4.1/build.gradle` 通过 `libs.versions.spark41.get()` 引用。

### `jmh.gradle` (+5/-0 lines)

**修改目的**：将 4.1 的 spark 与 spark-extensions 模块注册为 JMH 基准工程。

**工作逻辑**：当 `sparkVersions` 包含 `"4.1"` 时，把 `:iceberg-spark:iceberg-spark-4.1_2.13` 与 `:iceberg-spark:iceberg-spark-extensions-4.1_2.13` 加入 `jmhProjects`，使其应用 jmh 插件并参与基准构建。

### `settings.gradle` (+12/-0 lines)

**修改目的**：在 Gradle 设置中声明 4.1 的三个子工程及其目录/ artifact 名称映射。

**工作逻辑**：`include` 三个工程 `spark-4.1_2.13`、`spark-extensions-4.1_2.13`、`spark-runtime-4.1_2.13`，并将 `projectDir` 指向 `spark/v4.1/{spark,spark-extensions,spark-runtime}`，`name` 重写为 `iceberg-*` 前缀。与 4.0 的注册方式完全对称。

### `spark/build.gradle` (+4/-0 lines)

**修改目的**：在 spark 聚合构建脚本中应用 4.1 的子构建脚本。

**工作逻辑**：新增 `if (sparkVersions.contains("4.1")) { apply from: file("$projectDir/v4.1/build.gradle") }`，使 4.1 的具体工程配置被加载。

### `spark/v4.1/build.gradle` (+17/-7 lines)

**修改目的**：将 4.1 子构建从“占位的 4.0 副本”切换为真正面向 Spark 4.1.0 的配置。

**工作逻辑**：
- `sparkMajorVersion` 由 `'4.0'` 改为 `'4.1'`，日志与 artifact 名随之变化。
- 所有 `spark-hive` 依赖从 `libs.versions.spark40` 切换到 `libs.versions.spark41`。
- datafusion-comet 依赖暂仍指向 `comet-spark-spark4.0_2.13`（带 `// TODO: datafusion-comet Spark 4.1 support`），因 comet 尚未发布 4.1 构件。
- 关键修复：为 Scala 编译任务显式设置 `sourceCompatibility/targetCompatibility = "17"` 并追加 `-release:17`，解决 `ThetaSketchAgg.scala:52` 报 `Class java.lang.Record not found` 的编译错误——这是因为 Spark 4.1 依赖的 Scala 编译器在更高 JDK 下需要显式限定 release 才能正确解析 `Record`。

### `spark/v4.1/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala` (+1/-1 lines)

**修改目的**：适配 Spark 4.1 中 `Origin` case class 新增字段。

**工作逻辑**：异常构造处的模式匹配由 `Origin(Some(l), Some(p), Some(_), Some(_), Some(_), Some(_), Some(_), _, _)`（9 个字段）改为末尾再加一个 `_`（10 个字段），匹配 Spark 4.1 给 `Origin` 增加的成员，否则编译期模式匹配会因 arity 不符而失败。

### `spark/v4.1/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala` (+2/-4 lines)

**修改目的**：适配 Spark 4.1 视图解析流程的变化。

**工作逻辑**：`rewriteIdentifiers` 中移除了对 `SubstituteUnresolvedOrdinals.apply(...)` 的调用，仅保留 `CTESubstitution.apply(plan)`。注释也由“Substitute CTEs and Unresolved Ordinals”简化为“Rewrite unresolved functions and relations”。原因是在 Spark 4.1 中 `SubstituteUnresolvedOrdinals` 已被合并/移除或不再需要在此处显式调用，避免重复替换导致行为异常。

### `spark/v4.1/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ViewUtil.scala` (+1/-1 lines)

**修改目的**：适配 `QueryCompilationErrors` API 重命名。

**工作逻辑**：`missingCatalogAbilityError(plugin, "views")` 改为 `missingCatalogViewsAbilityError(plugin)`。Spark 4.1 把泛化的“缺少 catalog 能力”错误拆分为针对 views 的专用方法，调用方需同步改名。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/BaseCatalog.java` (+11/-0 lines)

**修改目的**：实现 Spark 4.1 新增的 `listProcedures` 接口方法。

**工作逻辑**：新增 `listProcedures(String[] namespace)`，当 namespace 为 system 命名空间时，从 `SparkProcedures.names()` 返回所有已注册过程标识符，否则返回空数组。这是 Spark 4.1 在 `Catalog` 接口层面要求的能力，使 `SHOW PROCEDURES` 等语法可列出 Iceberg 提供的存储过程。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java` (+5/-0 lines)

**修改目的**：暴露已注册过程名集合以支撑 `listProcedures`。

**工作逻辑**：新增静态方法 `names()` 返回 `BUILDERS.keySet()`，即所有过程名。配合 `BaseCatalog.listProcedures` 实现，让 4.1 的 catalog 能枚举可用过程。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+2/-1 lines)

**修改目的**：适配 `DataSourceV2Relation.create` 新签名。

**工作逻辑**：`createRelation` 中调用 `DataSourceV2Relation.create(sparkTable, Option.empty(), Option.empty(), options)` 增加第五个参数 `Option.empty()`。Spark 4.1 给该方法增加了（大概是 tableIdentifier 或 partitioning 之类的）可选参数，必须补齐才能编译。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java` (+12/-0 lines)

**修改目的**：支持 Spark 4.1 新增的 Geography/Geometry 类型读取。

**工作逻辑**：导入 `GeographyVal`/`GeometryVal`，并在内部 `RowPositionReaders`（基于 `SpecializedGetters` 的内部类）中实现 `getGeography(int ordinal)` 与 `getGeometry(int ordinal)`，直接从 `values` 数组强转返回。这两个方法是 4.1 在 `SpecializedGetters`/`InternalRow` 层面新增的抽象方法，必须实现否则该类无法实例化。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/StructInternalRow.java` (+12/-0 lines)

**修改目的**：在 Iceberg 的 `StructInternalRow` 中实现 Geography/Geometry 访问器。

**工作逻辑**：导入两种类型，新增 `getGeography`/`getGeometry`，实现为 `isNullAt(ordinal) ? null : GeographyVal.fromBytes(getBinaryInternal(ordinal))`（Geometry 同理）。即把内部二进制表示通过 `fromBytes` 还原为 Spark 的空间类型对象，与 Parquet 中以 binary 存储的 geography/geometry 对应。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java` (+1/-22 lines)

**修改目的**：移除在 Spark 4.1 中已不再适用的断言并适配新错误文案。

**工作逻辑**：删除了两处断言“`Cannot find data for the output column s.n2`”的用例——这是因为 Spark 4.1 对部分列 INSERT 的处理已改变，原先的报错不再出现；同时将不支持的 MERGE 断言由 `hasMessage("MERGE INTO TABLE is not supported temporarily.")` 改为 `hasMessageContaining("Table `unknown` does not support MERGE INTO TABLE")`，匹配 4.1 新的错误信息格式。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java` (+1/-1 lines)

**修改目的**：迁移到 commons-collections4。

**工作逻辑**：导入由 `org.apache.commons.collections.ListUtils` 改为 `org.apache.commons.collections4.ListUtils`。Spark 4.1 依赖升级导致旧包不再可用，需使用 collections4 的同名工具类。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+1/-1 lines)

**修改目的**：更新引擎版本断言到 4.1。

**工作逻辑**：`EnvironmentContext.ENGINE_VERSION` 断言由 `startsWith("4.0")` 改为 `startsWith("4.1")`，反映运行时 Spark 版本。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteManifestsProcedure.java` (+1/-1 lines)

**修改目的**：适配 Spark 4.1 新的错误码与文案。

**工作逻辑**：重复命名参数 `tAbLe` 的错误由 `[UNRECOGNIZED_PARAMETER_NAME] ... does not include any signature containing an argument with this name` 改为 `[DUPLICATE_ROUTINE_PARAMETER_ASSIGNMENT.DOUBLE_NAMED_ARGUMENT_REFERENCE] ... includes multiple argument assignments to the same parameter name`。Spark 4.1 对同一参数被多个命名引用命中的场景改用了更精确的错误码。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesProcedure.java` (+1/-1 lines)

**修改目的**：更新引擎版本断言到 4.1。

**工作逻辑**：与 `TestRewriteDataFilesProcedure` 相同，`ENGINE_VERSION` 断言改为 `startsWith("4.1")`。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestUpdate.java` (+1/-1 lines)

**修改目的**：适配 UPDATE 不支持的错误文案。

**工作逻辑**：断言由 `hasMessage("UPDATE TABLE is not supported temporarily.")` 改为 `hasMessageContaining("Table `unknown` does not support UPDATE TABLE.")`，与 MERGE 的文案变化一致。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReader.java` (+1/-0 lines)

**修改目的**：启用 Spark 4.1 新的 variant 逻辑类型标注。

**工作逻辑**：测试配置新增 `spark.sql.parquet.variant.annotateLogicalType.enabled = true`，使 Parquet 写入时为 variant 类型标注逻辑类型，配合 4.1 对 variant/geometry 等类型的读写一致性校验。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestForwardCompatibility.java` (+5/-5 lines)

**修改目的**：适配 `MemoryStream` 包路径与构造签名变化。

**工作逻辑**：导入由 `org.apache.spark.sql.execution.streaming.MemoryStream` 改为 `org.apache.spark.sql.execution.streaming.runtime.MemoryStream`（4.1 把 MemoryStream 移到 runtime 子包）；同时 `newMemoryStream` 的参数由 `SQLContext` 改为 `SparkSession`，构造调用由 `new MemoryStream<>(id, sqlContext, ...)` 改为 `new MemoryStream<>(id, sparkSession, ...)`，移除 `SQLContext` 导入。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreaming.java` (+9/-7 lines)

**修改目的**：同上，适配 `MemoryStream` 迁移与构造签名。

**工作逻辑**：与 `TestForwardCompatibility` 完全相同的改法——包路径加 `.runtime`、`SQLContext` 换 `SparkSession`，并在 4 处 `newMemoryStream` 调用点同步更新。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkDefaultValues.java` (+0/-26 lines)

**修改目的**：移除在 Spark 4.1 中已不适用的部分列 INSERT 不支持测试。

**工作逻辑**：删除 `testPartialInsertUnsupported` 整个测试方法及其类注释中关于“4.0 不支持部分列 INSERT”的说明。原因在于 Spark 4.1 已支持 DSV2 的部分列 INSERT，该限制性测试不再成立。

### benchmark 文件（共 25 个 JMH 基准类，+50/-50 lines 左右）

**修改目的**：把各基准类 Javadoc 中的运行示例从 spark-4.0 改为 spark-4.1。

**工作逻辑**：涉及 `spark/v4.1/spark-extensions/src/jmh/` 与 `spark/v4.1/spark/src/jmh/` 下全部基准类（如 `DeleteFileIndexBenchmark`、`PlanningBenchmark`、`SparkParquetReadersFlatDataBenchmark`、`IcebergSourceFlatParquetDataReadBenchmark`、`VectorizedReadFlatParquetDataBenchmark` 等）。每个文件仅修改注释中的 `./gradlew -DsparkVersions=4.0 :iceberg-spark:iceberg-spark-extensions-4.0_2.13:jmh` 为对应的 `4.1` 形式，无功能代码变化。部分文件还把示例的 `_2.12` 修正为 `_2.13`（4.x 仅支持 Scala 2.13）。

## 总结

此提交为 Iceberg 引入对 Apache Spark 4.1.0 的初始支持，核心价值在于把 `spark/v4.1` 从占位副本升级为可构建、可测试、可发布的一等模块，并完整接入 CI 矩阵与发布流水线。技术上重点适配了 Spark 4.1 的多项 API 变更（`Origin` 字段、`DataSourceV2Relation.create` 签名、`MemoryStream` 迁移、`QueryCompilationErrors` 重命名、`SubstituteUnresolvedOrdinals` 移除、新增 Geography/Geometry 类型与 `listProcedures` 接口），并通过 `-release:17` 解决了 Scala 编译 `Record` 的问题。剩余的 datafusion-comet 4.1 依赖以 TODO 标记，留待后续补齐。整体影响是让 Iceberg 在 Spark 4.1 正式可用时即可被用户使用，保持版本跟进的及时性。
