# 提交 0262：Data: Add GenericFileWriterFactory (#9267)

## 提交信息

- **序号**：0262 / 4088
- **哈希**：0c5b87a27e0564ddaae2fa1cb93c32dd7a5e8681
- **短哈希**：0c5b87a27
- **日期**：2023-12-12 10:17:08 +0100
- **作者**：Anton Okolnychyi
- **提交说明**：Data: Add GenericFileWriterFactory (#9267)
- **PR/Issue**：#9267

## 总体目的

Iceberg 在 `iceberg-core` 中定义了 `FileWriterFactory<T>` 接口以及 `DataWriter`、`EqualityDeleteWriter`、`PositionDeleteWriter` 三类"统一写"抽象，它们在内部封装了文件写入 + 指标采集 + DataFile/DeleteFile 构造的全过程，可以通过 `writer.toDataFile()` 直接产出元数据对象。然而在 `iceberg-data` 模块中，原有基于通用 `Record` 模型的写入工具仍然停留在老的 `FileAppenderFactory` + `FileAppender` 模型上（即 `GenericAppenderFactory`），测试代码和 `FileHelpers` 都需要自己根据 `FileAppender.length()`、`metrics()`、`splitOffsets()` 再手工拼装 `DataFiles.builder(...)`。这导致了三方面问题：

1. 老的 `FileAppender` API 不感知分区、spec、加密元数据等上下文，写完后需要调用方手动装配 `DataFile`/`DeleteFile`，样板代码多且容易出错。
2. `iceberg-data` 没有一个对外的、与 Spark 等引擎侧对齐的 `FileWriterFactory<Record>` 实现，无法直接复用统一的 `TestFileWriterFactory` 套件进行端到端的写入测试。
3. 引擎集成（如 Flink）在测试 readable metrics 时使用的工具没有走新的统一写入路径，导致同一份数据在不同实现下产生的 file size / value count 等指标略有偏差，既不一致也难维护。

本提交通过新增 `GenericFileWriterFactory` 来解决上述问题：它继承 `BaseFileWriterFactory<Record>`，以通用 `Record` 为类型参数，对 Avro/Parquet/ORC 三种格式的数据/等值删除/位置删除六种组合各实现一个 `configureXxx` 钩子，注入 `DataWriter::create`、`GenericParquetWriter::buildWriter`、`GenericOrcWriter::buildWriter` 作为 `createWriterFunc`。同时把 `FileHelpers` 中所有写文件的工具方法切到新工厂，并新增 `TestGenericFileWriterFactory` 接入标准 `TestFileWriterFactory` 套件。Flink 三个版本（1.16/1.17/1.18）的 `TestMetadataTableReadableMetrics` 也跟着调整了预期指标值以匹配新写入器产生的实际值。

## 如何达成设计目的

整体思路是"复用已有的 `BaseFileWriterFactory` 抽象基类 + 配套 `FileWriterFactory` 接口，为 `iceberg-data` 补一个具体实现"。`BaseFileWriterFactory<T>` 已经把三种格式 × 三种写入类型的装配流程（取加密输出文件、注入表属性、`MetricsConfig`、spec/partition/sortOrder、加密元数据等）封装到模板方法里，并暴露 `configureDataWrite/configureEqualityDelete/configurePositionDelete` 三个钩子；子类只需针对每种格式的 Builder 注入 `createWriterFunc`。本提交新增的 `GenericFileWriterFactory` 就是把这 6 个钩子全部填好，对应 `iceberg-data` 已有的 `DataWriter`（Avro）、`GenericParquetWriter`、`GenericOrcWriter`。然后通过一个内部 `Builder` 把工厂的 9 个参数（table、dataFileFormat、dataSchema、dataSortOrder、deleteFileFormat、equalityFieldIds、equalityDeleteRowSchema、equalityDeleteSortOrder、positionDeleteRowSchema）按需装配，默认值从表属性中读取，从而让 `FileHelpers` 这类调用方一行代码就能拿到工厂。最后所有写测试工具改用新工厂的 `newDataWriter/newEqualityDeleteWriter/newPositionDeleteWriter` 并直接调 `writer.toDataFile()`，去掉手工拼装 `DataFiles.builder(...)` 的代码。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/GenericFileWriterFactory.java`（新文件）

**修改目的**：为 `iceberg-data` 提供基于 `Record` 的 `FileWriterFactory` 实现，统一数据/等值删除/位置删除文件的写入入口。

**工作逻辑**：

- 类声明为 `class GenericFileWriterFactory extends BaseFileWriterFactory<Record>`，包私有构造方法接收 9 个参数（table、dataFileFormat、dataSchema、dataSortOrder、deleteFileFormat、equalityFieldIds、equalityDeleteRowSchema、equalityDeleteSortOrder、positionDeleteRowSchema），全部透传给 `super(...)`。
- 提供 `static Builder builderFor(Table table)` 作为唯一构造入口。
- 实现六个 `configureXxx` 钩子：
  - Avro 数据/等值删除/位置删除：均通过 `builder.createWriterFunc(DataWriter::create)` 注入 Avro `DataWriter` 作为记录写入器（`DataWriter` 同时负责写 Avro 数据 + 收集 metrics + 写出 `DataFile` 元数据）。
  - Parquet 三种：`builder.createWriterFunc(GenericParquetWriter::buildWriter)`。
  - ORC 三种：`builder.createWriterFunc(GenericOrcWriter::buildWriter)`。
- 内部 `Builder` 类持有与构造参数对应的字段。构造时 `dataSchema = table.schema()`；`dataFileFormat` 由 `table.properties()` 中的 `DEFAULT_FILE_FORMAT`（默认 `DEFAULT_FILE_FORMAT_DEFAULT`）解析；`deleteFileFormat` 由 `DELETE_DEFAULT_FILE_FORMAT` 解析，缺省回退到 `dataFileFormat`，与 Iceberg 表属性语义一致。其余字段由链式 setter 设置。
- `build()` 中加入约束：`equalityFieldIds` 与 `equalityDeleteRowSchema` 必须同时设置或同时不设置（`Preconditions.checkArgument(noEqualityDeleteConf || fullEqualityDeleteConf, "Equality field IDs and equality delete row schema must be set together")`），避免半配置状态导致等值删除写入异常。

### `data/src/test/java/org/apache/iceberg/data/FileHelpers.java`

**修改目的**：把所有写文件的工具方法切换到新的 `GenericFileWriterFactory`，简化样板代码并统一指标采集路径。

**工作逻辑**：

- 删除 `defaultFormat(Map)` 私有方法和相关 import（`FileFormat`、`Map`、`DEFAULT_FILE_FORMAT`、`DEFAULT_FILE_FORMAT_DEFAULT`、`DataFiles`），把对 `FileFormat` 的解析下沉到 `GenericFileWriterFactory.Builder`。
- import 从 `FileAppender` / `FileAppenderFactory` 改为 `DataWriter` / `FileWriterFactory`，与新 API 对齐。
- `writeDataFile(Table, OutputFile, List<Record>)` 与 `writeDataFile(Table, OutputFile, StructLike, List<Record>)`：原先用 `GenericAppenderFactory.newAppender(out, format)` 得到 `FileAppender`，调用 `addAll(rows)`，再用 `DataFiles.builder(table.spec()).withFormat(...).withPath(...).withFileSizeInBytes(writer.length()).withSplitOffsets(...).withMetrics(writer.metrics()).build()` 手工拼装 `DataFile`。新版本直接 `GenericFileWriterFactory.builderFor(table).build()`，调 `factory.newDataWriter(encrypt(out), table.spec(), partition)` 拿到 `DataWriter<Record>`，写入后 `writer.toDataFile()` 即可得到 `DataFile`。分区场景下分区值通过 `newDataWriter(..., partition)` 直接传入，不再需要在拼装阶段单独 `withPartition`。
- `writeDeleteFile(Table, OutputFile, StructLike, List<Pair<CharSequence, Long>>)`（位置删除）：由原 `GenericAppenderFactory` + `newPosDeleteWriter(encrypt(out), format, partition)` 改为 `GenericFileWriterFactory.builderFor(table).build()` + `factory.newPositionDeleteWriter(encrypt(out), table.spec(), partition)`，方法名从 `newPosDeleteWriter` 改为 `newPositionDeleteWriter`，与新接口签名一致。
- `writeEqualityDeleteFile(Table, OutputFile, StructLike, List<Record>, Schema)`（等值删除）：原先手工构造 `int[] equalityFieldIds` 并用 `new GenericAppenderFactory(table.schema(), table.spec(), equalityFieldIds, deleteRowSchema, null)` + `newEqDeleteWriter`，新版改为 `GenericFileWriterFactory.builderFor(table).equalityDeleteRowSchema(deleteRowSchema).equalityFieldIds(equalityFieldIds).build()` + `factory.newEqualityDeleteWriter(...)`，把工厂的内部配置职责清晰化。
- `writePosDeleteFile(Table, OutputFile, StructLike, List<PositionDelete<?>>)`：原本要显式传 `table.schema()` 作为 position delete row schema，新版用 `GenericFileWriterFactory.builderFor(table).positionDeleteRowSchema(table.schema()).build()` 显式表达意图，再调 `newPositionDeleteWriter`。
- 所有 `EncryptedOutputFile` 仍由 `encrypt(out)` 包装为带空 key metadata 的加密输出（兼容现有测试场景）。

### `data/src/test/java/org/apache/iceberg/data/TestGenericFileWriterFactory.java`（新文件）

**修改目的**：让 `iceberg-data` 接入 Iceberg 标准 `TestFileWriterFactory<Record>` 套件，统一验证 `GenericFileWriterFactory` 的数据/等值删除/位置删除写入行为。

**工作逻辑**：

- 继承 `TestFileWriterFactory<Record>`，构造方法接收 `FileFormat` 和 `boolean partitioned`，由父类参数化测试驱动。
- `newWriterFactory(...)` 实现按基类要求构造 `GenericFileWriterFactory`：通过 `builderFor(table).dataSchema(...).dataFileFormat(format()).deleteFileFormat(format()).equalityFieldIds(ArrayUtil.toIntArray(equalityFieldIds)).equalityDeleteRowSchema(...).positionDeleteRowSchema(...).build()`，把基类传入的 schema 信息装配进工厂。
- `toRow(Integer, String)` 用 `GenericRecord.create(table.schema().asStruct())` 构造一条 `(id, data)` 记录，配合父类生成测试数据。
- `toSet(Iterable<Record>)` 用 `StructLikeSet.create(table.schema().asStruct())` 把记录收集成集合，用于和读回的记录做无序比较。

### `data/src/test/java/org/apache/iceberg/io/TestFileWriterFactory.java`

**修改目的**：把 `dataRows` 的初始化从构造方法挪到 `setup` 阶段，确保子类在 `setup` 中有机会覆盖 `table` 后再生成数据。

**工作逻辑**：

- 移除 `final` 修饰并删除构造方法中 `this.dataRows = ImmutableList.of(...)`。
- 在 `setup` 中（`table` 已经初始化完成之后）赋值 `this.dataRows = ImmutableList.of(toRow(1, "aaa"), toRow(2, "aaa"), toRow(3, "aaa"), toRow(4, "aaa"), toRow(5, "aaa"))`。这样 `toRow` 调用时 `table.schema()` 已经可用，避免子类在 schema 未就绪时构造记录报错，是与新 `TestGenericFileWriterFactory.toRow` 配合所必需的调整。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`、`flink/v1.17/.../TestMetadataTableReadableMetrics.java`、`flink/v1.18/.../TestMetadataTableReadableMetrics.java`

**修改目的**：调整 readable metrics 测试中预期的文件大小（第一列字段），使其与新写入器实际产生的文件大小一致。

**工作逻辑**：

- `testNestedValues`、`testPrimitiveAndDerivedMetrics` 等用例中，每种类型的 `Row.of(sizeInBytes, valueCount, nullCount, nanCount, lower, upper)` 的"sizeInBytes"列（每行第一个 `Long`）被更新为更小的数值，例如 binaryCol 59→52、booleanCol 44→32、decimalCol 97→85、doubleCol 99→85、fixedCol 55→44、floatCol 90→71、intCol 91→71、longCol 91→79、stringCol 99→79、leafDoubleCol 53→46。
- 这些值变小是因为 `FileHelpers.writeDataFile` 切换到 `GenericFileWriterFactory` + `DataWriter`/`GenericParquetWriter` 后，输出文件不再走旧的 `FileAppender` 路径，Parquet 行组、统计、压缩等细节略有差异，导致文件字节数变少；Flink 这三个版本（1.16/1.17/1.18）的 `TestMetadataTableReadableMetrics` 用例一致地更新了预期值以匹配新写入器。这种调整属于测试期望跟随实现的"机械"修正，本身不改变 readable metrics 的语义。

## 小结

本提交通过新增 `GenericFileWriterFactory` 并把 `iceberg-data` 的测试工具切换到新的 `FileWriterFactory` 路径，补齐了 `iceberg-data` 在统一写 API 上的实现缺口，使基于通用 `Record` 的数据/等值删除/位置删除写入可以与 Spark、Flink 等引擎使用同一套 `FileWriterFactory` 抽象，显著降低了样板代码并让 Flink readable metrics 测试与新写入路径保持一致。
