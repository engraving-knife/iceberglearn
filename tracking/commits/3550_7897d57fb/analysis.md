# 提交 3550：Flink: Add passthroughRecords option to DynamicIcebergSink (#15433)

## 提交信息

- **序号**：3550 / 4088
- **哈希**：7897d57fbe6c9c52de93195debbd24ebb41fc163
- **短哈希**：7897d57fb
- **日期**：2026-04-17 11:37:33 +0200
- **作者**：Han You
- **提交说明**：Flink: Add passthroughRecords option to DynamicIcebergSink (#15433)
- **PR/Issue**：#15433

## 总体目的

`DynamicIcebergSink` 是 Iceberg Flink 的动态 sink，支持将来自不同表/schema/分区的记录动态路由到对应的 Iceberg 表。此前的设计中，所有记录都会经过 distribution（HASH 或 NONE 模式）进行 shuffle/rebalance，从 processor 流向 writer。对于高吞吐场景（每个分区数据量都很大），序列化和网络 shuffle 的开销成为瓶颈。

本提交为 `DynamicIcebergSink` 引入「forward（passthrough）模式」：当 `DynamicRecord` 的 `distributionMode` 为 `null` 时，记录跳过 distribution，通过 Flink 的 forward edge 直接从 processor 流向 writer，启用 operator chaining，避免 shuffle 开销。这适用于高吞吐管道，用户需自行在上游保证数据已正确分布。

forward 记录和普通 shuffle 记录可在同一管道中混合使用：processor 将它们分别路由到两个输出（shuffle sink 和 forward sink），两条路径最终汇入同一个 pre-commit aggregator 和 committer，保证原子提交。

## 如何达成设计目的

核心设计：
1. **DynamicRecord 新增 forward 语义**：`distributionMode` 改为 `@Nullable`，`null` 表示 forward 模式。新增一个不含 `distributionMode` 参数的构造函数，默认 forward。
2. **DynamicRecordProcessor 路由分流**：新增 `DYNAMIC_FORWARD_STREAM` side output。处理记录时判断 `isForward = distributionMode == null`。forward 记录始终强制立即更新表元数据（因为没有专门的 table-update operator，避免额外 shuffle），然后输出到 forward side output；普通记录走原有逻辑（immediateUpdate 或 side output 更新）。
3. **DynamicIcebergSink 双路径拓扑**：
   - forward 路径：forward side output → `ForwardWriterSink`（用 `SinkWriterOperatorFactory` 创建，可被 chaining）→ forwardWriteResults
   - shuffle 路径：table-update side output + 主输出 → sinkTo（原有逻辑）
   - 在 `addPreCommitTopology` 中将 `writeResults.union(forwardWriteResults)` 合并后进入 aggregator
4. **ForwardWriterSink**：轻量级 Sink，只负责写不负责 commit（commit 由主 sink 处理），实现 `SupportsCommitter` 以便 `SinkWriterOperator` 发出 committables。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecord.java` (+19/-7 lines)

**修改目的**：支持 forward 模式（distributionMode 为 null）。

**工作逻辑**：
- `distributionMode` 字段加 `@Nullable` 注解
- 新增构造函数重载（不含 distributionMode），委托给主构造函数并传 `null`：
```java
public DynamicRecord(TableIdentifier tableIdentifier, String branch, Schema schema,
    RowData rowData, PartitionSpec partitionSpec) {
  this(tableIdentifier, branch, schema, rowData, partitionSpec, null, -1);
}
```
- 主构造函数 Javadoc 说明 `distributionMode` 为 null 表示 forward（不 shuffle）

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (+39/-19 lines)

**修改目的**：processor 将 forward 记录路由到 side output，并强制立即更新。

**工作逻辑**：
1. 新增 `DYNAMIC_FORWARD_STREAM` OutputTag 常量和 `forwardStream` 字段
2. `open()` 中始终创建 `updater`（forward 记录需要强制立即更新）和 `forwardStream` tag；只有 `!immediateUpdate` 时才创建 `updateStream`
3. `collect()` 中判断 `isForward = data.distributionMode() == null`，计算 `needsUpdate`。需要更新时：forward 或 immediateUpdate 走立即更新，否则走 update side output
4. `emit()` 方法重构：新增 `boolean forward` 参数。forward 时 writerKey 设为 -1（不使用），记录通过 `context.output(forwardStream, record)` 输出到 side output；非 forward 时走 `collector.collect(record)`

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+110/-26 lines)

**修改目的**：构建双路径拓扑，合并 forward 和 shuffle 写入结果。

**工作逻辑**：
1. 新增字段 `forwardWriteResults`（transient DataStream），由 builder 在 `build()` 中注入
2. 构造函数新增 `forwardWriteResults` 参数
3. `addPreCommitTopology()` 中 `writeResults.union(forwardWriteResults)` 合并两条路径的写入结果后进入 aggregator
4. 新增内部类 `ForwardWriterSink`：实现 `Sink<DynamicRecordInternal>` 和 `SupportsCommitter<DynamicWriteResult>`，`createWriter` 返回 `DynamicWriter`，`createCommitter` 抛 `UnsupportedOperationException`（commit 由主 sink 处理）
5. `Builder.build()` 重构：从 forward side output 创建 `ForwardWriterSink` 的 transform，生成 `forwardWriteResults`，注入到 `DynamicIcebergSink`
6. `Builder.append()` 重构：创建 `sideOutputType`，先 `build(converted, sideOutputType)`，shuffle 路径由 table-update side output + 主输出 union 后 `sinkTo(sink)`

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+155/-10 lines)

**修改目的**：测试 forward 模式的端到端行为。

**工作逻辑**：新增多个测试用例验证 forward 记录的写入、与 shuffle 记录混合、表更新行为等。

### `docs/docs/flink-writes.md` (+24/-2 lines)

**修改目的**：文档化 DistributionMode 三种模式（NONE、HASH、null）和 Forward 模式。

**工作逻辑**：
- 更新 DynamicRecord 表格中 DistributionMode 描述，说明 null 表示不 shuffle
- 新增「Distribution Modes」章节，用表格说明三种模式行为
- 新增「Forward Mode」章节，解释 forward 模式的适用场景、双路径拓扑、与 shuffle 记录混合
- 新增 warning 提示：forward 路径中 schema 变更始终立即应用可能导致冲突提交；forward 跳过 distribution，用户需自行保证上游数据分布

## 总结

本提交为 `DynamicIcebergSink` 引入 forward（passthrough）模式，允许 `DynamicRecord` 的 `distributionMode` 为 null 时跳过 distribution shuffle，通过 Flink forward edge 直接从 processor 流向 writer，启用 operator chaining，适用于高吞吐场景。processor 将 forward 和 shuffle 记录分别路由到两个输出，两条路径汇入同一个 aggregator/committer 保证原子提交。forward 记录强制立即更新表元数据。配有测试和文档。这是 DynamicIcebergSink 的一个重要性能优化特性。
