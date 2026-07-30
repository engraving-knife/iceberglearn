# 提交 1410：Spark: Fix changelog table bug for start time older than current snapshot (#11564)

## 提交信息

- **序号**：1410 / 4088
- **哈希**：c448a4b87007ae8aad57456e19d32db45de3a4f6
- **短哈希**：c448a4b87
- **日期**：2024-11-20（Wed Nov 20 21:15:44 2024 -0500）
- **作者**：Ace Haidrey <acehaidrey@gmail.com>
- **提交说明**：Spark: Fix changelog table bug for start time older than current snapshot (#11564)
- **PR/Issue**：#11564

注意：PR 标题措辞为「start time older than current snapshot」，但根据代码与测试，实际描述的是反向场景：**用户传入的 `start-timestamp` 比当前快照时间还新**（即 changelog 时间窗口的起点在最新快照之后），此时应返回空扫描。下文按代码实际语义描述。

## 总体目的

Spark 3.5 的 `SparkScanBuilder` 在构建 changelog（增量变更日志）扫描时支持以 `start-timestamp` / `end-timestamp` 指定时间窗口。原有逻辑：

```java
boolean emptyScan = false;
if (startTimestamp != null) {
  startSnapshotId = getStartSnapshotId(startTimestamp);
  if (startSnapshotId == null && endTimestamp == null) {
    emptyScan = true;
  }
}

if (endTimestamp != null) {
  endSnapshotId = getEndSnapshotId(endTimestamp);
  if ((startSnapshotId == null && endSnapshotId == null)
      || (startSnapshotId != null && startSnapshotId.equals(endSnapshotId))) {
    emptyScan = true;
  }
}
```

`getStartSnapshotId(startTimestamp)` 调用 `SnapshotUtil.oldestAncestorAfter(table, startTimestamp)`：

- 若没有任何祖先快照的 timestamp 严格大于 `startTimestamp`（即 `startTimestamp` 比当前最新快照还新），返回 `null`。

Bug 场景：当 `startTimestamp > table.currentSnapshot().timestampMillis()`，且 `endTimestamp != null` 时：

1. `startSnapshotId = null`（因为 oldestAncestorAfter 找不到符合的快照）；
2. 第一个 `if` 的 `endTimestamp == null` 不成立，`emptyScan` 保持 false；
3. 进入第二个 `if`：`getEndSnapshotId(endTimestamp)` 在 `currentAncestors` 里找第一个 `timestampMillis() <= endTimestamp` 的快照——由于 `endTimestamp > startTimestamp > currentSnapshot.timestamp`，会返回**当前快照**作为 `endSnapshotId`；
4. `(startSnapshotId == null && endSnapshotId == null)` 不成立（`endSnapshotId` 非空），`(startSnapshotId != null && ...)` 也不成立（`startSnapshotId` 为 null），于是 `emptyScan` 仍为 false；
5. 后续 `scan = scan.toSnapshot(endSnapshotId)` 配置了到当前快照的扫描，但 `fromSnapshotExclusive` 未设置（因为 `startSnapshotId == null`），结果**返回从表起始到当前快照的所有变更**，而不是用户期望的空结果。

这与用户的语义期望不符：用户指定了一个完全在已有提交之后的时间窗口，理应没有变更，结果却拿到全表历史变更。

本提交的目的是：在进入 `startTimestamp` 分支时先判断「当前快照的提交时间是否早于 `startTimestamp`」，若是，则该时间窗口必然不覆盖任何已提交变更，直接置 `emptyScan = true`，避免后续逻辑误把当前快照当作窗口终点并回放历史。

## 如何达成设计目的

在 `SparkScanBuilder` 的 changelog 构建方法中，于 `if (startTimestamp != null)` 块的第一行加入短路判断：

```java
if (startTimestamp != null) {
  if (table.currentSnapshot() != null
      && table.currentSnapshot().timestampMillis() < startTimestamp) {
    emptyScan = true;
  }
  startSnapshotId = getStartSnapshotId(startTimestamp);
  if (startSnapshotId == null && endTimestamp == null) {
    emptyScan = true;
  }
}
```

- `table.currentSnapshot()` 可能为 null（空表），先做 null 检查避免 NPE；
- `currentSnapshot().timestampMillis() < startTimestamp` 表示当前最新快照早于窗口起点 → 窗口内不可能有任何已提交变更 → `emptyScan = true`。

后续 `getStartSnapshotId` 仍会执行（保持原有数据流，便于 endTimestamp 分支的判断），但由于 `emptyScan` 已被置 true，最终的 `SparkChangelogScan` 会以 `emptyScan = true` 构造，查询时直接返回空结果，不再回放历史变更。

注意：这里使用 `<` 而非 `<=`，因为如果 `currentSnapshot().timestampMillis() == startTimestamp`，`oldestAncestorAfter` 会找到该快照（其 timestamp 等于 startTimestamp，按 `oldestAncestorAfter` 的语义是「timestamp >= startTimestamp 的最早祖先」），后续 `getStartSnapshotId` 会返回该快照 ID 作为起点，changelog 会包含该快照的变更——这是合理的，不应被短路。所以只有「严格小于」才短路。

同时新增测试 `testChangelogViewOutsideTimeRange`，通过 `system.create_changelog_view` 存储过程构造该场景并断言返回空。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：在 changelog 时间窗口起点晚于当前快照时短路为空扫描。

**工作逻辑**：在 `if (startTimestamp != null)` 块开头插入：

```java
if (table.currentSnapshot() != null
    && table.currentSnapshot().timestampMillis() < startTimestamp) {
  emptyScan = true;
}
```

紧随其后的原有逻辑（`getStartSnapshotId`、`endTimestamp` 分支、`scan.fromSnapshotExclusive` / `scan.toSnapshot` 配置）保持不变。`emptyScan` 会传给 `new SparkChangelogScan(spark, table, scan, readConf, expectedSchema, filterExpressions, emptyScan)`，由 `SparkChangelogScan` 在执行时返回空批次。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestChangelogTable.java`

**修改目的**：回归覆盖「时间窗口完全在最新提交之后」的场景。

**工作逻辑**：新增 `testChangelogViewOutsideTimeRange`：

1. `createTableWithDefaultRows()` 创建表并写入默认行；
2. 两次 `INSERT INTO ... VALUES (3, 'c')` 与 `(4, 'd')` 触发两次提交（产生两个新快照）；
3. `validationCatalog.loadTable(tableIdent)` 加载表，记录 `insertSnapshot = table.currentSnapshot()`；
4. 记录 `beforeWindowTime = System.currentTimeMillis()`（在 inserts 之后）；
5. `Thread.sleep(100)` 保证后续时间戳与 `beforeWindowTime` 不重叠；
6. `startTime = System.currentTimeMillis()`、`endTime = startTime + 1000`，于是窗口 `[startTime, endTime]` 完全在所有 inserts 之后；
7. 调用 `system.create_changelog_view(table => '...', options => map('start-timestamp', '<startTime>', 'end-timestamp', '<endTime>'), changelog_view => 'test_changelog_view')`；
8. 查询 `SELECT * FROM test_changelog_view WHERE _change_type IN ('INSERT', 'DELETE') ORDER BY _change_ordinal`；
9. 断言 `results` 为空（`assertThat(results).as("Num records must be zero").isEmpty()`）——这正是修复前会失败、修复后通过的断言；
10. `DROP VIEW IF EXISTS test_changelog_view` 清理。

测试刻意使用 `Thread.sleep(100)` 让窗口起点严格晚于最近一次提交的 timestampMillis，从而触发新加的短路分支。

## 小结

- **成效**：当用户为 changelog 查询指定的 `start-timestamp` 晚于表当前快照的提交时间时，扫描现在返回空结果，而不是错误地回放从表起始到当前快照的全部变更。修复了「时间窗口在已有提交之后却返回历史变更」的语义错误。
- **影响范围**：仅 Spark 3.5 的 `SparkScanBuilder` 一处新增 4 行短路判断，加 1 个回归测试用例（约 48 行）。无 API 变更，无对正常时间窗口的影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 同样存在该 changelog 时间窗口逻辑缺陷，**建议回迁**。回迁要点：
  1. 确认 1.4.x 的 `SparkScanBuilder` 中对应方法结构一致（`startTimestamp`/`endTimestamp`/`getStartSnapshotId`/`getEndSnapshotId` 的相对位置）；
  2. 短路判断放在 `if (startTimestamp != null)` 块的第一行，先于 `getStartSnapshotId` 调用；
  3. 注意 `table.currentSnapshot()` 可能为 null（空表），必须先做 null 检查；
  4. 测试用到 `system.create_changelog_view` 存储过程，确认 1.4.x Spark 3.5 模块已具备该过程；
  5. 若 1.4.x 维护 Spark 3.3 / 3.4 也存在同样逻辑，应同步回迁（本提交只改 3.5，但缺陷可能跨版本）。
