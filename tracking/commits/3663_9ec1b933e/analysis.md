# 提交 3663：Spark: Backport support writing shredded variant in Iceberg-Spark (#16241)

## 提交信息

- **序号**：3663 / 4088
- **哈希**：9ec1b933ed1d9253c6019773a624ba9b3d4d3c0a
- **短哈希**：9ec1b933e
- **日期**：2026-05-07 16:48:58 +0200
- **作者**：pvary
- **提交说明**：Spark: Backport support writing shredded variant in Iceberg-Spark (#16241)
- **PR/Issue**：#16241（backport #14297）

## 总体目的

这个提交将 PR #14297（即本系列第 3655 号提交，Variant shredding 写入支持）backport 到 Spark 4.0。

第 3655 号提交为 Spark 4.1 实现了 Variant 列的 shredding（拆分）写入能力：将 Variant 对象中高频字段拆分为强类型列存储，提升查询性能和压缩率。本 backport 将该功能扩展到 Spark 4.0，使两个 Spark 4.x 版本都能使用 variant shredding。

backport 仅包含 Spark 4.0 侧的文件（配置、format model、analyzer 适配器、测试），因为 core/parquet 模块的改动（`BufferedFileAppender`、`VariantShreddingAnalyzer`、`ParquetFormatModel` 等）已在原 PR 中提交，为两个版本共享。

## 如何达成设计目的

将 Spark 4.1 侧的 shredding 相关文件复制到 Spark 4.0 对应目录：
1. `SparkSQLProperties`：新增 `SHRED_VARIANTS` 和 `VARIANT_INFERENCE_BUFFER_SIZE` 配置键。
2. `SparkWriteConf`：新增 `shredVariants()` 和 `variantInferenceBufferSize()` 配置读取方法。
3. `SparkWriteOptions`：新增写入选项。
4. `SparkFormatModels`：接入 variant shredding format model。
5. `SparkVariantShreddingAnalyzer`：Spark 侧的 shredding analyzer 适配器。
6. 测试：`TestSparkWriteConf` 和 `TestVariantShredding`。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+8 lines)

**修改目的**：新增 Spark SQL 配置项 `spark.sql.iceberg.shred-variants` 和 `spark.sql.iceberg.variant-inference-buffer-size`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+30 lines)

**修改目的**：新增 `shredVariants()` 和 `variantInferenceBufferSize()` 方法，支持表属性、Spark SQL 配置、写入选项三级配置优先级。在 Parquet 写入属性中写入 shredding 配置。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java` (+6 lines)

**修改目的**：新增写入选项 `shred-variants` 和 `variant-inference-buffer-size`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkFormatModels.java` (+3/-1 lines)

**修改目的**：接入 variant shredding format model。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkVariantShreddingAnalyzer.java` (+69 lines, new)

**修改目的**：Spark 4.0 侧的 shredding analyzer 适配器，将 Spark Variant 值转换为通用 `VariantValue` 后委托给 `VariantShreddingAnalyzer`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java` (+85 lines)

**修改目的**：shredding 配置测试。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/variant/TestVariantShredding.java` (+1101 lines, new)

**修改目的**：端到端 shredding 写入测试。

## 总结

这个提交将 Variant shredding 写入支持 backport 到 Spark 4.0，使 Spark 4.0 和 4.1 都能使用该性能优化特性。backport 包含 Spark 4.0 侧的配置、format model 接入、analyzer 适配器和完整测试，core/parquet 模块的共享改动已在原 PR 中完成。至此 variant shredding 覆盖 Spark 4.0 和 4.1 两个版本。
