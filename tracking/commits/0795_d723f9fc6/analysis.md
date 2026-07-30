# 提交 0795：Spark 3.5: Only traverse ancestors of current snapshot when building changelog scan (#10252)

## 提交信息
- **序号**：0795 / 4088
- **哈希**：d723f9fc63d0be21475c8ca23fa2aefc2115147e
- **短哈希**：d723f9fc6
- **日期**：2024-05-30 14:20:28 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Only traverse ancestors of current snapshot when building changelog scan (#10252)
- **PR/Issue**：#10252

## 总体目的

本提交修复了 Spark 3.5 中增量 changelog 扫描在表发生过回滚（rollback）后行为错误的缺陷。

Iceberg 的 changelog 扫描允许用户通过起始/结束时间戳指定一个变更区间，引擎内部需将结束时间戳解析为对应的快照 ID（`endSnapshotId`）。此前的实现调用 `SnapshotUtil.nullableSnapshotIdAsOfTime(table, endTimestamp)`，该方法遍历的是 `table.history()`——即表的完整提交历史。`table.history()` 包含了所有曾经发生过的快照提交记录，包括那些因 `rollback_to_snapshot` 或 `set_current_snapshot` 操作而脱离当前分支的快照。

当表发生过回滚时，当前快照分支与被回滚的分支出现了分叉。此时若结束时间戳落在被回滚分支的时间区间内，旧代码会将该分支上的快照选为 `endSnapshotId`，导致 changelog 扫描跨越不同分支遍历快照，产出不正确甚至语义上无意义的变更记录。

本提交将结束快照的查找逻辑改为仅遍历当前快照的祖先链（`SnapshotUtil.currentAncestors(table)`），确保选出的 `endSnapshotId` 始终位于当前分支上，与起始快照查找逻辑（`getStartSnapshotId` 已使用 `oldestAncestorAfter` 即基于当前祖先链）保持对称一致。

## 如何达成设计目的

提交者在 `SparkScanBuilder` 中新增私有方法 `getEndSnapshotId(Long endTimestamp)`，替换原来的 `SnapshotUtil.nullableSnapshotIdAsOfTime(table, endTimestamp)` 调用。

新方法的逻辑：遍历 `SnapshotUtil.currentAncestors(table)` 返回的快照迭代器（从当前快照向最老祖先逐个遍历），找到第一个 `timestampMillis() <= endTimestamp` 的快照即返回其 ID；若当前祖先链中没有满足条件的快照则返回 null。`currentAncestors` 底层通过 `ancestorsOf(table.currentSnapshot(), table::snapshot)` 实现，沿 `parentId` 链向上遍历，天然排除了被回滚分支上的快照。

这一改动使 start/end 两个端点的快照查找都基于"当前快照祖先链"，消除了此前 start 用祖先链、end 用全量 history 的不对称性。对于未发生回滚的普通表，`currentAncestors` 与 `history` 的结果一致，行为不变；仅在表存在分支分叉时，新逻辑才会产生与旧逻辑不同的结果（即正确地排除了非当前分支的快照）。

测试方面，新增 `testQueryWithRollback` 用例，构造了一个包含回滚与分支分叉的完整场景，覆盖了多种时间戳组合下的 changelog 查询预期。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`
**修改目的**：将结束快照查找从全量历史改为仅遍历当前快照祖先链。
**工作逻辑**：
- 在 `buildChangelogScan` 方法的 `if (endTimestamp != null)` 分支中，将 `endSnapshotId = SnapshotUtil.nullableSnapshotIdAsOfTime(table, endTimestamp)` 替换为 `endSnapshotId = getEndSnapshotId(endTimestamp)`。
- 新增 `getEndSnapshotId` 私有方法：
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
  `currentAncestors` 返回的迭代器从当前快照开始按提交时间降序排列，因此找到的第一个 `timestampMillis <= endTimestamp` 的快照即为结束时间戳对应时刻及之前最新的当前分支快照。
- 后续的空扫描判断逻辑（startSnapshotId 与 endSnapshotId 均为 null 或相等）保持不变。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestChangelogTable.java`
**修改目的**：验证回滚场景下 changelog 扫描仅遍历当前分支的祖先快照。
**工作逻辑**：
- 新增 `testQueryWithRollback` 测试，构造如下场景：
  1. snap1: INSERT (1, 'a')
  2. snap2: INSERT (2, 'b')
  3. `rollback_to_snapshot` 回滚到 snap1（snap2 脱离当前分支）
  4. snap3: INSERT OVERWRITE (-2, 'a')（snap3 的 parent 为 snap1）
- 验证四组查询：
  - changelog 至 snap3 时间点：应包含 snap1 的 INSERT(1,'a') 和 snap3 的 DELETE(1,'a')+INSERT(-2,'a')，不含 snap2。
  - changelog 至 snap2 时间点（此时 snap2 仍在 history 中但不在当前祖先链）：应只返回 snap1 的 INSERT，因为 snap2 不在当前分支上。
  - changelog 区间 (rightAfterSnap1, snap3.timestamp)：应只返回 snap3 的变更，snap2 在不同分支被排除。
  - changelog 区间 (rightAfterSnap2, null)：同样应只返回 snap3 的变更。
- 最后通过 `set_current_snapshot` 切换到 snap2，验证 changelog (rightAfterSnap1, null) 应只返回 snap2 的 INSERT(2,'b')，证明 snap3 此时不在当前分支上被正确排除。
- 测试充分覆盖了回滚后当前分支变更、跨分支时间戳排除、切换当前快照后分支变化等场景。

## 小结
- **成效**：修复了表回滚后 changelog 扫描可能选中非当前分支快照导致结果错误的问题，使 start/end 快照查找逻辑对称一致。
- **影响范围**：仅影响 Spark 3.5 中通过时间戳指定结束点的 changelog 扫描（`endTimestamp` 非空的场景）。对于未发生回滚的表无行为变化；对通过 `endSnapshotId` 直接指定快照的场景不受影响。
- **回迁注意事项**：1.4.x 回迁需确认 Spark 3.5 模块中 `SparkScanBuilder.buildChangelogScan` 的上下文与 main 一致。该方法依赖 `SnapshotUtil.currentAncestors(table)`，该方法在 1.4.x 中应已存在（位于 core 模块）。回迁时需同步带上 `testQueryWithRollback` 测试以验证。若 1.4.x 的 Spark 3.5 测试基类缺少 `rollback_to_snapshot`/`set_current_snapshot` 存储过程支持，需确认 catalog 配置支持这些操作。
