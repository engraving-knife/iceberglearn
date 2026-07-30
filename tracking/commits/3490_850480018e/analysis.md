# 提交 3490：Spark 4.1: Fix async microbatch plan bugs (#15670)

## 提交信息

- **序号**：3490 / 4088
- **哈希**：850480018ed371bfbc7c28a68fb0a21a21a23f6b
- **短哈希**：850480018e
- **日期**：2026-03-31 17:40:06 -0700
- **作者**：Ruijing Li
- **提交说明**：Spark 4.1: Fix async microbatch plan bugs (#15670)
- **PR/Issue**：#15670

## 总体目的

修复 Spark 4.1 中 `AsyncSparkMicroBatchPlanner` 的多个 bug。该类负责异步预取文件扫描任务到队列中，用于结构化流式读取。主要修复的问题包括：
1. **队列类型问题**：使用 `LinkedBlockingQueue`（FIFO）但需要 `pollFirst`/`peekFirst` 语义，应使用 `LinkedBlockingDeque`。
2. **tail 指针同步问题**：使用单独的 `volatile tail` 字段跟踪队列尾部，需要 synchronized 同步，存在竞态条件。改用 Deque 后可直接从队列获取尾部。
3. **Trigger.AvailableNow 预加载越界**：构造时预加载使用 `table().currentSnapshot()` 作为终止点，但对于 Trigger.AvailableNow，应使用 `lastOffsetForTriggerAvailableNow` 对应的 snapshot 作为预加载上限。
4. **fillQueue 失败未传播**：`fillQueueFailedThrowable` 未在 `planFiles` 循环条件中检查，导致队列填充失败后仍继续等待。
5. **snapshotId 比较错误**：`reachedAvailableNowCap` 原使用 `>=` 比较 snapshotId，但 snapshotId 不保证单调递增，应使用 `==`。

## 如何达成设计目的

1. 将 `LinkedBlockingQueue` 替换为 `LinkedBlockingDeque`，移除 `tail` 字段，改用 `queue.peekFirst()`/`pollFirst()` 和从队列快照获取尾部。
2. 移除 `planFiles` 和 `addMicroBatchToQueue` 中的 synchronized 块，因为 Deque 本身线程安全。
3. 新增 `initialPreloadEndSnapshot()` 方法，在 Trigger.AvailableNow 场景返回 `lastOffsetForTriggerAvailableNow` 的 snapshot。
4. 将 `reachedAvailableNowCap` 提取为 `@VisibleForTesting` 静态方法，使用 `==` 比较 snapshotId。
5. 在 `planFiles` 循环条件中加入 `fillQueueFailedThrowable == null` 检查，并在循环后抛出异常。
6. 新增单元测试 `TestAsyncSparkMicroBatchPlanner` 和集成测试。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/AsyncSparkMicroBatchPlanner.java` (+54/-38 lines)

**修改目的**：修复队列类型、tail 指针、Trigger.AvailableNow 预加载和错误传播问题。

**工作逻辑**：
- **队列类型**：`LinkedBlockingQueue` → `LinkedBlockingDeque`，`queue.add(elem)` → `queue.addLast(elem)`，`queue.poll(...)` → `queue.pollFirst(...)`，`queue.peek()` → `queue.peekFirst()`。
- **移除 tail 字段**：在 `latestOffset` 中改为从队列快照获取尾部 `queueSnapshot.get(queueSnapshot.size() - 1)`，移除 `volatile tail` 字段。
- **移除 synchronized**：`planFiles` 中移除 `synchronized(queue)` 块，`addMicroBatchToQueue` 中移除 synchronized 块。
- **Trigger.AvailableNow 预加载**：新增 `initialPreloadEndSnapshot()` 方法，`fillQueue(Snapshot, Pair)` 中将 `table().currentSnapshot()` 替换为 `initialPreloadEndSnapshot()`。
- **reachedAvailableNowCap**：提取为静态方法，将 `>=` 改为 `==`：
```java
static boolean reachedAvailableNowCap(
    Snapshot readFrom, StreamingOffset lastOffsetForTriggerAvailableNow) {
  return lastOffsetForTriggerAvailableNow != null
      && readFrom != null
      && readFrom.snapshotId() == lastOffsetForTriggerAvailableNow.snapshotId();
}
```
- **错误传播**：`planFiles` 循环条件加入 `fillQueueFailedThrowable == null`，循环后检查并抛出 `fillQueueFailedThrowable`。
- 构造函数注释说明 Trigger.AvailableNow 的预加载行为。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestAsyncSparkMicroBatchPlanner.java` (+61 lines, 新文件)

**修改目的**：单元测试 `reachedAvailableNowCap` 方法。

**工作逻辑**：
- `reachedAvailableNowCapReturnsTrueOnlyForExactCapSnapshot`：验证仅当 snapshotId 完全匹配时返回 true，更高或更低 ID 返回 false。
- `reachedAvailableNowCapReturnsFalseWhenCapOrSnapshotIsMissing`：验证 null 参数时返回 false。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (+161 lines)

**修改目的**：集成测试覆盖三个修复场景。

**工作逻辑**：
- `testTriggerAvailableNowCapsAsyncPreloadAfterPrepare`：验证 Trigger.AvailableNow 场景下，prepare 后新增的数据不会被预加载，latestOffset 返回的 offset 不超过 cap snapshot。
- `testLatestOffsetReturnsNullAfterFinalBatchIsConsumed`：验证所有 batch 消费完后 latestOffset 返回 null，且 batch 数量与文件数一致。
- `testPlanInputPartitionsIsIdempotentForSameOffsets`：验证对相同 offset 多次调用 planInputPartitions 返回相同的文件列表。
- 新增 `newMicroBatchStream` 辅助方法创建流实例。

## 总结

修复了 AsyncSparkMicroBatchPlanner 中的多个并发和逻辑 bug：将 Queue 改为 Deque 并移除 tail 指针同步以消除竞态条件；修复 Trigger.AvailableNow 预加载越界问题；修复 snapshotId 比较从 `>=` 改为 `==`；传播队列填充失败异常。新增单元测试和集成测试覆盖这些修复。
