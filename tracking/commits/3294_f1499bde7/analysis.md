# 提交 3294：Spark, Arrow, Parquet: Add vectorized parquet read support for `DELTA_LENGTH_BYTE_ARRAY` & `DELTA_BYTE_ARRAY` encodings (#15362)

## 提交信息

- **序号**：3294 / 4088
- **哈希**：f1499bde7830a8f003995311cd2b3a3503214a81
- **短哈希**：f1499bde7
- **日期**：2026-02-21
- **作者**：jbewing（与 Eric Maynard 合著）
- **提交说明**：Spark, Arrow, Parquet: Add vectorized parquet read support for `DELTA_LENGTH_BYTE_ARRAY` & `DELTA_BYTE_ARRAY` encodings (#15362)
- **PR/Issue**：#15362

## 总体目的

Iceberg 在 `arrow` 模块中实现了一套向量化 Parquet 读取器（`VectorizedPageIterator` + 一组 `VectorizedValuesReader`），用于在 Spark 等引擎中以 Arrow `FieldVector` 批量解码 Parquet 数据页，从而显著提升读取吞吐。重构前，该向量化读取器只支持三种编码：`PLAIN`、`PLAIN_DICTIONARY`/`RLE_DICTIONARY`、以及 `DELTA_BINARY_PACKED`（用于 int/long）。当数据页使用其它编码时，`VectorizedPageIterator` 的 switch 会落入 `default` 分支抛出 `UnsupportedOperationException`，迫使读取回退到非向量化路径。

这其中影响最大的是 `DELTA_LENGTH_BYTE_ARRAY` 与 `DELTA_BYTE_ARRAY` 两种编码。关键背景在于：Parquet 的 V2 写入格式（`WriterVersion.PARQUET_2_0`）默认会对 `BYTE_ARRAY`/`STRING` 列采用 `DELTA_LENGTH_BYTE_ARRAY` 编码，并对字符串列进一步采用 `DELTA_BYTE_ARRAY`（前缀+后缀增量编码）。因此，凡是使用 Parquet V2 写出的 Iceberg 表，其字符串与二进制列就无法被向量化读取器处理，要么报错要么回退到慢路径，削弱了向量化读的实际收益。

本提交为这两种编码分别新增了向量化 `ValuesReader` 实现——`VectorizedDeltaLengthByteArrayValuesReader` 与 `VectorizedDeltaByteArrayValuesReader`（均注明改编自 Spark 的同名读取器，并在 LICENSE 中补充衍生声明），把它们接入 `VectorizedPageIterator` 的编码分派，并重构了 `VectorizedValuesReader` 接口为 default 方法抛 `UnsupportedOperationException`，使各 reader 只需实现自己支持的类型方法。这补齐了向量化 Parquet 读取在字符串/二进制列上的最后一块短板，使 V2 Parquet 文件也能享受向量化读带来的性能提升。配套补充了多种编码的 golden 测试 parquet 文件（含带 null 的变体），并在四个 Spark 版本（3.4/3.5/4.0/4.1）的向量化读测试中把这两种编码纳入参数化覆盖，同时扩展了 `testSupportedReadsForParquetV2` 以加入 string/binary 列。

## 如何达成设计目的

整体分四步：①新增两个 `VectorizedValuesReader` 实现类，分别按 Parquet 规范解码 DELTA_LENGTH_BYTE_ARRAY（增量长度数组 + 拼接字节数据）与 DELTA_BYTE_ARRAY（前缀长度增量 + 后缀按 DELTA_LENGTH_BYTE_ARRAY 编码，再以前一值前缀拼接后缀重建值），二者在 `initFromPage` 时复用 `VectorizedDeltaEncodedValuesReader` 批量读出长度/前缀数组；②在 `VectorizedPageIterator` 的编码 switch 中为这两种编码各加一个 case 实例化对应 reader；③把 `VectorizedValuesReader` 接口的所有方法改为 `default` 抛 `UnsupportedOperationException`，并据此精简 `VectorizedDeltaEncodedValuesReader`（移除冗余的"不支持"覆盖、新增 `totalValueCount()` 与 `readIntegers(int,int)` 包级辅助方法供新 reader 复用）；④新增/扩展测试 fixture 与参数化用例。改动集中在 `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/` 与各 Spark 版本的向量化读测试。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedDeltaLengthByteArrayValuesReader.java` (+83/-0 lines, 新增)

**修改目的**：为 `DELTA_LENGTH_BYTE_ARRAY` 编码提供向量化读取器。

**工作逻辑**：
该编码的存储格式为：先用 `DELTA_BINARY_PACKED` 存储所有值的长度数组，再紧跟所有值字节拼接而成的连续数据。`initFromPage` 先构造一个 `VectorizedDeltaEncodedValuesReader` 读出长度数组（`readIntegers(totalValueCount, 0)`），剩余的 `ByteBufferInputStream` 作为 `dataStream`。`readBinary(len)` 通过 `dataStream.slice(len)` 切出当前行的字节并包装为 `Binary.fromConstantByteArray`，同时推进 `currentRow`。包级方法 `lengthForCurrentRow()` 返回当前行长，供 DELTA_BYTE_ARRAY reader 查询。`readInteger()` 返回当前行长。`skip()` 不支持。该类继承 Parquet 的 `ValuesReader` 并实现 `VectorizedValuesReader`。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedDeltaByteArrayValuesReader.java` (+83/-0 lines, 新增)

**修改目的**：为 `DELTA_BYTE_ARRAY` 编码提供向量化读取器。

**工作逻辑**：
该编码（Parquet 又称 delta strings）存储格式为：先用 `DELTA_BINARY_PACKED` 存前缀长度数组，再用 `DELTA_LENGTH_BYTE_ARRAY` 存后缀。每个值 = 前一值的前 `prefixLength` 字节 + 当前后缀。`initFromPage` 先用 `VectorizedDeltaEncodedValuesReader` 读出 `prefixLengths` 数组，再构造 `VectorizedDeltaLengthByteArrayValuesReader` 作为 `suffixReader` 从流中继续读后缀。`readBinary(len)`：取 `prefixLength = prefixLengths[currentRow]`，从 suffixReader 读 `len - prefixLength` 字节作为后缀；若 prefixLength 为 0 直接返回后缀并更新 `previous`；否则把 `previous` 的前 prefixLength 字节与后缀拼接成新值，更新 `previous` 并返回。`readInteger()` 返回 `prefixLengths[currentRow] + suffixReader.lengthForCurrentRow()`（即完整值长度）。`skip()` 不支持。通过维护 `previous` 与 `currentRow` 实现前缀复用。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedPageIterator.java` (+6/-0 lines)

**修改目的**：把两种新编码接入数据页解码分派。

**工作逻辑**：
在 `VectorizedPageIterator` 创建 `valuesReader` 的 switch 中，新增两个 case：`case DELTA_LENGTH_BYTE_ARRAY: valuesReader = new VectorizedDeltaLengthByteArrayValuesReader(); break;` 与 `case DELTA_BYTE_ARRAY: valuesReader = new VectorizedDeltaByteArrayValuesReader(); break;`。此前这两种编码会落入 default 抛 `UnsupportedOperationException`，现在可走向量化路径。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedValuesReader.java` (+35/-13 lines)

**修改目的**：将接口方法改为 default 抛异常，降低各实现类的样板代码。

**工作逻辑**：
原先 `VectorizedValuesReader` 的每个方法都是抽象方法，每个实现类都必须为不支持的类型提供抛 `UnsupportedOperationException` 的实现。重构后所有方法（`readBoolean`/`readByte`/`readShort`/`readInteger`/`readLong`/`readFloat`/`readDouble`/`readBinary`/`readIntegers`/`readLongs`/`readFloats`/`readDoubles`）均改为 `default` 并默认抛 `UnsupportedOperationException`，实现类只需覆盖自己支持的少数方法。这样新增的两个 byte-array reader 只实现 `readBinary`/`readInteger` 即可，其余继承默认抛错；同时也让 `VectorizedDeltaEncodedValuesReader` 能删掉冗余的"不支持"覆盖。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedDeltaEncodedValuesReader.java` (+13/-29 lines)

**修改目的**：精简冗余的"不支持"方法，并为新 reader 暴露批量读整数的辅助能力。

**工作逻辑**：
- 移除 `readByte`、`readShort`、`readBinary`、`readFloats`、`readDoubles` 这五个原本显式抛 `UnsupportedOperationException` 的方法（现由接口 default 提供），并删除不再使用的 `import org.apache.parquet.io.api.Binary;`。
- 新增包级方法 `int totalValueCount()` 返回当前页的总值数，供 byte-array reader 在 `initFromPage` 时知道要批量读多少个长度。
- 新增包级方法 `int[] readIntegers(int total, int rowId)`：复用内部 `readValues` 机制但不写入 `FieldVector`，而是把解码出的 int 收集到新数组返回。`VectorizedDeltaLengthByteArrayValuesReader` 与 `VectorizedDeltaByteArrayValuesReader` 用它在 `initFromPage` 一次性读出长度/前缀数组。注意其 lambda 中用 `idx / INT_SIZE` 计算数组下标，与 `readValues` 内部按 `INT_SIZE` 步进写入的约定对齐。
- 保留 `readIntegers(total, vec, rowId)`、`readLongs(total, vec, rowId)`、`readInteger`、`readLong`、`skip` 等原有方法不变。

### `LICENSE` (+2/-0 lines)

**修改目的**：补充衍生自 Apache Spark 的代码声明。

**工作逻辑**：
在 "This product includes code from Apache Spark." 段落，于已有的 `implementation of VectorizedDeltaEncodedValuesReader` 之后追加 `implementation of VectorizedDeltaLengthByteArrayValuesReader` 与 `implementation of VectorizedDeltaByteArrayValuesReader` 两行，因为这两个新 reader 是改编自 Spark 的同名读取器实现。

### `parquet/src/testFixtures/resources/encodings/...` (12 个新增 parquet 二进制 + 6 个 PLAIN with_nulls)

**修改目的**：为编码测试提供 golden 文件。

**工作逻辑**：
新增 `DELTA_BYTE_ARRAY/` 目录下 `binary.parquet`、`binary_with_nulls.parquet`、`string.parquet`、`string_with_nulls.parquet`，以及 `DELTA_LENGTH_BYTE_ARRAY/` 目录下同名四份文件，作为对应编码向量化读的正确性基准。另外新增 `PLAIN/` 目录下 `binary_with_nulls.parquet`、`boolean_with_nulls.parquet`、`float_with_nulls.parquet`、`int32_with_nulls.parquet`、`int64_with_nulls.parquet`、`string_with_nulls.parquet`，用于补齐带 null 场景的 golden 文件（参数化测试新增了 `_with_nulls` 变体）。

### `spark/v4.1/.../TestParquetVectorizedReads.java` (+22/-7 lines) 及 v3.5/v4.0 同名测试 (+22/-7 lines each)

**修改目的**：把两种新编码纳入参数化向量化读测试，并扩展 Parquet V2 测试。

**工作逻辑**：
- `GOLDEN_FILE_ENCODINGS` 列表由 `["PLAIN_DICTIONARY", "RLE_DICTIONARY", "DELTA_BINARY_PACKED"]` 扩展为再加入 `"DELTA_LENGTH_BYTE_ARRAY"`、`"DELTA_BYTE_ARRAY"`，使参数化读测试覆盖这两种编码的 string/binary golden 文件。
- `testSupportedReadsForParquetV2`：更新注释说明 Parquet V2 对各类型的编码选择（float/double 用 PLAIN、int/long 用 DELTA_BINARY_PACKED、string/binary 用 DELTA_LENGTH_BYTE_ARRAY、大 decimal 用字典），并在 schema 中新增 `string_data`（StringType）与 `binary_data`（BinaryType）两列，验证 V2 写出的字符串/二进制列现在可被向量化读取。
- 参数化用例生成逻辑由 `map` 改为 `flatMap`：对每个编码/类型组合，除了原有用例，再追加一个 `_with_nulls` golden 文件用例，覆盖带 null 值的解码正确性。

### `spark/v3.4/.../TestParquetVectorizedReads.java` (+10/-2 lines)

**修改目的**：同步扩展 v3.4 的 Parquet V2 测试。

**工作逻辑**：
v3.4 测试结构与 3.5/4.0/4.1 略有不同（无 `GOLDEN_FILE_ENCODINGS` 参数化机制），主要在 `testSupportedReadsForParquetV2` 中更新注释并新增 `int_data`、`long_data`、`string_data`、`binary_data` 四列，验证 V2 写出的 int/long/string/binary 列（分别使用 DELTA_BINARY_PACKED 与 DELTA_LENGTH_BYTE_ARRAY）可被向量化读取。

## 总结

本提交为 Iceberg 向量化 Parquet 读取器补齐了 `DELTA_LENGTH_BYTE_ARRAY` 与 `DELTA_BYTE_ARRAY` 两种编码的支持，使采用 Parquet V2 格式写出的字符串与二进制列（这两种编码是 V2 的默认选择）能够走向量化读路径而非回退或报错。通过新增两个改编自 Spark 的 reader、重构接口为 default 方法、复用 DELTA_BINARY_PACKED reader 的批量解码能力，并配以完整的 golden 文件与跨四个 Spark 版本的参数化测试，整体显著提升了向量化读的编码覆盖面与实际性能收益。
