# 提交 2847：Kafka Connect: validate offsets for refreshed table state on commit (#14510)

## 提交信息

- **序号**：2847 / 4088
- **哈希**：6b28b0edc88a533476ceabcf0c21028506702efa
- **短哈希**：6b28b0edc
- **日期**：2025-11-07 16:14:57 -0800
- **作者**：Daniel Weeks
- **提交说明**：Kafka Connect: validate offsets for refreshed table state on commit (#14510)
- **PR/Issue**：#14510

## 总体目的

Kafka Connect 的 Coordinator 负责收集各 task 的数据文件并提交到 Iceberg 表。在提交时，Coordinator 会记录 Kafka 的消费偏移量（offsets）到快照的 summary 属性中，用于追踪提交进度。然而，在 commit 流程中表元数据会被刷新（refresh）以获取最新状态，此时如果另一个独立的提交已经更新了表的偏移量，Coordinator 的提交就会基于过期的偏移量状态进行，导致偏移量信息丢失或不一致。

本提交的目的就是在 commit 时增加偏移量校验机制：利用 Iceberg 新引入的 `SnapshotAncestryValidator`（来自 PR #14509）接口，在提交时检查刷新后的表快照祖先链中记录的最后提交偏移量是否与 Coordinator 期望提交的偏移量一致。如果不一致，说明在提交过程中发生了其他提交，此时应拒绝本次提交（抛出 `ValidationException`），从而避免基于过期状态的提交覆盖更新偏移量。

## 如何达成设计目的

整体设计思路是在 Coordinator 的 `commit` 方法中，对 `AppendFiles` 和 `RowDelta` 两种提交操作都通过 `validateWith()` 注册一个自定义的 `SnapshotAncestryValidator`。该验证器在 Iceberg 提交流程中、表元数据刷新之后被调用，接收刷新后的快照祖先链，从中查找最近一个包含偏移量信息的快照，将其偏移量与期望偏移量比较。如果不匹配，验证器返回 false 并提供错误信息，Iceberg 会据此拒绝提交。

同时，本提交还对偏移量查找逻辑进行了重构：原先 `lastCommittedOffsetsForTable` 方法手动遍历快照父链查找偏移量，现在改为使用 `SnapshotUtil.ancestorsOf` 获取祖先链，并抽取了 `lastCommittedOffsets` 和 `parseOffsets` 两个可复用方法，使代码更清晰且验证器与原有逻辑共享同一套偏移量解析逻辑。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+77/-18 lines)

**修改目的**：在提交操作中增加偏移量校验，防止基于过期偏移量状态的提交；重构偏移量查找逻辑。

**工作逻辑**：

1. **注册验证器**：在 `commit` 方法中，对无删除文件的 append 分支和有删除文件的 RowDelta 分支，分别添加 `validateWith(offsetValidator(tableIdentifier, committedOffsets))`：

```java
AppendFiles appendOp =
    table.newAppend().validateWith(offsetValidator(tableIdentifier, committedOffsets));
```
```java
RowDelta deltaOp =
    table.newRowDelta().validateWith(offsetValidator(tableIdentifier, committedOffsets));
```

2. **新增 `offsetValidator` 方法**：创建一个匿名 `SnapshotAncestryValidator` 实现类，在 `apply` 方法中接收刷新后的快照祖先链，调用 `lastCommittedOffsets` 提取最新提交偏移量，并与期望偏移量比较；`errorMessage` 方法返回包含表标识、期望偏移量和实际提交偏移量的错误信息。

```java
private SnapshotAncestryValidator offsetValidator(
    TableIdentifier tableIdentifier, Map<Integer, Long> expectedOffsets) {
  return new SnapshotAncestryValidator() {
    private Map<Integer, Long> lastCommittedOffsets;
    @Override
    public Boolean apply(Iterable<Snapshot> baseSnapshots) {
      lastCommittedOffsets = lastCommittedOffsets(baseSnapshots);
      return expectedOffsets.equals(lastCommittedOffsets);
    }
    @Override
    public String errorMessage() {
      return String.format(
          "Cannot commit to %s, stale offsets: Expected: %s Committed: %s",
          tableIdentifier, expectedOffsets, lastCommittedOffsets);
    }
  };
}
```

3. **重构偏移量查找**：`lastCommittedOffsetsForTable` 改用 `SnapshotUtil.ancestorsOf` 获取祖先链，委托给新的 `lastCommittedOffsets` 方法。后者通过 Streams 流式处理，过滤出第一个包含偏移量属性的快照并解析其偏移量。`parseOffsets` 方法被抽取出来用于 JSON 解析。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/ChannelTestBase.java` (+3/-1 lines)

**修改目的**：将 catalog 改为 spy 对象，以支持在测试中模拟 `loadTable` 的行为。

**工作逻辑**：在 `before` 方法中，将 `catalog = initInMemoryCatalog()` 改为 `catalog = spy(initInMemoryCatalog())`，使测试可以对 catalog 的方法调用进行桩接（stubbing），特别是 `loadTable` 方法。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCoordinator.java` (+41/-0 lines)

**修改目的**：新增测试验证当提交过程中发生独立提交时，Coordinator 的提交会被拒绝。

**工作逻辑**：`testCoordinatorCommittedOffsetValidation` 测试首先设置初始偏移量 `{"0":1}`，然后通过 mock `catalog.loadTable` 让 Coordinator 在第一次加载时获取旧状态（frozenTable），模拟提交过程中的表刷新。在 Coordinator 提交前，独立地将偏移量更新为 `{"0":7}`。然后触发 Coordinator 提交，断言表快照数量仍为 2（未被新增），偏移量仍为 `{"0":7}`（Coordinator 的提交被拒绝，未覆盖新偏移量）。

## 总结

本提交为 Kafka Connect Coordinator 增加了提交时的偏移量校验机制，利用 Iceberg 新引入的 `SnapshotAncestryValidator` 接口，在表元数据刷新后检查偏移量是否过期，防止并发提交场景下基于过期状态的提交覆盖最新的偏移量信息。同时重构了偏移量查找逻辑使其更清晰可复用，并新增了完整的测试覆盖该并发场景。这是一个重要的正确性修复，确保了 Kafka Connect 在并发写入场景下偏移量追踪的可靠性。
