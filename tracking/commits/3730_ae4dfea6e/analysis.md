# 提交 3730：Flink: Add decimal write/read roundtrip test for FlinkParquetReaders (#16346)

## 提交信息

- **序号**：3730 / 4088
- **哈希**：ae4dfea6e40907459c611c99f0c22952177c6f3a
- **短哈希**：ae4dfea6e
- **日期**：2026-05-18 14:16:09 +0200
- **作者**：Vova Kolmakov
- **提交说明**：Flink: Add decimal write/read roundtrip test for FlinkParquetReaders (#16346)
- **PR/Issue**：#16346

## 总体目的

本提交旨在为 Flink Parquet 读取器（`FlinkParquetReaders`）的 decimal 类型读写添加往返（roundtrip）回归测试，填补原先代码中标记的 TODO 缺口。

在 `FlinkParquetReaders` 的 decimal 读取逻辑中，原本有一行注释 `// TODO: need a unit test to write-read-validate decimal via FlinkParquetWrite/Reader`，表明该读取路径缺乏对应的单元测试覆盖。decimal 类型在不同精度（precision）和标度（scale）下的二进制编解码逻辑较易出错，且 Flink 使用 `DecimalData` 这一专用类型，与 Iceberg 的 `BigDecimal` 之间存在转换，因此需要专门的测试来验证：用 Flink 引擎类型写入 decimal 数据，再用 Flink 引擎类型读取，确认数据一致。

## 如何达成设计目的

通过在共享的测试基础设施中新增：
1. 一个新的 `DataGenerator` 实现 `Decimals`，定义包含三种不同精度/标度 decimal 字段的 schema（`dec_9_2`、`dec_15_3`、`dec_38_10`），覆盖小精度、中精度和最大精度场景。
2. 将该 `Decimals` 生成器注册到 `DataGenerators.ALL` 数组，使其自动被参数化测试使用。
3. 在 `BaseFormatModelTests` 中新增 `testDataWriterEngineWriteEngineRead` 测试方法，使用引擎类型 T 写入数据，再用引擎类型 T 读取，验证 roundtrip 一致性。
4. 删除 Flink 1.20/2.0/2.1 三个版本 `FlinkParquetReaders` 中已完成的 TODO 注释。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+36/-0 lines)

**修改目的**：新增"引擎类型写入 + 引擎类型读取"的 roundtrip 测试方法。

**工作逻辑**：
新增 `testDataWriterEngineWriteEngineRead` 方法，使用 `@ParameterizedTest` + `@FieldSource("FORMAT_AND_GENERATOR")` 对文件格式与数据生成器进行参数化。
- 通过 `FormatModelRegistry.dataWriteBuilder` 构建引擎类型 `DataWriter<T>`。
- 用 `dataGenerator.generateRecords()` 生成 Generic Record，再 `convertToEngineRecords` 转为引擎类型 T 的记录。
- 用 writer 写入所有引擎记录，关闭后获取 `DataFile`。
- 断言 dataFile 不为 null、记录数一致、格式一致。
- 通过 `FormatModelRegistry.readBuilder` 构建读取器，project 原 schema，读取所有记录。
- 调用 `assertEquals(schema, engineRecords, readRecords)` 验证写入与读取的引擎记录一致。
该方法与已有的"引擎写 + Generic 读"测试互补，专门验证同引擎类型的 roundtrip。

### `data/src/test/java/org/apache/iceberg/data/DataGenerators.java` (+14/-1 lines)

**修改目的**：新增 `Decimals` 数据生成器并注册到 `ALL` 数组。

**工作逻辑**：
- 新增 `Decimals` 内部类，实现 `DataGenerator` 接口，定义 schema：
```java
private final Schema schema =
    new Schema(
        required(1, "dec_9_2", Types.DecimalType.of(9, 2)),
        required(2, "dec_15_3", Types.DecimalType.of(15, 3)),
        required(3, "dec_38_10", Types.DecimalType.of(38, 10)));
```
覆盖三种典型精度：9/2（INT32 范围）、15/3（INT64 范围）、38/10（最大精度，需要 FIXED_LEN_BYTE_ARRAY）。
- 将 `ALL` 数组从 `{new StructOfPrimitive()}` 改为 `{new StructOfPrimitive(), new Decimals()}`，使新生成器自动被参数化测试使用。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+0/-1 lines)

**修改目的**：删除已完成的 TODO 注释。

**工作逻辑**：
删除 decimal 读取方法中的注释 `// TODO: need a unit test to write-read-validate decimal via FlinkParquetWrite/Reader`，因为对应的测试已通过 `BaseFormatModelTests.testDataWriterEngineWriteEngineRead` + `Decimals` 生成器实现。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+0/-1 lines)

**修改目的**：删除 v2.0 版本中相同的 TODO 注释。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+0/-1 lines)

**修改目的**：删除 v2.1 版本中相同的 TODO 注释。

## 总结

本提交通过新增 `Decimals` 数据生成器和 `testDataWriterEngineWriteEngineRead` roundtrip 测试方法，为 Flink Parquet 读取器的 decimal 类型读写路径补充了回归测试覆盖，覆盖了 9/2、15/3、38/10 三种典型精度/标度组合。同时清理了 Flink 1.20/2.0/2.1 三个版本中已完成的 TODO 注释。这有助于及早发现 decimal 编解码或 `DecimalData` 与 `BigDecimal` 之间转换的潜在 Bug，提升 Flink 集成对 decimal 类型的正确性保障。
