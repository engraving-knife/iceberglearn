# 提交 3854：Flink: Backport implement wakeup method to fix thread/memory leak (#16745)

## 提交信息

- **序号**：3854 / 4088
- **哈希**：0440aa6837337a8109b892d81b27d42cf75348c9
- **短哈希**：0440aa683
- **日期**：2026-06-10 16:26:37 +0200
- **作者**：Chase Zhang
- **提交说明**：Flink: Backport implement wakeup method to fix thread/memory leak (#16745)
- **PR/Issue**：#16745（backports #16545）

## 总体目的

本提交是将提交 3848（#16545，Flink 2.1 的 wakeup 方法实现）向后移植（backport）到 Flink 1.20 和 2.0 版本。原提交修复了 Flink Iceberg Source 在任务取消时因 `wakeUp()` 空实现导致的线程和内存泄漏问题。

由于 Iceberg 同时维护多个 Flink 版本（1.20、2.0、2.1），bug 修复需要同步到所有受影响的版本。本提交将 3848 中引入的 `PoolWithWakeup`、`WakeableIterator`、`ArrayBatchRecords.emptyBatch()`、`IcebergSourceSplitReader.wakeUp()` 实现等变更应用到 Flink 1.20 和 2.0 模块。

## 如何达成设计目的

将 3848 提交中针对 `flink/v2.1/` 的所有修改，逐一复制到 `flink/v1.20/` 和 `flink/v2.0/` 对应的文件路径下。涉及的文件和变更内容与 3848 完全一致。

## 修改详情

### Flink 1.20 模块（7 个文件，+504/-14 lines）

以下文件的变更与提交 3848 中对应文件完全一致：

- `flink/v1.20/flink/src/main/java/.../ArrayBatchRecords.java` (+8)：新增 `emptyBatch()` 工厂方法。
- `flink/v1.20/flink/src/main/java/.../ArrayPoolDataIteratorBatcher.java` (+45/-14)：使用 `PoolWithWakeup`，实现 `WakeableIterator`，处理唤醒返回空批次。
- `flink/v1.20/flink/src/main/java/.../IcebergSourceSplitReader.java` (+25/-1)：`currentReader` 标记 volatile，实现 `wakeUp()`。
- `flink/v1.20/flink/src/main/java/.../PoolWithWakeup.java` (+108, new)：可唤醒对象池实现。
- `flink/v1.20/flink/src/main/java/.../WakeableIterator.java` (+35, new)：可唤醒迭代器接口。
- `flink/v1.20/flink/src/test/java/.../TestArrayPoolDataIteratorBatcherWakeup.java` (+175, new)：batcher 唤醒测试。
- `flink/v1.20/flink/src/test/java/.../TestPoolWithWakeup.java` (+122, new)：PoolWithWakeup 并发测试。

### Flink 2.0 模块（7 个文件，+504/-14 lines）

与 Flink 1.20 完全相同的变更，应用到 `flink/v2.0/` 路径下。

## 总结

这是提交 3848 的向后移植，将 Flink 2.1 的 wakeup 线程/内存泄漏修复同步到 Flink 1.20 和 2.0 版本。变更内容与原提交完全一致，确保所有受支持的 Flink 版本都获得该修复。这体现了 Iceberg 多版本维护策略中 bug 修复同步的重要性。
