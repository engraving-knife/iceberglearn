# 提交 1907：Spark 3.5: Reduce repeated logs in SparkWrite and SparkPositionDeltaWrite (#12404)

## 提交信息

- **序号**：1907 / 4088
- **哈希**：cb7d5a827ba162b0e7ad12dbb22d2e587ab50eb7
- **短哈希**：cb7d5a82
- **日期**：2025-03-24 07:59:57 +0100
- **作者**：Manu Zhang
- **提交说明**：Spark 3.5: Reduce repeated logs in SparkWrite and SparkPositionDeltaWrite (#12404)
- **PR/Issue**：#12404

## 总体目的

这个提交将 Spark 3.5 中 `SparkWrite` 和 `SparkPositionDeltaWrite` 的写分布和排序请求日志级别从 INFO 降为 DEBUG，以减少日志噪音。

在 Spark 写入 Iceberg 表时，Spark 会调用 Iceberg 写入器的 `requiredDistribution()`、`requiredOrdering()` 和 `advisoryPartitionSizeInBytes()` 方法来获取写入分布和排序要求。这些方法在每次调用时都会以 INFO 级别记录日志。由于 Spark 在查询规划阶段可能频繁调用这些方法，大量重复的 INFO 级别日志会淹没真正重要的信息，降低日志的可读性和实用性。

将这些日志降为 DEBUG 级别后，默认运行时不再输出这些重复信息，但在需要调试写入分布和排序问题时，仍可通过调整日志级别来查看。

## 如何达成设计目的

将 `SparkWrite` 和 `SparkPositionDeltaWrite` 中三处日志调用的 `LOG.info` 改为 `LOG.debug`。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (修改, +3/-3 lines)

**修改目的**：降低写入分布和排序请求的日志级别。

**工作逻辑**：将以下三处 `LOG.info` 改为 `LOG.debug`：
- `requiredDistribution()` 中的 "Requesting {} as write distribution for table {}"
- `requiredOrdering()` 中的 "Requesting {} as write ordering for table {}"
- `advisoryPartitionSizeInBytes()` 中的 "Requesting {} bytes advisory partition size for table {}"

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (修改, +3/-3 lines)

**修改目的**：与 SparkPositionDeltaWrite 相同的日志级别调整。

**工作逻辑**：同样将三处 `LOG.info` 改为 `LOG.debug`。

## 总结

本提交将 Spark 3.5 写入器中的写入分布和排序请求日志从 INFO 降为 DEBUG，减少正常运行时的日志噪音。涉及 6 处日志级别变更，是一个简单但实用的日志优化。
