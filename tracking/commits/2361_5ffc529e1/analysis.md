# 提交 2361：Spark 4: Support Parquet dictionary encoded UUIDs (#13573)

## 提交信息

- **序号**：2361 / 4088
- **哈希**：5ffc529e17126e44cedaf958bd9ce575094e2e85
- **短哈希**：5ffc529e1
- **日期**：2025-07-16 16:42:17 -0600
- **作者**：Fokko Driesprong
- **提交说明**：Spark 4: Support Parquet dictionary encoded UUIDs (#13573)
- **PR/Issue**：#13573

## 总体目的

这个提交为 Spark 4.0 的向量化读取路径添加了对 Parquet 字典编码（dictionary encoded）UUID 列的支持。此前，当 Parquet 文件中的 UUID 列使用字典编码时，Spark 4.0 的向量化读取器无法正确解码，会抛出"Cannot support vectorized reads for column"错误，迫使禁用向量化读取。

背景：Parquet 支持字典编码，当列中重复值较多时（如 UUID 列中只有少量不同值），使用字典编码可以显著减小文件体积。Iceberg 将 UUID 存储为 16 字节的固定长度二进制（`FixedSizeBinary`）。在向量化读取路径中，`ArrowVectorAccessorFactory` 负责为不同数据类型创建访问器。此前 UUID 的访问器缺少字典编码版本的 `ofRow` 方法，导致遇到字典编码的 UUID 列时无法处理。

本提交是 PR #13324 在 Spark 4.0 分支的对应改动，为 UUID 访问器补充字典解码支持。

## 如何达成设计目的

1. 在 `ArrowVectorAccessorFactory` 的 UUID 访问器中新增 `ofRow(IntVector offsetVector, Dictionary dictionary, int rowId)` 方法，使用字典解码 UUID 值。
2. 添加测试验证字典编码的 UUID 列能被向量化读取。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ArrowVectorAccessorFactory.java` (+8/-0 lines)

**修改目的**：为 UUID 访问器添加字典解码支持。

**工作逻辑**：在 UUID 的 `ArrowColumnVectorAccessor` 子类中新增 `ofRow(IntVector offsetVector, Dictionary dictionary, int rowId)` 方法。该方法通过 `dictionary.decodeToBinary(offsetVector.get(rowId)).getBytes()` 从 Parquet 字典中按偏移量解码出 UUID 的字节数组，再通过 `UUIDUtil.convert(bytes).toString()` 转换为字符串并包装为 `UTF8String` 返回。新增导入 `org.apache.arrow.vector.IntVector` 和 `org.apache.parquet.column.Dictionary`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+15/-0 lines)

**修改目的**：添加字典编码 UUID 向量化读取测试。

**工作逻辑**：新增 `testUuidReads()` 测试方法，创建一个仅含单行 UUID 列的 schema（`optional(100, "uuid", Types.UUIDType.get())`），生成 1 行测试数据（单行以确保 Parquet 使用字典编码），写入 Parquet v2 文件后通过向量化读取验证能正确读回数据。使用 `assertRecordsMatch` 验证读取结果与写入数据一致。

## 总结

该提交为 Spark 4.0 的向量化读取路径补充了对 Parquet 字典编码 UUID 列的支持，通过在 UUID 访问器中新增字典解码方法解决了此前向量化读取遇到字典编码 UUID 时报错的问题。配套测试验证了单行（字典编码）UUID 列的向量化读取正确性。这是 PR #13324 在 Spark 4.0 分支的移植。
