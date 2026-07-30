# 提交 1593：Spark: Fix empty scan issue when start timestamp retrieves root snapshot and end timestamp is missing (#11967)

## 提交信息

- **序号**：1593
- **哈希**：246439a966001a1d8c8f5109cb38011acb44d549
- **短哈希**：246439a96
- **日期**：2025-01-16（Thu Jan 16 09:47:59 2025 -0800）
- **作者**：Leon Lin <52585348+lliangyu-lin@users.noreply.github.com>
- **提交说明**：Spark: Fix empty scan issue when start timestamp retrieves root snapshot and end timestamp is missing (#11967)
- **共同作者**：Leon Lin <lliangyu@amazon.com>

## 总体目的

Iceberg 的 Spark 集成支持通过 `create_changelog_view` 存储过程生成增量变更日志视图，用户可指定 `start-timestamp`/`end-timestamp` 或 `start-snapshot-id`/`end-snapshot-id` 来界定变更区间。在 `SparkScanBuilder` 构建变更日志扫描时，需要根据时间戳推断起止 snapshot，并判断是否为"空扫描"（emptyScan）以短路返回空结果。

本提交修复一个 bug：当 `start-timestamp` 早于表的根快照（root snapshot，即第一个快照）的时间戳，且未指定 `end-timestamp` 时，扫描会被错误地判定为空扫描，导致用户明明应得到从根快照开始的全部变更，却得到空结果。

问题根源在于 `getStartSnapshotId(startTimestamp)` 的返回值语义：当 `startTimestamp` 早于根快照时间戳时，`SnapshotUtil.oldestAncestorAfter(table, startTimestamp)` 返回根快照，而根快照的 `parentId()` 为 `null`，于是 `getStartSnapshotId` 返回 `null`。原逻辑用 `startSnapshotId == null && endTimestamp == null` 作为"空扫描"判据，把这种"起点在根快照之前、应从头扫描"的合法场景误判为空。

修复后的判据改为：仅当表无任何快照（`currentSnapshot() == null`），或 `startTimestamp` 晚于当前（最新）快照时间戳（即起点之后没有任何快照可扫描）时，才判定为空扫描。同时移除原先依赖 `startSnapshotId == null` 的错误判据，使 v3.3/v3.4/v3.5 三个 Spark 版本逻辑统一。

此外，新增多个端到端测试用例覆盖各种起止参数组合，防止回归。

## 如何达成设计目的

### 核心逻辑修复

在 `SparkScanBuilder.buildChangelogScan()` 中，原本的空扫描判断为：

```java
if (startTimestamp != null) {
  startSnapshotId = getStartSnapshotId(startTimestamp);
  if (startSnapshotId == null && endTimestamp == null) {
    emptyScan = true;   // 错误：startTimestamp 早于根快照时 startSnapshotId 为 null，但不应为空
  }
}
```

v3.5 此前已有一段基于 `currentSnapshot().timestampMillis() < startTimestamp` 的判断，但仍保留了上述错误判据。本次修复统一三个版本为：

```java
if (startTimestamp != null) {
  if (table.currentSnapshot() == null
      || startTimestamp > table.currentSnapshot().timestampMillis()) {
    emptyScan = true;
  }
  startSnapshotId = getStartSnapshotId(startTimestamp);
}
```

语义：
- `table.currentSnapshot() == null`：表无快照，无可扫描内容，空扫描。
- `startTimestamp > table.currentSnapshot().timestampMillis()`：起点晚于最新快照，起点之后无快照，空扫描。
- 其余情况（含 `startTimestamp` 早于根快照）均不判为空。此时 `startSnapshotId` 可能为 `null`（根快照无父），下游 `if (startSnapshotId != null) { scan = scan.fromSnapshotExclusive(startSnapshotId); }` 会在 `null` 时不设置排他起点，即从根快照开始扫描，行为正确。

`getStartSnapshotId` 逻辑（未改动，用于理解）：
```java
private Long getStartSnapshotId(Long startTimestamp) {
  Snapshot oldestSnapshotAfter = SnapshotUtil.oldestAncestorAfter(table, startTimestamp);
  if (oldestSnapshotAfter == null) {
    return null;
  } else if (oldestSnapshotAfter.timestampMillis() == startTimestamp) {
    return oldestSnapshotAfter.snapshotId();
  } else {
    return oldestSnapshotAfter.parentId();  // 根快照的 parentId 为 null
  }
}
```

### 修改详情

#### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` 与 `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：修复 v3.3/v3.4 的空扫描误判。

**工作逻辑**：将 `startTimestamp != null` 分支替换为先判断空扫描（基于 `currentSnapshot` 是否为空、`startTimestamp` 是否晚于最新快照），再调用 `getStartSnapshotId`。移除原先 `startSnapshotId == null && endTimestamp == null` 的判据。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：统一 v3.5 与 v3.3/v3.4 逻辑，移除冗余且错误的判据。

**工作逻辑**：v3.5 原有两段判断：
1. `currentSnapshot() != null && currentSnapshot().timestampMillis() < startTimestamp` → emptyScan（正确方向，但条件写反为 `<`，等价于 `startTimestamp > currentSnapshot.timestampMillis()`）。
2. `startSnapshotId == null && endTimestamp == null` → emptyScan（错误）。

修复后合并为与 v3.3/v3.4 完全一致的单段判断，移除第二段错误判据，三个版本逻辑对齐。

#### `spark/v3.3|v3.4|v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCreateChangelogViewProcedure.java`

**修改目的**：覆盖各种起止参数组合，防止回归。

**工作逻辑**：每个 Spark 版本新增相同的 6 个测试用例：
1. `testOnlyStartSnapshotIdInput`：仅指定 `start-snapshot-id`（根快照），验证返回从其子快照开始的全部变更。
2. `testOnlyEndTimestampIdInput`：仅指定 `end-snapshot-id`，验证返回从根快照到该快照的变更。
3. `testOnlyStartTimestampInput`：仅指定 `start-timestamp`，分别用"早于根快照"和"早于第二个快照"两个时间点验证，前者应返回从根快照开始的全部变更（修复前的 bug 场景），后者返回从第二快照开始的变更。
4. `testOnlyEndTimestampInput`：仅指定 `end-timestamp`，验证返回到该时间点为止的变更。
5. `testStartTimeStampEndSnapshotId`：混合 `start-timestamp` + `end-snapshot-id`。
6. `testStartSnapshotIdEndTimestamp`：混合 `start-snapshot-id` + `end-timestamp`。

每个用例构造三次写入（INSERT、INSERT、INSERT OVERWRITE）产生三个快照 snap0/snap1/snap2，调用 `system.create_changelog_view` 后校验变更行（含 INSERT/DELETE、`_change_ordinal`、快照 ID）。其中 `testOnlyStartTimestampInput` 的第一个断言正是针对本 bug 的回归测试：`beginning` 早于 snap0 时间戳，应返回 snap0、snap1、snap2 的全部变更。

## 小结

- **成效**：修复了 `start-timestamp` 早于根快照且未指定 `end-timestamp` 时变更日志扫描误判为空的 bug，使该场景正确返回从根快照开始的全部变更；并统一了 v3.3/v3.4/v3.5 三个版本的空扫描判断逻辑。
- **影响范围**：3 个 `SparkScanBuilder.java`（v3.3/v3.4/v3.5）共约 13 行逻辑修改，3 个 `TestCreateChangelogViewProcedure.java` 共新增约 597 行测试代码（三个版本相同测试）。
- **回迁到 1.4.x 的注意事项**：1.4.x 支持 v3.3/v3.4，若存在相同 bug 建议回迁主代码修复与对应测试，提升增量变更日志的正确性。回迁时注意 v3.3/v3.4 的原始逻辑与 v3.5 不同（v3.3/v3.4 缺少 `currentSnapshot` 时间戳判断），需按本提交的方式统一。
