# 提交 3274：Core, Spark: Moving Spark to use the new FormatModel API (#15328)

## 提交信息

- **序号**：3274 / 4088
- **哈希**：dde0dc233d1be51f0f67cb57017c5f69febbbecc
- **短哈希**：dde0dc233
- **日期**：2026-02-18
- **作者**：pvary
- **提交说明**：Core, Spark: Moving Spark to use the new FormatModel API (#15328)
- **PR/Issue**：#15328

## 总体目的

本提交把 Spark 4.1 集成（`spark/v4.1/spark/`）从旧的格式特定 builder 回调模型迁移到新的 `FormatModelRegistry` 统一对象模型，是 3271（Data）与 3272（Flink）之后第三个、也是覆盖面最大的引擎迁移。Spark 适配器在读写两端都有大量格式耦合代码：

写侧的 `SparkFileWriterFactory extends BaseFileWriterFactory<InternalRow>` 同样要覆写 9 个 `configureDataWrite/configureEqualityDelete/configurePositionDelete(Avro|Parquet|ORC...)` 回调，分别用 `SparkAvroWriter`、`SparkParquetWriters.buildWriter`、`SparkOrcWriter` 配置格式特定 builder，position delete 还要 `transformPaths(path -> UTF8String.fromString(...))`。Spark 还要维护 `dataSparkType`/`equalityDeleteSparkType`/`positionDeleteSparkType` 三个 `StructType`（从 Iceberg `Schema` 经 `SparkSchemaUtil.convert` 得到），惰性计算。此外 Spark 有一个"带行数据的位置删除"（position delete with row data）的废弃特性——通过 `DELETE_FILE_ROW_FIELD_NAME` 字段判断，写时要从中提取行 schema 用 `SparkAvroWriter` 写——这条路径在新 FormatModel 的 position-delete builder 中不被支持（新模型只支持纯 (path, pos)）。

读侧 Spark 有两个 reader 基类：`BaseBatchReader`（向量化批量读，输出 `ColumnarBatch`，支持 Parquet/ORC，Parquet 还分普通与 Comet 两种 reader type）与 `BaseRowReader`（行式读，输出 `InternalRow`，支持 Parquet/Avro/ORC）。二者都在 `newBatchIterable`/`newIterable` 中按 `format` 做 `switch (PARQUET/AVRO/ORC)`，分别调用 `newParquetIterable`/`newAvroIterable`/`newOrcIterable`，各自构造 `Parquet.ReadBuilder`/`Avro.ReadBuilder`/`ORC.ReadBuilder` 并 `createReaderFunc/createBatchedReaderFunc(SparkParquetReaders::buildReader / SparkPlannedAvroReader::create / SparkOrcReader::new / VectorizedSparkParquetReaders::buildReader / VectorizedSparkOrcReaders::buildReader)`。ORC 还多一步 `TypeUtil.selectNot(schema, Sets.union(idToConstant.keySet(), MetadataColumns.metadataFieldIds()))` 剔除常量与元数据字段。这些 switch + 多段重复是典型的格式耦合。

本提交把 Spark 读写两侧都迁移到 `FormatModelRegistry`：(1) 新增 `SparkFormatModels` 注册类，把 Spark 的多种对象模型（`InternalRow` 行式、`ColumnarBatch` 向量化、`CometColumnarBatch` Comet 向量化）与 Avro/Parquet/ORC 三种格式的 writer/reader 函数注册到全局 registry；(2) `FormatModelRegistry.CLASSES_TO_REGISTER` 追加 `SparkFormatModels`；(3) `SparkFileWriterFactory` 改为继承 `RegistryBasedFileWriterFactory<InternalRow, StructType>`，删除 9 个回调与惰性 helper，构造期一次性计算 `StructType`，并覆写 `newPositionDeleteWriter` 处理 legacy 带行数据的位置删除；(4) `BaseBatchReader`/`BaseRowReader` 用 `FormatModelRegistry.readBuilder(format, readType, file)` 替换 switch；(5) `RewriteTablePathSparkAction` 的 position delete 读写也迁移；(6) 4 个 JMH benchmark 同步迁移；(7) `SparkParquetWriters` 增加接受 Iceberg `Schema` 或 `StructType` 的重载以适配新 API；(8) `VectorizedSparkParquetReaders` 新增 `CometColumnarBatch extends ColumnarBatch` 标记类，让 Comet 向量化路径可作为独立对象模型注册。

## 如何达成设计目的

整体思路与 Flink 迁移一致："注册 Spark 的格式模型 + 用 RegistryBasedFileWriterFactory 替换旧基类 + 用 FormatModelRegistry.readBuilder 替换 switch + 处理 legacy position-delete-with-row-data 兼容"。涉及 12 个文件：core 模块的 `FormatModelRegistry` 加一项；新增 `SparkFormatModels` 注册类；改写 `SparkFileWriterFactory`、`BaseBatchReader`、`BaseRowReader`、`RewriteTablePathSparkAction`；为支持新 API 调整 `SparkParquetWriters`、`VectorizedSparkParquetReaders`；4 个 benchmark 同步迁移。

## 修改详情

### `core/src/main/java/org/apache/iceberg/formats/FormatModelRegistry.java` (+2/-1 lines)

**修改目的**：把 Spark 的格式模型注册类纳入 registry 静态初始化。

**工作逻辑**：`CLASSES_TO_REGISTER` 列表在 `GenericFormatModels`、`ArrowFormatModels`、`FlinkFormatModels` 之后追加 `"org.apache.iceberg.spark.source.SparkFormatModels"`。registry 静态块反射调用其 `register()`，core 模块编译期不依赖 spark 模块。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkFormatModels.java` (+89/-0 lines，新文件)

**修改目的**：把 Spark 的多种对象模型与三种格式的读写函数注册为 `FormatModel`。

**工作逻辑**：`SparkFormatModels.register()` 注册 6 个 FormatModel：
- Avro + `InternalRow`：`AvroFormatModel.create(InternalRow.class, StructType.class, writer -> new SparkAvroWriter(engineSchema), reader -> SparkPlannedAvroReader.create(icebergSchema, idToConstant))`。
- Parquet + `InternalRow`：`ParquetFormatModel.create(InternalRow.class, StructType.class, SparkParquetWriters::buildWriter, reader -> SparkParquetReaders.buildReader(icebergSchema, fileSchema, idToConstant))`。
- Parquet + `ColumnarBatch`：只注册 reader（向量化），`VectorizedSparkParquetReaders.buildReader(icebergSchema, fileSchema, idToConstant)`。
- Parquet + `CometColumnarBatch`：只注册 reader，`VectorizedSparkParquetReaders.buildCometReader(icebergSchema, fileSchema, idToConstant)`。
- ORC + `InternalRow`：writer `new SparkOrcWriter(icebergSchema, fileSchema)`，reader `new SparkOrcReader(icebergSchema, fileSchema, idToConstant)`。
- ORC + `ColumnarBatch`：只注册 reader，`VectorizedSparkOrcReaders.buildReader(icebergSchema, fileSchema, idToConstant)`。

通过注册不同的"对象模型类"（`InternalRow` vs `ColumnarBatch` vs `CometColumnarBatch`），同一格式可以服务于不同的读取产物，由调用方在 `FormatModelRegistry.readBuilder(format, readType, file)` 时按需选择。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkFileWriterFactory.java` (+111/-111 lines)

**修改目的**：让 Spark 写工厂走新 FormatModel API，并保留 legacy 带行数据的位置删除兼容。

**工作逻辑**：类签名由 `extends BaseFileWriterFactory<InternalRow>` 改为 `extends RegistryBasedFileWriterFactory<InternalRow, StructType>`，新增 `LOG`、`useDeprecatedPositionDeleteWriter` 标志、`table`、`format`、`positionDeleteRowSchema`、`positionDeleteSparkType` 字段。两个构造器向 `super(...)` 透传 `InternalRow.class` 作为 inputType 与通过新私有静态方法 `useOrConvert(sparkType, schema)` 计算的引擎 `StructType`（sparkType 非空返回 sparkType，否则 `SparkSchemaUtil.convert(schema)`，把原惰性 `dataSparkType()/equalityDeleteSparkType()` 前移到构造期）。`useDeprecatedPositionDeleteWriter` 在第一个构造器中根据 `positionDeleteRowSchema != null` 或 `positionDeleteSparkType` 含 `DELETE_FILE_ROW_FIELD_NAME` 字段判定。删除全部 9 个 `configureXxx` 回调与 `dataSparkType()/equalityDeleteSparkType()` 惰性方法，保留 `positionDeleteSparkType()`（仍惰性，因为构造时 positionDeleteSparkType 可能为 null）。新增 `newPositionDeleteWriter` 覆写：若 `!useDeprecatedPositionDeleteWriter` 直接 `super.newPositionDeleteWriter(...)`（走新 FormatModel 路径）；否则 `LOG.warn("Position deletes with deleted rows are deprecated and will be removed in 1.12.0.")`，按 `format`（AVRO/ORC/PARQUET）分别用 `Avro.writeDeletes`/`ORC.writeDeletes`/`Parquet.writeDeletes` + `createWriterFunc` + `transformPaths(path -> UTF8String.fromString(...))` + `rowSchema(positionDeleteRowSchema)` + `withSpec` + `withKeyMetadata` + `buildPositionWriter()` 构造带行数据的位置删除 writer（保留旧路径的等价实现，含 Parquet 路径里重复一次 `.metricsConfig(metricsConfig)` 的细节）。新增私有静态方法 `useOrConvert(StructType, Schema)`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java` (+14/-65 lines)

**修改目的**：让 Spark 向量化批量读走新 FormatModel API。

**工作逻辑**：`newBatchIterable` 中原 `switch (PARQUET/ORC)` 与 `newParquetIterable`/`newOrcIterable` 两个私有方法被替换为：先按 `useComet()`（新增私有方法，判断 `parquetConf != null && parquetConf.readerType() == ParquetReaderType.COMET`）决定 `readType` 为 `CometColumnarBatch.class` 或 `ColumnarBatch.class`；调用 `FormatModelRegistry.readBuilder(format, readType, inputFile)` 得到 `ReadBuilder<ColumnarBatch, ?>`；按 `parquetConf`/`orcConf` 调 `.recordsPerBatch(batchSize)`；链式 `.project(deleteFilter.requiredSchema()).idToConstant(idToConstant).split(start, length).filter(residual).caseSensitive(caseSensitive()).reuseContainers().withNameMapping(nameMapping()).build()`；最后 `CloseableIterable.transform(iterable, new BatchDeleteFilter(deleteFilter)::filterBatch)`。删除 `Set`/`ORC`/`Parquet`/`Sets`/`VectorizedSparkOrcReaders`/`TypeUtil`/`MetadataColumns` 等 import。ORC 路径里原先的 `TypeUtil.selectNot` 剔除常量/元数据字段不再由 Spark 侧负责，由 FormatModel 内部统一处理。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseRowReader.java` (+4/-69 lines)

**修改目的**：让 Spark 行式读走新 FormatModel API。

**工作逻辑**：`newIterable` 中原 `switch (PARQUET/AVRO/ORC)` 与 `newAvroIterable`/`newParquetIterable`/`newOrcIterable` 三个私有方法被替换为：`FormatModelRegistry.readBuilder(format, InternalRow.class, file)` 得到 `ReadBuilder<InternalRow, ?>`，链式 `.project(projection).idToConstant(idToConstant).reuseContainers().split(start, length).caseSensitive(caseSensitive()).filter(residual).withNameMapping(nameMapping()).build()`。删除 `Avro`/`Parquet`/`ORC`/`Sets`/`MetadataColumns`/`SparkOrcReader`/`SparkParquetReaders`/`SparkPlannedAvroReader`/`TypeUtil` 等 import。同样，ORC 的 `selectNot` 特殊处理交由 FormatModel 内部。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+19/-3 lines)

**修改目的**：为 FormatModel API 提供能从 Iceberg `Schema` 或 Spark `StructType` 任一出发构造 writer 的重载。

**工作逻辑**：原 `buildWriter(StructType dfSchema, MessageType type)` 保留为转调新方法 `buildWriter(null, type, dfSchema)`。新增两个重载：
- `buildWriter(Schema icebergSchema, MessageType type, StructType dfSchema)`：用 `dfSchema != null ? dfSchema : SparkSchemaUtil.convert(icebergSchema)` 调 `ParquetWithSparkSchemaVisitor.visit(...)`。
- `buildWriter(StructType dfSchema, MessageType type, Schema icebergSchema)`：同上，但参数顺序不同。

这样新 FormatModel 的 writer 函数 `(icebergSchema, fileSchema, engineSchema) -> ...` 可以灵活地传 Iceberg schema 或 Spark type。新增 `Schema`、`SparkSchemaUtil` import。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java` (+11/-1 lines)

**修改目的**：让 Comet 向量化路径可作为独立对象模型注册。

**工作逻辑**：`buildCometReader` 返回类型由 `CometColumnarBatchReader` 改为 `VectorizedReader<ColumnarBatch>`（更通用，匹配 `ReadBuilder<ColumnarBatch, ?>` 的类型）。新增公共静态内部类 `CometColumnarBatch extends ColumnarBatch`，仅是一个标记子类（构造器转调 `super(columns)`），用于在 `FormatModelRegistry` 中以 `CometColumnarBatch.class` 作为独立的对象模型类注册——这样调用方 `readBuilder(PARQUET, CometColumnarBatch.class, file)` 就能精确选中 Comet reader，而不与普通 `ColumnarBatch` 路径混淆。新增 `ColumnVector`、`ColumnarBatch` import。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+38/-49 lines)

**修改目的**：把表路径重写 action 中的 position delete 读写也迁移到 FormatModel API。

**工作逻辑**：
- `positionDeletesReader`：原 `switch (AVRO/PARQUET/ORC)` + 三段 `Avro.read/Parquet.read/ORC.read` + `createReaderFunc(PlannedDataReader/GenericParquetReaders/GenericOrcReader)` 替换为 `FormatModelRegistry.readBuilder(format, Record.class, inputFile).project(DeleteSchemaUtil.posDeleteReadSchema(spec.schema())).reuseContainers().build()`。
- `positionDeletesWriter`：原 `switch` + 三段 `Avro.writeDeletes/Parquet.writeDeletes/ORC.writeDeletes` + `createWriterFunc(DataWriter/GenericParquetWriter/GenericOrcWriter)` 改为分支：若 `rowSchema == null` 用 `FormatModelRegistry.<Record>positionDeleteWriteBuilder(format, EncryptedFiles.plainAsEncryptedOutput(outputFile)).partition(partition).spec(spec).build()`（新 API，注意要把 `InputFile` 包装为 `EncryptedOutputFile`）；否则保留原 switch 三段（带行 schema 的 legacy 路径），与 `SparkFileWriterFactory` 的 legacy 处理一致。删除 `PlannedDataReader`/`GenericOrcReader`/`GenericParquetReaders` import，新增 `EncryptedFiles`/`FormatModelRegistry` import。

### `spark/v4.1/spark/src/jmh/java/org/apache/iceberg/spark/data/parquet/SparkParquetReadersFlatDataBenchmark.java`、`SparkParquetReadersNestedDataBenchmark.java`（各 +5/-2 lines）

**修改目的**：benchmark 适配新读 API。

**工作逻辑**：两份 reader benchmark 中 `Parquet.read(Files.localInput(dataFile)).project(SCHEMA).createReaderFunc(type -> SparkParquetReaders.buildReader(SCHEMA, type)).build()` 改为 `FormatModelRegistry.readBuilder(FileFormat.PARQUET, InternalRow.class, Files.localInput(dataFile)).project(SCHEMA).build()`，删除 `createReaderFunc`。新增 `FileFormat`、`FormatModelRegistry` import，移除 `SparkParquetReaders` import。

### `spark/v4.1/spark/src/jmh/java/org/apache/iceberg/spark/data/parquet/SparkParquetWritersFlatDataBenchmark.java`、`SparkParquetWritersNestedDataBenchmark.java`（各 +11/-4 lines）

**修改目的**：benchmark 适配新写 API。

**工作逻辑**：两份 writer benchmark 中 `Parquet.write(Files.localOutput(dataFile)).createWriterFunc(msgType -> SparkParquetWriters.buildWriter(SparkSchemaUtil.convert(SCHEMA), msgType)).build()` 改为 `FormatModelRegistry.dataWriteBuilder(FileFormat.PARQUET, InternalRow.class, EncryptedFiles.plainAsEncryptedOutput(Files.localOutput(dataFile))).schema(SCHEMA).spec(PartitionSpec.unpartitioned()).build()`，返回类型由 `FileAppender<InternalRow>` 改为 `DataWriter<InternalRow>`，`writer.addAll(rows)` 改为 `writer.write(rows)`。此外在 Spark 原生写入路径的 SQLConf 中新增 `.set("spark.sql.parquet.variant.annotateLogicalType.enabled", "false")`，与 Iceberg writer 的行为对齐（避免 variant 逻辑类型标注差异影响 benchmark）。新增 `FileFormat`、`PartitionSpec`、`EncryptedFiles`、`FormatModelRegistry`、`DataWriter` import，移除 `SparkParquetWriters` import。

## 总结

本提交通过新增 `SparkFormatModels` 注册类并把 `SparkFileWriterFactory`、`BaseBatchReader`、`BaseRowReader`、`RewriteTablePathSparkAction` 迁移到 `FormatModelRegistry` 与 `RegistryBasedFileWriterFactory<InternalRow, StructType>`，消除了 Spark 4.1 适配器中 9 个格式特定写回调与多段格式特定读分支（含 Parquet 普通/Comet、ORC 向量化等多种对象模型），使 Spark 读写路径不再直接依赖 Avro/Parquet/ORC 的 builder API。通过新增 `CometColumnarBatch` 标记类、`SparkParquetWriters` 重载、`useOrConvert` 工具方法，解决了新统一 API 与 Spark 现有读写器签名的适配；通过 `useDeprecatedPositionDeleteWriter` 标志与 legacy 覆写路径保留了"带行数据的位置删除"这一废弃特性的兼容。这是 FormatModel API 迁移系列在引擎侧的最大一次落地，与 3271（Data）、3272（Flink）共同完成了 1.11.0 的整体格式抽象重构。
