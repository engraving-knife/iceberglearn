# 提交 2507：Flink: Fix hash code comparison for requesting global statistics in DataStatisticsCoordinator (#13827)

## 提交信息

- **序号**：2507 / 4088
- **哈希**：3685b5558fa9e1c3d9b96aebb5937462f4cd2d07
- **短哈希**：3685b5558
- **日期**：2025-08-15 20:45:09 -0700
- **作者**：GuoYu
- **提交说明**：Flink: Fix hash code comparison for requesting global statistics in DataStatisticsCoordinator (#13827)
- **PR/Issue**：#13827

## 总体目的

本提交修复了 Flink sink 中 `DataStatisticsCoordinator` 处理全局统计信息请求时的一处逻辑错误——哈希码比较运算符方向反了。

在 Iceberg Flink sink 的 shuffle 机制中，`DataStatisticsCoordinator` 负责收集各 subtask 的数据统计信息并聚合为全局统计信息。当某个 subtask 请求全局统计信息时，它可以在请求中携带一个 signature（即它当前持有的全局统计信息的 hashCode）。如果该 hashCode 与 coordinator 当前持有的全局统计信息 hashCode 相同，说明 subtask 已经拥有最新的全局统计信息，coordinator 可以跳过响应以节省网络开销。

原代码中的比较逻辑为：
```java
if (event.signature() != null && event.signature() != globalStatistics.hashCode())
```
这表示"当 signature 不为 null 且 hashCode **不相同**时跳过响应"——这与预期逻辑完全相反。正确的逻辑应该是"当 signature 不为 null 且 hashCode **相同**时跳过响应"。

本提交将 `!=` 修正为 `==`，并同步修正了日志消息以反映正确的语义。

## 如何达成设计目的

1. 将比较运算符从 `!=` 改为 `==`，使跳过响应的条件变为"hashCode 相同"。
2. 修正日志消息，从"as hashCode matches or not included in the request"改为"as the operator task already holds the same global statistics"，更准确地描述跳过原因。
3. 新增测试 `testMultipleRequestGlobalStatisticsEvents` 覆盖三种场景：signature 为 null、signature 匹配（应跳过）、signature 不匹配（应响应）。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java` (+2/-2 lines)

**修改目的**：修复哈希码比较逻辑。

**工作逻辑**：
- 将 `event.signature() != globalStatistics.hashCode()` 改为 `event.signature() == globalStatistics.hashCode()`。
- 将日志消息从 "Skip responding to statistics request from subtask {}, as hashCode matches or not included in the request" 改为 "Skip responding to statistics request from subtask {}, as the operator task already holds the same global statistics"。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinator.java` (+53/-0 lines)

**修改目的**：验证修复后的行为。

**工作逻辑**：新增 `testMultipleRequestGlobalStatisticsEvents` 测试，覆盖三种场景：
1. signature 为 null（无签名）：coordinator 应响应并返回全局统计信息。
2. signature 等于当前 hashCode（已持有最新）：coordinator 应跳过响应，sentEvents 数量不变。
3. signature 不等于当前 hashCode（持有过期的）：coordinator 应响应并返回最新统计信息。

## 总结

本提交修复了一个逻辑反转 bug，该 bug 导致当 subtask 持有**过期**的全局统计信息时 coordinator 反而跳过响应，而持有**最新**信息时却重复发送——完全与预期相反。这会导致不必要的网络传输和可能的统计信息不一致。修复后，coordinator 正确地仅在 subtask 已持有最新信息时跳过响应，提升了 shuffle 机制的效率和正确性。测试覆盖了三种关键场景，确保修复的正确性。
