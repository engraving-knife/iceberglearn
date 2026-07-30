# 提交 3897：Kafka Connect: Add metric for partial commit failures (#16433)

## 提交信息

- **序号**：3897 / 4088
- **哈希**：de4f1ab1a4e2a41dc0543ed67211186989a2c1e0
- **短哈希**：de4f1ab1a
- **日期**：2026-06-17 19:48:16 +0200
- **作者**：Anupam Yadav
- **提交说明**：Kafka Connect: Add metric for partial commit failures (#16433)
- **PR/Issue**：#16433, Fixes #16392

## 总体目的

为 Kafka Connect 集成添加部分提交（partial commit）失败的可观测指标。当 Kafka Connect 的 Coordinator 在部分提交模式下发生失败时，之前只能通过日志观察到，缺乏可聚合统计的指标。Issue #16392 反映了需要跟踪部分提交失败次数的需求，以便监控和告警。

部分提交是 Kafka Connect Iceberg sink 的一种优化机制：当提交超时或部分分区数据就绪时，可以先提交已就绪的数据（partial commit），而非等待所有分区。但如果部分提交失败，需要重试，频繁的部分提交失败可能指示底层问题（如表锁竞争、网络问题等）。

## 如何达成设计目的

在 `Coordinator` 类中添加一个 `AtomicLong` 计数器 `partialCommitFailures`，在部分提交失败时递增，并暴露 `partialCommitFailureCount()` 方法供外部读取。同时添加单元测试验证计数器行为。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+7/-0 lines)

**修改目的**：添加部分提交失败计数器。

**工作逻辑**：
```java
private final AtomicLong partialCommitFailures = new AtomicLong();
```

在 `process()` 方法的 partial commit 异常处理中递增计数器：
```java
} catch (RuntimeException e) {
  if (partialCommit) {
    partialCommitFailures.incrementAndGet();
    LOG.warn("Partial commit {} failed for task {}, will retry", ...);
  }
}
```

暴露查询方法：
```java
long partialCommitFailureCount() {
  return partialCommitFailures.get();
}
```

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCoordinator.java` (+41/-0 lines)

**修改目的**：验证部分提交失败计数器。

**工作逻辑**：
新增 `testPartialCommitFailureMetric` 测试：
1. 配置 commitIntervalMs=0 和 commitTimeoutMs=0 触发立即提交
2. Mock table 的 `newAppend().commit()` 抛出 `CommitFailedException`
3. 第一次 process() 后断言计数器为 0（尚未有数据触发提交）
4. 模拟发送 DataWritten 响应到控制主题
5. 第二次 process() 后断言计数器为 1（部分提交失败被计数）

## 总结

为 Kafka Connect Coordinator 添加了部分提交失败计数器指标，使运维人员能够监控和告警部分提交失败的发生频率。实现简单有效，使用 AtomicLong 保证线程安全，并通过单元测试验证了计数器的正确递增行为。这是提升 Kafka Connect 集成可观测性的重要一步。
