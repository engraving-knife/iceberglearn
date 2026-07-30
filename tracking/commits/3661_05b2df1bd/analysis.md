# 提交 3661：Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration (#16183)

## 提交信息

- **序号**：3661 / 4088
- **哈希**：05b2df1bd234c95d8edb51f1b78ee9b60ff59021
- **短哈希**：05b2df1bd
- **日期**：2026-05-07 12:02:23 +0200
- **作者**：Talat UYARER
- **提交说明**：Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration (#16183)
- **PR/Issue**：#16183（backport #15475）

## 总体目的

这个提交将 PR #15475（Flink-Iceberg 纳秒精度时间戳支持）backport 到 Flink 2.0。

Iceberg v3 规范引入了纳秒精度时间戳类型（`TimestampNanoType`，`timestamp_ns` 和 `timestamptz_ns`），提供比微秒精度更高的时间分辨率。Flink 的 `TimestampType` 和 `LocalZonedTimestampType` 支持精度 0-9，当精度大于 6 时表示纳秒精度。此前 Flink-Iceberg 集成仅支持微秒精度，无法正确读写 Iceberg 的纳秒时间戳类型。

本 backport 为 Flink 2.0 添加纳秒精度支持，涵盖：
1. 类型映射：Flink 精度 > 6 的时间戳映射为 Iceberg `TimestampNanoType`。
2. 数据读写：RowDataWrapper、ORC 读写器、Avro 转换器等支持纳秒值转换。
3. 由于 Flink 2.0 自带的 Avro 转换器不支持纳秒精度，新增了自定义的 Avro 转换器和 schema 转换器。

## 如何达成设计目的

1. 在 `FlinkTypeToType` 中，当 Flink 时间戳精度 > 6 时映射为 `TimestampNanoType`。
2. 在 `RowDataWrapper` 中，根据 Iceberg 类型（`TIMESTAMP_NANO` vs `TIMESTAMP`）选择纳秒或微秒转换。
3. 在 ORC 读写器中新增 `TIMESTAMP_NANO` case 和对应的 `TimestampNanoWriter`/`TimestampNanoTzWriter`。
4. 新增自定义 Avro 转换器（`AvroToRowDataConverters`、`RowDataToAvroConverters`、`AvroSchemaConverter`、`JodaConverter`），因为 Flink 2.0 自带的 Avro 转换器不支持纳秒精度。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkTypeToType.java` (+6 lines)

**修改目的**：Flink 时间戳类型到 Iceberg 类型的映射支持纳秒。

**工作逻辑**：
```java
public Type visit(TimestampType timestampType) {
  if (timestampType.getPrecision() > 6) {
    return Types.TimestampNanoType.withoutZone();
  }
  return Types.TimestampType.withoutZone();
}
// LocalZonedTimestampType 同理
```

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/RowDataWrapper.java` (+26/-10 lines)

**修改目的**：RowData 包装器支持纳秒时间戳值转换。

**工作逻辑**：根据 `type.typeId()` 判断是 `TIMESTAMP_NANO` 还是 `TIMESTAMP`，分别使用 `DateTimeUtil.nanosFromTimestamp`（纳秒）或 `DateTimeUtil.microsFromTimestamp`（微秒）。对带时区的时间戳，纳秒分支用 `millisecond * 1_000_000L + nanoOfMillisecond`，微秒分支用 `millisecond * 1000L + nanoOfMillisecond / 1000`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcReader.java` (+5 lines)

**修改目的**：ORC 读取器支持纳秒时间戳。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriter.java` (+7 lines)

**修改目的**：ORC 写入器支持纳秒时间戳。

**工作逻辑**：新增 `TIMESTAMP_NANO` case，根据 `shouldAdjustToUTC()` 选择 `timestampNanoTzs()` 或 `timestampNanos()`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriters.java` (+35/-2 lines)

**修改目的**：新增纳秒时间戳 ORC 写入器。

**工作逻辑**：新增 `TimestampNanoWriter` 和 `TimestampNanoTzWriter` 内部类，将 `TimestampData` 转换为 ORC 的 `TimestampColumnVector`（time 为毫秒，nanos 为纳秒部分）。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/RowDataUtil.java` (+2 lines)

**修改目的**：支持纳秒时间戳的工具方法。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/StructRowData.java` (+38/-10 lines)

**修改目的**：StructRowData 支持纳秒时间戳读取。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/formats/avro/AvroToRowDataConverters.java` (+303 lines, new)

**修改目的**：自定义 Avro 到 RowData 转换器，支持纳秒精度。

**工作逻辑**：从 Flink 代码库复制并改造，支持将 Avro 的纳秒精度时间戳（logicalType "timestamp-nanos"）转换为 Flink `TimestampData`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/formats/avro/JodaConverter.java` (+69 lines, new)

**修改目的**：Joda 时间转换工具，支持纳秒。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/formats/avro/RowDataToAvroConverters.java` (+394 lines, new)

**修改目的**：自定义 RowData 到 Avro 转换器，支持纳秒精度。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/formats/avro/typeutils/AvroSchemaConverter.java` (+625 lines, new)

**修改目的**：自定义 Avro schema 转换器，支持纳秒精度时间戳的 schema 映射。

### `flink/v2.0/build.gradle` (+1 line)

**修改目的**：新增 Joda-Time 依赖（用于 Avro 纳秒转换）。

### 其他文件

- `AvroGenericRecordToRowDataMapper.java`、`RowDataToAvroGenericRecordConverter.java`、`AvroGenericRecordConverter.java`：改用自定义 Avro 转换器。
- `DataGenerators.java`：测试数据生成器支持纳秒。
- 测试文件：适配纳秒精度。

## 总结

这个提交将纳秒精度时间戳支持 backport 到 Flink 2.0，使 Flink 2.0 能正确读写 Iceberg v3 的 `timestamp_ns`/`timestamptz_ns` 类型。改动涵盖类型映射、RowData 转换、ORC 读写和 Avro 读写。由于 Flink 2.0 自带的 Avro 转换器不支持纳秒精度，新增了完整的自定义 Avro 转换器实现。后续通过 #16239 和 #16240 分别 backport 到 Flink 2.0（补充）和 1.20。
