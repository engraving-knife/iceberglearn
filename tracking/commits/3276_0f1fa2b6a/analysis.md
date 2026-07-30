# 提交 3276：Spark: Backport moving Spark to use the new FormatModel API (#15355)

## 提交信息

- **序号**：3276 / 4088
- **哈希**：0f1fa2b6a88389a2cfd0734e83a1d9b13becbfab
- **短哈希**：0f1fa2b6a
- **日期**：2026-02-18
- **作者**：pvary
- **提交说明**：Spark: Backport moving Spark to use the new FormatModel API (#15355)
- **PR/Issue**：#15355

## 总体目的

本提交将 main 分支上引入的全新 `FormatModel` API 回移（backport）到 1.4.x 维护分支，覆盖 Spark 3.4、3.5 和 4.0 三个版本目录。在此之前，Iceberg 的 Spark 模块在读取和写入数据时，需要在各调用点通过 `switch (format)` 分别处理 Parquet、ORC、Avro 三种格式，并在每个 case 中显式调用 `Parquet.read(...).createReaderFunc(...)`、`ORC.read(...).createReaderFunc(...)`、`Avro.read(...).createResolvingReader(...)` 等构建器，把 Spark 专用的 reader/writer 工厂函数注入进去。这种模式导致格式相关的逻辑散落在 `BaseBatchReader`、`BaseRowReader`、`RewriteTablePathSparkAction`、`SparkFileWriterFactory` 以及多个 JMH 基准测试中，代码重复且难以扩展新格式。

`FormatModelRegistry` 的设计思路是：把"某种格式 + 某种引擎数据类型（如 `InternalRow`、`ColumnarBatch`）应该用哪个 reader/writer 工厂"这一映射在启动时集中注册一次，之后所有读写调用点只需通过 `FormatModelRegistry.readBuilder(format, readType, inputFile)` 或 `FormatModelRegistry.dataWriteBuilder(...)` 拿到一个统一的 `ReadBuilder`/`WriteBuilder`，链式调用 `.project()`、`.split()`、`.filter()`、`.idToConstant()` 等通用方法即可，不再关心底层是 Parquet 还是 ORC。这样既消除了重复的 switch 分支，也使得新增格式或新增引擎数据类型（例如 Comet 向量化读取）只需注册一处。

本回移的动机是让 1.4.x 分支与 main 分支在 Spark 读写架构上保持一致，便于后续把更多修复和优化（例如 `SparkFileWriterFactory` 的后续修复，即紧随其后的 #15356、#15357）一并回移，同时减少维护分支与主分支之间的代码分歧。

## 如何达成设计目的

新增 `SparkFormatModels` 工具类，在 `register()` 方法中集中注册 Avro/Parquet/ORC 三种格式分别对应 `InternalRow`（行读）、`ColumnarBatch`（向量化批读）、`CometColumnarBatch`（Comet 向量化批读）等数据类型的 reader/writer 工厂。各读写调用点改为调用 `FormatModelRegistry` 提供的统一构建器；`SparkFileWriterFactory` 的基类从 `BaseFileWriterFactory` 改为 `RegistryBasedFileWriterFactory`，删除了大量 per-format 的 `configureDataWrite`/`configureEqualityDelete`/`configurePositionDelete` 覆写方法；`RewriteTablePathSparkAction` 与各 JMH 基准测试也同步切换到注册表构建器。改动同时涉及 v3.4、v3.5、v4.0 三个目录，文件路径与改动内容基本一致。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkFormatModels.java` (+89/-0 lines)

**修改目的**：新增集中注册 Spark 各格式 reader/writer 工厂的工具类。

**工作逻辑**：
`SparkFormatModels.register()` 调用 `FormatModelRegistry.register(...)` 为每种"格式 + 引擎数据类型"组合注册一个工厂。具体包括：
- Avro + `InternalRow`：writer 用 `SparkAvroWriter`，reader 用 `SparkPlannedAvroReader`。
- Parquet + `InternalRow`：writer 用 `SparkParquetWriters::buildWriter`，reader 用 `SparkParquetReaders.buildReader`。
- Parquet + `ColumnarBatch`：reader 用 `VectorizedSparkParquetReaders.buildReader`。
- Parquet + `CometColumnarBatch`：reader 用 `VectorizedSparkParquetReaders.buildCometReader`。
- ORC + `InternalRow`：writer 用 `SparkOrcWriter`，reader 用 `SparkOrcReader`。
- ORC + `ColumnarBatch`：reader 用 `VectorizedSparkOrcReaders.buildReader`。

工厂函数签名统一为 `(icebergSchema, fileSchema, engineSchema, idToConstant) -> reader/writer`，由注册表在构建时按需回调。该类提供私有构造函数，仅作为静态注册入口。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkFileWriterFactory.java` (+132/-90 lines)

**修改目的**：将 writer 工厂切换到基于注册表的基类，并保留对已弃用的"带行数据的 position delete"的兼容写入路径。

**工作逻辑**：
基类由 `BaseFileWriterFactory<InternalRow>` 改为 `RegistryBasedFileWriterFactory<InternalRow, StructType>`，构造时传入 `InternalRow.class` 以及 `useOrConvert(dataSparkType, dataSchema)` 转换出的引擎类型。原来针对 Avro/Parquet/ORC 的 6 个 `configureDataWrite`/`configureEqualityDelete`/`configurePositionDelete` 覆写方法全部删除——这些工作现在由注册表根据已注册的工厂自动完成。

新增字段 `useDeprecatedPositionDeleteWriter`（布尔）、`positionDeleteRowSchema`、`table`、`format`。覆写 `newPositionDeleteWriter`：当 `useDeprecatedPositionDeleteWriter` 为 false 时直接走父类（注册表）路径；为 true 时（即 position delete 仍携带被删行数据这一已弃用场景）打印一条 `LOG.warn` 提示该特性将在 1.12.0 移除，并保留旧的 `switch (format)` 手动构建逻辑，分别调用 `Avro.writeDeletes`/`ORC.writeDeletes`/`Parquet.writeDeletes`，设置 `MetricsConfig`、`writeProperties`、partition、spec、keyMetadata 等参数。新增静态方法 `useOrConvert(StructType, Schema)`：优先用传入的 Spark 类型，否则由 Iceberg schema 转换，二者皆空返回 null。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java` (+20/-82 lines)

**修改目的**：用 `FormatModelRegistry.readBuilder` 替代 switch 分支的向量化批读构建逻辑。

**工作逻辑**：
原来 `newBatchIterable` 内对 `PARQUET`/`ORC` 分别调用 `newParquetIterable`/`newOrcIterable`，两个私有方法各自调用 `Parquet.read`/`ORC.read` 并注入 `createBatchedReaderFunc`。改写后先根据 `useComet()` 选择读取类型 `CometColumnarBatch.class` 或 `ColumnarBatch.class`，调用 `FormatModelRegistry.readBuilder(format, readType, inputFile)` 获得 `ReadBuilder`，再统一链式设置 `recordsPerBatch`（来自 parquetConf 或 orcConf）、`project`、`idToConstant`、`split`、`filter`、`caseSensitive`、`reuseContainers`、`withNameMapping`，最后 `build()`。ORC 原来手动剔除常量列与元数据列（`TypeUtil.selectNot`）的逻辑被移除——该处理下沉到注册表/底层 reader 内部。新增私有方法 `useComet()` 判断是否启用 Comet reader。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseRowReader.java` (+7/-66 lines)

**修改目的**：用 `FormatModelRegistry.readBuilder` 替代 switch 分支的行读构建逻辑。

**工作逻辑**：
原 `newIterable` 对 `PARQUET`/`AVRO`/`ORC` 分别调用三个私有方法，各自使用 `Avro.read`/`Parquet.read`/`ORC.read` 并注入 `SparkPlannedAvroReader`/`SparkParquetReaders`/`SparkOrcReader`。改写后统一为 `FormatModelRegistry.readBuilder(format, InternalRow.class, file)` 返回的 `ReadBuilder`，链式 `.project(projection).idToConstant(idToConstant).reuseContainers().split(start, length).caseSensitive(...).filter(residual).withNameMapping(nameMapping()).build()`。ORC 原来手动剔除常量列与元数据列的逻辑同样被移除。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+30/-60 lines)

**修改目的**：将 position delete 的读取和写入切换到注册表构建器，保留带行 schema 的旧写入路径。

**工作逻辑**：
`positionDeletesReader` 由原 switch 三格式改为 `FormatModelRegistry.readBuilder(format, Record.class, inputFile).project(...).reuseContainers().build()`。`positionDeletesWriter` 增加分支：当 `rowSchema == null` 时使用新 API `FormatModelRegistry.positionDeleteWriteBuilder(format, EncryptedFiles.plainAsEncryptedOutput(outputFile)).partition(partition).spec(spec).build()`；当 `rowSchema != null` 时保留原 switch 逻辑（`Avro.writeDeletes`/`Parquet.writeDeletes`/`ORC.writeDeletes` 配合 `DataWriter`/`GenericParquetWriter`/`GenericOrcWriter`）。新增 `EncryptedFiles` 与 `FormatModelRegistry` 的 import，移除了不再使用的 `PlannedDataReader`/`GenericOrcReader`/`GenericParquetReaders` import。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+18/-1 lines)

**修改目的**：新增可由 Iceberg schema 构建写入器的重载，供注册表回调使用。

**工作逻辑**：
原 `buildWriter(StructType dfSchema, MessageType type)` 保留为兼容入口，内部转调 `buildWriter(null, type, dfSchema)`。新增 `buildWriter(Schema icebergSchema, MessageType type, StructType dfSchema)`：通过 `ParquetWithSparkSchemaVisitor.visit(dfSchema != null ? dfSchema : SparkSchemaUtil.convert(icebergSchema), type, new WriteBuilder(type))` 构建，即优先用 Spark 类型，缺失时由 Iceberg schema 转换。另新增 `buildWriter(StructType dfSchema, MessageType type, Schema icebergSchema)` 重载，逻辑相同，参数顺序不同以适配不同回调签名。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java` (+9/-2 lines)

**修改目的**：引入 `CometColumnarBatch` 类型标记，便于注册表按类型区分 Comet 与普通向量化读取。

**工作逻辑**：
新增静态内部类 `CometColumnarBatch extends ColumnarBatch`，仅作为类型标识（携带 `ColumnVector[]` 构造），使注册表可以用 `CometColumnarBatch.class` 作为 key 注册专门的 Comet reader 工厂。`buildCometReader` 返回类型由具体的 `CometColumnarBatchReader` 放宽为 `VectorizedReader<ColumnarBatch>`，以匹配注册表统一签名。新增 `ColumnVector`/`ColumnarBatch` 的 import。

### `spark/v3.5/spark/src/jmh/java/.../SparkParquetReadersFlatDataBenchmark.java` (+5/-3 lines) 及 `SparkParquetReadersNestedDataBenchmark.java` (+5/-3 lines)

**修改目的**：基准测试改用注册表构建读取器。

**工作逻辑**：
`readUsingIcebergReader` 与 `readWithProjectionUsingIcebergReader` 方法由 `Parquet.read(Files.localInput(dataFile)).project(SCHEMA).createReaderFunc(type -> SparkParquetReaders.buildReader(SCHEMA, type)).build()` 改为 `FormatModelRegistry.readBuilder(FileFormat.PARQUET, InternalRow.class, Files.localInput(dataFile)).project(SCHEMA).build()`，不再需要显式提供 reader 工厂 lambda。

### `spark/v3.5/spark/src/jmh/java/.../SparkParquetWritersFlatDataBenchmark.java` (+15/-5 lines) 及 `SparkParquetWritersNestedDataBenchmark.java` (+15/-5 lines)

**修改目的**：基准测试改用注册表构建写入器并修正配置。

**工作逻辑**：
`writeUsingIcebergWriter` 由 `Parquet.write(Files.localOutput(dataFile)).createWriterFunc(...).build()` 返回 `FileAppender<InternalRow>` 改为 `FormatModelRegistry.dataWriteBuilder(FileFormat.PARQUET, InternalRow.class, EncryptedFiles.plainAsEncryptedOutput(Files.localOutput(dataFile))).schema(SCHEMA).spec(PartitionSpec.unpartitioned()).build()` 返回 `DataWriter<InternalRow>`，写入由 `writer.addAll(rows)` 改为 `writer.write(rows)`。Spark 原生写入基准的配置新增 `spark.sql.parquet.variant.annotateLogicalType.enabled` 设为 false，避免新版 Spark 的 variant 类型逻辑干扰基准结果。

### v3.4 与 v4.0 目录下的同名文件

上述改动在 `spark/v3.4/` 与 `spark/v4.0/` 下逐文件同步应用，文件路径、改动内容与 v3.5 完全一致，此处不再赘述。

## 总结

本提交通过引入 `FormatModelRegistry` 注册表模式，把 Spark 模块中分散在读写调用点的格式分支逻辑集中到 `SparkFormatModels` 一处注册，显著减少了 `BaseBatchReader`、`BaseRowReader`、`SparkFileWriterFactory`、`RewriteTablePathSparkAction` 及基准测试中的重复代码，为后续新增格式与引擎数据类型（如 Comet）提供了统一扩展点。同时保留了对已弃用"带行数据 position delete"写入的兼容路径并打印弃用警告，兼顾了向前兼容与未来清理。这是 1.4.x 分支与 main 分支架构对齐的关键一步。
