# 提交 3773：Kafka Connect: Add end-to-end test for commit failure propagation (#16432)

## 提交信息

- **序号**：3773 / 4088
- **哈希**：4c767c14b02b7b48e0030c7da01b9f87d65dcad5
- **短哈希**：4c767c14b
- **日期**：2026-05-22 23:59:36 +0200
- **作者**：Anupam Yadav
- **提交说明**：Kafka Connect: Add end-to-end test for commit failure propagation (#16432)
- **PR/Issue**：#16432（Fixes #16380）

## 总体目的

这个提交为 Kafka Connect Iceberg Sink 添加了端到端的提交失败传播测试。Issue #16380 报告了当 Coordinator 线程因异常而意外终止时，`CommitterImpl.save()` 方法应该抛出 `NotRunningException` 而不是让失败被静默忽略。这两个测试用例验证了这一行为：

1. **提交失败传播**：当 `coordinator.process()` 抛出异常时，Coordinator 线程应捕获异常、设置终止状态、调用 stop，后续的 `save()` 调用应抛出 `NotRunningException`。
2. **启动失败传播**：当 `coordinator.start()` 抛出异常时，同样的传播机制应生效。

## 如何达成设计目的

通过模拟 Coordinator 的 `process()` 和 `start()` 方法抛出异常，验证 `CoordinatorThread` 正确处理异常并设置终止状态，然后通过反射注入终止的 CoordinatorThread 到 CommitterImpl 中，验证 `save()` 方法抛出正确的异常类型和消息。

## 修改详情

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCommitterImpl.java` (+51/-0 lines)

**修改目的**：新增两个端到端失败传播测试。

**工作逻辑**：

1. **`testCommitFailurePropagatesAsNotRunningException()`**：
   - 创建 mock 的 `Coordinator`，使 `process()` 抛出 `RuntimeException("commit failed")`。
   - 创建并启动 `CoordinatorThread`。
   - 使用 `verify(coordinator, timeout(1000)).stop()` 验证线程在异常后调用了 stop。
   - 验证 `coordinatorThread.isTerminated()` 为 true。
   - 通过反射将终止的 coordinatorThread 注入到 CommitterImpl 实例中。
   - 验证 `committer.save(Collections.emptyList())` 抛出 `NotRunningException`，消息包含 "Coordinator unexpectedly terminated"。

2. **`testStartFailurePropagatesAsNotRunningException()`**：
   - 类似逻辑，但模拟 `start()` 方法抛出异常。
   - 验证同样的失败传播机制。

两个测试都使用反射来注入 mock 的 CoordinatorThread，因为正常创建路径不易模拟这些失败场景。这是端到端测试，验证从 Coordinator 异常到 CommitterImpl 异常的完整传播链。

## 总结

这个提交通过添加两个端到端测试验证了 Kafka Connect Iceberg Sink 中提交失败的传播机制。当 Coordinator 线程因 `process()` 或 `start()` 异常而终止时，后续的 `save()` 调用应抛出 `NotRunningException`，确保失败不会被静默忽略。这些测试覆盖了 Issue #16380 报告的场景，提高了 Kafka Connect 集成的可靠性保证。
