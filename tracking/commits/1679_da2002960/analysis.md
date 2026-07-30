# 提交 1679：Spark: Update benchmark instructions (#12171)

## 提交信息

- **序号**：1679 / 4088
- **哈希**：da20029609a320c8da29d5fdbb5cbfcd54309e30
- **短哈希**：da2002960
- **日期**：2025-02-04（Tue Feb 4 15:38:13 2025 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Spark: Update benchmark instructions (#12171)
- **PR/Issue**：#12171

## 总体目的

`site/docs/benchmarks.md` 是 Iceberg 网站上的基准测试文档，列出了所有可用的 JMH 基准测试及其运行命令。此前文档中所有命令的 Gradle 任务路径都使用 `iceberg-spark[2|3]` 这种通配写法，暗示用户可以选择 Spark 2 或 Spark 3 模块。但 Iceberg 早已移除 Spark 2 支持，且 Spark 3 也已拆分为 3.3/3.4/3.5 等具体版本模块，`iceberg-spark[2|3]` 这个路径在实际的 Gradle 项目中已不存在，用户照抄命令会直接报错。

本提交把所有 18 条基准测试命令中的 `iceberg-spark[2|3]` 统一替换为具体的 `iceberg-spark-3.5_2.12`，使文档中的命令可直接复制运行。同时每条命令上方的说明文字"To run this benchmark for either spark-2 or spark-3"也保留（虽然 spark-2 已不存在，但本提交未改文字描述，只改命令）。

## 如何达成设计目的

全文替换：把 `iceberg-spark[2|3]` 替换为 `iceberg-spark-3.5_2.12`，共 18 处。

## 修改详情

### `site/docs/benchmarks.md`（修改，+18/-18 行）

**修改目的**：把基准测试运行命令中的过期模块路径替换为当前可用的具体模块路径。

**工作逻辑**：18 条 JMH 基准测试命令中的 `:iceberg-spark:iceberg-spark[2|3]:jmh` 全部改为 `:iceberg-spark:iceberg-spark-3.5_2.12:jmh`。涉及的基准测试包括：

- IcebergSourceNestedListParquetDataWriteBenchmark
- SparkParquetReadersNestedDataBenchmark
- SparkParquetWritersFlatDataBenchmark
- IcebergSourceFlatORCDataReadBenchmark
- SparkParquetReadersFlatDataBenchmark
- VectorizedReadDictionaryEncodedFlatParquetDataBenchmark
- IcebergSourceNestedListORCDataWriteBenchmark
- VectorizedReadFlatParquetDataBenchmark
- IcebergSourceFlatParquetDataWriteBenchmark
- IcebergSourceNestedAvroDataReadBenchmark
- IcebergSourceFlatAvroDataReadBenchmark
- IcebergSourceNestedParquetDataWriteBenchmark
- IcebergSourceNestedParquetDataReadBenchmark
- IcebergSourceNestedORCDataReadBenchmark
- IcebergSourceFlatParquetDataReadBenchmark
- IcebergSourceFlatParquetDataFilterBenchmark
- IcebergSourceNestedParquetDataFilterBenchmark
- SparkParquetWritersNestedDataBenchmark

## 小结

- **成效**：使基准测试文档中的命令可直接复制运行，不再引用已不存在的 `iceberg-spark[2|3]` 模块路径。
- **影响范围**：仅网站文档，无代码变更。
- **回迁到 1.4.x 的注意事项**：纯文档修订，回迁安全。需注意 1.4.x 分支支持的 Spark 版本可能不同（如可能仍支持 3.2/3.3），回迁时应根据 1.4.x 实际支持的 Spark 版本调整模块路径（如改为 `iceberg-spark-3.4_2.12` 等）。文档中"for either spark-2 or spark-3"的文字描述也已过时，可一并修正。
