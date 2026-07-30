# 提交 2491：Core: Batch load new files when validating replaced partitions (#13556)

## 提交信息

- **序号**：2491 / 4088
- **哈希**：159d2535346190af8ba1164427f9abae677ae91f
- **短哈希**：159d25353
- **日期**：2025-08-12 15:26:20 -0600
- **作者**：Gabriel Igliozzi
- **提交说明**：Core: Batch load new files when validating replaced partitions (#13556)
- **PR/Issue**：#13556

## 总体目的

本提交解决了 CherryPick 操作在验证被替换分区时的性能问题。在 `CherryPickOperation` 验证被替换分区的逻辑中，原先使用 `SnapshotUtil.newFiles` 方法加载新增数据文件，该方法将所有文件一次性加载到一个 `List` 中返回，无法利用并行加载和流式读取的优势。

当快照之间存在大量新增文件时，原有的 `newFiles` 方法会串行遍历快照并逐一加载文件 manifest，将所有 `DataFile` 收集到内存列表后再返回。这种做法存在两个问题：一是无法利用并行加载提高吞吐；二是将所有文件一次性加载到内存，可能造成内存压力。

本提交新增了 `SnapshotUtil.newFilesBetween` 方法，返回 `CloseableIterable<DataFile>`，使用 `ParallelIterable` 并行加载多个快照的文件，并支持流式迭代和资源关闭。同时将原 `newFiles` 方法标记为 `@Deprecated`，引导后续使用新方法。

## 如何达成设计目的

关键设计点：

1. **新增 `newFilesBetween` 方法**：返回 `CloseableIterable<DataFile>` 而非 `List<DataFile>`，支持惰性迭代和资源管理。
2. **并行加载**：使用 `ParallelIterable` 包装多个快照的 `snapshot.addedDataFiles(io)` 迭代器，通过 `ThreadPools.getWorkerPool()` 并行加载。
3. **资源管理**：调用方使用 try-with-resources 确保 `CloseableIterable` 正确关闭。
4. **验证逻辑保持不变**：仍然遍历新增文件检查是否落在被替换分区中，只是加载方式改为批量并行。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CherryPickOperation.java` (+14/-7 lines)

**修改目的**：将验证被替换分区时的文件加载方式从 `newFiles` 切换为 `newFilesBetween`。

**工作逻辑**：
- 原先直接调用 `SnapshotUtil.newFiles(parentId, meta.currentSnapshot().snapshotId(), meta::snapshot, io)` 返回 `List<DataFile>`。
- 改为使用 try-with-resources 包裹 `SnapshotUtil.newFilesBetween(...)` 返回的 `CloseableIterable<DataFile>`，确保迭代完成后资源被释放。
- 捕获 `IOException` 并包装为 `UncheckedIOException`，保持调用方法不需要声明受检异常。

### `core/src/main/java/org/apache/iceberg/util/SnapshotUtil.java` (+34/-0 lines)

**修改目的**：新增 `newFilesBetween` 方法实现并行批量加载，并标记原 `newFiles` 为废弃。

**工作逻辑**：
- `newFilesBetween` 接收起止快照 ID，通过 `ancestorsOf(endSnapshotId, lookup)` 从终点向起点遍历祖先快照链，收集到 `startSnapshotId` 为止。
- 验证最后一个快照的 ID 或 parent ID 与 startSnapshotId 匹配，确保快照区间有效。
- 使用 `ParallelIterable` 并行迭代各快照的 `addedDataFiles(io)`，利用工作线程池提升加载吞吐。
- 原 `newFiles` 方法添加 `@Deprecated` 注解，说明将在 2.0.0 移除。

## 总结

本提交通过引入 `newFilesBetween` 方法，将 CherryPick 操作中验证被替换分区时的文件加载改为并行批量模式，提升了大表场景下的验证性能。同时正确管理了资源关闭，并保留了向后兼容性。这是一个聚焦于性能优化的改动，对功能逻辑无影响。
