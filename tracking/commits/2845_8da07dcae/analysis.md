# 提交 2845：Merge control topic and last persisted offsets (#14525)

## 提交信息

- **序号**：2845 / 4088
- **哈希**：8da07dcae8ccf5ce1a0c61a7456413c1ce3b65fd
- **短哈希**：8da07dcae
- **日期**：2025-11-07 07:55:15 -0800
- **作者**：Daniel Weeks
- **提交说明**：Merge control topic and last persisted offsets (#14525)
- **PR/Issue**：#14525

## 总体目的

Iceberg Kafka Connect 的 Coordinator 在提交快照时，会把"控制 topic 的消费 offset"以 JSON 形式写入快照 summary 属性（`snapshotOffsetsProp`），记录"已提交到哪一步"。这个 offset 信息用于崩溃恢复与Exactly-Once 语义——重启后从该 offset 继续。

问题在于：`controlTopicOffsets()` 只包含**本次提交周期内实际有记录**的分区的 offset。如果某个分区在本次周期内没有记录，它就不会出现在 `controlTopicOffsets()` 返回的 map 中。于是新快照的 offsets JSON 只包含部分分区，**覆盖性地丢失了之前已提交的其他分区的 offset 进度**。下次重启时，丢失分区的 offset 进度就找不到了，可能导致重复消费或无法正确定位。

该提交在写快照前，把 `controlTopicOffsets()`（本次新进度）与 `lastCommittedOffsetsForTable(table, branch)`（该表上次已提交的全部 offset）合并：对每个分区取两者 offset 的较大值（`Long::max`），确保新快照的 offsets JSON 始终包含所有曾提交过分区的最新进度，不因某分区本轮无记录而丢失。

## 如何达成设计目的

1. 把 `offsetsJson()`（无参，直接序列化 `controlTopicOffsets()`）重构为 `offsetsToJson(Map<Integer, Long> offsets)`，接受任意 offsets map 序列化。
2. `commitToTable` 的入参由 `String offsetsJson` 改为 `Map<Integer, Long> controlTopicOffsets`（原始 map），把 JSON 序列化时机后移到合并之后。
3. 在 `commitToTable` 内部，读取 `committedOffsets = lastCommittedOffsetsForTable(table, branch)`（上次已提交），用 `Stream.of(committedOffsets, controlTopicOffsets).flatMap(...).collect(Collectors.toMap(key, value, Long::max))` 合并两份 map，对同一分区取较大 offset；再 `offsetsJson = offsetsToJson(mergedOffsets)` 序列化。
4. `doCommit` 调用从传 `offsetsJson` 改为传 `controlTopicOffsets()`。
5. 新增测试 `testCoordinatorCommittedOffsetMerging`：先提交一个只含 `{"1":7}` 的快照，再触发一次含 partition 0 数据的提交，断言新快照的 offsets 为 `{"0":3,"1":7}`（partition 1 的进度被保留）。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+14/-6 lines)

**修改目的**：合并本轮控制 topic offset 与上次已提交 offset，避免丢失分区进度。

**工作逻辑**：
- `doCommit`：移除 `String offsetsJson = offsetsJson();`，改为在 `run` 中直接传 `controlTopicOffsets()`（原始 map）给 `commitToTable`。
- `offsetsJson()` → `offsetsToJson(Map<Integer, Long> offsets)`：接受任意 map 序列化为 JSON。
- `commitToTable(..., Map<Integer, Long> controlTopicOffsets, ...)`：
  - 读取 `committedOffsets = lastCommittedOffsetsForTable(table, branch)`。
  - 合并：`Stream.of(committedOffsets, controlTopicOffsets).flatMap(map -> map.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::max))`。对同一分区取 `Long::max`，保证取较新（较大）的 offset。
  - `String offsetsJson = offsetsToJson(mergedOffsets);` 序列化合并后的 map。
  - 后续 append/rowDelta 用这个 `offsetsJson` 写入 `snapshotOffsetsProp`。
- 注释说明：control topic 分区 offset 可能只含部分分区（本轮有记录的），需与上次已提交 offset 合并。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCoordinator.java` (+24/-0 lines)

**修改目的**：验证 offset 合并逻辑。

**工作逻辑**：
- `testCoordinatorCommittedOffsetMerging`：
  - 先 `table.newAppend().appendFile(...).set(OFFSETS_SNAPSHOT_PROP, "{\"1\":7}").commit()`，模拟上次提交只含 partition 1 的 offset 7。
  - 断言快照数为 1，summary 含 `OFFSETS_SNAPSHOT_PROP={"\"1\":7"}`。
  - 调用 `coordinatorTest(...)` 触发一次含 partition 0 数据的提交（mock 的 controlTopicOffsets 会返回 `{"0":3}` 之类）。
  - 断言快照数为 2，新快照 summary 的 `OFFSETS_SNAPSHOT_PROP` 为 `{"0":3,"1":7}`——partition 1 的进度 7 被保留，partition 0 的新进度 3 被加入。

## 总结

该提交修复了 Kafka Connect Coordinator 在写快照 offset 时可能丢失分区进度的问题：当某分区本轮无记录时，原实现会用本轮的 `controlTopicOffsets()`（部分分区）覆盖整个 offsets JSON，丢失其他分区的已提交进度。修改后把本轮 offset 与该表上次已提交 offset 合并（取较大值），确保快照始终记录所有分区的最新进度。配套测试验证了"上次有 partition 1、本轮有 partition 0"的合并场景。这是 Kafka Connect Exactly-Once 与崩溃恢复正确性的重要修复。
