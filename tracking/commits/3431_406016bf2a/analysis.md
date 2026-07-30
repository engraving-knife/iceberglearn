# 提交 3431：Data, Orc, Parquet: Throw exception when non-vectorized reader set recordsPerBatch (#15701)

## 提交信息

- **序号**：3431 / 4088
- **哈希**：406016bf2a60e25db121ee4895f1008037664dc7
- **短哈希**：406016bf2a
- **日期**：2026-03-21 12:58:36 +0100
- **作者**：GuoYu
- **提交说明**：Data, Orc, Parquet: Throw exception when non-vectorized reader set recordsPerBatch (#15701)
- **PR/Issue**：#15701

## 总体目的

在非向量化读取器上调用 `recordsPerBatch` 时抛出明确异常。此前 `ORCFormatModel` 和 `ParquetFormatModel` 的 `recordsPerBatch` 方法在非向量化模式下被调用时不会报错，但该设置实际上不生效，可能导致用户误以为批处理设置已生效。本提交在非向量化读取器上调用此方法时抛出 `UnsupportedOperationException`，提供明确的错误反馈。同时移除了 `FEATURE_RECORDS_PER_BATCH` 常量，因为 Avro 不支持批处理读取不应作为"缺失功能"对待。

## 如何达成设计目的

1. 在 `ORCFormatModel.ReadBuilderImpl.recordsPerBatch` 方法中检查 `isBatchReader`，若为 false 则抛出异常
2. 在 `ParquetFormatModel.ReadBuilderImpl.recordsPerBatch` 方法中做相同检查
3. 在 `BaseFormatModelTests` 中新增 `supportsBatchReads()` 方法（默认返回 false）
4. 新增测试验证非向量化读取器调用 `recordsPerBatch` 时抛出异常
5. 移除 `FEATURE_RECORDS_PER_BATCH` 常量，从 Avro 的缺失功能列表中移除该项

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/ORCFormatModel.java` (+5 lines)

**修改目的**：在非向量化模式下抛出异常。

**工作逻辑**：
```java
@Override
public ReadBuilder<D, S> recordsPerBatch(int numRowsPerBatch) {
    if (!isBatchReader) {
        throw new UnsupportedOperationException(
            "Batch reading is not supported in non-vectorized reader");
    }
    internal.recordsPerBatch(numRowsPerBatch);
    return this;
}
```

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetFormatModel.java` (+5 lines)

**修改目的**：在非向量化模式下抛出异常。

**工作逻辑**：与 ORCFormatModel 相同的检查逻辑。

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+29/-4 lines)

**修改目的**：新增测试并调整缺失功能列表。

**工作逻辑**：
- 新增 `supportsBatchReads()` 方法，默认返回 false，子类可覆盖
- 移除 `FEATURE_RECORDS_PER_BATCH` 常量
- 从 Avro 的 `MISSING_FEATURES` 列表中移除 `FEATURE_RECORDS_PER_BATCH`
- 新增 `testReaderBuilderRecordsPerBatchNotSupported` 测试：
  - 使用 `assumeFalse(supportsBatchReads())` 跳过支持批读取的引擎
  - 验证非向量化读取器调用 `recordsPerBatch(100)` 抛出 `UnsupportedOperationException`
  - 验证异常消息包含 "Batch reading is not supported"

## 总结

本提交在 ORC 和 Parquet 的非向量化读取器上调用 `recordsPerBatch` 时抛出 `UnsupportedOperationException`，提供明确的错误反馈而非静默忽略。同时移除了 `FEATURE_RECORDS_PER_BATCH` 常量，将其从 Avro 的缺失功能列表中移除。新增测试验证了异常的正确抛出。
