# 提交 2646：Backport Parquet encoding tests for Spark 3.5 (#13859)

## 提交信息

- **序号**：2646 / 4088
- **哈希**：b7ee7c0748a41dabbfaa954629ccf9af6e2620c7
- **短哈希**：b7ee7c074
- **日期**：2025-09-17 07:29:01 -0700
- **作者**：Eric Maynard
- **提交说明**：Backport Parquet encoding tests for Spark 3.5 (#13859)
- **PR/Issue**：#13859

## 总体目的

这是一个 backport 提交，将 Parquet 编码（encoding）的黄金文件测试从主分支/Spark 4.0 移植到 Spark 3.5 模块。这些测试使用预生成的 Parquet "黄金文件"（golden files）来验证 Iceberg 在不同 Parquet 编码（PLAIN、PLAIN_DICTIONARY、RLE_DICTIONARY、DELTA_BINARY_PACKED）和不同数据类型（string、float、int32、int64、binary、boolean）下的向量化读取和非向量化读取的正确性。

此前的测试在 Spark 4.0 中已存在，但 Spark 3.5 缺少这些测试覆盖。本提交将测试资源和测试代码 backport 到 Spark 3.5，并改进了资源加载方式（通过 `resourceUrlToLocalFile` 将资源复制到临时文件），使测试在 JAR 内打包资源时也能正常运行。

## 如何达成设计目的

1. 在 `iceberg-parquet` 模块上应用 `java-test-fixtures` 插件，使 Parquet 测试资源（golden files）和工具能被其他模块（Spark 3.5/4.0）作为测试固件复用。
2. 在 Spark 3.5 的 build.gradle 中添加 `testFixtures(project(':iceberg-parquet'))` 依赖。
3. 在 Spark 3.5 的 `TestParquetVectorizedReads` 中新增参数化测试 `testGoldenFiles`，遍历编码和类型的组合，验证向量化/非向量化读取结果与 PLAIN 编码文件一致。
4. 新增辅助方法 `resourceUrlToLocalFile`（处理 JAR 资源需复制到本地文件）和 `assertIdenticalFileContents`。
5. 更新 `testSupportedReadsForParquetV2` 测试，新增 int/long 类型（使用 DELTA_BINARY_PACKED 编码）。
6. 同步对 Spark 4.0 做一致性改进（使用 `Preconditions.checkState`、`resourceUrlToLocalFile`）。
7. 添加 Parquet golden file 资源（各编码/类型的 .parquet 文件）。

## 修改详情

### `build.gradle` (+2/-0 lines)

**修改目的**：为 iceberg-parquet 模块启用测试固件插件。

**工作逻辑**：在 `project(':iceberg-parquet')` 中添加 `apply plugin: 'java-test-fixtures'`，使该模块的测试资源和辅助类可被其他模块通过 `testFixtures(...)` 依赖复用。

### `spark/v3.5/build.gradle` (+2/-0 lines)

**修改目的**：引入 iceberg-parquet 测试固件依赖。

**工作逻辑**：在 spark 和 spark-extensions 子项目中添加 `testImplementation(testFixtures(project(':iceberg-parquet')))`，使 Spark 3.5 测试能访问 Parquet golden file 资源。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+115/-10 lines)

**修改目的**：新增 Parquet 编码黄金文件测试。

**工作逻辑**：
- 定义编码列表（PLAIN_DICTIONARY、RLE_DICTIONARY、DELTA_BINARY_PACKED）和类型映射（string、float、int32、int64、binary、boolean）。
- `goldenFilesAndEncodings()` 生成参数化测试参数（编码 x 类型 x 向量化布尔）。
- `testGoldenFiles`：加载对应编码和 PLAIN 编码的 golden file，断言两者读取结果一致。
- `resourceUrlToLocalFile`：将资源 URL（可能是 JAR 内）复制到临时文件，确保 Parquet 读取器能访问。
- `assertIdenticalFileContents`：比较实际文件和期望文件的读取结果。
- 更新 `testSupportedReadsForParquetV2` 新增 int/long 字段。

### `spark/v4.0/build.gradle` (+2/-0 lines) 及 `spark/v4.0/.../TestParquetVectorizedReads.java` (+29/-10 lines)

**修改目的**：与 Spark 3.5 保持一致。

**工作逻辑**：同样添加 testFixtures 依赖。测试代码改用 `Preconditions.checkState` 替代手动抛异常，使用 `resourceUrlToLocalFile` 处理资源加载，使 4.0 测试与 3.5 行为一致。

### Parquet golden file 资源（多个 .parquet 文件）

**修改目的**：提供预生成的测试数据文件。

**工作逻辑**：在 `encodings/<ENCODING>/<type>.parquet` 路径下添加各编码和类型的预生成 Parquet 文件，作为黄金标准验证读取正确性。

## 总结

本提交将 Parquet 编码黄金文件测试 backport 到 Spark 3.5，通过共享 iceberg-parquet 模块的测试固件资源，验证 Iceberg 在多种 Parquet 编码和类型下的向量化/非向量化读取正确性。同时改进了资源加载方式以支持 JAR 内资源，并保持 Spark 4.0 测试的一致性。这增强了 Spark 3.5 的测试覆盖，确保 Parquet 读取在各种编码下的正确性。
