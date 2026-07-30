# 提交 3313：Spark, Arrow, Parquet: Add vectorized read support for parquet BYTE_STREAM_SPLIT encoding (#15373)

## 提交信息

- **序号**：3313 / 4088
- **哈希**：08a4a0cde2f4ae4f67fa776b7d46c31be287c54f
- **短哈希**：08a4a0cde
- **日期**：2026-02-25
- **作者**：jbewing
- **提交说明**：Spark, Arrow, Parquet: Add vectorized read support for parquet BYTE_STREAM_SPLIT encoding (#15373)
- **PR/Issue**：#15373

## 总体目的

Parquet 格式支持一种名为 `BYTE_STREAM_SPLIT`（编码值 9）的编码方式，它将数据的每个字节拆分到独立的流中（例如一个 double 有 8 字节，则所有 double 的第 0 字节连续存放、第 1 字节连续存放……以此类推）。这种编码对浮点型数据（float、double）和定长类型（int32、int64、UUID/FIXED_LEN_BYTE_ARRAY）有较好的压缩率和向量化读取潜力，常用于科学计算场景。

Iceberg 的向量化读取路径（基于 Apache Arrow 的 `VectorizedPageIterator`）此前不支持 `BYTE_STREAM_SPLIT` 编码，遇到该编码时会抛出 `UnsupportedOperationException`，导致包含此编码的 Parquet 文件无法通过向量化路径读取，只能回退到非向量化读取或直接失败。本提交新增了 `VectorizedByteStreamSplitValuesReader`，使 Iceberg 的 Arrow 向量化读取路径能够原生解码 `BYTE_STREAM_SPLIT` 编码，支持 int32、int64、float、double 和 FIXED_LEN_BYTE_ARRAY（如 UUID）类型，包括含 null 值的情况。

## 如何达成设计目的

设计分为三部分：一是实现核心的 `VectorizedByteStreamSplitValuesReader`（继承 Parquet 的 `ValuesReader` 并实现 Iceberg 的 `VectorizedValuesReader` 接口），负责将字节流分割编码解码为连续的字节序列；二是在 `VectorizedPageIterator` 的编码 switch 中添加 `BYTE_STREAM_SPLIT` 分支，并根据列的 `PrimitiveType` 计算元素字节大小；三是为 Spark v3.5/v4.0/v4.1 的向量化读取测试添加 `BYTE_STREAM_SPLIT` 编码和 `double`/`uuid` 类型的黄金测试文件。同时在根 LICENSE 文件中声明了对 Parquet `VectorizedByteStreamSplitValuesReader` 实现的代码借用。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedByteStreamSplitValuesReader.java` (+151 lines, 新文件)

**修改目的**：实现 BYTE_STREAM_SPLIT 编码的向量化读取器。

**工作逻辑**：
- 构造时接收 `elementSizeInBytes`（如 int32/float 为 4，int64/double 为 8，UUID 为 16）。
- `initFromPage` 中记录流的总字节数和数据流引用，**延迟解码**（lazy decode）——只有在首次读取时才执行解码。
- `decode(valuesCount)` 是核心解码逻辑：从编码流中按字节流分割的逆序重组数据。外层遍历每个值索引 `srcValueIndex`，内层遍历每个字节流 `stream`，按 `encoded.get(srcValueIndex + stream * valuesCount)` 取出对应字节，写入 `decoded[destByteIndex]`。最终包装为 `ByteOrder.LITTLE_ENDIAN` 的 ByteBuffer。
- 批量读取 `readBatch` 通过 `vec.getDataBuffer().setBytes(destOffset, slice)` 直接将解码后的字节拷贝到 Arrow 向量的底层 buffer，实现真正的向量化零拷贝。
- 单值读取（`readInteger`/`readLong`/`readFloat`/`readDouble`/`readBinary`）从解码后的 ByteBuffer 按类型读取。
- `ensureDecoded` 中校验 `totalBytesInStream % elementSizeInBytes == 0`，确保流大小是元素大小的整数倍。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedPageIterator.java` (+22 lines)

**修改目的**：在向量化页面迭代器中添加 BYTE_STREAM_SPLIT 编码分支。

**工作逻辑**：
- 在编码 switch 的 `case BYTE_STREAM_SPLIT:` 分支中，创建 `VectorizedByteStreamSplitValuesReader`，通过新增的 `byteStreamSplitElementSize(desc.getPrimitiveType())` 方法计算元素字节大小。
- `byteStreamSplitElementSize` 根据 `PrimitiveTypeName` 返回：`INT32`/`FLOAT` -> `INT_SIZE`(4)，`INT64`/`DOUBLE` -> `LONG_SIZE`(8)，`FIXED_LEN_BYTE_ARRAY` -> `type.getTypeLength()`（如 UUID 为 16），其他类型抛出 `UnsupportedOperationException`。
- 新增 `import org.apache.parquet.schema.PrimitiveType`。

### `LICENSE` (+1 lines)

**修改目的**：声明借用 Parquet 的 `VectorizedByteStreamSplitValuesReader` 实现。

**工作逻辑**：在根 LICENSE 的 "This product includes code from Apache Parquet" 代码清单中添加 `* implementation of VectorizedByteStreamSplitValuesReader`，表明该实现改编自 Apache Parquet 的 `ByteStreamSplitValuesReader`。

### `spark/v3.5/spark/src/test/.../TestParquetVectorizedReads.java` (+14/-6 lines)

**修改目的**：扩展 Spark 3.5 向量化读取测试以覆盖 BYTE_STREAM_SPLIT 编码。

**工作逻辑**：
- 在 `ENCODINGS` 列表中添加 `"BYTE_STREAM_SPLIT"`。
- 将 `GOLDEN_FILE_TYPES` 从 `ImmutableMap.of(...)`（最多 5 对）改为 `ImmutableMap.builder()` 形式，新增 `"double"` -> `Types.DoubleType.get()` 和 `"uuid"` -> `Types.UUIDType.get()` 两个类型，使测试覆盖更多数据类型。

### `spark/v4.0/spark/src/test/.../TestParquetVectorizedReads.java` (+14/-6 lines)

**修改目的**：同上，为 Spark 4.0 扩展测试。

**工作逻辑**：与 v3.5 完全相同的改动。

### `spark/v4.1/spark/src/test/.../TestParquetVectorizedReads.java` (+14/-6 lines)

**修改目的**：同上，为 Spark 4.1 扩展测试。

**工作逻辑**：与 v3.5 完全相同的改动。

### 测试资源 parquet 文件（14 个新文件）

**修改目的**：提供 BYTE_STREAM_SPLIT 和补充 PLAIN 编码的黄金测试文件。

**工作逻辑**：
- `encodings/BYTE_STREAM_SPLIT/` 下新增 `double.parquet`、`double_with_nulls.parquet`、`float.parquet`、`float_with_nulls.parquet`、`int32.parquet`、`int32_with_nulls.parquet`、`int64.parquet`、`int64_with_nulls.parquet`、`uuid.parquet`、`uuid_with_nulls.parquet` 共 10 个文件，覆盖所有支持的类型和含 null 场景。
- `encodings/PLAIN/` 下补充 `double.parquet`、`double_with_nulls.parquet`、`uuid.parquet`、`uuid_with_nulls.parquet` 共 4 个文件，用于与 BYTE_STREAM_SPLIT 编码的结果做正确性对照。

## 总结

本提交为 Iceberg 的 Arrow 向量化 Parquet 读取路径新增了 `BYTE_STREAM_SPLIT` 编码支持，覆盖 int32、int64、float、double 和 UUID 类型。核心 `VectorizedByteStreamSplitValuesReader` 采用延迟解码策略，批量读取时直接将解码字节拷贝到 Arrow 向量 buffer 实现向量化。配套的 14 个黄金测试文件和 3 个 Spark 版本的测试扩展确保了正确性。这消除了包含 BYTE_STREAM_SPLIT 编码的 Parquet 文件在向量化读取路径上的支持缺口。
