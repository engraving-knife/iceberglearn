# 提交 1666：Spark 3.4, 3.5: Iceberg / DataFusion Comet integration (#12147)

## 提交信息

- **序号**：1666 / 4088
- **哈希**：c5822c40889abe9d00921705b986d2c262595b4b
- **短哈希**：c5822c408
- **日期**：2025-01-31（Fri Jan 31 23:47:00 2025 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.4, 3.5: Iceberg / DataFusion Comet integration (#12147)
- **PR/Issue**：#12147

## 总体目的

紧随前一个提交 1664（为 Spark 3.4 引入 Comet Parquet reader 支持，基于 Comet 0.3.0），本提交完成两件事：

1. **把 Comet 依赖从 0.3.0 升级到 0.5.0**：Comet 0.5.0 的 `MetadataColumnReader` 构造器新增了一个 `boolean isConstant` 参数，导致 1664 中基于 0.3.0 API 编写的 `CometDeleteColumnReader` 与 `CometPositionColumnReader` 无法编译。本提交在 Spark 3.4 上修复这两处构造器调用，补上 `false /* isConstant */` 参数，并把 build.gradle 中的 Comet 版本号升到 0.5.0。
2. **把 Comet 集成扩展到 Spark 3.5**：1664 只改了 Spark 3.4，本提交把同一套 Comet reader 实现（`ParquetReaderType`、`ParquetBatchReadConf`/`OrcBatchReadConf`、`CometVectorizedReaderBuilder`、`CometColumnarBatchReader`、`CometColumnReader`、`CometConstantColumnReader`、`CometDeleteColumnReader`、`CometPositionColumnReader`、`VectorizedSparkParquetReaders.buildCometReader`、`BaseBatchReader`/`BatchDataReader`/`SparkBatch`/`SparkColumnarReaderFactory` 的 conf 化重构、`ColumnVectorWithFilter` 通用化、`ColumnarBatchReader` 适配、`IcebergArrowColumnVector` 移除 `vector()`、checkstyle suppress）整体复制到 `spark/v3.5`，使 Spark 3.5 也支持 `spark.sql.iceberg.parquet.reader-type=comet`。同时更新 Spark 3.5 的 `TestSparkReaderDeletes` 以适配 `BatchDataReader` 新构造签名。

简言之，这是 1664 的"补全 + 升级 + 跨版本扩展"提交。

## 如何达成设计目的

- **Comet 0.5.0 适配（v3.4）**：在 `CometDeleteColumnReader.DeleteColumnReader` 与 `CometPositionColumnReader.PositionColumnReader` 的 `super(...)` 调用中追加第四个参数 `false /* isConstant */`，对应 Comet 0.5.0 `MetadataColumnReader` 新增的 isConstant 形参。同时把 v3.4 build.gradle 中 compileOnly 与 testImplementation 的 Comet 版本从 0.3.0 改为 0.5.0。
- **扩展到 v3.5**：把 1664 在 v3.4 上新增/修改的全部生产代码文件原样复制到 v3.5 对应路径（v3.5 的包结构与 v3.4 一致）；在 v3.5 build.gradle 中新增 Comet 0.5.0 依赖与 Immutables annotation processor；更新 v3.5 的 `TestSparkReaderDeletes` 中 `BatchDataReader` 构造调用为新签名（传 `ParquetBatchReadConf` + null orc conf）。注意 v3.5 的 `CometDeleteColumnReader`/`CometPositionColumnReader` 直接按 0.5.0 API 编写（带 isConstant 参数），无需再经历 0.3.0→0.5.0 的修补。

## 修改详情

### `spark/v3.4/build.gradle`（修改，4 行变化）

**修改目的**：把 Comet 依赖从 0.3.0 升级到 0.5.0。

**工作逻辑**：compileOnly 与 testImplementation 两处的 `comet-spark-spark${sparkMajorVersion}_${scalaVersion}` 版本号 `0.3.0` → `0.5.0`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometDeleteColumnReader.java`（修改，3 行变化）

**修改目的**：适配 Comet 0.5.0 `MetadataColumnReader` 新增的 isConstant 参数。

**工作逻辑**：`DeleteColumnReader` 构造器中 `super(DataTypes.BooleanType, TypeUtil.convertToParquet(...), false /* useDecimal128 */)` 改为追加第四参数 `false /* isConstant */`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometPositionColumnReader.java`（修改，6 行变化）

**修改目的**：同上，适配 0.5.0 API。

**工作逻辑**：`PositionColumnReader` 构造器 `super(DataTypes.LongType, descriptor, false /* useDecimal128 */)` 改为追加 `false /* isConstant */`。

### `spark/v3.5/build.gradle`（修改，5 行）

**修改目的**：为 Spark 3.5 引入 Comet 0.5.0 依赖与 Immutables 注解处理器。

**工作逻辑**：
- 主项目 dependencies 新增 `annotationProcessor libs.immutables.value` + `compileOnly libs.immutables.value`；
- 新增 `compileOnly "org.apache.datafusion:comet-spark-spark${sparkMajorVersion}_${scalaVersion}:0.5.0"`；
- extensions 测试依赖新增 `testImplementation "...comet-spark-spark...:0.5.0"`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/OrcBatchReadConf.java`（新增，27 行）

**修改目的**：v3.5 版本的 ORC 批读 conf（与 v3.4 同名文件一致）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/ParquetBatchReadConf.java`（新增，29 行）

**修改目的**：v3.5 版本的 Parquet 批读 conf（含 readerType）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/ParquetReaderType.java`（新增，47 行）

**修改目的**：v3.5 版本的 reader 类型枚举。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`（修改，8 行）

**修改目的**：v3.5 新增 `parquetReaderType()` 方法。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java`（修改，3 行）

**修改目的**：v3.5 新增 `PARQUET_READER_TYPE` 属性与默认值。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometColumnReader.java`（新增，150 行）

**修改目的**：v3.5 版本的 Comet native 列 reader 基类。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometColumnarBatchReader.java`（新增，203 行）

**修改目的**：v3.5 版本的 Comet 批 reader，组装 ColumnarBatch 并集成删除过滤。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometConstantColumnReader.java`（新增，65 行）

**修改目的**：v3.5 版本的常量列 reader。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometDeleteColumnReader.java`（新增，75 行）

**修改目的**：v3.5 版本的 `_deleted` 列 reader。注意此处为 75 行（比 v3.4 的 74 行多 1 行），因为直接按 Comet 0.5.0 API 编写，`DeleteColumnReader` 构造器的 `super(...)` 调用含第四个 `false /* isConstant */` 参数。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometPositionColumnReader.java`（新增，62 行）

**修改目的**：v3.5 版本的 `_pos` 列 reader。同理直接按 0.5.0 API 编写（62 行，比 v3.4 的 58 行多 4 行，因 isConstant 参数换行格式化）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometVectorizedReaderBuilder.java`（新增，147 行）

**修改目的**：v3.5 版本的 Comet reader 构建器。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java`（修改，17 行）

**修改目的**：v3.5 新增 `buildCometReader` 入口方法。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnVectorWithFilter.java`（修改/重写）

**修改目的**：v3.5 同步 `ColumnVectorWithFilter` 通用化重构（继承 `ColumnVector`、持有 delegate、补全方法）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java`（修改）

**修改目的**：v3.5 同步适配新的 `ColumnVectorWithFilter` 构造与辅助方法抽取。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/IcebergArrowColumnVector.java`（修改）

**修改目的**：v3.5 移除 `vector()` 访问器与 holder 字段。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java`（修改，28 行）

**修改目的**：v3.5 用 conf 对象替代 batchSize 并按 reader 类型分发。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BatchDataReader.java`（修改，15 行）

**修改目的**：v3.5 透传 conf 对象。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java`（修改，39 行）

**修改目的**：v3.5 新增 `useCometBatchReads`/`supportsCometBatchReads` 与 conf 构造。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkColumnarReaderFactory.java`（修改，19 行）

**修改目的**：v3.5 用 conf 对象替代 batchSize。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`（修改，17 行）

**修改目的**：适配 `BatchDataReader` 新构造签名。

**工作逻辑**：
- 新增 import `ImmutableParquetBatchReadConf`/`ParquetBatchReadConf`/`ParquetReaderType`；
- 测试中构造 `BatchDataReader` 时，原 `new BatchDataReader(dateTable, task, ..., false, 7)` 改为先构造 `ImmutableParquetBatchReadConf.builder().batchSize(7).readerType(ParquetReaderType.ICEBERG).build()`，再 `new BatchDataReader(dateTable, ..., false, conf, null)`（orc conf 传 null，因为该测试只读 Parquet）。

## 小结

- **成效**：完成 Comet 集成的两件后续工作——把 Comet 依赖统一升级到 0.5.0 并修复 v3.4 上的 API 兼容问题；把 Comet Parquet reader 支持从 Spark 3.4 扩展到 Spark 3.5，使两个 Spark 版本都可通过 `spark.sql.iceberg.parquet.reader-type=comet` 启用 native 加速读取。
- **影响范围**：`spark/v3.4`（Comet 升级 + 2 处构造器修复）与 `spark/v3.5`（整体引入 Comet 集成，与 1664 在 v3.4 上的改动等价）。v3.5 的 `TestSparkReaderDeletes` 同步更新构造调用。Comet 仍为 compileOnly，运行期由用户引入。
- **回迁到 1.4.x 的注意事项**：与 1664 同属一个特性，回迁应一并考虑。建议回迁时直接采用 Comet 0.5.0（即 1666 的最终状态），跳过 0.3.0 中间态，避免 API 不兼容。1.4.x 分支通常同时维护 Spark 3.3/3.4/3.5，若 1.4.x 还支持 Spark 3.3 且希望 3.3 也有 Comet 支持，需额外补 3.3 模块（本提交未涉及 3.3）。需注意 Comet 制品 `comet-spark-spark3.x_2.12` 是否对 1.4.x 所支持的 Spark 小版本都有发布。`ColumnVectorWithFilter`/`ColumnarBatchReader`/`IcebergArrowColumnVector` 的重构会影响 1.4.x 既有删除读路径，须整体回迁保持一致。
