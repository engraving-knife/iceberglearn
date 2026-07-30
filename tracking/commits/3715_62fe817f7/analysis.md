# 提交 3715：Spark 4.1: Add session configs for adaptive split sizing and parallelism (#16088)

## 提交信息

- **序号**：3715 / 4088
- **哈希**：62fe817f7459a904944f5b902867db61816635ee
- **短哈希**：62fe817f7
- **日期**：2026-05-14 19:30:28 -0500
- **作者**：Karuppayya
- **提交说明**：Spark 4.1: Add session configs for adaptive split sizing and parallelism (#16088)
- **PR/Issue**：#16088

## 总体目的

这个提交为 Spark 4.1 模块的 adaptive split sizing（自适应分片大小）功能添加了会话级别的配置覆盖能力。Iceberg 的 adaptive split sizing 功能可以根据扫描的数据总量和并行度自动调整分片大小，以优化查询性能。

此前，adaptive split sizing 的配置只能通过表属性（table properties）设置，用户无法在不修改表属性的情况下针对特定会话或查询进行覆盖。这在多租户或临时调优场景下不够灵活。

此提交添加了两个 Spark 会话级配置：
1. `spark.sql.iceberg.read.adaptive-split-size.enabled`：控制是否启用 adaptive split sizing
2. `spark.sql.iceberg.read.adaptive-split-size.parallelism`：覆盖自适应分片大小计算使用的并行度

同时改进了 `SparkScan.adjustSplitSize` 方法的日志输出，便于调试。

## 如何达成设计目的

通过以下修改实现：
1. 在 `SparkSQLProperties` 中定义新的会话级配置属性键
2. 在 `SparkReadConf` 中添加 `.sessionConf()` 链接到现有配置解析，使会话级配置能覆盖表属性
3. 新增 `splitParallelism()` 方法获取并行度配置
4. 修改 `SparkScan.adjustSplitSize` 使用新的 `splitParallelism()` 并添加日志

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+9 lines)

**修改目的**：定义新的会话级配置属性键。

**工作逻辑**：

```java
// Controls whether adaptive split sizing is enabled
public static final String READ_ADAPTIVE_SPLIT_SIZE_ENABLED =
    "spark.sql.iceberg.read.adaptive-split-size.enabled";

// Overrides the parallelism used for adaptive split sizing. When unset, the parallelism
// defaults to max(spark.default.parallelism, spark.sql.shuffle.partitions).
public static final String READ_ADAPTIVE_SPLIT_SIZE_PARALLELISM =
    "spark.sql.iceberg.read.adaptive-split-size.parallelism";
```

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+12 lines)

**修改目的**：添加会话级配置支持。

**工作逻辑**：

1. 为 `adaptiveSplitSizeEnabled()` 添加 `.sessionConf()` 链接：
```java
public boolean adaptiveSplitSizeEnabled() {
  return confParser
      .booleanConf()
+     .sessionConf(SparkSQLProperties.READ_ADAPTIVE_SPLIT_SIZE_ENABLED)
      .tableProperty(TableProperties.ADAPTIVE_SPLIT_SIZE_ENABLED)
      .defaultValue(TableProperties.ADAPTIVE_SPLIT_SIZE_ENABLED_DEFAULT)
      .parse();
}
```

2. 新增 `splitParallelism()` 方法：
```java
public int splitParallelism() {
  int parallelism =
      confParser
          .intConf()
          .sessionConf(SparkSQLProperties.READ_ADAPTIVE_SPLIT_SIZE_PARALLELISM)
          .defaultValue(parallelism())
          .parse();
  Preconditions.checkArgument(parallelism > 0, "Split parallelism must be > 0: %s", parallelism);
  return parallelism;
}
```

当会话级配置未设置时，默认使用 `parallelism()`（即 `max(spark.default.parallelism, spark.sql.shuffle.partitions)`）。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java` (+11/-2 lines)

**修改目的**：使用新的 splitParallelism 并添加日志。

**工作逻辑**：

```java
protected long adjustSplitSize(List<? extends ScanTask> tasks, long splitSize) {
  if (readConf.splitSizeOption() == null && readConf.adaptiveSplitSizeEnabled()) {
    long scanSize = tasks.stream().mapToLong(ScanTask::sizeBytes).sum();
-   int parallelism = readConf.parallelism();
-   return TableScanUtil.adjustSplitSize(scanSize, parallelism, splitSize);
+   int parallelism = readConf.splitParallelism();
+   long adjustedSplitSize = TableScanUtil.adjustSplitSize(scanSize, parallelism, splitSize);
+   if (adjustedSplitSize != splitSize) {
+     LOG.debug(
+         "Adjusted split size from {} to {} for table {} with parallelism {}",
+         splitSize, adjustedSplitSize, table().name(), parallelism);
+   }
+   return adjustedSplitSize;
  }
```

将 `readConf.parallelism()` 替换为 `readConf.splitParallelism()`，并添加 DEBUG 日志记录分片大小调整。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkReadConf.java` (+95 lines)

**修改目的**：添加测试验证会话级配置。

## 总结

这是一个 Spark 4.1 功能增强提交，为 adaptive split sizing 功能添加了会话级配置覆盖能力。用户现在可以通过 Spark SQL 会话配置临时调整自适应分片大小和并行度，无需修改表属性。这在多租户和临时调优场景下提供了更大的灵活性。同时改进了日志输出便于调试。
