# 提交 3568：Kafka Connect: Do not fail if no partitions assigned (#15955)

## 提交信息

- **序号**：3568 / 4088
- **哈希**：a675f88b0d8b1bc5b9f37bc8db304bd694cf5f18
- **短哈希**：a675f88b0
- **日期**：2026-04-21 11:08:50 -0700
- **作者**：kumarpritam863
- **提交说明**：Kafka Connect: Do not fail if no partitions assigned (#15955)
- **PR/Issue**：#15955

## 总体目的

该提交修复了 Kafka Connect Iceberg Sink 在没有分区分配时抛出异常导致失败的问题。在 Kafka Connect 的协调器（Coordinator）选举逻辑中，`containsFirstPartition` 方法用于确定哪个 task 持有第一个分区（作为 leader）。之前，当所有成员都没有分配任何分区时，该方法会抛出 `ConnectException("No partitions assigned, cannot determine leader")`，导致整个连接器失败。

在某些场景下（如分区重新分配期间、消费者组重平衡期间），可能短暂出现没有分区分配的情况。该提交将抛出异常的行为改为返回 false 并记录警告日志，使连接器能够优雅地处理这种暂时性状态而不是直接失败。此外，该提交还统一了日志消息中的 task 标识符格式，通过预计算的 `taskId` 字段替代每次拼接 `config.connectorName()` 和 `config.taskId()`。

## 如何达成设计目的

1. 将 `findFirstTopicPartition` 抽取为独立方法，当没有分区时返回 `null` 而非抛出异常。
2. `containsFirstPartition` 检查 `firstTopicPartition == null` 时返回 false 并记录警告日志。
3. 在 `CommitterImpl` 和 `Coordinator` 中引入 `taskId` 字段（`connectorName + "-" + taskId`），统一日志格式并避免重复拼接。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CommitterImpl.java` (+72/-32 lines)

**修改目的**：修复无分区时的异常抛出，优化日志。

**工作逻辑**：
- 移除 `ConnectException` 导入，新增 `taskId` 字段，在 `initialize` 中初始化为 `config.connectorName() + "-" + config.taskId()`。
- `containsFirstPartition` 方法重构：调用新方法 `findFirstTopicPartition` 获取第一个分区，若为 null 则记录警告日志并返回 false（不再抛出异常）；否则检查当前分区集合是否包含该分区，并记录不同级别的日志（info/debug）。
- 新增 `findFirstTopicPartition` 方法：从所有成员的分区中找到最小的 `TopicPartition`，使用 `orElse(null)` 而非 `orElseThrow`。
- 多处日志消息统一使用 `taskId` 字段替代 `config.connectorName()` + `config.taskId()` 的拼接。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+32/-12 lines)

**修改目的**：统一 Coordinator 的日志标识符。

**工作逻辑**：
- 新增 `taskId` 字段，在构造函数中初始化为 `config.connectorName() + "-" + config.taskId()`。
- 多处日志消息添加 `taskId` 前缀，包括 commit 发起、失败重试、完成、跳过空提交等场景。
- `commitToTable` 方法中移除了局部变量 `taskId` 的重复定义（改为使用字段）。
- 终止异常消息格式化为包含 `taskId`。

## 总结

该提交修复了 Kafka Connect Iceberg Sink 在无分区分配时的健壮性问题，将致命异常降级为警告日志，使连接器能够在分区重平衡等暂时性无分区场景下继续运行。同时通过预计算 `taskId` 优化了日志一致性和可读性，便于在多 task 环境中区分不同协调器的日志输出。
