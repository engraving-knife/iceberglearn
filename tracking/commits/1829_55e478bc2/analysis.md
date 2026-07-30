# 提交 1829：Data: Expose snapshot-id instead of branch for computing partition stats (#12464)

## 提交信息

- **序号**：1829 / 4088
- **哈希**：55e478bc226487d92ba7ce6ff713199a10f73893
- **短哈希**：55e478bc2
- **日期**：2025-03-06 18:28:33 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Data: Expose snapshot-id instead of branch for computing partition stats (#12464)
- **PR/Issue**：#12464

## 总体目的

本提交修改了 `PartitionStatsHandler` 中计算分区统计信息的接口，将原来基于"分支（branch）"选取快照的方式改为直接暴露"快照 ID（snapshot-id）"。在此之前，`computeAndWriteStatsFile(Table table, String branch)` 方法通过 `SnapshotUtil.latestSnapshot(table, branch)` 根据分支名查找最新快照，这种方式存在两个问题：一是分支名是一个间接且不稳定的选择维度，调用方真正需要的是某个具体快照的统计；二是当传入非 main 分支但找不到快照时，方法会抛出 `IllegalArgumentException`，错误处理不够清晰。

改为 snapshot-id 后，调用方直接指定要计算统计的快照，语义更加明确。无参重载方法 `computeAndWriteStatsFile(Table table)` 现在直接使用 `table.currentSnapshot().snapshotId()`，并在当前快照为 null 时返回 null，行为更加直观。这种设计也使得分区统计的计算可以针对任意历史快照进行，而不仅仅是某个分支的最新快照，为后续的统计复用和审计提供了更大的灵活性。

## 如何达成设计目的

整体思路是将 `String branch` 参数替换为 `long snapshotId` 参数，并在方法内部用 `table.snapshot(snapshotId)` 取代 `SnapshotUtil.latestSnapshot(table, branch)`。无参重载方法负责从 `table.currentSnapshot()` 取得 snapshotId 再委托给带参方法。同时移除了对 `SnapshotRef` 和 `SnapshotUtil` 的导入依赖，简化了错误处理逻辑：找不到快照时统一抛出 "Snapshot not found: %s" 的 `IllegalArgumentException`。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/PartitionStatsHandler.java` (修改, 30 lines)

**修改目的**：将 `computeAndWriteStatsFile` 的参数从 `String branch` 改为 `long snapshotId`，并用 `table.snapshot(snapshotId)` 直接查找快照。

**工作逻辑**：

1. 无参重载 `computeAndWriteStatsFile(Table table)`：原来调用 `computeAndWriteStatsFile(table, SnapshotRef.MAIN_BRANCH)`，现在改为先检查 `table.currentSnapshot() == null` 则返回 null，否则调用 `computeAndWriteStatsFile(table, table.currentSnapshot().snapshotId())`。这避免了在无快照表上做无意义的分支查找。

2. 带参方法签名从 `computeAndWriteStatsFile(Table table, String branch)` 改为 `computeAndWriteStatsFile(Table table, long snapshotId)`，文档注释相应从 "for a given table and branch" 改为 "for a given table and snapshot"。

3. 方法体内部：原来通过 `SnapshotUtil.latestSnapshot(table, branch)` 查找快照，并对 null 快照做分支合法性校验（非 main 分支找不到快照时抛异常）；现在改为 `Snapshot snapshot = table.snapshot(snapshotId)`，并用 `Preconditions.checkArgument(snapshot != null, "Snapshot not found: %s", snapshotId)` 做简单校验。后续 `PartitionStatsUtil.computeStats` 和 `writePartitionStatsFile` 调用从使用 `currentSnapshot` 改为使用 `snapshot`。

4. 移除了 `org.apache.iceberg.SnapshotRef` 和 `org.apache.iceberg.util.SnapshotUtil` 两个不再需要的导入。

### `data/src/test/java/org/apache/iceberg/data/TestPartitionStatsHandler.java` (修改, 8 lines)

**修改目的**：适配新的 snapshot-id 接口，更新测试用例。

**工作逻辑**：

1. `testPartitionStatsOnEmptyBranch`：原来调用 `computeAndWriteStatsFile(testTable, "b1")`，现在改为先通过 `testTable.refs().get("b1").snapshotId()` 取得分支 b1 的快照 ID，再调用 `computeAndWriteStatsFile(testTable, branchSnapshot)`，仍断言结果为 null。

2. `testPartitionStatsOnInvalidSnapshot`：原来传入无效分支名 "INVALID_BRANCH"，期望异常消息为 "Couldn't find the snapshot for the branch INVALID_BRANCH"；现在传入不存在的快照 ID `42L`，期望异常消息为 "Snapshot not found: 42"。这验证了新的错误处理路径。

## 小结

本提交通过将分区统计接口从"按分支查找快照"改为"直接传入快照 ID"，使 API 语义更清晰、调用方控制更精确，同时简化了内部错误处理。改动范围小且聚焦，涉及 data 模块的两个文件。回迁到 1.4.x 时需注意：1.4.x 中若已有调用方使用了旧的 branch 重载，需要同步改为 snapshot-id 方式；该改动是 API 行为变更，需检查所有 `PartitionStatsHandler.computeAndWriteStatsFile` 的调用点。
