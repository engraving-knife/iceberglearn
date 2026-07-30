# 提交 3861：Core: Add EntryStatus.MODIFIED and TrackingBuilder status derivation (#16689)

## 提交信息

- **序号**：3861 / 4088
- **哈希**：cc7fec9f28c68aa182090ca71953f0674cc45a5d
- **短哈希**：cc7fec9f2
- **日期**：2026-06-11 14:27:11 -0700
- **作者**：Anoop Johnson
- **提交说明**：Core: Add EntryStatus.MODIFIED and TrackingBuilder status derivation (#16689)
- **PR/Issue**：#16689

## 总体目的

本提交为 Iceberg V4 规范引入了新的 `EntryStatus.MODIFIED` 状态，并重构了 `TrackingBuilder` 的状态推导逻辑。这是 Iceberg 行级溯源和追踪（tracking）功能演进的一部分。

在之前的实现中，清单条目状态只有 `EXISTING`、`ADDED`、`DELETED`、`REPLACED` 四种。当数据文件的 DV（删除向量）更新或清单文件的删除/替换位置变化时，条目状态保持为 `EXISTING`，只是附加了 `dvSnapshotId` 或 `deletedPositions`/`replacedPositions`。这种方式的问题是：无法区分"未变化的现存条目"和"已被修改的条目"，导致读取端无法正确判断是否需要重新处理。

本提交引入 `MODIFIED` 状态（ID=4），表示条目已被修改但仍然存活（live）。同时引入 `isLive()` 标志，`EXISTING`/`ADDED`/`MODIFIED` 为 live，`DELETED`/`REPLACED` 为非 live。`TrackingBuilder` 在调用 `dvUpdated()`、`deletedPositions()`、`replacedPositions()` 时自动将状态推导为 `MODIFIED`。

## 如何达成设计目的

整体设计分三部分：

1. **`EntryStatus` 枚举扩展**：新增 `MODIFIED(4, true)`，每个状态增加 `live` 布尔标志。`isLive()` 方法返回该标志。

2. **`Tracking` 接口更新**：`isLive()` 默认方法改为委托给 `status().isLive()`，更新文档说明。

3. **`TrackingBuilder` 状态推导**：`status` 字段从 final 改为可变。`dvUpdated()` 在状态为 `EXISTING` 时推导为 `MODIFIED`；`deletedPositions()` 和 `replacedPositions()` 直接推导为 `MODIFIED`（除非是 `ADDED`）。简化了互斥校验逻辑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/EntryStatus.java` (+22/-1 lines)

**修改目的**：新增 MODIFIED 状态和 isLive 标志。

**工作逻辑**：

1. 枚举值扩展，每个状态增加 `live` 标志：
```java
EXISTING(0, true),
ADDED(1, true),
DELETED(2, false),
REPLACED(3, false),  // 被 MODIFIED 替代的旧状态
MODIFIED(4, true);   // 新增：被修改但仍存活
```

2. 构造器新增 `live` 参数，新增 `isLive()` 方法。

### `core/src/main/java/org/apache/iceberg/Tracking.java` (+8/-2 lines)

**修改目的**：更新 isLive 委托和文档。

**工作逻辑**：
- `STATUS` 字段文档更新为包含 `4=modified`。
- `isLive()` 默认方法改为 `status().isLive()`（替代原来的 `status() == ADDED || status() == EXISTING`）。
- `snapshotId()` 文档更新为 "added, deleted, or replaced"。

### `core/src/main/java/org/apache/iceberg/TrackingBuilder.java` (+28/-28 lines)

**修改目的**：实现状态推导逻辑。

**工作逻辑**：

1. `status` 字段从 final 改为可变。

2. `dvUpdated()`：DV 更新时，若状态为 `EXISTING` 则推导为 `MODIFIED`：
```java
TrackingBuilder dvUpdated() {
  Preconditions.checkState(
      deletedPositions == null && replacedPositions == null,
      "Cannot mark DV updated on a manifest entry (deleted/replaced positions are set)");
  this.dvSnapshotId = newSnapshotId;
  if (status == EntryStatus.EXISTING) {
    this.status = EntryStatus.MODIFIED;
  }
  return this;
}
```

3. `deletedPositions()` 和 `replacedPositions()`：设置位置时直接推导为 `MODIFIED`，同时设置 `dvSnapshotId`。校验简化为只拒绝 `ADDED` 状态。

### `core/src/test/java/org/apache/iceberg/TestEntryStatus.java` (+10/-0 lines)

**修改目的**：测试 isLive 方法。

**工作逻辑**：
验证 `EXISTING`、`ADDED`、`MODIFIED` 为 live，`DELETED`、`REPLACED` 为非 live。

### `core/src/test/java/org/apache/iceberg/TestTrackingStruct.java` (+130/-72 lines)

**修改目的**：更新测试以匹配新的 MODIFIED 状态推导。

**工作逻辑**：

1. `testAddedWithSameCommitDvStaysAdded`（原 `testAddedBuilder`）：ADDED 条目 dvUpdated 后保持 ADDED（不推导为 MODIFIED，因为是同一次提交新增的）。

2. `testDvUpdatedProducesModifiedAndAdvancesDvSnapshotId`（原 `testExistingBuilderAllowsDvMutation`）：EXISTING 条目 dvUpdated 后变为 MODIFIED，snapshotId 保留原值，dvSnapshotId 前进到新提交。

3. `testDoNotInheritSequenceNumberForModifiedEntries`：MODIFIED 条目不继承序列号。

4. `testCarryForwardFromModifiedSourceChangesToExisting`：MODIFIED 源条目 carry-forward 后变为 EXISTING。

5. `testManifestDVPositionsProduceModified`：清单 DV 位置设置后产生 MODIFIED 状态。

6. 序列化往返测试更新：验证 MODIFIED 状态和 dvSnapshotId 正确序列化。

## 总结

本提交引入了 `EntryStatus.MODIFIED` 状态，使 Iceberg V4 能区分"未变化的现存条目"和"已修改的条目"。`TrackingBuilder` 自动推导状态，简化了调用方逻辑。这对行级溯源和 DV 更新场景的正确性至关重要——读取端可以基于 MODIFIED 状态判断是否需要重新处理条目。isLive 标志的引入也使存活判断更加清晰和可扩展。
