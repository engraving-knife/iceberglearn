# 提交 2402：Core: backport PR #13100 from 1.9 to main branch that partially revert 12670 by sending single snapshot rather than in bulk (#13647)

## 提交信息

- **序号**：2402 / 4088
- **哈希**：7d40d328a8ddc06795ec931b63fda3f3f98875ef
- **短哈希**：7d40d328a
- **日期**：2025-07-24 09:33:02 +0200
- **作者**：Steven Zhen Wu
- **提交说明**：Core: backport PR #13100 from 1.9 to main branch that partially revert 12670 by sending single snapshot rather than in bulk (#13647)
- **PR/Issue**：#13647（backport #13100，部分回退 #12670）

## 总体目的

此提交将 PR #13100 从 1.9 分支 backport 到 main 分支，部分回退了 PR #12670 的变更。核心变更是**在 `TableMetadata` 的快照移除（remove snapshots）操作中，恢复为逐个发送 `RemoveSnapshots` 更新事件，而非批量发送**。

PR #12670 此前将快照移除逻辑改为批量发送——先收集所有要移除的快照 ID 到一个 Set 中，最后统一创建一个 `MetadataUpdate.RemoveSnapshots(snapshotIdsToRemove)` 事件。然而这种批量方式在某些场景下存在问题（如快照移除通知的消费者需要逐个处理），因此 PR #13100 将其回退为逐个发送 `RemoveSnapshots(snapshotId)` 事件的方式。

## 如何达成设计目的

通过修改 `TableMetadata.Builder.rewriteSnapshotsInternal` 方法，将快照移除从批量模式改为逐个模式：

1. **移除批量收集**：删除 `Set<Long> snapshotIdsToRemove` 变量及其收集逻辑。
2. **逐个发送更新**：在每个快照被移除时，立即 `changes.add(new MetadataUpdate.RemoveSnapshots(snapshotId))`。
3. **移除批量提交**：删除循环后的批量 `changes.add(new MetadataUpdate.RemoveSnapshots(snapshotIdsToRemove))` 代码块。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (+1/-7 lines)

**修改目的**：将快照移除从批量发送改为逐个发送。

**工作逻辑**：在 `rewriteSnapshotsInternal` 方法中：
- 移除 `Set<Long> snapshotIdsToRemove = Sets.newHashSet()` 声明。
- 在循环内，当快照被移除且 `!suppress` 时，直接 `changes.add(new MetadataUpdate.RemoveSnapshots(snapshotId))`，而非将 ID 添加到 Set 中。
- 移除循环后的 `if (!snapshotIdsToRemove.isEmpty()) { changes.add(new MetadataUpdate.RemoveSnapshots(snapshotIdsToRemove)); }` 代码块。

这样，每个被移除的快照都会产生一个独立的 `RemoveSnapshots` 更新事件，而非所有快照合并为一个事件。

## 总结

此提交部分回退了 PR #12670 的批量快照移除优化，恢复为逐个发送 `RemoveSnapshots` 更新事件的方式。这是一个从 1.9 分支 backport 到 main 分支的修复，确保快照移除事件的消费者能逐个处理每个被移除的快照。修改简洁，仅涉及 `TableMetadata` 中 7 行代码的删减和 1 行新增。
