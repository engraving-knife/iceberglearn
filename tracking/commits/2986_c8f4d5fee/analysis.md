# 提交 2986：Spark: Add comet reader test (#14807)

## 提交信息

- **序号**：2986 / 4088
- **哈希**：c8f4d5fee230dc9f240149938ecad84a9a979b5e
- **短哈希**：c8f4d5fee
- **日期**：2025-12-09
- **作者**：pvary
- **提交说明**：Spark: Add comet reader test (#14807)
- **PR/Issue**：#14807

## 总体目的

Apache Iceberg 的 Spark 模块支持通过配置 `spark.sql.iceberg.parquet.reader-type` 选择不同的 Parquet 读取器实现。其中 `COMET` 读取器对应 Apache Comet 项目（基于 Arrow/GPU 加速的向量化执行引擎）的集成路径，与默认的 `VECTORIZED` 向量化读取器在底层读取链路上存在差异。

在此之前，Spark v4.0 模块下针对 Parquet 扫描的测试覆盖了默认向量化路径，但缺少一个专门验证 Comet 读取器在 Iceberg 端能正确工作的测试用例。这意味着如果 Comet 读取器与 Iceberg 的 `TestParquetScan` 测试基类所覆盖的扫描行为（如过滤、投影、分区裁剪等）出现回归，无法被现有测试及时捕获。

本提交通过新增 `TestParquetCometVectorizedScan` 测试类来填补这一缺口。该测试继承自 `TestParquetScan`，复用其全部扫描测试用例，仅在初始化阶段将 reader-type 切换为 `COMET`，从而以最小的代价为 Comet 读取器建立起与默认向量化读取器等价的回归保护网。

## 如何达成设计目的

设计思路非常简洁：利用已有的 `TestParquetScan` 测试基类所提供的完整扫描测试套件，通过子类化并覆盖 Spark 会话配置的方式，将读取器类型指向 `COMET`。新增的测试类位于 `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/` 目录下，仅 33 行，纯粹是测试基础设施的扩展，不涉及任何产品代码改动。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestParquetCometVectorizedScan.java` (+33/-0 lines)

**修改目的**：为 Spark v4.0 的 Comet Parquet 读取器新增扫描回归测试。

**工作逻辑**：

新增类 `TestParquetCometVectorizedScan` 继承自 `TestParquetScan`，并做了两处关键定制：

1. 在 `@BeforeAll` 静态方法 `setComet()` 中，通过 `ScanTestBase.spark.conf().set("spark.sql.iceberg.parquet.reader-type", "COMET")` 将整个测试套件所共享的 Spark 会话的 Parquet 读取器类型设置为 `COMET`。`@BeforeAll` 保证该配置在所有测试方法执行前生效一次。
2. 覆盖 `vectorized()` 方法返回 `true`，告知基类本测试走的是向量化路径（Comet 读取器本身也是向量化读取器），从而让基类中依赖 `vectorized()` 判断的测试用例按向量化语义执行。

由于继承了 `TestParquetScan`，父类的所有扫描测试（涉及过滤下推、列投影、分区扫描等场景）都会自动以 Comet 读取器重新执行一遍，实现对 Comet 集成路径的回归覆盖。

## 总结

本提交以极小的成本（一个 33 行的测试类）为 Spark v4.0 的 Comet Parquet 读取器建立了与默认向量化读取器对等的测试覆盖，能够在不影响产品代码的前提下，及时发现 Comet 读取路径相对于标准向量化路径的行为偏差，提升了 Comet 集成的稳定性保障。
