# 提交 2597：Spark: backport PR #13993 to use loopback address for spark driver in tests (#13994)

## 提交信息

- **序号**：2597 / 4088
- **哈希**：173d4c32840e1be1efb559a174e8b97a0e62fb98
- **短哈希**：173d4c328
- **日期**：2025-09-04 12:34:46 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Spark: backport PR #13993 to use loopback address for spark driver in tests (#13994)
- **PR/Issue**：#13994（backport #13993）

## 总体目的

本次提交是将提交 2595（PR #13993）的改动 backport（移植）到 Spark v3.4 和 v3.5 版本的测试代码中。

PR #13993 最初只修改了 Spark v4.0 的测试代码，为 SparkSession 设置 `spark.driver.host` 为 loopback（回环）地址以解决严格防火墙环境下的测试连接问题。由于 Iceberg 同时维护 Spark 3.4、3.5 和 4.0 三个版本，相同的问题也存在于旧版本中，因此需要将相同的修改应用到 v3.4 和 v3.5。

## 如何达成设计目的

与 PR #13993 完全相同的修改方式，在 Spark v3.4 和 v3.5 的所有创建 SparkSession 的测试类中，添加 `.config("spark.driver.host", InetAddress.getLoopbackAddress().getHostAddress())` 配置。

涉及的文件结构与 v4.0 完全对应，包括 `ExtensionsTestBase` 以及多个 `Test*` 类（TestFilteredScan、TestForwardCompatibility、TestIcebergSpark、TestPartitionPruning、TestPartitionValues、TestSnapshotSelection、TestSparkDataFile、TestSparkDataWrite、TestSparkReadProjection、TestStructuredStreaming、TestWriteMetricsConfig）。

## 修改详情

### Spark v3.4 测试文件 (12 files, +64/-10 lines)

对 `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/ExtensionsTestBase.java` 及 `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/` 下的 11 个测试类执行相同修改：

**修改目的**：为 v3.4 的 SparkSession 设置 loopback driver host。

**工作逻辑**：在每个 `SparkSession.builder().master("local[2]")` 链中添加 `.config("spark.driver.host", InetAddress.getLoopbackAddress().getHostAddress())`，并导入 `java.net.InetAddress`。对于原本是单行 builder 的，改为多行格式以容纳新配置。

### Spark v3.5 测试文件 (12 files, +64/-10 lines)

对 `spark/v3.5/` 下对应的 12 个测试文件执行完全相同的修改。

## 总结

这是一个标准的 backport 提交，确保所有支持的 Spark 版本的测试代码保持一致。通过在 v3.4 和 v3.5 中应用与 v4.0 相同的 loopback 地址修改，保证开发者在任何 Spark 版本上运行测试时都不会遇到防火墙相关问题。
