# 提交 2489：Add golden file tests for vectorized Parquet reads (#13450)

## 提交信息

- **序号**：2489 / 4088
- **哈希**：e6676705f74f7c1e6974afc86f103848e0fab8bd
- **短哈希**：e6676705f
- **日期**：2025-08-12 12:06:07 -0700
- **作者**：Eric Maynard
- **提交说明**：Add golden file tests for vectorized Parquet reads (#13450)
- **PR/Issue**：#13450

## 总体目的

本提交为 Spark 4.0 的向量化 Parquet 读取功能添加了基于 golden file（黄金文件）的测试。Parquet 格式支持多种编码方式（如 PLAIN、PLAIN_DICTIONARY、RLE、RLE_DICTIONARY、DELTA_BINARY_PACKED 等），不同的编码方式在向量化读取时应当产生相同的数据结果。

此测试的核心动机是确保不同 Parquet 编码方式下的向量化读取结果的一致性。通过预先准备一组用 PLAIN 编码写入的"黄金文件"作为基准，再与使用其他编码方式写入的文件进行对比，验证向量化读取器能够正确解码各种编码格式。这是一种回归测试手段，可以在未来修改读取器逻辑时防止引入编码相关的 bug。

该测试框架使用参数化测试（ParameterizedTest），将编码方式与数据类型进行笛卡尔积组合，覆盖 string、float、int32、int64、binary、boolean 六种数据类型，以及 PLAIN_DICTIONARY、RLE、RLE_DICTIONARY、DELTA_BINARY_PACKED 四种非 PLAIN 编码方式。

## 如何达成设计目的

关键设计点如下：

1. **黄金文件资源**：在 `spark/v4.0/spark/src/test/resources/encodings/` 目录下预置了各编码方式和数据类型对应的 `.parquet` 二进制文件，作为测试基准。
2. **参数化测试**：通过 `goldenFilesAndEncodings()` 方法生成编码与类型的组合流，每个组合运行一次 `testGoldenFiles` 测试。
3. **一致性比较**：`assertIdenticalFileContents` 方法使用 `SparkParquetReaders` 分别读取待测文件和 PLAIN 基准文件，将结果收集到 List 中后用 AssertJ 的 `hasSameSizeAs` 和 `hasSameElementsAs` 进行比较。
4. **跳过缺失组合**：当某编码/类型组合不存在对应黄金文件时，使用 `assumeThat` 跳过该测试而非失败。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+81/-0 lines)

**修改目的**：添加参数化的 golden file 对比测试，验证不同编码下的向量化读取一致性。

**工作逻辑**：
- 定义了 `GOLDEN_FILE_ENCODINGS` 列表（4 种非 PLAIN 编码）和 `GOLDEN_FILE_TYPES` 映射（6 种基本类型）。
- `goldenFilesAndEncodings()` 方法通过 flatMap 将编码与类型做笛卡尔积，生成 `Stream<Arguments>`。
- `testGoldenFiles` 根据编码和类型名构造资源路径，加载对应黄金文件和 PLAIN 基准文件，构建只含单字段 `data` 的 schema，调用 `assertIdenticalFileContents` 进行逐行比较。
- `assertIdenticalFileContents` 使用 `Parquet.read` 配合 `SparkParquetReaders.buildReader` 读取两个文件，收集为 List 后断言大小和元素一致。

### 二进制黄金文件资源 (19 个 .parquet 文件)

**修改目的**：提供各编码/类型组合的预生成 Parquet 测试数据文件，分布在 `encodings/{PLAIN,PLAIN_DICTIONARY,RLE,RLE_DICTIONARY,DELTA_BINARY_PACKED}/{typename}.parquet` 路径下。

## 总结

本提交通过引入 golden file 测试机制，显著增强了 Spark 4.0 向量化 Parquet 读取的测试覆盖度。参数化设计使得新增编码或类型时只需添加资源文件即可自动纳入测试，具有良好的可扩展性。这对于保证 Iceberg 在不同 Parquet 编码下的读取正确性具有重要意义。
