# 提交 3784：fix int96 timestamp offset in arrow dictionary decode (#16435)

## 提交信息

- **序号**：3784 / 4088
- **哈希**：23c7f231a2e8b8197d4cbecf04a23d66bc033b46
- **短哈希**：23c7f231a
- **日期**：2026-05-24 17:55:06 -0400
- **作者**：Sung Yun
- **提交说明**：fix int96 timestamp offset in arrow dictionary decode (#16435)
- **PR/Issue**：#16435

## 总体目的

这个提交修复了 Arrow 向量化 Parquet 读取器中 INT96 时间戳在字典解码（dictionary decode）时的偏移量计算 bug。

问题在于 `VectorizedParquetDefinitionLevelReader` 中处理 INT96 时间戳的字典编码值时，写入 Arrow `BigIntVector` 的位置偏移量计算错误。原来使用 `idx`（行索引）直接作为字节偏移量，但 Arrow 的 `DataBuffer.setLong` 需要的是字节偏移量而非元素索引。对于 8 字节的 `BIGINT` 类型，正确的偏移量应该是 `idx * typeWidth`（即 `idx * 8`）。

这个 bug 导致当行索引大于 0 时，时间戳值会被写入错误的位置，导致数据损坏。具体来说，第一行（idx=0）写入位置正确（偏移 0），但第二行（idx=1）会写入字节偏移 1 而非 8，导致数据错位。

## 如何达成设计目的

将 `setLong(idx, timestampInt96)` 修改为 `setLong((long) idx * typeWidth, timestampInt96)`，使用行索引乘以类型宽度来计算正确的字节偏移量。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedParquetDefinitionLevelReader.java` (+1/-1 lines)

**修改目的**：修复 INT96 时间戳字典解码的字节偏移量。

**工作逻辑**：
```java
// 修改前
vector.getDataBuffer().setLong(idx, timestampInt96);
// 修改后
vector.getDataBuffer().setLong((long) idx * typeWidth, timestampInt96);
```

`idx` 是行索引，`typeWidth` 是元素的字节宽度（对于 BigIntVector 是 8 字节）。`DataBuffer.setLong(offset, value)` 的第一个参数是字节偏移量，因此需要将行索引乘以类型宽度。注意这里转换为 `(long)` 是为了防止 int 溢出（当行数很多时 `idx * typeWidth` 可能超过 int 范围）。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/parquet/TestVectorizedParquetDefinitionLevelReader.java` (+115/-0 lines, 新文件)

**修改目的**：新增测试验证 INT96 时间戳字典解码的正确性。

**工作逻辑**：
新增 `timestampInt96ReaderPackedDictionaryDecodeDecodesRowsCorrectly` 测试：
1. 创建 `BigIntVector`，分配 2 个位置，初始化为 -1。
2. 设置字典解码器为 PACKED 模式，包含 2 个值（索引 0 和 1）。
3. 创建 mock 字典，索引 0 对应时间戳 111111，索引 1 对应 222222。
4. 调用 `nextDictEncodedVal` 解码两行数据。
5. 验证 `vector.get(0)` 等于 111111，`vector.get(1)` 等于 222222。

`int96Binary` 辅助方法将微秒时间戳编码为 Parquet INT96 格式（12 字节：8 字节纳秒 + 4 字节 Julian 日）。

这个测试精确复现了 bug 场景：如果没有偏移量修复，第二行的值会写入错误位置，`vector.get(1)` 会返回 -1 而非 222222。

## 总结

这个提交修复了 Arrow 向量化 Parquet 读取器中 INT96 时间戳字典解码的字节偏移量计算 bug。修复前，只有第一行数据能正确解码，后续行的时间戳会写入错误位置导致数据损坏。这是一个影响数据正确性的重要 bug 修复，特别是对于使用旧版 Parquet INT96 时间戳格式（常见于 Hive/Impala 生成的数据）的场景。
