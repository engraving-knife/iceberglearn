# 提交 2090：Build: Bump Comet from 0.5.0 to 0.8.1 (#12974)

## 提交信息

- **序号**：2090 / 4088
- **哈希**：94ba295960a5da1ce87c35a1e28353f4a71ccd72
- **短哈希**：94ba29596
- **日期**：2025-05-07 08:57:45 +0200
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Build: Bump Comet from 0.5.0 to 0.8.1 (#12974)
- **PR/Issue**：#12974

## 总体目的

Apache Comet 是基于 Apache Arrow 和 DataFusion 的 Spark 原生向量化执行加速器，Iceberg 在 Spark 3.4 / 3.5 模块中通过 `CometColumnReader` 集成 Comet 以加速 Parquet 向量化读取。本次提交将 Iceberg 依赖的 Comet 版本从 `0.5.0` 升级到 `0.8.1`，以跟进 Comet 上游的演进、修复与 API 变更。

升级过程中需要同步处理 Comet 0.8.x 的包路径变化：原 `org.apache.comet.shaded.arrow.c.CometSchemaImporter` 在新版本中被提升到顶层包 `org.apache.comet.CometSchemaImporter`，因此必须修改导入语句，否则代码无法编译。同时把版本号抽到 Gradle 版本目录（version catalog）中统一管理，避免在多处硬编码字符串。

## 如何达成设计目的

1. 在 `gradle/libs.versions.toml` 中新增 `comet = "0.8.1"` 版本别名，作为单一来源。
2. 在 `spark/v3.4/build.gradle` 与 `spark/v3.5/build.gradle` 中，把 `compileOnly` 与 `testImplementation` 两处的 Comet 依赖版本从硬编码的 `0.5.0` 替换为 `${libs.versions.comet.get()}`，统一从版本目录取值。
3. 修改 Spark 3.4 与 3.5 下的 `CometColumnReader.java`，把 `CometSchemaImporter` 的导入包从 `org.apache.comet.shaded.arrow.c` 改为 `org.apache.comet`，以匹配 Comet 0.8.1 的包结构。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-0 lines)

**修改目的**：在版本目录中登记 Comet 版本。

**工作逻辑**：新增 `comet = "0.8.1"`，与其它依赖版本一同集中管理，便于后续升级与一致性维护。

### `spark/v3.4/build.gradle` (修改, +2/-2 lines)

**修改目的**：将 Spark 3.4 模块对 Comet 的依赖版本切换到版本目录。

**工作逻辑**：`iceberg-spark` 子项目的 `compileOnly` 与 `iceberg-spark-extensions` 子项目的 `testImplementation` 中，把 `org.apache.datafusion:comet-spark-spark${sparkMajorVersion}_${scalaVersion}:0.5.0` 改为 `:${libs.versions.comet.get()}`。

### `spark/v3.5/build.gradle` (修改, +2/-2 lines)

**修改目的**：将 Spark 3.5 模块对 Comet 的依赖版本切换到版本目录。

**工作逻辑**：与 Spark 3.4 完全对称，把两处 `0.5.0` 替换为 `${libs.versions.comet.get()}`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometColumnReader.java` (修改, +1/-1 lines)

**修改目的**：适配 Comet 0.8.1 中 `CometSchemaImporter` 包路径的变更。

**工作逻辑**：把 `import org.apache.comet.shaded.arrow.c.CometSchemaImporter;` 改为 `import org.apache.comet.CometSchemaImporter;`。其余对 `AbstractColumnReader`、`ColumnReader`、`TypeUtil`、`Utils`、`RootAllocator`（仍位于 `org.apache.comet.shaded.arrow.memory`）的导入保持不变，因为只有 `CometSchemaImporter` 在新版本中被移出了 shaded arrow 包。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometColumnReader.java` (修改, +1/-1 lines)

**修改目的**：与 Spark 3.4 同步适配 Comet 0.8.1 的包路径。

**工作逻辑**：修改内容与 Spark 3.4 版本完全一致，把 `CometSchemaImporter` 的导入包改为 `org.apache.comet`。

## 总结

本次提交是一次常规的依赖升级，把 Iceberg Spark 3.4 / 3.5 模块依赖的 Apache Comet 从 0.5.0 升级到 0.8.1，并通过版本目录统一管理版本号。代码侧仅需同步 `CometSchemaImporter` 的包路径变更（从 shaded arrow 包移到顶层 comet 包），其余集成代码不变。升级后可获得 Comet 0.6–0.8.x 的性能改进与缺陷修复。
