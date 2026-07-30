# 提交 3977：Flink: Hold back equality delete converter watermark until completion (#17038)

## 提交信息

- **序号**：3977 / 4088
- **哈希**：49b89a8c59d7d88290c6e925f41b65fa9fc99a88
- **短哈希**：49b89a8c5
- **日期**：2026-07-02 23:33:17 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Hold back equality delete converter watermark until completion (#17038)
- **PR/Issue**：#17038

## 总体目的

本提交修复了 Flink 表维护框架中 equality delete 转换器的并发安全问题，该问题导致 CI 测试 `TestConvertEqualityDeletesE2E` flaky。

问题的根因是：`EqualityConvertPlanner` 在执行过程中会发射阶段水印（phase watermarks），而 `EqualityConvertCommitter` 会转发这些水印。`LockRemover` 组件在水印超过任务开始时间戳时会释放维护锁。由于阶段水印在 cycle 完成前就被转发，锁被提前释放，导致 `TriggerManager` 启动了一个并发的 cycle，两个 cycle 同时处理同一个未提交的 staging snapshot。重叠的 commit 冲突导致其中一个 commit 在丢弃 deletion vector 时推进了 commit marker，引发测试断言失败（期望 2L 但得到 1L）。

解决方案是：committer 只在 cycle 完成（commit）后才转发水印，而不是在 cycle 执行中途转发，确保维护任务的互斥执行。

## 如何达成设计目的

重构 `EqualityConvertCommitter.processWatermark()` 方法，改变水印转发逻辑：
- **旧逻辑**：如果水印 >= plan 的 doneTimestamp，则执行 commit 并转发水印；否则也转发水印（防止下游停滞）。
- **新逻辑**：如果 planResult 为 null 或水印 < doneTimestamp，则**不转发**水印（hold back），直接返回。只有当水印 >= doneTimestamp 时，才执行 commit 并转发水印。

这样阶段水印（< doneTimestamp）不会到达 LockRemover，锁保持到 cycle 完成才释放。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertCommitter.java` (+26/-18 lines)

**修改目的**：延迟水印转发直到 cycle 完成。

**工作逻辑**：
```java
@Override
public void processWatermark(Watermark mark) throws Exception {
  if (planResult == null || mark.getTimestamp() < planResult.doneTimestamp()) {
    // Hold back watermarks until the cycle commits so the LockRemover keeps the maintenance lock
    // for the whole cycle.
    return;
  }
  try {
    commitIfNeeded();
  } catch (Exception e) {
    // ... error handling
  }
  // Emit Trigger for the Aggregator
  output.collect(new StreamRecord<>(Trigger.create(planResult.triggerTimestamp(), 0)));
  bufferedResults.clear();
  planResult = null;
  super.processWatermark(mark);  // 只在 commit 后才转发
}
```
同时更新类级 Javadoc，说明水印转发策略和原因。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertCommitter.java` (+45/-0 lines)

**修改目的**：验证水印延迟转发行为。

**工作逻辑**：新增 `holdsBackWatermarkUntilCommit` 测试：
1. 发送 plan result。
2. 发送 phase watermark（doneTs - 1，小于 doneTimestamp），验证无输出且无水印转发。
3. 发送 done-timestamp watermark（doneTs），验证此时才 commit（输出 1 个 Trigger）并转发水印。

还新增了 `watermarks()` 辅助方法从 test harness 输出中提取 Watermark 记录。

## 总结

本提交修复了一个微妙的并发安全问题：阶段水印提前释放维护锁导致 cycle 重叠执行。修复方案简洁——在 commit 完成前 hold back 水印——但根因分析深入，涉及 Flink 水印机制、维护锁和 commit 冲突的交互。这是一个重要的正确性修复，消除了 CI 测试 flakiness 并防止潜在的并发数据损坏。
