# 提交 3359：Spark: Fix javadoc mentioning Spark 3.3 in Spark 3.4 benchmark classes (#15544)

## 提交信息

- **序号**：3359 / 4088
- **哈希**：9f58ec6c7a8b9dfac2d4d72b818d27210a8bc840
- **短哈希**：9f58ec6c7
- **日期**：2026-03-09
- **作者**：Anshul Baliga
- **提交说明**：Spark: Fix javadoc mentioning Spark 3.3 in Spark 3.4 benchmark classes (#15544)
- **PR/Issue**：#15544

## 总体目的

这是一个文档修复提交，用于纠正 Spark 3.4 模块下 JMH 基准测试类中的 Javadoc 注释。这些基准测试类位于 `spark/v3.4/spark/src/jmh/` 目录下，但其 Javadoc 中运行示例仍然引用的是 Spark 3.3 的命令（如 `./gradlew -DsparkVersions=3.3 :iceberg-spark:iceberg-spark-3.3_2.12:jmh`），而不是正确的 Spark 3.4 命令。

这种文档与代码位置不一致的问题会给开发者带来困扰：当开发者在 Spark 3.4 的 benchmark 类中查看如何运行该基准测试时，按照 Javadoc 中的指引执行命令实际上会去构建 Spark 3.3 的模块，导致运行的不是预期的基准测试，或者构建失败。Iceberg 项目为不同的 Spark 版本维护了独立的模块目录（`spark/v3.4`、`spark/v3.5`、`spark/v4.0`、`spark/v4.1`），每个目录下的代码应当引用对应版本的构建命令。本次提交覆盖了 26 个基准测试类文件，统一将 Javadoc 中的 `spark-3.3` / `3.3` 修正为 `spark-3.4` / `3.4`。

## 如何达成设计目的

对 `spark/v3.4` 模块下的所有 JMH 基准测试 Java 文件的 Javadoc 注释进行批量替换，将版本标识从 `3.3` 改为 `3.4`，同时修正 Gradle 命令中的模块名引用（如 `iceberg-spark-3.3_2.12` 改为 `iceberg-spark-3.4_2.12`）。每个文件的改动量都是 +2/-2 行，模式完全一致。

## 修改详情

以下对 26 个改动文件按类别归纳说明。所有文件的修改模式完全相同，即将类级 Javadoc 中的运行说明从 Spark 3.3 改为 Spark 3.4。

### `spark/v3.4/spark/src/jmh/java/org/apache/iceberg/spark/action/DeleteOrphanFilesBenchmark.java` (+2/-2 lines)

**修改目的**：修正 Javadoc 中的 Spark 版本引用。

**工作逻辑**：
将 Javadoc 中的运行示例从：
```
 * <p>To run this benchmark for spark-3.3: <code>
 *   ./gradlew -DsparkVersions=3.3 :iceberg-spark:iceberg-spark-3.3_2.12:jmh
```
改为：
```
 * <p>To run this benchmark for spark-3.4: <code>
 *   ./gradlew -DsparkVersions=3.4 :iceberg-spark:iceberg-spark-3.4_2.12:jmh
```

### `spark/v3.4/spark/src/jmh/java/org/apache/iceberg/spark/data/parquet/` 下 4 个文件 (+2/-2 lines each)

涉及文件：
- `SparkParquetReadersFlatDataBenchmark.java`
- `SparkParquetReadersNestedDataBenchmark.java`
- `SparkParquetWritersFlatDataBenchmark.java`
- `SparkParquetWritersNestedDataBenchmark.java`

**修改目的**：同上，修正 Parquet 读写基准测试的 Javadoc 版本引用。

### `spark/v3.4/spark/src/jmh/java/org/apache/iceberg/spark/source/avro/` 下 3 个文件 (+2/-2 lines each)

涉及文件：
- `AvroWritersBenchmark.java`
- `IcebergSourceFlatAvroDataReadBenchmark.java`
- `IcebergSourceNestedAvroDataReadBenchmark.java`

**修改目的**：同上，修正 Avro 读写基准测试的 Javadoc 版本引用。

### `spark/v3.4/spark/src/jmh/java/org/apache/iceberg/spark/source/orc/` 下 3 个文件 (+2/-2 lines each)

涉及文件：
- `IcebergSourceFlatORCDataReadBenchmark.java`
- `IcebergSourceNestedListORCDataWriteBenchmark.java`
- `IcebergSourceNestedORCDataReadBenchmark.java`

**修改目的**：同上，修正 ORC 读写基准测试的 Javadoc 版本引用。

### `spark/v3.4/spark/src/jmh/java/org/apache/iceberg/spark/source/parquet/` 下 14 个文件 (+2/-2 lines each)

涉及文件：
- `IcebergSourceFlatParquetDataFilterBenchmark.java`
- `IcebergSourceFlatParquetDataReadBenchmark.java`
- `IcebergSourceFlatParquetDataWriteBenchmark.java`
- `IcebergSourceNestedListParquetDataWriteBenchmark.java`
- `IcebergSourceNestedParquetDataFilterBenchmark.java`
- `IcebergSourceNestedParquetDataReadBenchmark.java`
- `IcebergSourceNestedParquetDataWriteBenchmark.java`
- `IcebergSourceParquetEqDeleteBenchmark.java`
- `IcebergSourceParquetMultiDeleteFileBenchmark.java`
- `IcebergSourceParquetPosDeleteBenchmark.java`
- `IcebergSourceParquetWithUnrelatedDeleteBenchmark.java`
- `ParquetWritersBenchmark.java`
- `VectorizedReadDictionaryEncodedFlatParquetDataBenchmark.java`
- `VectorizedReadFlatParquetDataBenchmark.java`
- `VectorizedReadParquetDecimalBenchmark.java`
- 以及 `parquet/vectorized/` 下的相关文件

**修改目的**：同上，修正 Parquet 相关基准测试（含向量化读取、删除文件等）的 Javadoc 版本引用。

## 总结

本次提交修复了 Spark 3.4 模块下全部 26 个 JMH 基准测试类的 Javadoc 注释，将错误的 Spark 3.3 版本引用统一更正为 Spark 3.4。这是一项纯文档质量改进，确保开发者在使用这些基准测试时能获得正确的运行命令，避免因文档误导而构建错误的 Spark 版本模块。
