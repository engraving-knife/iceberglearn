# 提交 1998：Flink: Fix TriggerManager to unlock task execution when previous job left an orphaned lock

## 提交信息

- **序号**：1998 / 4088
- **哈希**：4587d0083ec1db3ae742a56bb7d97d47e9a93b70
- **短哈希**：4587d0083
- **日期**：2025-04-15 14:01:53 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Fix TriggerManager to unlock task execution when previous job left an orphaned lock (#12794)
- **PR/Issue**：#12794

## 总体目的

本提交修复了 Flink 维护模块中 `TriggerManager` 的一个重要 bug：当前一个 Flink 作业留下了孤立的锁（orphaned lock）时，新启动的作业无法获取锁来执行维护任务。

在 Flink 的维护（maintenance）框架中，`TriggerManager` 使用锁来协调维护任务的执行。当 Flink 作业正常关闭时，锁会被释放。但如果作业异常终止（如 crash、kill 等），锁可能未被释放，形成"孤立锁"。当新作业启动时，`TriggerManager.initialize()` 会获取锁实例，但此前如果锁已被占用，新的 `TriggerManager` 就无法执行任何维护任务，导致表维护停滞。

修复方案是：在 `TriggerManager.initialize()` 中，当检测到状态不是从检查点恢复（即全新启动）时，主动释放维护锁和恢复锁，清除可能残留的孤立锁。如果是状态恢复的情况，则不释放锁（因为恢复流程会在后续正确处理锁状态）。

## 如何达成设计目的

在 `TriggerManager.initialize()` 方法中，对 `context.isRestored()` 的判断逻辑添加 `else` 分支：当上下文不是从检查点恢复时（即新作业启动），主动调用 `lock.unlock()` 和 `recoveryLock.unlock()` 释放锁。这样，无论前一个作业是否正常释放了锁，新作业都能从干净的状态开始。

同时添加了对应的测试用例 `testNewJobReleasesExistingLock`，验证新作业能正确释放前一个作业遗留的孤立锁。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java` (修改, +3/-0 lines)

**修改目的**：在新作业启动时释放可能残留的孤立锁。

**工作逻辑**：
原代码：
```java
if (context.isRestored()) {
    shouldRestoreTasks = true;
}
```
修改为：
```java
if (context.isRestored()) {
    shouldRestoreTasks = true;
} else {
    lock.unlock();
    recoveryLock.unlock();
}
```
逻辑为：如果是状态恢复，设置 `shouldRestoreTasks = true`（保持原行为）；如果是全新启动，则释放维护锁和恢复锁，清除前一个作业可能遗留的孤立锁。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManager.java` (修改, +18/-0 lines)

**修改目的**：添加测试验证新作业释放孤立锁的行为。

**工作逻辑**：
新增 `testNewJobReleasesExistingLock()` 测试方法：
1. 先通过 `lock.tryLock()` 和 `recoveringLock.tryLock()` 模拟前一个作业遗留的孤立锁
2. 创建 `TriggerManager` 实例并通过 `testHarness.open()` 触发初始化
3. 断言新作业启动后 `lock.isHeld()` 和 `recoveringLock.isHeld()` 均为 `false`，确认孤立锁已被释放

## 总结

本提交修复了 `TriggerManager` 在前一个作业异常终止后遗留孤立锁导致新作业无法执行维护任务的 bug。通过在 `initialize()` 中对非恢复场景（全新启动）主动释放锁，确保新作业能从干净状态开始。同时添加了对应的测试用例验证此行为。
