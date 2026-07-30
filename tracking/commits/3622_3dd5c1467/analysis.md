# 提交 3622：Flink: Add Nanosecond Precision Support for Flink-Iceberg Integration (#15475)

## 提交信息

- **序号**：3622 / 4088
- **哈希**：3dd5c146748e0f0eaee627457ed51d97759ee68c
- **短哈希**：3dd5c1467
- **日期**：2026-04-30 18:01:28 +0200
- **作者**：Talat UYARER
- **提交说明**：Flink: Add Nanosecond Precision Support for Flink-Iceberg Integration (#15475)
- **PR/Issue**：#15475

## 总体目的

这个提交为 Flink-Iceberg 集成添加了纳秒精度时间戳（Nanosecond Precision Timestamp）的支持。

Iceberg 支持纳秒精度的时间戳类型（`TimestampNanoType`），但之前 Flink 集成只支持微秒精度的时间戳（`TimestampType`）。Flink 2.1 引入了纳秒精度的时间戳类型（precision > 6），这个提交使 Iceberg 的 Flink 集成能够正确处理 Flink 的纳秒精度时间戳，将其映射到 Iceberg 的 `TimestampNanoType`。

当 Flink 的 `TimestampType` 或 `LocalZonedTimestampType` 的精度大于 6 时（即纳秒精度），Iceberg 会使用 `TimestampNanoType` 来存储；否则继续使用微秒精度的 `TimestampType`。这确保了高精度时间戳数据在写入和读取 Iceberg 表时不会丢失精度。

## 如何达成设计目的

1. 在类型转换层（`FlinkTypeToType`）中，根据 Flink 时间戳精度选择 Iceberg 时间戳类型。
2. 在数据包装层（`RowDataWrapper`、`StructRowData`）中，根据 Iceberg 类型使用不同的时间戳转换逻辑。
3. 在 ORC 读写层中添加纳秒时间戳的读写支持。
4. 新增 Avro 格式的纳秒时间戳转换器（通过自定义 Joda 转换器，因为 Avro 不原生支持纳秒时间戳）。
5. 更新测试数据生成器和测试用例。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkTypeToType.java` (+6/-0 lines)

**修改目的**：将 Flink 纳秒精度时间戳映射到 Iceberg TimestampNanoType。

**工作逻辑**：
```java
@Override
public Type visit(TimestampType timestampType) {
  if (timestampType.getPrecision() > 6) {
    return Types.TimestampNanoType.withoutZone();
  }
  return Types.TimestampType.withoutZone();
}

@Override
public Type visit(LocalZonedTimestampType localZonedTimestampType) {
  if (localZonedTimestampType.getPrecision() > 6) {
    return Types.TimestampNanoType.withZone();
  }
  return Types.TimestampType.withZone();
}
```
当精度 > 6 时（纳秒级），返回 `TimestampNanoType`；否则返回微秒级的 `TimestampType`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/RowDataWrapper.java` (+24/-12 lines)

**修改目的**：支持纳秒时间戳的数据转换。

**工作逻辑**：
根据 Iceberg 类型（`TIMESTAMP_NANO` vs `TIMESTAMP`）使用不同的转换逻辑：
- 纳秒：使用 `DateTimeUtil.nanosFromTimestamp()` 或 `timestampData.getMillisecond() * 1_000_000L + timestampData.getNanoOfMillisecond()`。
- 微秒：保持原有逻辑 `DateTimeUtil.microsFromTimestamp()` 或 `timestampData.getMillisecond() * 1000L + timestampData.getNanoOfMillisecond() / 1000`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcReader.java` (+7/-0 lines)

**修改目的**：ORC 读取支持纳秒时间戳。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriter.java` (+7/-0 lines)

**修改目的**：ORC 写入支持纳秒时间戳。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriters.java` (+37/-0 lines)

**修改目的**：新增 ORC 纳秒时间戳写入器。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/StructRowData.java` (+35/-17 lines)

**修改目的**：支持纳秒时间戳的读取。

### Avro 相关文件（新增）

- `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/formats/avro/AvroToRowDataConverters.java` (+303 lines, new)：Avro 到 RowData 的转换器，支持纳秒时间戳。
- `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/formats/avro/JodaConverter.java` (+69 lines, new)：Joda 时间转换器，用于 Avro 纳秒时间戳。
- `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/formats/avro/RowDataToAvroConverters.java` (+394 lines, new)：RowData 到 Avro 的转换器。
- `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/formats/avro/typeutils/AvroSchemaConverter.java` (+625 lines, new)：Avro schema 转换器，支持纳秒时间戳。

### `flink/v2.1/build.gradle` (+1/-0 lines)

**修改目的**：添加 Joda-Time 依赖。

### `LICENSE` (+4/-0 lines)

**修改目的**：添加 Joda-Time 的许可证声明。

### 其他文件

- `RowDataUtil.java`、`DataGenerators.java`：支持纳秒时间戳的工具方法。
- Avro 相关的 mapper/converter 文件：适配新的转换器。
- 测试文件：更新测试以覆盖纳秒精度场景。

## 总结

这个提交为 Flink-Iceberg 集成添加了纳秒精度时间戳支持，使 Flink 2.1 的纳秒精度时间戳能够正确映射到 Iceberg 的 `TimestampNanoType`。支持覆盖了类型转换、数据包装、ORC 读写和 Avro 读写等多个层面。这是一个重要的功能增强，确保了高精度时间戳数据在 Flink 和 Iceberg 之间的无损转换。
