# 提交 3424：Data, Spark, Flink: Add null engineSchema fallback for format model writers (#15688)

## 提交信息

- **序号**：3424 / 4088
- **哈希**：2874fc46e8b307b93e2ebb1a849fd7365fb3cb31
- **短哈希**：2874fc46e8
- **日期**：2026-03-20 12:49:20 +0100
- **作者**：Joy Haldar
- **提交说明**：Data, Spark, Flink: Add null engineSchema fallback for format model writers (#15688)
- **PR/Issue**：#15688

## 总体目的

为格式模型写入器（format model writers）添加 engineSchema 为 null 时的回退处理。当调用方未提供 engineSchema 时，写入器应能回退使用 Iceberg schema 进行写入。此前如果 engineSchema 为 null，写入器可能抛出 NullPointerException 或产生不正确的结果。本提交确保所有格式（Avro、ORC、Parquet）的 Flink 和 Spark 写入器在 engineSchema 为 null 时正确回退。

## 如何达成设计目的

1. 在各 Flink 写入器中，当 engineSchema 为 null 时使用 `FlinkSchemaUtil.convert(schema)` 从 Iceberg schema 转换
2. 在各 Spark 写入器中，当 engineSchema 为 null 时使用 Iceberg schema
3. 在 `BaseFormatModelTests` 中新增不提供 engineSchema 的测试用例
4. 提取 `readAndAssertGenericRecords` 辅助方法消除重复代码

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+94/-38 lines)

**修改目的**：新增无 engineSchema 的写入测试并提取公共验证方法。

**工作逻辑**：
- 新增 `testDataWriterEngineWriteWithoutEngineSchema` 测试：不设置 engineSchema 写入数据文件
- 新增 `testEqualityDeleteWriterEngineWriteWithoutEngineSchema` 测试：不设置 engineSchema 写入等值删除文件
- 提取 `readAndAssertGenericRecords(FileFormat, Schema, List<Record>)` 辅助方法，消除三处重复的读取验证代码

### Flink 写入器（3个版本各一份）

**`FlinkAvroWriter.java` (+5 lines each)**：当 engineSchema 为 null 时使用 `FlinkSchemaUtil.convert(schema)` 转换

**`FlinkFormatModels.java` (+6/-1 lines each)**：engineSchema 为 null 时回退到 Iceberg schema 转换

**`FlinkOrcWriter.java` (+4/-1 lines each)**：engineSchema 为 null 时回退

**`FlinkParquetWriters.java` (+8 lines each)**：engineSchema 为 null 时回退

### Spark 写入器（4个版本各一份）

**`SparkAvroWriter.java` (+5 lines each)**：engineSchema 为 null 时使用 Iceberg schema

**`SparkFormatModels.java` (+3/-1 lines each)**：engineSchema 为 null 时回退

## 总结

本提交为所有格式模型写入器（Avro、ORC、Parquet）在 Flink 和 Spark 引擎中添加了 engineSchema 为 null 时的回退处理，使用 Iceberg schema 进行转换。新增测试验证了不设置 engineSchema 时数据写入和等值删除写入的正确性。公共验证逻辑被提取为辅助方法以消除重复。
