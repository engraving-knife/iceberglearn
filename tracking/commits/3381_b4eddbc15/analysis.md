# 提交 3381：Data, Spark, Flink: Add TCK for File Format API (#15441)

## 提交信息

- **序号**：3381 / 4088
- **哈希**：b4eddbc1557a91698473bad6fee7f58c6edd7708
- **短哈希**：b4eddbc15
- **日期**：2026-03-13
- **作者**：Joy Haldar
- **提交说明**：Data, Spark, Flink: Add TCK for File Format API (#15441)
- **PR/Issue**：#15441

## 总体目的

本提交为 Iceberg 新引入的 File Format API（文件格式 API，即 `FormatModelRegistry` 及 `FileWriterBuilder` 等）建立技术兼容性工具包（TCK，Technology Compatibility Kit）测试体系，确保不同文件格式（Avro、Parquet、ORC）在不同引擎类型（Generic Record、Spark InternalRow、Flink RowData）之间的写入与读取能正确互操作。

Iceberg 的 File Format API 是一套统一的读写抽象：通过 `FormatModelRegistry.dataWriteBuilder(fileFormat, engineType, encryptedFile)` 可以针对指定文件格式和引擎类型构造写入器，通过 `readBuilder(fileFormat, engineType, inputFile)` 构造读取器。此前的测试 `TestGenericFormatModels` 仅覆盖 Generic Record 一种引擎类型，且只做了"写后读"的同引擎往返测试，无法验证"用引擎 A 写、用引擎 B 读"这种跨引擎互操作性——而这正是 File Format API 的核心价值所在。本提交将该测试重构为参数化的抽象基类 `BaseFormatModelTests<T>`，并为其在 Spark（`TestSparkFormatModel`）和 Flink（`TestFlinkFormatModel`）中提供具体实现，形成完整的 TCK。

测试覆盖三种文件格式与多种数据生成器的笛卡尔积组合，针对数据写入、等值删除写入、位置删除写入三类操作，分别验证"引擎写 + Generic 读"和"Generic 写 + 引擎读"两个方向的往返正确性，从而构成跨格式、跨引擎的全矩阵兼容性验证。

## 如何达成设计目的

整体设计采用抽象基类 + 引擎子类的模板方法模式。`BaseFormatModelTests<T>` 定义了所有测试逻辑与抽象钩子方法（`engineType`、`engineSchema`、`convertToEngine`、`assertEquals`），各引擎子类只需实现这四个钩子即可复用全部测试矩阵。配套新增 `DataGenerator`/`DataGenerators` 提供可扩展的测试数据生成器框架。同时删除了被替代的 `TestGenericFormatModels`，并为 Spark 新增 `InternalRowConverter` 工具类以将 Generic Record 转换为 Spark `InternalRow`。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+323/-0 lines，新建)

**修改目的**：建立跨格式、跨引擎的文件格式 TCK 抽象测试基类。

**工作逻辑**：
`BaseFormatModelTests<T>` 是泛型抽象类，`T` 代表引擎的行类型（如 Spark 的 `InternalRow`、Flink 的 `RowData`）。它定义四个抽象钩子：`engineType()` 返回引擎行类型的 `Class`；`engineSchema(Schema)` 将 Iceberg schema 转为引擎 schema；`convertToEngine(Record, Schema)` 将 Generic Record 转为引擎行；`assertEquals(Schema, List<T>, List<T>)` 比较引擎行列表。

测试矩阵由 `FILE_FORMATS`（AVRO、PARQUET、ORC）与 `DataGenerators.ALL` 笛卡尔积构成 `FORMAT_AND_GENERATOR`，通过 `@FieldSource` 喂给参数化测试。每个测试方法用 `InMemoryFileIO` 与 `EncryptedOutputFile` 作为文件后端。

核心测试方法分四组（均参数化）：
- `testDataWriterEngineWriteGenericRead`：用引擎类型 T 写入数据文件，再用 Generic Record 读取并校验——验证引擎写入的文件能被通用读取器正确解析。
- `testDataWriterGenericWriteEngineRead`：用 Generic Record 写入，再用引擎类型 T 读取并校验——验证通用写入的文件能被引擎读取器正确解析。
- `testEqualityDeleteWriterEngineWriteGenericRead` / `testEqualityDeleteWriterGenericWriteEngineRead`：对 equality delete 文件做相同的双向往返测试，校验 `equalityFieldIds` 等 `DeleteFile` 元数据。
- `testPositionDeleteWriterEngineWriteGenericRead`：对 position delete 文件做引擎写入 + Generic 读取的往返测试（仅按格式参数化，因 position delete schema 固定为 `DELETE_FILE_PATH` + `DELETE_FILE_POS`）。

`convertToEngineRecords` 辅助方法批量转换 Record 列表为引擎行列表。

### `data/src/test/java/org/apache/iceberg/data/DataGenerator.java` (+34/-0 lines，新建)

**修改目的**：定义测试数据生成器接口。

**工作逻辑**：
`DataGenerator` 接口定义 `schema()` 返回 Iceberg Schema，并提供默认方法 `generateRecords()` 调用 `RandomGenericData.generate(schema(), 10, 1L)` 生成 10 条随机记录（种子固定为 1L 以保证可复现）。接口设计为可扩展——新增数据生成器只需实现 `schema()` 即可。

### `data/src/test/java/org/apache/iceberg/data/DataGenerators.java` (+52/-0 lines，新建)

**修改目的**：提供具体数据生成器实现并注册到测试矩阵。

**工作逻辑**：
`DataGenerators.ALL` 数组是测试矩阵的数据来源，目前包含一个生成器 `StructOfPrimitive`，其 schema 为包含 `row_id`（String）和一个嵌套 struct（`id` Integer + `name` String）的结构。注释说明可通过向 `ALL` 数组添加新生成器来扩展测试覆盖。嵌套 struct 的设计可验证格式 API 对嵌套类型的处理。

### `data/src/test/java/org/apache/iceberg/data/TestGenericFormatModels.java` (+0/-205 lines，删除)

**修改目的**：移除被 `BaseFormatModelTests` 替代的旧测试。

**工作逻辑**：
删除原 `TestGenericFormatModels` 类。该类仅覆盖 Generic Record 的同格式往返测试，且依赖 `TestBase.SCHEMA`，已被参数化、跨引擎的 `BaseFormatModelTests` 体系完全取代。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkFormatModel.java` (+51/-0 lines，新建)

**修改目的**：为 Flink 引擎提供 TCK 实现。

**工作逻辑**：
`TestFlinkFormatModel extends BaseFormatModelTests<RowData>`，实现四个钩子：`engineType()` 返回 `RowData.class`；`engineSchema()` 调用 `FlinkSchemaUtil.convert(schema)`；`convertToEngine()` 调用 `RowDataConverter.convert(schema, record)`；`assertEquals()` 调用 `TestHelpers.assertRows(actual, expected, FlinkSchemaUtil.convert(schema))`。该类本身不包含测试逻辑——全部继承自基类，仅提供 Flink 特定的类型转换与断言适配。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/InternalRowConverter.java` (+115/-0 lines，新建)

**修改目的**：提供 Iceberg Record 到 Spark InternalRow 的转换工具。

**工作逻辑**：
`InternalRowConverter` 是静态工具类，`convert(Schema, Record)` 入口将 Iceberg Record 转为 Spark `GenericInternalRow`。内部按 `Type.typeId()` 分支处理各类型：基本类型（BOOLEAN、INTEGER、LONG、FLOAT、DOUBLE）直接透传；DATE 转为自 epoch 的天数；TIMESTAMP 根据 `shouldAdjustToUTC()` 分别处理 `OffsetDateTime` 与 `LocalDateTime`，转为自 epoch 的微秒；STRING/UUID 转为 `UTF8String`；FIXED/BINARY 从 `ByteBuffer` 提取字节数组；DECIMAL 转为 Spark `Decimal`；STRUCT 递归转换；LIST 转为 `GenericArrayData`；MAP 转为 `ArrayBasedMapData`。TIME 类型不被 Spark 支持、VARIANT 尚未实现，均落入 default 分支抛出 `UnsupportedOperationException`。该转换器使 Spark TCK 测试能将 Generic Record 期望值转为 InternalRow 进行比较。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkFormatModel.java` (+54/-0 lines，新建)

**修改目的**：为 Spark 引擎提供 TCK 实现。

**工作逻辑**：
`TestSparkFormatModel extends BaseFormatModelTests<InternalRow>`，实现四个钩子：`engineType()` 返回 `InternalRow.class`；`engineSchema()` 调用 `SparkSchemaUtil.convert(schema)`；`convertToEngine()` 调用 `InternalRowConverter.convert(schema, record)`；`assertEquals()` 逐行调用 `TestHelpers.assertEquals(schema, expected.get(i), actual.get(i))` 比较。与 Flink 实现对称，仅提供 Spark 特定的类型适配。

## 总结

本提交为 Iceberg File Format API 建立了完整的跨格式、跨引擎兼容性测试体系（TCK）。通过抽象基类 `BaseFormatModelTests<T>` 与模板方法模式，将测试逻辑与引擎适配解耦，Spark 和 Flink 各仅需实现四个钩子方法即可复用全部测试矩阵。测试覆盖 Avro/Parquet/ORC 三种格式与数据生成器的组合，针对数据写入与等值/位置删除写入做双向往返验证，有效保障了 File Format API 在不同引擎间的互操作正确性。同时引入 `DataGenerator`/`DataGenerators` 框架便于后续扩展测试数据覆盖范围。
