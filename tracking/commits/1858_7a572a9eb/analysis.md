# 提交 1858：Flink 1.20: Support Avro and Parquet timestamp(9), unknown, and defaults (#12470)

## 提交信息

- **序号**：1858 / 4088
- **哈希**：7a572a9eb942053b6fa603ede02067b0b2c4602c
- **短哈希**：7a572a9eb
- **日期**：2025-03-14 15:14:45 -0700
- **作者**：Ryan Blue
- **提交说明**：Flink 1.20: Support Avro and Parquet timestamp(9), unknown, and defaults (#12470)
- **PR/Issue**：#12470

## 总体目的

Iceberg v3 spec 引入了三类新特性，Flink 1.20 集成此前未完整支持：

1. **`TimestampNanoType`（timestamp(9)）**：纳秒精度时间戳。Iceberg 在 v3 新增 `TIMESTAMP_NANO` 类型，存储为 INT64 纳秒。Flink 1.20 的 `TimestampType(9)` / `LocalZonedTimestampType(9)` 可对应，但 Flink 1.20 的 `TimestampData` 内部用 `millis + nanosOfMillis` 表达，需要正确的纳秒↔毫秒+纳秒换算。
2. **`UnknownType`**：v3 引入的"未知类型"占位符，用于字段被删除后的兼容。Flink 需映射为 `NullType`，并在读写时跳过。
3. **列默认值（`initial-default` / `write-default`）**：v3 字段可声明默认值。读取时若字段在数据文件中不存在（旧文件），需用 `initial-default` 填充；Avro planned reader 需要把默认值作为常量注入读计划。

本提交让 Flink 1.20 模块完整支持这三类 v3 特性：扩展类型映射、新增纳秒时间戳的 Avro/Parquet 读写器、处理 UnknownType 跳过、把默认值常量传入 Avro/Parquet 读取计划，并适配测试工具与随机数据生成。

## 如何达成设计目的

整体思路是沿 Iceberg 的类型系统逐层打通：类型映射（`TypeToFlinkType`）→ 行数据访问（`FlinkRowData`）→ Avro 读写（`FlinkAvroWriter`/`FlinkPlannedAvroReader`/`FlinkValueReaders`/`FlinkValueWriters`）→ Parquet 读写（`FlinkParquetReaders`/`FlinkParquetWriters`/`ParquetWithFlinkSchemaVisitor`）→ 常量转换（`RowDataUtil`）→ 测试（`TestHelpers`/`RowDataConverter`/`RandomUtil`）。

对 Parquet writer 还顺手把基于 `primitive.getOriginalType()` 的老式 switch 重构为 `LogicalTypeAnnotationVisitor` 模式，便于扩展 NANOS 等新逻辑类型。

## 修改详情

### `api/src/test/java/org/apache/iceberg/util/RandomUtil.java` (修改, +1 line)

**修改目的**：随机数据生成支持 `TIMESTAMP_NANO`。

**工作逻辑**：在 `case TIMESTAMP:` 后新增 `case TIMESTAMP_NANO:`，与 TIMESTAMP 一样返回 `(long) value`（纳秒值）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkRowData.java` (修改, +5 lines)

**修改目的**：处理 `NullType`（UnknownType 的 Flink 映射）的字段访问。

**工作逻辑**：`createFieldGetter` 开头判断 `fieldType instanceof NullType`，若是则返回 `rowData -> null`（永远返回 null），避免 `RowData.createFieldGetter` 对 NullType 抛异常。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/TypeToFlinkType.java` (修改, +12 lines)

**修改目的**：扩展 Iceberg→Flink 类型映射，支持 UNKNOWN 与 TIMESTAMP_NANO。

**工作逻辑**：`primitive(...)` switch 中：
- `case UNKNOWN:` → `new NullType()`；
- `case TIMESTAMP_NANO:` → 根据 `shouldAdjustToUTC()` 返回 `new LocalZonedTimestampType(9)` 或 `new TimestampType(9)`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkAvroWriter.java` (修改, +3 lines)

**修改目的**：Avro 写入支持 `timestamp-nanos` 逻辑类型。

**工作逻辑**：在 logicalType switch 中新增 `case "timestamp-nanos": return FlinkValueWriters.timestampNanos();`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (修改, +14/-54 lines)

**修改目的**：Parquet 读取支持纳秒时间戳，并简化 timestamp 读取逻辑。

**工作逻辑**：
- `visit(TimestampLogicalTypeAnnotation)` 中新增 `else if (unit == NANOS) return Optional.of(new NanosToTimestampReader(desc));`。
- 新增 `NanosToTimestampReader`：`readLong()` 读纳秒，`TimestampData.fromEpochMillis(floorDiv(nanos, 1_000_000), floorMod(nanos, 1_000_000))`。
- 删除 `MicrosToTimestampTzReader`、`MillisToTimestampTzReader` 两个 Tz 专用 reader（不再按 `isAdjustedToUTC` 分流，统一用非 Tz reader，因为 Flink 的 `TimestampData` 不区分时区）。
- `MicrosToTimestampReader` 改用 `floorDiv/floorMod` 处理负数微秒。
- 移除 `java.time.Instant`/`ZoneOffset` import。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetWriters.java` (修改, +159/-30 lines)

**修改目的**：Parquet 写入支持纳秒时间戳，并把逻辑类型分发重构为 visitor 模式。

**工作逻辑**：
- 把 `primitive(...)` 中基于 `primitive.getOriginalType()` 的大 switch 替换为 `annotation.accept(new LogicalTypeWriterBuilder(flinkType, desc))`，返回 `Optional<ParquetValueWriter<?>>`。
- 新增内部类 `LogicalTypeWriterBuilder implements LogicalTypeAnnotationVisitor<ParquetValueWriter<?>>`，为每种逻辑类型（String/Enum/Decimal/Date/Time/Timestamp/Int/Json/Bson）实现 `visit(...)`。
- `visit(TimestampLogicalTypeAnnotation)` 中按 `getUnit()` 分发：`NANOS` → `timestampNanos(desc)`，`MICROS` → `timestamps(desc)`。
- 新增 `timestampNanos(ColumnDescriptor desc)` 工厂方法返回 `TimestampNanoDataWriter`。
- 把多处返回类型从 `ParquetValueWriters.PrimitiveWriter<?>` 改为 `ParquetValueWriter<?>`（向上转型，统一类型）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkPlannedAvroReader.java` (修改, +4/-2 lines)

**修改目的**：Avro planned reader 支持纳秒与默认值常量注入。

**工作逻辑**：
- `buildReadPlan(expected, record, fieldReaders, idToConstant)` → `buildReadPlan(expected, record, fieldReaders, idToConstant, RowDataUtil::convertConstant)`，传入常量转换函数，让读取计划能注入 `initial-default`。
- logicalType switch 新增 `case "timestamp-nanos": return FlinkValueReaders.timestampNanos();`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkValueReaders.java` (修改, +18/-6 lines)

**修改目的**：Avro 读取支持纳秒时间戳，并修复微秒负数处理。

**工作逻辑**：
- 新增 `timestampNanos()` 工厂返回 `TimestampNanosReader.INSTANCE`。
- `TimestampNanosReader.read`：`long nanos = decoder.readLong();` → `TimestampData.fromEpochMillis(floorDiv(nanos, 1_000_000), floorMod(nanos, 1_000_000))`。
- `TimestampMicrosReader.read` 改用 `floorDiv/floorMod` 替代手工负数修正。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkValueWriters.java` (修改, +15 lines)

**修改目的**：Avro 写入支持纳秒时间戳。

**工作逻辑**：新增 `timestampNanos()` 工厂与 `TimestampNanosWriter`：`write` 时 `long nanos = timestampData.getMillisecond() * 1_000_000 + timestampData.getNanoOfMillisecond(); encoder.writeLong(nanos);`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/ParquetWithFlinkSchemaVisitor.java` (修改, +11/-6 lines)

**修改目的**：Parquet schema visitor 跳过 NullType 字段。

**工作逻辑**：`visitFields` 中不再要求 `sFields.size() == group.getFieldCount()`，改为遍历 `sFields` 时若 `sField.getType().getTypeRoot() == LogicalTypeRoot.NULL` 则 `continue` 跳过（NullType 字段不出现在 Parquet schema 中），用独立 `pos` 索引 group 字段。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/RowDataUtil.java` (修改, +4 lines)

**修改目的**：常量转换支持 UUID，供默认值注入。

**工作逻辑**：`convertConstant` 中新增 `case UUID: return UUIDUtil.convert((UUID) value);`，把 Iceberg UUID 常量转为 Flink 可用的字节数组。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/RowDataConverter.java` (修改, +14/-6 lines)

**修改目的**：测试用 RowDataConverter 支持 TIMESTAMP_NANO 与统一 timestamp 转换。

**工作逻辑**：`TIMESTAMP` 与新增的 `TIMESTAMP_NANO` 都调 `convertTimestamp(object, shouldAdjustToUTC)`。新增私有方法 `convertTimestamp`：用 `OffsetDateTime.toInstant().toEpochMilli()` 或 `LocalDateTime.toInstant(UTC).toEpochMilli()` 配合 `getNano() % 1_000_000` 构造 `TimestampData`，正确处理纳秒部分。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java` (修改, +44/-13 lines)

**修改目的**：测试断言支持默认值字段与 TIMESTAMP_NANO。

**工作逻辑**：
- `assertEquals` 中若 `expectedRecord instanceof Record`，按 fieldId 在 expected 中查找；若找不到（字段只有默认值无数据），用 `GenericDataUtil.internalToGeneric(field.type(), field.initialDefault())` 转换默认值后比较。
- 新增 `case TIMESTAMP_NANO:` 断言分支，按 `shouldAdjustToUTC` 比较 `OffsetDateTime`/`LocalDateTime`。

### 测试文件 (新增/修改)

- `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkAvroReaderWriter.java`（新增, 136 lines）：合并并替代旧的 `AbstractTestFlinkAvroReaderWriter`（删除 182 行）与 `TestFlinkAvroPlannedReaderWriter`（删除 34 行），统一 Avro 读写测试。
- `TestFlinkParquetReader.java`（+10）、`TestFlinkParquetWriter.java`（+21/-2）：补充纳秒时间戳的 Parquet 读写测试。

## 小结

- **成效**：Flink 1.20 模块完整支持 v3 spec 的 `TimestampNanoType`、`UnknownType` 与列默认值，Avro/Parquet 读写均覆盖；Parquet writer 的逻辑类型分发重构为 visitor 模式，便于后续扩展。
- **影响范围**：flink 1.20 模块 16 个文件 + api 测试 1 个文件，+456/-348 行。属于功能增强，影响所有使用 v3 类型的 Flink 1.20 作业。
- **回迁到 1.4.x 的注意事项**：依赖 v3 spec（`TimestampNanoType`/`UnknownType`/列默认值）在 1.4.x 已支持。若 1.4.x 的 Flink 1.20 模块需要 v3 类型支持，建议回迁。回迁时需同步检查 `ValueReaders.buildReadPlan` 是否已支持 `convertConstant` 参数（本提交调用了 5 参数版本）。Parquet writer 的 visitor 重构改动较大，回迁时建议整体替换 `FlinkParquetWriters` 的 primitive 方法。
