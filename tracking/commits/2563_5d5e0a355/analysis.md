# 提交 2563：Test both vectorized and nonvectorized readers in Parquet golden file tests (#13890)

## 提交信息

- **序号**：2563 / 4088
- **哈希**：5d5e0a3559946a94be2979f3ff9a09f3b8e67f19
- **短哈希**：5d5e0a355
- **日期**：2025-08-25 13:01:29 -0700
- **作者**：Eric Maynard
- **提交说明**：Test both vectorized and nonvectorized readers in Parquet golden file tests (#13890)
- **PR/Issue**：#13890

## 总体目的

该提交改进了 Spark 模块中 Parquet golden file 测试的覆盖范围，使其同时测试向量化（vectorized）和非向量化（nonvectorized）两种读取器。此前 `TestParquetVectorizedReads` 中的 golden file 测试只验证了向量化读取器（`SparkParquetReaders`）的输出，但同一个 Parquet 文件在向量和非向量读取模式下可能产生不同的结果（例如编码处理差异），因此需要同时覆盖两种读取路径以确保数据一致性。

此外，该提交还移除了 RLE 编码的 `boolean.parquet` golden file 测试资源，因为该编码类型在当前测试框架下不够稳定。

测试改进的核心思路是：使用 `GenericParquetReaders`（非向量化读取器）读取期望值，然后用向量化读取器和非向量化读取器分别读取实际值进行对比，确保两种读取路径都能正确解析 golden file。

## 如何达成设计目的

- 将 `assertIdenticalFileContents` 方法重构为接受 `vectorized` 布尔参数，根据参数选择使用向量化或非向量化读取器读取实际文件。
- 使用 `GenericParquetReaders` 读取期望文件作为基准（非 Spark 特定读取器）。
- 参数化测试方法 `testGoldenFiles` 新增 `vectorized` 参数，通过 `Stream.of(true, false)` 生成两组参数化测试。
- 引入 `GenericsHelpers.assertEqualsUnsafe` 进行 Record 与 InternalRow 的不安全比较。
- 移除 `RLE` 编码从 golden file 编码列表中，删除对应的 `boolean.parquet` 资源文件。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+37/-29)

**修改目的**：扩展测试覆盖向量和非向量两种读取模式。

**工作逻辑**：
- `GOLDEN_FILE_ENCODINGS` 列表中移除 `"RLE"`，保留 `PLAIN_DICTIONARY`、`RLE_DICTIONARY`、`DELTA_BINARY_PACKED`。
- `assertIdenticalFileContents` 方法重构：新增 `vectorized` 参数。使用 `GenericParquetReaders.buildReader` 读取期望文件得到 `List<Record>`。如果 `vectorized` 为 true，调用 `assertRecordsMatch` 进行向量化读取验证；如果为 false，使用 `SparkParquetReaders.buildReader` 非向量化读取实际文件，逐行用 `GenericsHelpers.assertEqualsUnsafe` 比较期望 Record 和实际 InternalRow。
- `goldenFilesAndEncodings` 参数源方法通过 `Stream.of(true, false)` 为每个编码和类型组合生成两组参数（向量化 true/false）。
- `testGoldenFiles` 方法新增 `vectorized` 参数，传递给 `assertIdenticalFileContents`。

### `spark/v4.0/spark/src/test/resources/encodings/RLE/boolean.parquet` (+0/-0, deleted)

**修改目的**：移除不再使用的 RLE 编码测试资源文件。

**工作逻辑**：删除 `boolean.parquet` golden file，因为 RLE 编码已从测试编码列表中移除。

## 总结

该提交将 Parquet golden file 测试扩展为同时覆盖向量化和非向量化两种读取路径，使用 GenericParquetReaders 作为期望值基准，通过参数化测试生成两组测试用例。同时移除了不稳定的 RLE 编码测试资源。
