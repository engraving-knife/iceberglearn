# 提交 3664：Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration to Flink 1.20 (#16240)

## 提交信息

- **序号**：3664 / 4088
- **哈希**：f767dad20e9a47495ce37f1acf6acdffbf63664d
- **短哈希**：f767dad20
- **日期**：2026-05-07 16:49:25 +0200
- **作者**：pvary
- **提交说明**：Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration to Flink 1.20 (#16240)
- **PR/Issue**：#16240（backport #15475）

## 总体目的

这个提交将 PR #15475（Flink-Iceberg 纳秒精度时间戳支持）backport 到 Flink 1.20。

这是继第 3661 号提交（backport 到 Flink 2.0）之后的进一步 backport。Iceberg v3 引入了纳秒精度时间戳类型（`timestamp_ns`/`timestamptz_ns`），Flink 的 `TimestampType`/`LocalZonedTimestampType` 当精度 > 6 时表示纳秒精度。本 backport 为 Flink 1.20 添加与 Flink 2.0 相同的纳秒精度支持，涵盖类型映射、RowData 转换、ORC 读写和 Avro 读写（含自定义 Avro 转换器，因为 Flink 1.20 自带的也不支持纳秒）。

## 如何达成设计目的

与第 3661 号提交（Flink 2.0 backport）完全一致的方案，应用于 Flink 1.20 目录：
1. `FlinkTypeToType`：精度 > 6 映射为 `TimestampNanoType`。
2. `RowDataWrapper`：根据 Iceberg 类型选择纳秒或微秒转换。
3. ORC 读写器：新增 `TIMESTAMP_NANO` case 和 `TimestampNanoWriter`/`TimestampNanoTzWriter`。
4. 新增自定义 Avro 转换器（`AvroToRowDataConverters`、`RowDataToAvroConverters`、`AvroSchemaConverter`、`JodaConverter`）。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkTypeToType.java` (+6 lines)

**修改目的**：Flink 时间戳类型到 Iceberg 类型的映射支持纳秒。

**工作逻辑**：精度 > 6 时映射为 `Types.TimestampNanoType.withoutZone()` / `withZone()`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/RowDataWrapper.java` (+26/-10 lines)

**修改目的**：RowData 包装器支持纳秒时间戳值转换。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcReader.java` (+7 lines)

**修改目的**：ORC 读取器支持纳秒时间戳。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriter.java` (+7 lines)

**修改目的**：ORC 写入器新增 `TIMESTAMP_NANO` case。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriters.java` (+35/-2 lines)

**修改目的**：新增 `TimestampNanoWriter` 和 `TimestampNanoTzWriter`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/RowDataUtil.java` (+2 lines)

**修改目的**：支持纳秒时间戳的工具方法。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/StructRowData.java` (+44/-8 lines)

**修改目的**：StructRowData 支持纳秒时间戳读取，使用 pattern matching for instanceof。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/formats/avro/AvroToRowDataConverters.java` (+303 lines, new)

**修改目的**：自定义 Avro 到 RowData 转换器，支持纳秒精度。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/formats/avro/JodaConverter.java` (+69 lines, new)

**修改目的**：Joda 时间转换工具，支持纳秒。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/formats/avro/RowDataToAvroConverters.java` (+394 lines, new)

**修改目的**：自定义 RowData 到 Avro 转换器。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/formats/avro/typeutils/AvroSchemaConverter.java` (+625 lines, new)

**修改目的**：自定义 Avro schema 转换器，支持纳秒精度时间戳的 schema 映射。

### `flink/v1.20/build.gradle` (+1 line)

**修改目的**：新增 Joda-Time 依赖。

### 其他文件

- `AvroGenericRecordToRowDataMapper.java`、`RowDataToAvroGenericRecordConverter.java`、`AvroGenericRecordConverter.java`：改用自定义 Avro 转换器。
- `DataGenerators.java`：测试数据生成器支持纳秒。
- 测试文件：适配纳秒精度。

## 总结

这个提交将纳秒精度时间戳支持 backport 到 Flink 1.20，使 Flink 1.20 能正确读写 Iceberg v3 的纳秒时间戳类型。改动与 Flink 2.0 backport 完全一致，涵盖类型映射、RowData 转换、ORC 读写和 Avro 读写（含自定义 Avro 转换器）。至此纳秒精度支持覆盖 Flink 1.20、2.0、2.1 三个版本。
