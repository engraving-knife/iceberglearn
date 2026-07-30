# 提交 2889：Arrow: Fix vectorized reads for Parquet TIMESTAMP_MILLIS types (#14499)

## 提交信息

- **序号**：2889 / 4088
- **哈希**：b2379e5f60e792b581d41d441aea7616966c1a85
- **短哈希**：b2379e5f6
- **日期**：2025-11-19 09:38:25 +0100
- **作者**：Shubham Baldava
- **提交说明**：Arrow: Fix vectorized reads for Parquet TIMESTAMP_MILLIS types (#14499)
- **PR/Issue**：#14499

## 总体目的

Iceberg 的 Arrow 向量化读取器（`VectorizedArrowReader`）支持将 Parquet 文件中的时间戳类型读取到 Apache Arrow 的 `FieldVector` 中。Parquet 支持三种时间戳精度：`TIMESTAMP_MILLIS`（毫秒）、`TIMESTAMP_MICROS`（微秒）、`TIMESTAMP_NANOS`（纳秒）。Iceberg 规范内部统一使用微秒精度存储时间戳。

在处理 `TIMESTAMP_MILLIS` 类型时，向量化读取器存在两个 bug：

1. **向量类型不匹配**：原代码使用 `arrowField.createVector(rootAlloc)` 创建向量，`arrowField` 是根据 Iceberg schema 构建的 Arrow 字段，其类型为时间戳类型（`TimeStampMicroTZVector` 或 `TimeStampMicroVector`）。然而 `TIMESTAMP_MILLIS` 的读取逻辑使用 `ReadType.TIMESTAMP_MILLIS`，该读取类型期望将值写入 `BigIntVector`（64位整数向量），而不是时间戳向量。类型不匹配导致读取时数据无法正确写入向量，产生错误结果或异常。

2. **有效性位未设置**：在 `VectorizedParquetDefinitionLevelReader` 中，当读取非空值时，对于 `TIMESTAMP_MILLIS` 类型（使用 `BigIntVector`），没有调用 `BitVectorHelper.setBit()` 设置有效性位（validity bit），导致 Arrow 向量认为这些值为 null。

此修复确保 `TIMESTAMP_MILLIS` 类型使用正确的 `BigIntVector` 向量类型，并在读取值时正确设置有效性位。

## 如何达成设计目的

1. 在 `VectorizedArrowReader` 中，为 `TIMESTAMP_MILLIS` 分支创建独立的 `BigIntVector`（通过新建 `BigInt` 类型的 Arrow Field），而不是复用 `arrowField` 创建的时间戳向量。同时重构 `MICROS` 和 `NANOS` 分支，使每个分支独立创建向量，避免混淆。

2. 在 `VectorizedParquetDefinitionLevelReader` 的两个读取路径（非空值读取和 packed values 读取）中，当 `setArrowValidityVector` 为 true 时，调用 `BitVectorHelper.setBit()` 设置有效性位。

3. 新增测试验证 `TIMESTAMP_MILLIS` 类型的正确读取（毫秒值被转换为微秒值）。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (+19/-9 lines)

**修改目的**：修复 TIMESTAMP_MILLIS 的向量类型不匹配问题。

**工作逻辑**：重构 `visit(TimestampLogicalTypeAnnotation)` 方法，将原来在方法开头统一创建 `vector` 的逻辑移除，改为每个 case 分支独立创建向量：
- `MILLIS` 分支：新建一个 `BigInt` 类型的 Arrow Field（`ArrowType.Int(Long.SIZE, true)`），创建 `BigIntVector`（`millisVector`），返回 `ReadType.TIMESTAMP_MILLIS`。这与 `TIMESTAMP_MILLIS` 读取逻辑期望的 `BigIntVector` 类型匹配。
- `MICROS` 分支：使用 `arrowField.createVector(rootAlloc)` 创建 `microsVector`（时间戳向量），根据是否调整 UTC 选择 `TimeStampMicroTZVector` 或 `TimeStampMicroVector`，返回 `ReadType.LONG`。
- `NANOS` 分支：使用 `arrowField.createVector(rootAlloc)` 创建 `nanosVector`（时间戳向量），根据是否调整 UTC 选择 `TimeStampNanoTZVector` 或 `TimeStampNanoVector`，返回 `ReadType.LONG`。

关键修复：`MILLIS` 分支不再使用 `arrowField`（时间戳类型）创建向量，而是创建 `BigIntVector`，使向量类型与 `ReadType.TIMESTAMP_MILLIS` 的写入逻辑一致。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedParquetDefinitionLevelReader.java` (+7/-0 lines)

**修改目的**：修复非空值读取时有效性位未设置的问题。

**工作逻辑**：在两个读取非空值的路径中，`nextVal()` 之后新增：
```java
if (setArrowValidityVector) {
  BitVectorHelper.setBit(vector.getValidityBuffer(), bufferIdx);
}
```
- 第一个路径（所有值非空的快速路径）：在 `nextVal` 和 `setNotNull` 之后设置 bit。
- 第二个路径（packed values，部分值可能为 null）：在非空值分支（`packedValuesBuffer[idx] == maxDefLevel`）的 `nextVal` 和 `setNotNull` 之后设置 bit。

`setArrowValidityVector` 标志控制是否需要手动设置有效性位（某些向量类型需要，如 `BigIntVector`）。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/TestArrowReader.java` (+91/-0 lines)

**修改目的**：验证 TIMESTAMP_MILLIS 类型的正确向量化读取。

**工作逻辑**：`testTimestampMillisAreReadCorrectly` 测试：
1. 使用 Parquet 底层 API 创建一个包含 `TIMESTAMP_MILLIS`（`INT64` + `TIMESTAMP_MILLIS` 逻辑类型注解，`adjustToUTC=true`）列的 Parquet 文件，写入三个毫秒时间戳值（1609459200000L、1640995200000L、1672531200000L）。
2. 创建 Iceberg 表（schema 为 `TimestampType.withZone()`），将 Parquet 文件作为数据文件追加。
3. 使用 `VectorizedTableScanIterable` 向量化读取。
4. 验证：读取向量类型为 `BigIntVector`，且值被正确转换为微秒（毫秒值 × 1000）。

## 总结

该提交修复了 Arrow 向量化读取器处理 Parquet `TIMESTAMP_MILLIS` 类型的两个 bug：向量类型不匹配（应使用 `BigIntVector` 而非时间戳向量）和有效性位未设置。修复后，`TIMESTAMP_MILLIS` 类型的 Parquet 文件可以被正确读取到 Arrow `BigIntVector` 中，毫秒值被转换为微秒值（与 Iceberg 规范一致）。此修复对依赖 Arrow 向量化读取的引擎（如 Spark、Trino）在读取旧版 Parquet 文件（使用毫秒精度时间戳）时的正确性至关重要。
