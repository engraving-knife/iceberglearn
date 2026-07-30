# 提交 2461：Spark: Use ORC batch for orcBatchReadConf() (#13748)

## 提交信息

- **序号**：2461 / 4088
- **哈希**：270e2bc6fdb2e175b0bfaeaa07b4812e2ebf61c6
- **短哈希**：270e2bc6f
- **日期**：2025-08-06 07:54:22 +0200
- **作者**：hsieng-c
- **提交说明**：Spark: Use ORC batch for orcBatchReadConf() (#13748)
- **PR/Issue**：#13748

## 总体目的

该提交修复了 Spark 中 ORC 批量读取配置的一个错误：`orcBatchReadConf()` 方法错误地使用了 Parquet 的批量大小配置（`parquetBatchSize()`），而不是 ORC 自己的批量大小配置（`orcBatchSize()`）。

`SparkBatch` 类中的 `orcBatchReadConf()` 方法负责构建 ORC 批量读取的配置对象 `OrcBatchReadConf`。该配置中的 `batchSize` 参数应该控制 ORC 文件读取时的批次大小。然而，原代码错误地调用了 `readConf.parquetBatchSize()` 来获取批次大小，这意味着 ORC 读取实际上使用了 Parquet 的批量大小设置。

这是一个配置混淆 bug——用户通过 ORC 相关配置设置的批量大小不会生效，反而 Parquet 的配置会影响 ORC 读取行为。

## 如何达成设计目的

通过简单的一行修改，将 `readConf.parquetBatchSize()` 替换为 `readConf.orcBatchSize()`，确保 ORC 批量读取使用正确的配置项。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (+1/-1 lines)

**修改目的**：修复 Spark 3.4 中 ORC 批量读取配置使用错误的批次大小来源。

**工作逻辑**：
```java
// 修改前
private OrcBatchReadConf orcBatchReadConf() {
    return ImmutableOrcBatchReadConf.builder().batchSize(readConf.parquetBatchSize()).build();
}
// 修改后
private OrcBatchReadConf orcBatchReadConf() {
    return ImmutableOrcBatchReadConf.builder().batchSize(readConf.orcBatchSize()).build();
}
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (+1/-1 lines)

**修改目的**：对 Spark 3.5 应用相同的修复。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (+1/-1 lines)

**修改目的**：对 Spark 4.0 应用相同的修复。

## 总结

该提交修复了一个配置混淆 bug：`orcBatchReadConf()` 方法错误地使用了 `parquetBatchSize()` 而非 `orcBatchSize()`。这导致 ORC 文件读取时使用了 Parquet 的批量大小配置，而非 ORC 自己的配置。修复后，ORC 读取将正确使用 ORC 专用的批量大小设置。修改覆盖了 Spark 3.4、3.5 和 4.0 三个版本，虽然改动量小但修复了一个实际影响 ORC 读取性能的配置问题。
