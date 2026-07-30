# 提交 0799：Spark 3.4 构建变更日志扫描时仅遍历当前快照的祖先

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 0799 |
| 完整哈希 | 6a594546b06df9fb75dd7e9713a8dc173e67c870 |
| 短哈希 | 6a594546b |
| 日期 | 2024-06-01 09:12:54 -0700（北京时间 2024-06-02 00:12:54 +0800）|
| 作者 | Manu Zhang |
| 提交说明 | Spark 3.4: Only traverse ancestors of current snapshot when building changelog scan (#10405) |
| PR/Issue | PR #10405 |

## 总体目的

本提交修复 Spark 3.4 中 Iceberg 变更日志（changelog）扫描在表经历过 `rollback_to_snapshot` 或 `set_current_snapshot` 等切换分支操作后的一个正确性缺陷。

具体问题：`SparkScanBuilder` 在根据 `end-timestamp`（结束时间戳）确定变更日志扫描的结束快照时，原实现调用 `SnapshotUtil.nullableSnapshotIdAsOfTime(table, endTimestamp)`，该方法是遍历 `table.history()`（即表的历史日志，记录所有曾经成为过当前快照的快照，包括已被回滚到其他分支的快照）。当表存在分支切换时，`table.history()` 中可能包含不在当前快照祖先链上的快照。这样确定的结束快照可能落在另一条分支上，导致变更日志扫描跨越不同分支，产生错误的结果（要么包含本不应包含的变更，要么在底层扫描时因为快照不在祖先链上而抛错或行为异常）。

修复方式：新增私有方法 `getEndSnapshotId(endTimestamp)`，改为遍历 `SnapshotUtil.currentAncestors(table)`（仅当前快照的祖先链），从中找出时间戳 <= endTimestamp 的最新快照作为结束快照。这保证了结束快照一定在当前快照的祖先链上，从而避免了跨分支扫描的问题。

同时新增测试 `testQueryWithRollback` 覆盖回滚 + INSERT OVERWRITE + set_current_snapshot 的场景，验证修复后变更日志在各个时间区间下的正确性。

## 如何达成设计目的

### 1. 原实现的缺陷分析

原 `buildChangelogScan` 中确定结束快照的代码：

```java
if (endTimestamp != null) {
    endSnapshotId = SnapshotUtil.nullableSnapshotIdAsOfTime(table, endTimestamp);
    ...
}
```

`SnapshotUtil.nullableSnapshotIdAsOfTime` 的实现：

```java
public static Long nullableSnapshotIdAsOfTime(Table table, long timestampMillis) {
    Long snapshotId = null;
    for (HistoryEntry logEntry : table.history()) {
        if (logEntry.timestampMillis() <= timestampMillis) {
            snapshotId = logEntry.snapshotId();
        }
    }
    return snapshotId;
}
```

关键点：`table.history()` 返回的是 `HistoryEntry` 列表，记录的是"曾经成为当前快照"的所有快照（按时间顺序）。当发生 `rollback_to_snapshot` 后再 commit 新快照时，`table.history()` 会同时包含被回滚分支上的快照和新分支上的快照。因此 `nullableSnapshotIdAsOfTime` 可能返回一个不在当前快照祖先链上的快照 id。

举例（与新增测试 `testQueryWithRollback` 一致）：
1. `INSERT (1,'a')` → snap1（current=snap1）
2. `INSERT (2,'b')` → snap2（parent=snap1，current=snap2）
3. `rollback_to_snapshot(snap1)` → current 回到 snap1
4. `INSERT OVERWRITE (-2,'a')` → snap3（parent=snap1，current=snap3）

此时 `table.history()` ≈ [snap1, snap2, snap1, snap3]（每次 current 变化都记一条），而当前快照 snap3 的祖先链只有 [snap3, snap1]。snap2 不在当前祖先链上。

若用户查询 `end-timestamp = rightAfterSnap2`：
- 原实现：遍历 history，snap2 的时间戳 <= rightAfterSnap2，返回 snap2。但 snap2 不在当前祖先链上，后续 `scan.toSnapshot(snap2)` 会试图扫描一条不在当前分支上的快照，结果错误。
- 修复后：遍历 currentAncestors [snap3, snap1]，snap3 时间戳 > rightAfterSnap2 跳过，snap1 时间戳 <= rightAfterSnap2，返回 snap1。snap1 在当前祖先链上，正确。

### 2. 修复实现

新增私有方法 `getEndSnapshotId`：

```java
private Long getEndSnapshotId(Long endTimestamp) {
    Long endSnapshotId = null;
    for (Snapshot snapshot : SnapshotUtil.currentAncestors(table)) {
        if (snapshot.timestampMillis() <= endTimestamp) {
            endSnapshotId = snapshot.snapshotId();
            break;
        }
    }
    return endSnapshotId;
}
```

工作逻辑：
- `SnapshotUtil.currentAncestors(table)` 返回从当前快照开始沿 `parentId` 向上遍历的祖先链迭代器（包含当前快照本身），顺序是从新到旧（current → parent → ... → root）。
- 由于迭代顺序是从新到旧，第一个满足 `timestampMillis() <= endTimestamp` 的快照就是"当前祖先链上时间戳 <= endTimestamp 的最新快照"，立即 break 返回。
- 若祖先链上所有快照的时间戳都 > endTimestamp（即 endTimestamp 早于表第一个快照），则返回 null，与原 `nullableSnapshotIdAsOfTime` 在找不到时返回 null 的语义一致。

在 `buildChangelogScan` 中把对 `SnapshotUtil.nullableSnapshotIdAsOfTime(table, endTimestamp)` 的调用替换为 `getEndSnapshotId(endTimestamp)`，其余逻辑（startSnapshotId 计算、空扫描判定、`fromSnapshotExclusive` / `toSnapshot` 调用）保持不变。

### 3. 与 startSnapshotId 计算的一致性

值得注意的是，`startSnapshotId` 的计算原本就使用 `getStartSnapshotId(startTimestamp)`，而后者调用的是 `SnapshotUtil.oldestAncestorAfter(table, startTimestamp)`，该方法内部遍历的也是 `currentAncestors(table)`。因此本修复使 endSnapshotId 的计算与 startSnapshotId 在遍历范围上保持一致——都只在当前快照的祖先链上查找，避免了 start/end 一个在祖先链、一个在 history 上的不对称问题。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`（修改）

1. 在 `buildChangelogScan` 方法中，将结束快照的确定方式由：
   ```java
   endSnapshotId = SnapshotUtil.nullableSnapshotIdAsOfTime(table, endTimestamp);
   ```
   改为：
   ```java
   endSnapshotId = getEndSnapshotId(endTimestamp);
   ```

2. 新增私有方法 `getEndSnapshotId(Long endTimestamp)`：
   - 遍历 `SnapshotUtil.currentAncestors(table)`，返回第一个时间戳 <= endTimestamp 的快照 id；找不到返回 null。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestChangelogTable.java`（修改）

新增测试方法 `testQueryWithRollback`，覆盖回滚 + 覆盖写入 + 切换当前快照的场景。测试步骤与断言如下：

1. 建表，`INSERT (1,'a')` → snap1，记录 `rightAfterSnap1`。
2. `INSERT (2,'b')` → snap2，记录 `rightAfterSnap2`。
3. `CALL system.rollback_to_snapshot(snap1)`，断言 current = snap1。
4. `INSERT OVERWRITE (-2,'a')` → snap3，记录 `rightAfterSnap3`。

然后分 5 组断言：

| 断言 | start-ts | end-ts | 期望变更行 | 含义 |
|------|----------|--------|-----------|------|
| 1 | null | rightAfterSnap3 | (1,'a',INSERT,0,snap1)、(1,'a',DELETE,1,snap3)、(-2,'a',INSERT,1,snap3) | 截止 snap3 的累计变更，snap2 因不在当前祖先链被排除 |
| 2 | null | rightAfterSnap2 | (1,'a',INSERT,0,snap1) | end-ts 落在 snap2 时间段，但 snap2 不在当前祖先链，结束快照回退到 snap1 |
| 3 | rightAfterSnap1 | snap3.timestampMillis() | (1,'a',DELETE,0,snap3)、(-2,'a',INSERT,0,snap3) | 仅 snap3 的变更，change ordinal 从 0 开始 |
| 4 | rightAfterSnap2 | null | (1,'a',DELETE,0,snap3)、(-2,'a',INSERT,0,snap3) | 从 snap1（exclusive）到当前 snap3，仅 snap3 变更 |
| 5（set_current_snapshot 到 snap2 后） | rightAfterSnap1 | null | (2,'b',INSERT,0,snap2) | 当前切换到 snap2 分支后，仅返回 snap2 的变更，snap3 不在当前祖先链被排除 |

这些断言全面覆盖了"结束快照在另一分支""起始快照与结束快照在不同分支"等关键边界，验证修复后变更日志扫描严格限定在当前快照的祖先链上。

## 小结

- **成效**：修复了 Spark 3.4 变更日志扫描在表经历 `rollback_to_snapshot` / `set_current_snapshot` 后，因结束快照可能落在非当前分支而导致的正确性问题。修复后结束快照的确定与起始快照一样，都严格遍历当前快照的祖先链，保证变更日志只包含当前分支上的变更。
- **影响范围**：仅影响 Spark 3.4 模块的 `SparkScanBuilder.buildChangelogScan` 中基于 `end-timestamp` 确定结束快照的路径。对基于 `end-snapshot-id` 的显式指定路径无影响；对其他 Spark 版本（3.3、3.5）理论上存在同样问题但本提交未一并修改。
- **回迁到 1.4.x 分支的注意事项**：
  - 这是一个正确性 bug 修复，建议回迁到 1.4.x 分支（前提是 1.4.x 分支同样支持 Spark 3.4 且存在同样的代码路径）。
  - 回迁时需确认 1.4.x 分支的 `SparkScanBuilder` 中 `buildChangelogScan` 方法结构与 main 一致（即同样调用 `SnapshotUtil.nullableSnapshotIdAsOfTime`），否则需手工对齐。
  - 需确认 1.4.x 分支的 `SnapshotUtil.currentAncestors`、`table.history()` 语义与 main 一致（这两个方法较为稳定，通常一致）。
  - 测试 `testQueryWithRollback` 依赖 `system.rollback_to_snapshot` 和 `system.set_current_snapshot` 两个 procedure，需确认 1.4.x 分支已支持这两个 procedure；同时测试用到的 `changelogRecords` 辅助方法、`waitUntilAfter` 等需在 1.4.x 测试基类中存在。
  - 若 1.4.x 同时维护 Spark 3.3 / 3.5，可考虑将同样的修复同步到对应版本的 `SparkScanBuilder`（本提交只改了 3.4）。
