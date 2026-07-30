# 提交 1265：Arrow: Fix indexing in Parquet dictionary encoded values readers (#11247)

## 提交信息

- **序号**：1265 / 4088
- **哈希**：d0a7ff915e6307e200a02ed76daff1a2fb363a3b
- **短哈希**：d0a7ff915
- **日期**：2024-10-21（Mon Oct 21 11:53:13 2024 -0700）
- **作者**：Wing Yew Poon <wypoon@cloudera.com>
- **提交说明**：Arrow: Fix indexing in Parquet dictionary encoded values readers (#11247)
- **PR/Issue**：#11247

## 总体目的

Iceberg 的 Arrow 向量化 Parquet 读取器中，`VectorizedDictionaryEncodedParquetValuesReader` 负责读取 Parquet 字典编码（RLE 编码的字典 ID）的列值并写入 Arrow `FieldVector`。该类内部有一个 `BaseDictEncodedReader.nextBatch` 方法，循环读取字典 ID 并调用各类型专用的 `nextVal` 把解码值写入 Arrow 向量的 data buffer。

**Bug 所在**：`nextBatch` 循环中预先计算了字节偏移 `int index = idx * typeWidth`（当 `typeWidth == -1` 时退化为 `index = idx`），然后把**已乘过的 index** 传给 `nextVal`。而不同的 `nextVal` 实现对 `idx` 参数的语义期望不一致：

1. **定宽数值类 reader**（`LongDictEncodedReader`、`IntegerDictEncodedReader`、`FloatDictEncodedReader`、`DoubleDictEncodedReader`、`TimestampMillisDictEncodedReader`、`TimestampInt96DictEncodedReader`）：`nextVal` 调用 `vector.getDataBuffer().setLong(idx, ...)` 等，这些 Arrow API 期望**字节偏移**，所以 `idx` 应为 `slotIndex * typeWidth`。原代码传入预乘后的 `index`，在这类 reader 上恰好正确。
2. **slot 索引类 reader**（`DictionaryIdReader` 的 `IntVector.set(idx, ...)`、`VarWidthBinaryDictEncodedReader` 的 `setSafe(idx, ...)`）：这些 Arrow API 期望**槽位索引**（slot index），不是字节偏移。原代码在 `typeWidth == -1` 时传 `index = idx`（正确），但在 `typeWidth != -1` 时传 `index = idx * typeWidth`（**错误**，传了字节偏移给期望槽位索引的 API）。
3. **`FixedWidthBinaryDictEncodedReader`**（已 `@Deprecated`）：`nextVal` 调用 `vector.getDataBuffer().setBytes(idx, buffer)`，期望**字节偏移**。原代码传预乘后的 `index`，在 `typeWidth != -1` 时正确。

此外，原代码用 `int` 乘法 `idx * typeWidth` 计算字节偏移，当 `idx` 很大（大文件、向量槽位数多）时存在**整数溢出**风险。而同文件中 PACKED 模式的调用方（`VectorizedParquetDefinitionLevelReader` 中各 reader 的 `nextDictEncodedVal`）已经用 `(long) idx * typeWidth` 计算，RLE 路径与之不一致。

**实际触发场景**（由新增测试体现）：当一个 Parquet 列的某些页是字典编码、某些页是 plain 编码时（如 `decimal(38,0)` 存为 binary，或 binary 列跨多页且并非所有页都字典编码），字典编码页的值会被写到 Arrow 向量的错误位置，导致读取结果数据错乱。

本提交通过把"乘 typeWidth"的操作从 `nextBatch` 循环下沉到各 `nextVal` 实现中，让每个 reader 自行决定如何使用 `idx`（槽位索引）和 `typeWidth`（类型宽度），并统一使用 `(long) idx * typeWidth` 防止整数溢出。

## 如何达成设计目的

1. **循环简化**：`nextBatch` 不再预乘，直接把原始槽位索引 `idx` 传给 `nextVal`，删除 `typeWidth == -1` 的特殊分支。这样所有 `nextVal` 收到的都是语义明确的"槽位索引"。
2. **各 reader 自行计算字节偏移**：需要字节偏移的 reader（Long、Integer、Float、Double、TimestampMillis、TimestampInt96、FixedWidthBinary）在 `nextVal` 内部用 `(long) idx * typeWidth` 计算；需要槽位索引的 reader（DictionaryIdReader 用 `IntVector.set(idx, ...)`、VarWidthBinaryDictEncodedReader 用 `setSafe(idx, ...)`）直接用 `idx`，无需乘法（这两个 reader 未在 diff 中修改，因为它们原本就用 `idx` 直接调用 slot-index API，原代码的 `typeWidth == -1` 分支恰好让它们收到正确的 slot index，新代码直接传 `idx` 也正确）。
3. **long 强制转换**：所有字节偏移计算改为 `(long) idx * typeWidth`，与 PACKED 模式一致，防止大文件场景下的整数溢出。
4. **新增测试**：`testBinaryNotAllPagesDictionaryEncoded` 和 `testDecimalNotAllPagesDictionaryEncoded` 验证混合编码场景下数据正确性，并新增 `TestHelpers.assertEqualsBatchWithRows` 工具方法用于对比 ColumnarBatch 与 Spark Row。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedDictionaryEncodedParquetValuesReader.java`（修改，+9/-13 行）

**修改目的**：修复 `BaseDictEncodedReader.nextBatch` 的索引计算 bug。

**工作逻辑**：

- `nextBatch` 循环：删除预乘逻辑
  ```java
  // 删除：
  int index = idx * typeWidth;
  if (typeWidth == -1) {
    index = idx;
  }
  // nextVal 调用改为传原始 idx：
  nextVal(vector, dict, idx, currentValue, typeWidth);  // RLE
  nextVal(vector, dict, idx, packedValuesBuffer[packedValuesBufferIdx++], typeWidth);  // PACKED
  ```

- 7 个定宽 reader 的 `nextVal` 改为内部计算字节偏移（以 `LongDictEncodedReader` 为例）：
  ```java
  // 原：vector.getDataBuffer().setLong(idx, dict.decodeToLong(currentVal));
  // 新：
  vector.getDataBuffer().setLong((long) idx * typeWidth, dict.decodeToLong(currentVal));
  ```
  涉及：`LongDictEncodedReader`、`TimestampMillisDictEncodedReader`、`TimestampInt96DictEncodedReader`、`IntegerDictEncodedReader`、`FloatDictEncodedReader`、`DoubleDictEncodedReader`、`FixedWidthBinaryDictEncodedReader`（`setBytes` 同理）。

- 未修改的 reader：`DictionaryIdReader`（`IntVector.set(idx, ...)` 用槽位索引）、`VarWidthBinaryDictEncodedReader`（`setSafe(idx, ...)` 用槽位索引）、`FixedSizeBinaryDictEncodedReader`（`set(idx, ...)` 用槽位索引）——这些 reader 原本就用槽位索引，新代码直接传 `idx` 即可。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetDictionaryEncodedVectorizedReads.java`（修改，+92 行）

**修改目的**：新增两个测试覆盖混合编码场景。

**工作逻辑**：

- 新增 SparkSession 的 `@BeforeAll`/`@AfterAll` 启停（该测试类需要 Spark 读取 parquet 生成期望值）。
- `testBinaryNotAllPagesDictionaryEncoded`：
  - 生成 500 行 binary 数据（100 个不同值），用 `PARQUET_DICT_SIZE_BYTES=4096` + `PARQUET_PAGE_ROW_LIMIT=100` 写入，产出 5 页：前 2 页 RLE 字典编码，后 3 页 plain 编码。
  - 用 `assertRecordsMatch` 验证向量化读取结果与原始记录一致。
- `testDecimalNotAllPagesDictionaryEncoded`：
  - 使用预置资源文件 `decimal_dict_and_plain_encoding.parquet`（`decimal(38,0)`，2 页各 200 行，1 页字典编码 + 1 页 plain 编码）。
  - 用 Spark 读取生成期望 Row 列表，用 Iceberg 向量化 reader 读取生成 ColumnarBatch，逐行对比验证。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`（修改，+15 行）

**修改目的**：新增 `assertEqualsBatchWithRows` 工具方法。

**工作逻辑**：遍历 ColumnarBatch 的每一行，与 Spark `Row` 迭代器逐字段对比，用于 `testDecimalNotAllPagesDictionaryEncoded` 中对比向量化读取结果与 Spark 读取结果。

### `spark/v3.5/spark/src/test/resources/decimal_dict_and_plain_encoding.parquet`（新增，二进制）

**修改目的**：预置的测试 parquet 文件，包含 `decimal(38,0)` 列、2 页（1 页字典编码 + 1 页 plain 编码），各 200 行。用于复现并验证混合编码场景下的索引 bug。

## 小结

- **成效**：修复了 Arrow 向量化 Parquet 字典编码读取器中的索引计算 bug。该 bug 在"列的某些页字典编码、某些页 plain 编码"时会导致字典编码页的值被写到 Arrow 向量的错误位置，造成数据错乱。修复后各 reader 正确使用槽位索引或字节偏移，并用 `long` 乘法防止整数溢出。新增两个测试覆盖 binary 和 decimal 混合编码场景。
- **影响范围**：修改 `arrow` 模块的 `VectorizedDictionaryEncodedParquetValuesReader`（核心修复）和 `spark/v3.5` 的测试。影响所有使用 Arrow 向量化读取 Parquet 字典编码数据的场景（Spark 3.5 的 Iceberg 读取器），尤其是大文件或混合编码页的场景。
- **回迁到 1.4.x 的注意事项**：
  1. 这是一个数据正确性 bug 修复，回迁价值高。回迁时需确认 1.4.x 分支上 `arrow` 模块的 `VectorizedDictionaryEncodedParquetValuesReader` 结构与 main 一致。
  2. 测试依赖 Spark 3.5（`spark/v3.5`），1.4.x 若支持 Spark 3.5 可直接回迁测试；若不支持，需调整测试到对应 Spark 版本目录。
  3. 预置 parquet 文件 `decimal_dict_and_plain_encoding.parquet` 需一并回迁到测试资源目录。
  4. `TestHelpers.assertEqualsBatchWithRows` 是新增工具方法，回迁时需确认 `TestHelpers` 类存在且包路径一致。
  5. 该 bug 在 1.4.x 上同样存在，回迁后建议验证 decimal/binary 混合编码场景的读取正确性。
