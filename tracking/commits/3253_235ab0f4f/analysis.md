# 提交 3253：Parquet, Data: Implementation of ParquetFormatModel (#15253)

## 提交信息

- **序号**：3253 / 4088
- **哈希**：235ab0f4fd34f5e71642030577a7ea996e2c4489
- **短哈希**：235ab0f4f
- **日期**：2026-02-15
- **作者**：pvary
- **提交说明**：Parquet, Data: Implementation of ParquetFormatModel (#15253)
- **PR/Issue**：#15253

## 总体目的

Iceberg 正在引入 FormatModel 抽象层，旨在将文件格式特定的读写逻辑从引擎集成代码（Spark、Flink 等）中解耦。在此前的提交 #15254（序号 3245）中已建立了 `AvroFormatModel` 以及 `BaseFormatModel`、`FormatModelRegistry`、`ReadBuilder`、`ModelWriteBuilder` 等抽象接口，确立了格式模型的注册和使用模式。本次提交为 Parquet 格式实现对应的 `ParquetFormatModel`，是该抽象层在 Parquet 这一 Iceberg 最主要列式存储格式上的落地。

此前，各引擎模块需要直接调用 `Parquet.write(...)` / `Parquet.read(...)` 并自行处理 writer function、reader function、加密属性、metrics 配置、文件内容类型（DATA/POSITION_DELETES/EQUALITY_DELETES）等细节。这种紧耦合导致每个引擎都需要重复实现格式特定的适配逻辑，且难以统一演进。FormatModel 抽象层通过提供统一的 `writeBuilder` / `readBuilder` API，使引擎只需通过 `FormatModelRegistry` 获取已注册的格式模型，即可用统一接口完成所有文件格式的读写，无需关心底层 Parquet API 的细节。

本次提交还解决了 Parquet 向量化批量读取的一个实际限制：原有 `Parquet.ReadBuilder.createBatchedReaderFunc` 仅接受 `Function<MessageType, VectorizedReader<?>>`，即创建批量读取器时只能拿到 Parquet 的 `MessageType`（文件 schema），无法获取 Iceberg 的 `Schema`（投影 schema）和常量字段映射（`idToConstant`）。新增的 `createBatchedReaderFunc(BiFunction<Schema, MessageType, VectorizedReader<?>>)` 方法将 Iceberg schema 一起传入，使 FormatModel 包装层能够完整地构建带投影和常量注入的批量读取器。

## 如何达成设计目的

新建 `ParquetFormatModel` 类继承 `BaseFormatModel`，通过 `WriteBuilderWrapper` 和 `ReadBuilderWrapper` 两个内部类将统一的 `ModelWriteBuilder` / `ReadBuilder` API 适配到既有的 `Parquet.WriteBuilder` / `Parquet.ReadBuilder`。在 `data` 模块的 `GenericFormatModels` 中注册 Parquet 格式模型，绑定 `GenericParquetWriter` 和 `GenericParquetReaders` 作为具体的读写器工厂。同时修改 `Parquet.java`，将部分内部 API 的可见性从 private 提升到 package-private 以供包装层访问，并新增带 Iceberg Schema 的批量读取器构造方法。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetFormatModel.java` (+317 lines，新增文件)

**修改目的**：实现 Parquet 格式的 FormatModel，提供统一的读写构建器 API。

**工作逻辑**：
该类继承 `BaseFormatModel<D, S, ParquetValueWriter<?>, R, MessageType>`，其中泛型 `D` 为数据类型、`S` 为引擎 schema 类型、`R` 为读取器类型、`MessageType` 为 Parquet 文件 schema 类型。提供三个静态工厂方法：`forPositionDeletes()` 创建位置删除文件的格式模型（writer 为 `PositionDeleteStructWriter`，无 reader）；`create(Class, Class, WriterFunction, ReaderFunction)` 创建普通行式读取的格式模型；`create(Class, Class, ReaderFunction)` 创建向量化批量读取的格式模型（`isBatchReader=true`）。通过 `isBatchReader` 标志在 `ReadBuilderWrapper.build()` 中选择调用 `createReaderFunc`（行式）或 `createBatchedReaderFunc`（批量）。

`WriteBuilderWrapper` 包装 `Parquet.WriteBuilder`，在 `build()` 时根据 `FileContent` 分支处理：DATA 和 EQUALITY_DELETES 调用 `writerFunction.write(icebergSchema, messageType, engineSchema)` 创建 writer；POSITION_DELETES 则校验不传入 schema，使用 `GenericParquetWriter` 创建 struct writer 后包装为 `PositionDeleteStructWriter`，并设置 `DeleteSchemaUtil.pathPosSchema()` 作为写入 schema。每种内容类型还通过 `createContextFunc` 设置对应的 `Context`（dataContext 或 deleteContext），该方法是本次提交从 private 提升为 package-private 的。

`ReadBuilderWrapper` 包装 `Parquet.ReadBuilder`，在 `build()` 时根据 `isBatchReader` 调用对应的 reader function 构造方法，将 `icebergSchema`、`messageType`、`engineSchema` 和 `idToConstant` 传入用户提供的 `ReaderFunction`。`engineProjection` 方法仅暂存 engineSchema 供 build 时使用，不直接作用于内部 builder。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+47/-8 lines)

**修改目的**：开放内部 API 供 FormatModel 包装层访问，并新增带 Iceberg Schema 的批量读取器构造方法。

**工作逻辑**：
三处关键修改：

1. `WriteBuilder.createContextFunc` 和 `WriteBuilder.Context` 的可见性从 `private` 提升为 package-private（`WriteBuilder` 和 `static class Context`），使 `ParquetFormatModel.WriteBuilderWrapper` 能够调用 `createContextFunc` 设置上下文并访问 `Context.dataContext()` / `Context.deleteContext()`。

2. 新增字段 `BiFunction<Schema, MessageType, VectorizedReader<?>> batchedReaderFuncWithSchema` 和对应方法 `createBatchedReaderFunc(BiFunction<Schema, MessageType, VectorizedReader<?>> func)`。该方法与既有的 `batchedReaderFunc`（仅接受 MessageType）、`readerFunction` 互斥——所有设置 reader function 的方法都新增了 `Preconditions.checkArgument` 检查 `batchedReaderFuncWithSchema == null`，反之亦然。这确保同一 ReadBuilder 只会设置一种读取器构造方式。

3. 在 `build()` 方法中，将 `batchedReaderFuncWithSchema` 解析为 `batchedFunc`：若 `batchedReaderFuncWithSchema != null`，则通过 `messageType -> batchedReaderFuncWithSchema.apply(schema, messageType)` 将 Iceberg schema 绑定到 BiFunction 上，生成兼容原有签名的 `Function<MessageType, VectorizedReader<?>>`。条件判断也从 `batchedReaderFunc != null || readerFunction != null` 扩展为 `batchedReaderFunc != null || batchedReaderFuncWithSchema != null || readerFunction != null`。这种适配方式使底层 `VectorizedParquetReader` 无需修改即可同时支持新旧两种构造方式。

### `data/src/main/java/org/apache/iceberg/data/GenericFormatModels.java` (+14 lines)

**修改目的**：在格式模型注册表中注册 Parquet 格式的通用读写器。

**工作逻辑**：
在已有的 Avro 格式模型注册之后，新增两处注册：第一处通过 `ParquetFormatModel.create(Record.class, Void.class, ...)` 注册数据文件的 Parquet 格式模型，writer function 调用 `GenericParquetWriter.create(icebergSchema, fileSchema)`，reader function 调用 `GenericParquetReaders.buildReader(icebergSchema, fileSchema, idToConstant)`；第二处通过 `ParquetFormatModel.forPositionDeletes()` 注册位置删除文件的格式模型。`Void.class` 作为 engineSchema 类型表示通用数据层不使用引擎特定的 schema 投影。

### `data/src/test/java/org/apache/iceberg/data/TestGenericFormatModels.java` (+3/-1 lines)

**修改目的**：将 Parquet 格式纳入格式模型测试覆盖范围。

**工作逻辑**：
将 `FILE_FORMATS` 数组从 `{FileFormat.AVRO}` 扩展为 `{FileFormat.AVRO, FileFormat.PARQUET}`，使现有的格式模型读写测试自动覆盖 Parquet 格式，验证通过 FormatModel 抽象层进行 Parquet 数据写入和读取的正确性。

## 总结

本次提交实现了 Parquet 格式的 FormatModel 抽象层落地，新建 `ParquetFormatModel` 类将 Parquet 的读写构建器统一封装为 `BaseFormatModel` 标准接口，并在通用数据层注册了基于 `GenericParquetWriter`/`GenericParquetReaders` 的实现。同时扩展了 `Parquet.ReadBuilder` 支持带 Iceberg Schema 的向量化批量读取器构造，解决了原有 API 无法获取投影 schema 的限制。这是 Iceberg 格式读写解耦架构在 Parquet 上的关键一步，使引擎集成可通过统一 FormatModel API 操作 Parquet 文件。
