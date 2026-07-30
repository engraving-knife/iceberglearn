# 提交 2433：Arrow, Parquet: Add support for DELTA_BINARY_PACKED Parquet encoding (#13391)

## 提交信息

- **序号**：2433 / 4088
- **哈希**：c3d50e177ce17a45f4dd3af7593350dd6724ca8f
- **短哈希**：c3d50e177
- **日期**：2025-07-30 10:07:25 -0500
- **作者**：Eric Maynard
- **提交说明**：Arrow, Parquet: Add support for DELTA_BINARY_PACKED Parquet encoding (#13391)
- **PR/Issue**：#13391

## 总体目的

本提交为 Iceberg 的 Arrow 向量化 Parquet 读取路径新增了对 `DELTA_BINARY_PACKED` 编码的支持。此前，Iceberg 的向量化读取器（vectorized reader）只支持 `PLAIN` 编码；当遇到 Parquet V2 写入的 INT32/INT64 列（默认使用 DELTA_BINARY_PACKED 编码）时，会抛出 `UnsupportedOperationException`，迫使用户关闭向量化读取，严重影响读取性能。

`DELTA_BINARY_PACKED` 是 Parquet 格式中用于整数类型的 delta 编码，能够显著压缩整数列的存储空间。Parquet V2 写入时默认对 int 和 long 类型使用该编码。由于向量化读取是 Spark 等引擎高性能查询的关键路径，缺少对该编码的支持意味着 Parquet V2 文件的整数列无法享受向量化加速。本提交通过新增 `VectorizedDeltaEncodedValuesReader` 填补了这一空白。

实现参考了 Apache Spark 的 `VectorizedDeltaBinaryPackedReader`（在 LICENSE 中已声明来源），并将其适配到 Iceberg 的 Arrow 向量化读取框架中。

## 如何达成设计目的

1. 新增 `VectorizedDeltaEncodedValuesReader` 类（继承 `ValuesReader` 并实现 `VectorizedValuesReader` 接口），负责解码 DELTA_BINARY_PACKED 编码的整数数据并批量写入 Arrow `FieldVector`。
2. 在 `VectorizedPageIterator` 中将编码分发的 `if-else` 改为 `switch`，新增 `DELTA_BINARY_PACKED` 分支创建新 reader。
3. 重构 `VectorizedParquetDefinitionLevelReader`：将原 `setNextNValuesInVector` 中"批量读取字节并 setBytes"的逻辑改为通过抽象方法 `nextVals` 委托给各具体子类（IntegerReader/LongReader/FloatReader/DoubleReader），因为 DELTA 编码无法像 PLAIN 那样直接读二进制字节块，必须逐类型解码。
4. 将类型宽度常量（INT_SIZE 等）从 `VectorizedPlainValuesReader` 上移到 `VectorizedValuesReader` 接口，供新旧 reader 共享。
5. 更新测试：将 int/long 从"不支持"列表移到"支持"列表（Parquet V2 测试）。

## 修改详情

### `LICENSE` (+1/-0 lines)

**修改目的**：声明新代码来源于 Apache Spark。

**工作逻辑**：在"代码借用自 Apache Spark"的清单中新增 `* implementation of VectorizedDeltaEncodedValuesReader`，因为新 reader 是从 Spark 的 `VectorizedDeltaBinaryPackedReader` 改编而来。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedDeltaEncodedValuesReader.java` (+283/-0 lines, 新增文件)

**修改目的**：实现 DELTA_BINARY_PACKED 编码的向化解码器。

**工作逻辑**：
- `initFromPage`：读取页头，包括 `blockSizeInValues`、`miniBlocksPerBlock`、`totalValueCount` 和第一个值 `firstValue`（zigzag varint）。计算 mini block 大小（必须为 8 的倍数）。
- 数据结构：block 由多个 mini block 组成，每个 mini block 有自己的 bit width。`unpackedValuesBuffer` 存放当前 mini block 解包后的 long 数组。
- `readValues`：核心读取逻辑。第一个值直接使用 `firstValue`；后续值通过 `loadMiniBlockToOutput` 逐 mini block 解码。每个值 = `lastValueRead + minDeltaInCurrentBlock + unpackedValuesBuffer[i]`（累积 delta）。
- `loadMiniBlockToOutput`：新 block 时读 block header（min delta + 各 mini block 的 bit width）；新 mini block 时调用 `unpackMiniBlock` 解包；然后从解包缓冲区读取值并写入 Arrow 向量。
- `unpackMiniBlock`：使用 `BytePackerForLong`（基于当前 mini block 的 bit width）每次解包 8 个值到 `unpackedValuesBuffer`。
- `readIntegers`/`readLongs`：批量写入 Arrow `FieldVector` 的 data buffer（按 typeWidth 偏移）。
- `readByte`/`readShort`/`readBinary`/`readFloats`/`readDoubles`/`skip`：DELTA_BINARY_PACKED 仅支持 INT32/INT64，其余抛 `UnsupportedOperationException`。
- `IntegerOutputWriter`：函数式接口，用于将 long 值写入向量的指定偏移（int 截断或 long 直写）。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedPageIterator.java` (+25/-8 lines)

**修改目的**：在编码分发处新增 DELTA_BINARY_PACKED 分支。

**工作逻辑**：将原来 `if (dataEncoding == Encoding.PLAIN) ... else throw` 的逻辑改为 `switch`：`PLAIN` 创建 `VectorizedPlainValuesReader`，`DELTA_BINARY_PACKED` 创建 `VectorizedDeltaEncodedValuesReader`，其余编码仍抛 `UnsupportedOperationException`。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedParquetDefinitionLevelReader.java` (+71/-24 lines)

**修改目的**：重构批量值读取逻辑以支持非 PLAIN 编码。

**工作逻辑**：
- 原 `setNextNValuesInVector(typeWidth, ...)` 是外部私有方法，通过 `valuesReader.readBinary(numValues * typeWidth)` 一次性读取字节块再 `setBytes` 写入向量——这只适用于 PLAIN 编码（连续字节）。
- 新版将该方法改为 `NumericBaseReader` 的成员方法，去掉 `typeWidth` 参数，改为调用抽象方法 `nextVals(vector, rowId, valuesReader, total)`，由各子类实现具体批量读取方式。
- `IntegerReader.nextVals` 调用 `valuesReader.readIntegers(total, vector, rowId)`。
- `LongReader.nextVals` 调用 `valuesReader.readLongs(total, vector, rowId)`。
- `FloatReader.nextVals` / `DoubleReader.nextVals` 类似。
- 这样 PLAIN reader 继续按字节块读，DELTA reader 按批量解码读，互不干扰。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedPlainValuesReader.java` (+5/-0 lines, 实际为删除)

**修改目的**：上移类型宽度常量到接口。

**工作逻辑**：删除 `INT_SIZE`、`LONG_SIZE`、`FLOAT_SIZE`、`DOUBLE_SIZE` 常量定义（这些常量移到了 `VectorizedValuesReader` 接口中作为常量，所有实现类共享）。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedValuesReader.java` (+5/-0 lines)

**修改目的**：在接口中定义共享的类型宽度常量。

**工作逻辑**：新增 `INT_SIZE=4`、`LONG_SIZE=8`、`FLOAT_SIZE=4`、`DOUBLE_SIZE=8` 四个常量，供 `VectorizedPlainValuesReader` 和 `VectorizedDeltaEncodedValuesReader` 共用。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+9/-2 lines)

**修改目的**：更新测试以反映 DELTA_BINARY_PACKED 现已支持向量化读取。

**工作逻辑**：
- `testSupportedReadsForParquetV2`：新增 `int_data`（INT32）和 `long_data`（INT64）列，因为 Parquet V2 下这两种类型使用 DELTA_BINARY_PACKED 编码，现在可以被向量化读取了。
- `testUnsupportedReadsForParquetV2`：更新注释，将 int/long 从"不支持"描述中移除（原注释称 "Longs, ints, string types etc use delta encoding and which are not supported"），改为更通用的描述。

## 总结

本提交是一个重要的性能与兼容性增强：它使 Iceberg 的 Arrow 向量化 Parquet 读取路径支持了 DELTA_BINARY_PACKED 编码，从而让 Parquet V2 文件的 INT32/INT64 列也能享受向量化读取加速。核心新增是 `VectorizedDeltaEncodedValuesReader` 类（改编自 Spark），同时重构了定义级别读取器的批量值写入逻辑以适配不同编码。测试同步更新，将 int/long 列加入 Parquet V2 的支持读取列表。这对使用 Parquet V2 写入的表的查询性能有直接提升。
