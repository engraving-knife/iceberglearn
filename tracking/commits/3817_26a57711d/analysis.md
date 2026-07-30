# 提交 3817：Parquet: Fix timestamp_ns and timestamptz_ns predicate pushdown (#16619)

## 提交信息

- **序号**：3817 / 4088
- **哈希**：26a57711d990c695915afb5ce14f00736325d547
- **短哈希**：26a57711d
- **日期**：2026-06-02 12:48:51 +0200
- **作者**：Vova Kolmakov <wombatukun@gmail.com>
- **提交说明**：Parquet: Fix timestamp_ns and timestamptz_ns predicate pushdown (#16619)
- **PR/Issue**：#16619

## 总体目的

本提交修复 Iceberg Parquet 模块在处理纳秒级时间戳（`timestamp_ns`、`timestamptz_ns`）时的两个相关缺陷：

1. **schema 转换丢失纳秒单位**：`MessageTypeToType`（负责把 Parquet 的 `MessageType` 转回 Iceberg 的 `Type`）在访问 `TimestampLogicalTypeAnnotation` 时，不区分时间单位（MICROS 还是 NANOS），一律映射为 Iceberg 的 `TimestampType`（微秒）。这导致纳秒时间戳列在读回时被错误地当作微秒时间戳，丢失精度信息。实际上 Iceberg 已有 `Types.TimestampNanoType` 表示纳秒时间戳，但转换器未使用它。

2. **谓词下推不识别纳秒时间戳类型**：`ParquetFilters` 在为 Iceberg 类型构造 Parquet 谓词时，只处理了 `TIMESTAMP`（微秒）分支，没有处理 `TIMESTAMP_NANO` 分支。因此对纳秒时间戳列的过滤条件不会被下推到 Parquet 读取层，导致全表扫描，性能下降。

本提交让 schema 转换在遇到 `TimeUnit.NANOS` 时映射为 `TimestampNanoType`（带/不带时区），并在 `ParquetFilters` 的 switch 中把 `TIMESTAMP_NANO` 与 `TIMESTAMP` 一样走 `longColumn` 谓词路径，从而修复这两个问题。

## 如何达成设计目的

设计上分两点：第一，在 `MessageTypeToType.visit(TimestampLogicalTypeAnnotation)` 中检查 `getUnit()`，若是 NANOS 则返回 `TimestampNanoType.withZone()/withoutZone()`，否则维持原 `TimestampType` 行为；第二，在 `ParquetFilters` 的类型 switch 中，为 `TIMESTAMP_NANO` 增加 fall-through 到 `TIMESTAMP` 分支，复用 `FilterApi.longColumn` 谓词构造（因为纳秒时间戳在 Parquet 中同样以 INT64 存储）。同时新增针对 schema 转换的单元测试和端到端谓词下推测试。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/MessageTypeToType.java` (+9/-2 lines)

**修改目的**：让 Parquet→Iceberg schema 转换保留纳秒单位。

**工作逻辑**：
原实现无视图：
```java
return Optional.of(
    timestampType.isAdjustedToUTC() ? TimestampType.withZone() : TimestampType.withoutZone());
```
修改后先判断单位：
```java
boolean adjustToUtc = timestampType.isAdjustedToUTC();
if (timestampType.getUnit() == LogicalTypeAnnotation.TimeUnit.NANOS) {
  return Optional.of(
      adjustToUtc
          ? Types.TimestampNanoType.withZone()
          : Types.TimestampNanoType.withoutZone());
}
return Optional.of(adjustToUtc ? TimestampType.withZone() : TimestampType.withoutZone());
```
这样 NANOS 单位映射为 `TimestampNanoType`，MICROS 维持原行为。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetFilters.java` (+1/-0 lines)

**修改目的**：让纳秒时间戳列的谓词下推走 long 列路径。

**工作逻辑**：
在 switch 的 `TIMESTAMP` 分支前增加 `case TIMESTAMP_NANO:` fall-through：
```java
case LONG:
case TIME:
case TIMESTAMP:
case TIMESTAMP_NANO:
  return pred(op, FilterApi.longColumn(path), getParquetPrimitive(lit));
```
由于纳秒时间戳在 Parquet 中以 INT64 存储，谓词构造与微秒时间戳完全一致。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetSchemaUtil.java` (+32/-0 lines)

**修改目的**：覆盖 schema 转换的纳秒单位保留行为。

**工作逻辑**：
`testTimestampNanoConversionPreservesUnit` 构造一个包含三个 INT64 时间戳列的 Parquet MessageType：`ts_tz_ns`（NANOS, with zone）、`ts_ns`（NANOS, no zone）、`ts_tz_micros`（MICROS, with zone）。验证转换后的 Iceberg schema 中前两列分别为 `TimestampNanoType.withZone()` 与 `withoutZone()`，第三列仍为 `TimestampType.withZone()`，确认 NANOS 分支不影响 MICROS 映射。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (+114/-0 lines)

**修改目的**：端到端验证纳秒时间戳列的谓词下推生效。

**工作逻辑**：
新增测试构造包含纳秒时间戳列的 Parquet 文件，写入多行数据，然后使用包含纳秒时间戳过滤条件的扫描，验证读取的行确实被谓词过滤（而非全表扫描返回）。测试覆盖带时区与不带时区两种纳秒时间戳类型。

## 总结

本提交修复了 Iceberg Parquet 模块对纳秒时间戳的两个缺陷：schema 转换丢失单位、谓词下推不识别类型。修复使纳秒时间戳列能被正确识别并享受谓词下推带来的性能收益，避免全表扫描。这是 Iceberg 对纳秒时间戳类型完整支持的重要补全，测试覆盖单元与端到端两个层面。
