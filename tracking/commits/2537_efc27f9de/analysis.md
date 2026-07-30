# 提交 2537：Arrow: Add nanosec precision timestamp (#13562)

## 提交信息

- **序号**：2537 / 4088
- **哈希**：efc27f9deed0511653ffb94612a8bfcd1489df42
- **短哈希**：efc27f9de
- **日期**：2025-08-21 14:40:11 +0200
- **作者**：Nándor Kollár
- **提交说明**：Arrow: Add nanosec precision timestamp (#13562)
- **PR/Issue**：#13562

## 总体目的

Iceberg 在 spec v3 引入了纳秒精度时间戳类型 `TimestampNanoType`（对应 `TypeID.TIMESTAMP_NANO`），但 Arrow 模块的向量化读取路径和 schema 转换尚未支持该类型。这意味着使用 Arrow reader 读取包含 `timestamp_nano` 列的 v3 表时会失败或被错误解析。

本提交为 Arrow 模块完整补全纳秒时间戳支持：
1. `ArrowSchemaUtil` 把 `TIMESTAMP_NANO` 映射为 Arrow `ArrowType.Timestamp(NANOSECOND, "UTC"|null)`。
2. `ArrowReader` 在支持类型集合中添加 `TIMESTAMP_NANO`，并更新文档注释。
3. `VectorizedArrowReader` 在按 logical type 分配 vector 时，对 `TimestampLogicalTypeAnnotation` 的 NANOS 单位分配 `TimeStampNanoTZVector`/`TimeStampNanoVector`。
4. `DictEncodedArrowConverter` 与 `GenericArrowVectorAccessorFactory` 支持 nanosec timestamp vector 的字典编码与 accessor。
5. 测试覆盖 schema 转换与向量化读取（带/不带时区、required/nullable）。

附带的一个重要重构是把 `VectorizedArrowReader.allocateVectorBasedOnOriginalType` 中巨大的 switch（基于已废弃的 `OriginalType`）改造为基于 `LogicalTypeAnnotation` 的访问者模式 `LogicalTypeVisitor`，更易扩展（也方便后续 Parquet 类型扩展只在 logical type annotation 中存在的情况）。

## 如何达成设计目的

- **Schema 转换**：在 `ArrowSchemaUtil.primitive` 中新增 `case TIMESTAMP_NANO`，依据 `shouldAdjustToUTC()` 决定是否带 "UTC" 时区，使用 `TimeUnit.NANOSECOND`。
- **Reader 白名单**：`ArrowReader` 的 `SUPPORTED_TYPES` 集合加入 `TypeID.TIMESTAMP_NANO`，并在类注释中列出对应 Arrow 类型映射。
- **Vector 分配**：
  - 把原来基于 `getOriginalType()` 的 `allocateVectorBasedOnOriginalType` 改名为 `allocateVectorBasedOnLogicalType`，并改为通过 `getLogicalTypeAnnotation().accept(new LogicalTypeVisitor(...))` 走访问者。
  - `LogicalTypeVisitor` 为每种 logical type（String/Enum/UUID/Decimal/Date/Time/Timestamp/Int/Json/Bson）实现 `visit`，返回包含 `vec/readType/typeWidth` 的 `LogicalTypeVisitorResult`。
  - `TimestampLogicalTypeAnnotation.visit` 中按 unit 分支：MILLIS→BigIntVector+TIMESTAMP_MILLIS；MICROS→TimeStampMicro(Vector|TZ)Vector+LONG；NANOS→TimeStampNano(Vector|TZ)Vector+LONG，依据 Iceberg field 的 `shouldAdjustToUTC()` 决定是否带时区。
- **字典编码路径**：
  - `DictEncodedArrowConverter` 增加 `TIMESTAMP_NANO` 分支与 `toTimestampNanoVector` 方法，按 `shouldAdjustToUTC()` 选择 `TimeStampNanoTZVector`/`TimeStampNanoVector`，并通过 `accessor.getLong(idx)` 填充。
  - `GenericArrowVectorAccessorFactory` 把原来 `TimestampMicroTzAccessor` 与 `TimestampMicroAccessor` 合并为一个泛型 `TimestampAccessor<TimestampVectorT extends TimeStampVector>`，并新增对 `TimeStampNanoVector`/`TimeStampNanoTZVector` 的分支。同时加 TODO 注释建议未来用 logical type annotation 处理字典。
- **测试**：
  - `TestArrowSchemaUtil` 增加 `TIMESTAMP_NANO_FIELD` 字段并断言转换后 `ArrowType.Timestamp` 的 unit 为 `NANOSECOND`，同时为原 `TIMESTAMP` 字段补断言 unit 为 `MICROSECOND`。
  - `TestArrowReader` 增加 4 个 nanosec 列（带/不带时区 × required/nullable），表用 `FORMAT_VERSION=3`，写入与读取时通过 `timestampToNanos`/`timestampFromNanos` 在 `LocalDateTime`/`OffsetDateTime` 与 long nanos 之间转换，并断言 `TimeStampNanoVector`/`TimeStampNanoTZVector` 类型与值正确。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/ArrowSchemaUtil.java` (+6)

**修改目的**：增加 `TIMESTAMP_NANO` 到 Arrow 类型的映射。

**工作逻辑**：在 `primitive` 中新增 `case TIMESTAMP_NANO`，按 `shouldAdjustToUTC()` 输出 `ArrowType.Timestamp(NANOSECOND, "UTC"|null)`。

### `arrow/src/main/java/org/apache/iceberg/arrow/DictEncodedArrowConverter.java` (+25)

**修改目的**：字典编码路径支持 nanosec timestamp。

**工作逻辑**：在 `convert` 中识别 `TIMESTAMP_NANO` 走 `toTimestampNanoVector`，按是否带时区选择对应 Arrow vector 类型并用 `accessor.getLong` 填充。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ArrowReader.java` (+9/-3)

**修改目的**：将 `TIMESTAMP_NANO` 纳入支持类型并更新文档。

**工作逻辑**：`SUPPORTED_TYPES` 加入 `TypeID.TIMESTAMP_NANO`；类注释列出 nanosec timestamp 的 Arrow 类型映射，并把 Decimal 从"不支持"列表移到"支持"列表（顺手修正文档）。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ColumnVector.java` (+1)

**修改目的**：文档同步。

**工作逻辑**：在支持类型列表中加入 `Types.TimestampNanoType`。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/GenericArrowVectorAccessorFactory.java` (+24/-18)

**修改目的**：合并 timestamp accessor 并支持 nanosec。

**工作逻辑**：
- 把 `TimestampMicroTzAccessor` 与 `TimestampMicroAccessor` 合并为泛型 `TimestampAccessor<TimestampVectorT extends TimeStampVector>`。
- 在 `getVectorAccessor` 中新增 `TimeStampNanoVector`/`TimeStampNanoTZVector` 分支复用 `TimestampAccessor`。
- 加 TODO 注释建议未来基于 logical type annotation 处理字典路径。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (+199/-90)

**修改目的**：把基于 `OriginalType` 的 switch 重构为 `LogicalTypeAnnotation` 访问者，并支持 nanosec。

**工作逻辑**：
- `allocateVectorBasedOnLogicalType` 通过 `LogicalTypeVisitor` 获取 `LogicalTypeVisitorResult`（含 vec/readType/typeWidth）。
- `LogicalTypeVisitor` 为 String/Enum/UUID/Decimal/Date/Time/Timestamp/Int/Json/Bson 实现 visit。
- `TimestampLogicalTypeAnnotation.visit` 按 MILLIS/MICROS/NANOS 分配对应 vector，NANOS 时按 `shouldAdjustToUTC()` 选 `TimeStampNanoTZVector`/`TimeStampNanoVector`，readType=`LONG`。

### `arrow/src/test/java/org/apache/iceberg/arrow/TestArrowSchemaUtil.java` (+10/-2)

**修改目的**：覆盖 nanosec schema 转换。

**工作逻辑**：新增 `TIMESTAMP_NANO_FIELD` 字段，断言 Arrow 类型为 `Timestamp` 且 unit 为 `NANOSECOND`；同时为原 `TIMESTAMP` 字段补 unit=`MICROSECOND` 断言。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/TestArrowReader.java` (+146/-5)

**修改目的**：覆盖 nanosec 向量化读取。

**工作逻辑**：
- schema 增加 4 个 nanosec 列（with/without zone × required/nullable），表 `FORMAT_VERSION=3`。
- 写入与断言使用 `timestampToNanos`/`timestampFromNanos` 在时间对象与 long nanos 间转换。
- `assertEqualsForField` 验证 vector 类型为 `TimeStampNanoVector`/`TimeStampNanoTZVector`。
- `checkColumnarArrayValues` 与 `checkVectorValues` 验证值正确。

## 总结

为 Arrow 模块完整添加 `TimestampNanoType` 支持，覆盖 schema 转换、向量化读取、字典编码读取与 accessor 路径。同时把 `VectorizedArrowReader` 中基于已废弃 `OriginalType` 的 switch 重构为 `LogicalTypeAnnotation` 访问者模式，提升可扩展性。测试覆盖带/不带时区、required/nullable 四种组合的写入与读取正确性。
