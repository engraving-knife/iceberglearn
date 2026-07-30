# 提交 3995：Parquet: Fix variant shredding of large decimals (precision > 18) (#17002)

## 提交信息

- **序号**：3995 / 4088
- **哈希**：3cd56a70623e4fb5856b7ec58b4698febbaae5df
- **短哈希**：3cd56a706
- **日期**：2026-07-07 22:34:19 -0700
- **作者**：Xiaoxuan
- **提交说明**：Parquet: Fix variant shredding of large decimals (precision > 18) (#17002)
- **PR/Issue**：#17002

## 总体目的

本提交修复了 Variant 类型 shredding（分片存储）中大精度 decimal（precision > 18）的 FIXED_LEN_BYTE_ARRAY 长度不匹配 bug。`VariantShreddingAnalyzer` 在将 precision > 18 的 decimal shred 为 `FIXED_LEN_BYTE_ARRAY` 类型的 `typed_value` 时，硬编码了长度 16，但实际的 writer（`FixedDecimalWriter`）只写入 `TypeUtil.decimalRequiredBytes(precision)` 所需的最小字节数（如 precision 20 对应 9 字节）。

这导致 Parquet 写入时抛出 `IllegalArgumentException: 'Fixed Binary size 9 does not match field type length 16'`，破坏了任何持有 int128 范围值（19-35 位整数，存储为 DECIMAL16）的 variant 的读写。

硬编码的 16 是代码库中唯一未从 `decimalRequiredBytes` 派生的 fixed-decimal 宽度。`decimalRequiredBytes` 对 precision 19-35 返回 9-15 字节，仅对 precision 36-38 返回 16 字节，所以旧代码恰好在 precision 36-38 时碰巧与 writer 一致，而对 19-35 则声明的长度超过了 writer 实际写入的字节数。

## 如何达成设计目的

将 `VariantShreddingAnalyzer` 中硬编码的 `.length(16)` 改为 `.length(TypeUtil.decimalRequiredBytes(maxPrecision))`，使声明的 FIXED_LEN_BYTE_ARRAY 长度与 writer 实际写入的字节数一致。precision <= 18 的 decimal 不受影响（使用 INT32/INT64）。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantShreddingAnalyzer.java` (+2/-1 lines)

**修改目的**：修复 FIXED_LEN_BYTE_ARRAY 长度。

**工作逻辑**：
```java
// 旧：.length(16)
// 新：
.length(TypeUtil.decimalRequiredBytes(maxPrecision))
```

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantShreddingAnalyzer.java` (+37/-0 lines)

**修改目的**：验证不同 precision 的 FIXED 长度。

**工作逻辑**：新增参数化测试 `testDecimalExceedingPrecisionUsesMinimumFixedLength`，验证 precision 20→9、28→12、33→14 的长度映射。更新 `testDecimalForExactPrecision` 验证 precision 38 对应 16 字节。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantWriters.java` (+28/-0 lines)

**修改目的**：验证 DECIMAL16 值的往返读写。

**工作逻辑**：新增 `testShreddedDecimal16RoundTripUsesWriterCompatibleFixedLength`，构造 20 位整数的 variant，通过 shred + write + read 往返验证数据一致性。

### `spark/v4.0` 和 `spark/v4.1` 的 `TestVariantShredding.java` (+20/-1 lines each)

**修改目的**：更新期望 schema 长度并新增端到端测试。

**工作逻辑**：
- 将期望 schema 中的 `.length(16)` 改为 `.length(9)`（对应 precision 21）。
- 新增 `testShreddedLargeIntegerVariantReadBack`：通过 SQL 插入 20 位整数 variant，启用 shredding，读取后验证值一致。

### `flink/v2.1` 的 `TestFlinkVariantShreddingType.java` (+1/-1 lines)

**修改目的**：更新期望 schema 长度。

**工作逻辑**：将 `.length(16)` 改为 `.length(9)`（decimalRequiredBytes(21)）。

## 总结

本提交修复了一个导致大精度 decimal variant 无法 shred 存储的 bug。根因是 `VariantShreddingAnalyzer` 硬编码了 FIXED_LEN_BYTE_ARRAY 长度 16，与 writer 实际写入的最小字节数不一致。修复方案简洁——使用 `TypeUtil.decimalRequiredBytes(maxPrecision)` 计算正确长度——但影响显著，修复了 precision 19-35 范围内 variant 值的 shred 读写。这是一个重要的数据正确性修复。
