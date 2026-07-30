# 提交 3848：Flink: implement wakeup method to fix thread/memory leak (#16545)

## 提交信息

- **序号**：3848 / 4088
- **哈希**：4125f26d14c35db93480b825c020426ea9da2901
- **短哈希**：4125f26d1
- **日期**：2026-06-09 12:29:38 +0200
- **作者**：Chase Zhang
- **提交说明**：Flink: implement wakeup method to fix thread/memory leak (#16545)
- **PR/Issue**：#16545

## 总体目的

本提交修复了 Flink Iceberg Source 在取消任务时发生的线程和内存泄漏问题。问题的根源在于 `IcebergSourceSplitReader.wakeUp()` 方法是一个空实现（`{}`），当 Flink 在关闭/取消任务时调用 `wakeUp()` 试图解除 fetcher 线程的阻塞状态时，该调用被忽略，导致 fetcher 线程无法退出。

具体场景：当 Flink Source 使用 watermark alignment（水位对齐）时，`ArrayPoolDataIteratorBatcher` 内部的对象池（Pool）可能被耗尽，此时 `fetch()` 调用会在 `pool.pollEntry()` 处阻塞等待回收的数组。如果此时 Flink 发起关闭，由于 `wakeUp()` 是空实现，阻塞的线程永远不会被唤醒，导致线程泄漏和关联的内存泄漏。

本提交通过实现一个可唤醒的对象池 `PoolWithWakeup` 和 `WakeableIterator` 接口，让 `wakeUp()` 信号能传递到阻塞的 `pollEntry()` 调用，使线程能干净退出。

## 如何达成设计目的

整体设计分四部分：

1. **`PoolWithWakeup`**：替代 Flink 原生的 `Pool`，使用 `ReentrantLock` + `Condition` 实现可唤醒的阻塞 poll。`wakeUp()` 设置 `wokenUp` 标志并 signal，`pollEntry()` 检测到标志后返回 `null`（而非抛出中断异常），保持可重入性。

2. **`WakeableIterator` 接口**：扩展 `CloseableIterator`，新增 `wakeUp()` 方法。`ArrayPoolBatchIterator` 实现此接口，将 wakeUp 信号转发到 pool。

3. **`IcebergSourceSplitReader.wakeUp()`**：检查 `currentReader` 是否为 `WakeableIterator`，是则转发信号。`currentReader` 字段标记为 `volatile` 因为 `wakeUp()` 可能从不同线程调用。

4. **`ArrayBatchRecords.emptyBatch()`**：新增工厂方法返回空批次，当被唤醒时返回此空批次让 `fetch()` 交还控制权。

## 修改详情

### `flink/v2.1/flink/src/main/java/.../ArrayBatchRecords.java` (+8/-0 lines)

**修改目的**：新增空批次工厂方法。

**工作逻辑**：
```java
public static <T> ArrayBatchRecords<T> emptyBatch() {
  return new ArrayBatchRecords<>(null, null, null, 0, 0, 0, Collections.emptySet());
}
```
被唤醒时返回此空批次，无记录、无完成的 split，让 `fetch()` 正常返回。

### `flink/v2.1/flink/src/main/java/.../ArrayPoolDataIteratorBatcher.java` (+45/-14 lines)

**修改目的**：使用 `PoolWithWakeup` 替代 Flink `Pool`，实现可唤醒迭代器。

**工作逻辑**：

1. 字段类型从 `Pool<T[]>` 改为 `PoolWithWakeup<T[]>`。

2. `createPoolOfBatches()` 创建 `PoolWithWakeup` 而非 `Pool`。

3. `ArrayPoolBatchIterator` 实现 `WakeableIterator` 接口，新增 `wakeUp()` 方法转发到 pool：
```java
@Override
public void wakeUp() {
  pool.wakeUp();
}
```

4. `next()` 方法中检查 `getCachedEntry()` 返回值，若为 `null`（被唤醒）则返回空批次：
```java
T[] batch = getCachedEntry();
if (batch == null) {
  return ArrayBatchRecords.emptyBatch();
}
```

5. 新增 `setPoolForTesting()` 方法用于测试注入受控状态的 pool。

### `flink/v2.1/flink/src/main/java/.../IcebergSourceSplitReader.java` (+25/-1 lines)

**修改目的**：实现 `wakeUp()` 方法。

**工作逻辑**：

1. `currentReader` 字段标记为 `volatile`（因为 `wakeUp()` 可能从不同线程读取）。

2. 实现 `wakeUp()`：
```java
@Override
public void wakeUp() {
  if (currentReader instanceof WakeableIterator wakeableIterator) {
    wakeableIterator.wakeUp();
  }
}
```

3. 更新 `pauseOrResumeSplits()` 的注释，解释为何该方法保持空实现但 `wakeUp()` 不再为空。

### `flink/v2.1/flink/src/main/java/.../PoolWithWakeup.java` (+108/-0 lines, new file)

**修改目的**：可唤醒的对象池实现。

**工作逻辑**：

使用 `ReentrantLock` 和 `Condition` 实现线程安全的对象池：
- `pollEntry()`：循环等待直到有可用条目或被唤醒。被唤醒时消耗信号并返回 `null`。
- `wakeUp()`：设置 `wokenUp` 标志并 signal 唤醒等待线程。
- `addBack()` / `recycler()`：回收条目时 signal 唤醒等待线程。

关键设计：`wokenUp` 标志是"一次性"的——被 `pollEntry()` 消耗后重置为 `false`，保证可重入性（被唤醒后再次调用 `next()` 会正常阻塞）。

### `flink/v2.1/flink/src/main/java/.../WakeableIterator.java` (+35/-0 lines, new file)

**修改目的**：可唤醒迭代器接口。

**工作逻辑**：
```java
@Internal
interface WakeableIterator<T> extends CloseableIterator<T> {
  void wakeUp();
}
```

### `flink/v2.1/flink/src/test/java/.../TestArrayPoolDataIteratorBatcherWakeup.java` (+175/-0 lines, new file)

**修改目的**：测试 batcher 的唤醒行为。

**工作逻辑**：
三个测试：
1. `testWakeUpUnblocksExhaustedPoll`：pool 为空时 `next()` 阻塞，`wakeUp()` 后线程退出并返回空批次。
2. `testReentrantAfterWakeUp`：唤醒后再次 `next()` 仍会阻塞（信号已被消耗），证明可重入性。
3. `testNormalReadWhenEntryAvailable`：pool 有条目时正常返回真实批次。

### `flink/v2.1/flink/src/test/java/.../TestPoolWithWakeup.java` (+122/-0 lines, new file)

**修改目的**：测试 `PoolWithWakeup` 的并发行为。

**工作逻辑**：
四个测试：
1. `testPollReturnsAvailableEntry`：有条目时立即返回。
2. `testWakeUpUnblocksEmptyPoll`：空 pool 阻塞时 `wakeUp()` 返回 `null`。
3. `testRecycleUnblocksPoll`：阻塞时回收条目能唤醒并返回该条目。
4. `testWakeUpWithoutBlockedThreadReturnsNullOnce`：无线程阻塞时 `wakeUp()` 信号被锁存，下次 `pollEntry()` 消耗一次。

## 总结

本提交修复了 Flink Iceberg Source 在任务取消时的线程/内存泄漏问题。根因是 `wakeUp()` 空实现导致阻塞在对象池的 fetcher 线程无法退出。修复通过引入 `PoolWithWakeup`（基于 ReentrantLock/Condition 的可唤醒对象池）和 `WakeableIterator` 接口，让唤醒信号能传递到阻塞点，线程返回空批次后干净退出。设计保证了可重入性（唤醒信号一次性消耗），测试覆盖了并发场景下的各种行为。这是一个重要的稳定性修复，对长时间运行的 Flink 作业尤其关键。
