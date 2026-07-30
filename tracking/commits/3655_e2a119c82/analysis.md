# 提交 3655：Spark: Support writing shredded variant in Iceberg-Spark (#14297)

## 提交信息

- **序号**：3655 / 4088
- **哈希**：e2a119c822b65114ff34e4bc4967967927256603
- **短哈希**：e2a119c82
- **日期**：2026-05-06 13:14:15 -0700
- **作者**：Aihua Xu
- **提交说明**：Spark: Support writing shredded variant in Iceberg-Spark (#14297)
- **PR/Issue**：#14297

## 总体目的

这个提交为 Iceberg-Spark（Spark 4.1）实现了 Variant 列的"shredding"（拆分）写入能力。

Variant 类型是 Iceberg v3 引入的半结构化数据类型，默认情况下 Variant 列以整体的 metadata+value 二进制形式存储在 Parquet 中。这种存储方式虽然灵活，但查询时需要解析整个 Variant 值，性能较差，且无法利用 Parquet 的列式压缩和谓词下推。

Variant shredding 是一种优化技术：将 Variant 对象中频繁出现的字段"拆分"出来，作为独立的强类型列存储在 Parquet 中（shredded columns），而未拆分的部分仍以整体 Variant 形式存储（unshredded/metadata+value）。这样查询这些常见字段时可以直接读取强类型列，显著提升查询性能和压缩率。

本提交实现了完整的 shredding 写入路径：通过缓冲前 N 行数据推断 shredding schema，然后据此创建带拆分列的 Parquet writer。仅支持 Spark 4.1（后续 backport 到 4.0，见第 3663 号提交）。

## 如何达成设计目的

1. 新增 `BufferedFileAppender`：一个通用的 FileAppender 装饰器，先缓冲前 N 行数据，再用缓冲的数据通过 factory 创建真正的 appender，并回放缓冲的行。这使得在创建 Parquet writer 之前可以先看到部分数据以推断 schema。
2. 新增 `VariantShreddingAnalyzer`：分析缓冲的 Variant 数据，通过启发式算法确定最优的 shredding schema（哪些字段拆分、拆分为什么类型）。具有确定性保证、类型提升（INT/DECIMAL 向宽类型提升）、频率剪枝、深度限制等特性。
3. 改造 `ParquetFormatModel`：支持 variant shredding 写入路径，通过 `withFileSchema` 传入推断的 schema。
4. 新增 `SparkVariantShreddingAnalyzer`：Spark 侧的适配器，桥接 Spark 的 Variant 值到通用的 analyzer。
5. 在 `SparkWriteConf` 新增配置项 `shredVariants()` 和 `variantInferenceBufferSize()`，支持表属性、Spark SQL 配置、写入选项三级配置。
6. 在 `TableProperties` 新增表属性 `write.parquet.shred-variants`（默认 false）和 `write.parquet.variant-inference-buffer-size`（默认 100）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+6 lines)

**修改目的**：新增 variant shredding 表属性。

**工作逻辑**：
```java
public static final String PARQUET_SHRED_VARIANTS = "write.parquet.shred-variants";
public static final boolean PARQUET_SHRED_VARIANTS_DEFAULT = false;
public static final String PARQUET_VARIANT_BUFFER_SIZE = "write.parquet.variant-inference-buffer-size";
public static final int PARQUET_VARIANT_BUFFER_SIZE_DEFAULT = 100;
```

### `core/src/main/java/org/apache/iceberg/io/BufferedFileAppender.java` (+149 lines, new)

**修改目的**：通用的缓冲型 FileAppender，支持延迟创建 delegate appender。

**工作逻辑**：
- 构造时接收 `bufferRowCount`、`appenderFactory`（根据缓冲行创建 delegate）和可选的 `copyFunc`（复制行，用于行对象复用场景如 Spark InternalRow）。
- `add()` 方法：若 delegate 已创建则直接委托；否则缓冲行（copy 后），缓冲满则调用 `initialize()` 创建 delegate 并回放缓冲行。
- `initialize()`：调用 `appenderFactory.apply(buffer)` 创建 delegate，然后回放所有缓冲行。
- `metrics()`/`length()`：若 delegate 为 null（无数据写入）返回空 Metrics/0；否则返回 delegate 的值。
- 若写入行数不足 bufferRowCount 即 close，仍以实际缓冲行调用 factory。

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantShreddingAnalyzer.java` (+532 lines, new)

**修改目的**：分析 Variant 数据推断 shredding schema 的核心算法。

**工作逻辑**：
- 遍历缓冲的 Variant 值，统计每个对象字段的出现频率和类型分布。
- 字段排序使用 TreeMap（字母序）保证确定性。
- 类型选择：取最常见类型，INT8/16/32/64 互相提升为最宽，DECIMAL4/8/16 互相提升为最宽；有平局时按 `TIE_BREAK_PRIORITY` 决定。
- 频率剪枝：低于 `MIN_FIELD_FREQUENCY` 的字段不拆分；超过 `MAX_SHREDDED_FIELDS` 时保留最高频字段。
- 递归深度限制 `MAX_SHREDDING_DEPTH`（默认 50）；中间字段数超过 `MAX_INTERMEDIATE_FIELDS`（默认 1000）时停止跟踪新字段以限制内存。
- 输出 Parquet 的 shredded schema（GroupType，含 shredded 字段 + metadata + value）。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetFormatModel.java` (+83/-10 lines)

**修改目的**：在 Parquet 格式模型中接入 variant shredding 写入路径。

**工作逻辑**：新增 `withFileSchema` 支持传入推断的 schema，当启用 shredding 时使用 `BufferedFileAppender` 包装 writer 创建过程，缓冲行后通过 `VariantShreddingAnalyzer` 推断 schema 再创建真正的 writer。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantWriters.java` (+52/-9 lines)

**修改目的**：支持 shredded variant 的 Parquet 写入器。

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantWriterBuilder.java` (+9/-3 lines)

**修改目的**：variant writer 构建器支持 shredded schema。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+8 lines)

**修改目的**：新增 Spark SQL 配置项。

**工作逻辑**：
```java
public static final String SHRED_VARIANTS = "spark.sql.iceberg.shred-variants";
public static final String VARIANT_INFERENCE_BUFFER_SIZE = "spark.sql.iceberg.variant-inference-buffer-size";
```

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+30 lines)

**修改目的**：新增 shredVariants() 和 variantInferenceBufferSize() 配置读取。

**工作逻辑**：两个方法都支持三级配置优先级：写入选项 > Spark SQL 配置 > 表属性 > 默认值。在 Parquet 写入属性构建时，将 shredding 配置写入 writeProperties。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java` (+6 lines)

**修改目的**：新增写入选项 `shred-variants` 和 `variant-inference-buffer-size`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkFormatModels.java` (+2/-2 lines)

**修改目的**：接入 variant shredding format model。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkVariantShreddingAnalyzer.java` (+69 lines, new)

**修改目的**：Spark 侧的 shredding analyzer 适配器。

**工作逻辑**：将 Spark 的 Variant 值转换为通用的 `VariantValue`，委托给 `VariantShreddingAnalyzer` 分析。

### 文档

- `docs/docs/configuration.md` (+2 lines)：新增表属性文档。
- `docs/docs/spark-configuration.md` (+4 lines)：新增 Spark SQL 配置和写入选项文档。

### 测试

- `core/src/test/java/org/apache/iceberg/io/TestBufferedFileAppender.java` (+227 lines, new)：BufferedFileAppender 单元测试。
- `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantShreddingAnalyzer.java` (+475 lines, new)：analyzer 算法测试。
- `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetDataWriter.java` (+210 lines)：shredded variant 写入测试。
- `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java` (+84 lines)：配置测试。
- `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/variant/TestVariantShredding.java` (+1101 lines, new)：端到端 shredding 写入测试。

## 总结

这个提交是 Iceberg Variant 类型支持的重要性能优化，实现了 Variant 列的 shredding 写入能力（Spark 4.1）。通过将 Variant 中高频字段拆分为强类型列存储，显著提升了查询性能和压缩率。实现上引入了通用的 `BufferedFileAppender`（延迟 writer 创建）和 `VariantShreddingAnalyzer`（基于缓冲数据推断 shredding schema 的启发式算法），并通过三级配置控制 shredding 行为。该特性默认关闭，用户可通过表属性、Spark SQL 配置或写入选项启用。后续通过 #16241 backport 到 Spark 4.0。
