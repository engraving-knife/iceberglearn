# 提交 2508：Flink: Backport fix hash code comparison for requesting global statistics in DataStatisticsCoordinator (#13830)

## 提交信息

- **序号**：2508 / 4088
- **哈希**：0b599bc37e39b3893e443393136c7fddc8391d6d
- **短哈希**：0b599bc37
- **日期**：2025-08-16 09:19:08 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport fix hash code comparison for requesting global statistics in DataStatisticsCoordinator (#13830)
- **PR/Issue**：#13830（backport #13827）

## 总体目的

本提交是提交 2507 的 backport，将 `DataStatisticsCoordinator` 中哈希码比较逻辑的 bug 修复从 Flink 2.0 移植到 Flink 1.19 和 1.20 版本。

提交 2507 修复了 Flink 2.0 中 `DataStatisticsCoordinator` 的哈希码比较运算符方向错误（`!=` 应为 `==`），导致全局统计信息请求的跳过逻辑完全反转。为了保证三个 Flink 版本（1.19、1.20、2.0）的一致性，本提交将相同的修复和测试同步到 1.19 和 1.20 分支。

## 如何达成设计目的

与提交 2507 完全相同的修改：
1. 将比较运算符从 `!=` 改为 `==`。
2. 修正日志消息。
3. 新增 `testMultipleRequestGlobalStatisticsEvents` 测试。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java` (+2/-2 lines)

**修改目的**：修复哈希码比较逻辑。

**工作逻辑**：与提交 2507 相同，将 `event.signature() != globalStatistics.hashCode()` 改为 `event.signature() == globalStatistics.hashCode()`，并修正日志消息。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinator.java` (+53/-0 lines)

**修改目的**：验证修复后的行为，与提交 2507 的测试内容相同。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java` (+2/-2 lines)

**修改目的**：与 v1.19 相同的修复。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinator.java` (+53/-0 lines)

**修改目的**：与 v1.19 相同的测试。

## 总结

本提交是 Flink 2.0 bug 修复的 backport，确保三个 Flink 版本（1.19、1.20、2.0）在 DataStatisticsCoordinator 的哈希码比较逻辑上保持一致。这是一个重要的 bug 修复，因为原先的逻辑反转会导致全局统计信息在 subtask 持有过期数据时不更新、持有最新数据时重复发送，严重影响 shuffle 机制的效率和正确性。
