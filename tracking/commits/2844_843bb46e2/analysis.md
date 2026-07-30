# 提交 2844：Core: Support reading Avro logical timestamp-millis (#14401)

## 提交信息

- **序号**：2844 / 4088
- **哈希**：843bb46e27773e0e18df7ce3bd377a0b56f35074
- **短哈希**：843bb46e2
- **日期**：2025-11-07 07:47:59 +0100
- **作者**：James Faulkner
- **提交说明**：Core: Support reading Avro logical timestamp-millis (#14401)
- **PR/Issue**：#14401

## 总体目的

Avro 规范定义了多种时间相关的 logical type：`timestamp-millis`（毫秒精度，存为 long）、`timestamp-micros`（微秒）、`timestamp-nanos`（纳秒），以及带时区的 `timestamp-millis`/`timestamp-micros`/`timestamp-nanos`（通过 `adjust-to-utc=true` 区分）。Iceberg 自身的时间类型有 `TimestampType.withoutZone()`/`withZone()`，内部存储精度有 micros 与 nanos。

Iceberg 的 Avro 读取器（`DataReader`、`PlannedDataReader`）此前已经支持 `timestamp-nanos` 与 `timestamp-micros` 两种 logical type 的读取（通过 `GenericReaders.timestampNanos()`/`timestampMicros()` 等），但缺失对 `timestamp-millis` 的处理——当 Avro schema 中出现 `timestamp-millis` 时，读取器没有对应分支，会落到默认的 long 读取，无法转成 `LocalDateTime`/`OffsetDateTime`。

该提交补全 `timestamp-millis` 的读取支持，让 Iceberg 能正确读取以毫秒精度存储的 Avro 时间字段（常见于历史数据或外部 Avro 数据源），并补充 `DateTimeUtil` 中 millis 与 `LocalDateTime`/`OffsetDateTime` 的互转工具方法。

## 如何达成设计目的

1. **`DateTimeUtil` 新增 millis 互转方法**：
   - `timestampFromMillis(long millisFromEpoch)` → `LocalDateTime`（用 `ChronoUnit.MILLIS.addTo(EPOCH, ...)`）。
   - `millisFromTimestamp(LocalDateTime)` → `long`。
   - `timestamptzFromMillis(long)` → `OffsetDateTime`。
   - `millisFromTimestamptz(OffsetDateTime)` → `long`。
   这些方法与已有的 `timestampFromMicros`/`timestampFromNanos` 等对称。
2. **`GenericReaders` 新增 millis 读取器**：`timestampMillis()` 返回 `TimestampMillisReader`（读 `LocalDateTime`），`timestamptzMillis()` 返回 `TimestamptzMillisReader`（读 `OffsetDateTime`）。两个 reader 都是单例，`read` 时 `decoder.readLong()` 后用 `DateTimeUtil.timestampFromMillis`/`timestamptzFromMillis` 转换。
3. **`DataReader` 与 `PlannedDataReader` 新增 `case "timestamp-millis":` 分支**：根据 `AvroSchemaUtil.isTimestamptz(primitive)` 判断是否带时区，分别返回 `GenericReaders.timestamptzMillis()` 或 `GenericReaders.timestampMillis()`，与已有 `timestamp-nanos` 分支结构一致。
4. **测试**：
   - `TestDateTimeUtil` 新增 `timestampFromMillis` 与 `millisFromTimestamp` 测试，覆盖正/负/零毫秒值。
   - 新建 `TestDataReader` 与 `TestPlannedDataReader`，分别对 `DataReader` 和 `PlannedDataReader` 验证 `timestamp-nanos`/`timestamp-micros`/`timestamp-millis` 三种精度的不带时区与带时区读取，覆盖 epoch 前后两种情况。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/DateTimeUtil.java` (+16/-0 lines)

**修改目的**：提供 millis 与 `LocalDateTime`/`OffsetDateTime` 的互转方法。

**工作逻辑**：
- `timestampFromMillis(long)`：`ChronoUnit.MILLIS.addTo(EPOCH, millisFromEpoch).toLocalDateTime()`。
- `millisFromTimestamp(LocalDateTime)`：`ChronoUnit.MILLIS.between(EPOCH, dateTime.atOffset(ZoneOffset.UTC))`。
- `timestamptzFromMillis(long)`：`ChronoUnit.MILLIS.addTo(EPOCH, millisFromEpoch)`（返回 `OffsetDateTime`）。
- `millisFromTimestamptz(OffsetDateTime)`：`ChronoUnit.MILLIS.between(EPOCH, dateTime)`。

### `api/src/test/java/org/apache/iceberg/util/TestDateTimeUtil.java` (+21/-0 lines)

**修改目的**：验证 `timestampFromMillis` 与 `millisFromTimestamp` 的正/负/零值正确性。

**工作逻辑**：`timestampFromMillis` 测试用 `1510871468000L`、负值、0 校验转换结果；`millisFromTimestamp` 用相反方向校验往返。

### `core/src/main/java/org/apache/iceberg/data/avro/DataReader.java` (+6/-0 lines)

**修改目的**：在 Avro 读取器中处理 `timestamp-millis` logical type。

**工作逻辑**：在 `readPrimitive` 的 switch 中新增：
```java
case "timestamp-millis":
    if (AvroSchemaUtil.isTimestamptz(primitive)) {
        return GenericReaders.timestamptzMillis();
    }
    return GenericReaders.timestampMillis();
```
位置在 `timestamp-nanos` 分支之后、`decimal` 之前。

### `core/src/main/java/org/apache/iceberg/data/avro/GenericReaders.java` (+30/-0 lines)

**修改目的**：提供 millis 读取器实现。

**工作逻辑**：
- `timestampMillis()` 返回 `TimestampMillisReader.INSTANCE`。
- `timestamptzMillis()` 返回 `TimestamptzMillisReader.INSTANCE`。
- `TimestampMillisReader.read`：`DateTimeUtil.timestampFromMillis(decoder.readLong())`。
- `TimestamptzMillisReader.read`：`DateTimeUtil.timestamptzFromMillis(decoder.readLong())`。
- 两个 reader 均为单例私有静态内部类，与已有 `TimestampMicrosReader`/`TimestampNanosReader` 风格一致。

### `core/src/main/java/org/apache/iceberg/data/avro/PlannedDataReader.java` (+6/-0 lines)

**修改目的**：与 `DataReader` 相同的 `timestamp-millis` 分支。

**工作逻辑**：`PlannedDataReader` 是另一种 Avro 读取器（planned，支持投影/读计划），结构与 `DataReader` 一致，新增相同的 `case "timestamp-millis":` 分支。

### `core/src/test/java/org/apache/iceberg/data/avro/TestDataReader.java` (+208/-0 lines, 新文件)

**修改目的**：验证 `DataReader` 对三种时间精度的读取。

**工作逻辑**：
- `timestampDataReader`：构造含 `timestamp_nanos`/`timestamp_micros`/`timestamp_millis`（均不带时区）的 Iceberg schema 与对应 Avro schema（用 `LogicalTypes.timestampNanos()/timestampMicros()/timestampMillis()`），用 `GenericDatumWriter` 写入 Avro 二进制，再用 `DataReader` 读回，断言三种精度都能正确还原为 `LocalDateTime`；覆盖 epoch 后与 epoch 前两种。
- `timestampTzDataReader`：类似但带时区（`adjust-to-utc=true`），断言读回为 `OffsetDateTime` 并归一到 UTC。
- 辅助方法 `readRecord`（写 Avro 再读回）与 `utcAdjustedLongSchema`（构造带 `ADJUST_TO_UTC_PROP` 的 long schema）。

### `core/src/test/java/org/apache/iceberg/data/avro/TestPlannedDataReader.java` (+209/-0 lines, 新文件)

**修改目的**：验证 `PlannedDataReader` 对三种时间精度的读取。

**工作逻辑**：与 `TestDataReader` 几乎完全对称，区别仅在于用 `PlannedDataReader.create(icebergSchema)` 创建读取器。同样覆盖带/不带时区、epoch 前后。

## 总结

该提交为 Iceberg 的 Avro 读取器补全了 `timestamp-millis` logical type 的读取支持，使毫秒精度存储的 Avro 时间字段能正确转成 `LocalDateTime`/`OffsetDateTime`。修改包括：在 `DateTimeUtil` 新增 millis 互转方法、在 `GenericReaders` 新增 millis 读取器、在 `DataReader`/`PlannedDataReader` 新增 `timestamp-millis` 分支，并新建两个测试类覆盖三种精度（nanos/micros/millis）× 带时区/不带时区 × epoch 前后的读取正确性。这补齐了 Avro 时间 logical type 读取的最后一块拼图。
