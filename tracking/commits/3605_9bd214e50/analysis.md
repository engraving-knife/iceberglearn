# 提交 3605：Arrow: Align vectorized reader handling of unsigned Parquet integers with BaseParquetReaders (#16006)

## 提交信息

- **序号**：3605 / 4088
- **哈希**：9bd214e5063cccb41dbc724462ad0a3f8fed46f2
- **短哈希**：9bd214e50
- **日期**：2026-04-27 19:33:55 -0700
- **作者**：drexler-sky
- **提交说明**：Arrow: Align vectorized reader handling of unsigned Parquet integers with BaseParquetReaders (#16006)
- **PR/Issue**：#16006，修复 #14547

## 总体目的

这个提交修复了 Arrow 向量化读取器在处理 Parquet 无符号整数列时的一个数据正确性 bug。

之前，Arrow 向量化读取器在遇到 Parquet 的无符号整数逻辑类型（UINT8、UINT16、UINT32、UINT64）时，会静默地将它们作为有符号整数读取。对于 UINT32 和 UINT64，当值超过对应有符号类型的最大值时，会产生错误的负数值。例如，UINT32 的值 3000000000 会被读取为负数。

Iceberg 本身没有无符号整数类型，而 schema 转换层（BaseParquetReaders）已经拒绝读取 UINT64。但向量化 Arrow 读取器路径没有进行同样的检查，导致行为不一致。这个提交使 Arrow 读取器与 BaseParquetReaders 的策略保持一致：拒绝 UINT32 和 UINT64，允许 UINT8 和 UINT16（因为它们可以无损地放入 int32 中）。

## 如何达成设计目的

在 `VectorizedArrowReader` 的逻辑类型处理代码中添加前置条件检查（Preconditions.checkArgument）：
- 对于 8/16/32 位整数：检查 `intLogicalType.isSigned() || bitWidth < 32`，即只允许有符号整数或 8/16 位无符号整数。
- 对于 64 位整数：检查 `intLogicalType.isSigned()`，即只允许有符号 long。

添加了参数化测试覆盖拒绝和接受两种场景。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (+8/-0 lines)

**修改目的**：添加无符号整数的校验逻辑。

**工作逻辑**：

在处理 8/16/32 位整数逻辑类型时，添加校验：
```java
// Iceberg has no unsigned integer type. Reading UINT32 into a 32-bit signed value would
// silently produce negative results for inputs above Integer.MAX_VALUE. UINT8 and UINT16
// both fit losslessly in a signed int32 and are allowed, matching the policy in
// BaseParquetReaders for the non-vectorized path.
Preconditions.checkArgument(
    intLogicalType.isSigned() || bitWidth < 32, "Cannot read UINT32 as an int value");
```

在处理 64 位整数逻辑类型时，添加校验：
```java
Preconditions.checkArgument(
    intLogicalType.isSigned(), "Cannot read UINT64 as a long value");
```

当遇到无符号 32/64 位整数时，抛出 `IllegalArgumentException`，而不是静默读取错误数据。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/TestArrowReader.java` (+110/-0 lines)

**修改目的**：添加测试覆盖无符号整数的处理。

**工作逻辑**：

1. **`testUnsignedIntegerColumnThrowsException`**：参数化测试，验证 UINT32 和 UINT64 读取时抛出 `IllegalArgumentException`，并检查错误消息包含预期文本。

2. **`testUnsignedSmallIntegerColumnRoundtrips`**：参数化测试，验证 UINT8（值 250）和 UINT16（值 50000）可以正确 round-trip 读取为 int 值。

3. **`createSingleRowUnsignedIntTable`**：辅助方法，创建包含无符号整数列的单行 Parquet 文件表，使用 `LogicalTypeAnnotation.intType(unsignedBitWidth, false)` 指定无符号逻辑类型。

4. **`rejectedUnsignedIntegerCases`** 和 **`acceptedUnsignedSmallIntegerCases`**：参数提供方法，分别提供拒绝和接受的测试用例。

## 总结

这个提交修复了一个数据正确性 bug：Arrow 向量化读取器之前会静默地将 Parquet 无符号整数读取为有符号值，可能导致数据错误。修复方案与非向量化路径（BaseParquetReaders）的策略保持一致，拒绝无法无损转换的 UINT32 和 UINT64，允许可以无损放入 int32 的 UINT8 和 UINT16。这是一个重要的数据正确性修复，避免了静默的数据损坏。
